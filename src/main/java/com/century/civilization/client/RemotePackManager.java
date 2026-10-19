package com.century.civilization.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackMetadataResources;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackCompatibility;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.world.flag.FeatureFlagSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

public class RemotePackManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("century-pack-manager");
    private static final String GITHUB_RESOURCEPACKS_URL = "https://github.com/Success009/century-civilization-releases/raw/main/resourcepacks/";

    public static final PackSource CENTURY_PACK_SOURCE = PackSource.create(
            name -> Component.literal("").append(name),
            false
    );

    public static class RemotePackEntry {
        public final String id;
        public final String title;
        public final String fileName;
        public final String description;

        public RemotePackEntry(String title, String fileName, String description) {
            this.id = "file/" + fileName;
            this.title = title;
            this.fileName = fileName;
            this.description = description;
        }
    }

    private static final List<RemotePackEntry> REMOTE_PACKS = new ArrayList<>();
    private static final Map<String, Object> DOWNLOAD_LOCKS = new ConcurrentHashMap<>();

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();

    static {
        REMOTE_PACKS.add(new RemotePackEntry(
                "Re-covered",
                "Re-covered.zip",
                "New book covers for enchanted books"
        ));
        REMOTE_PACKS.add(new RemotePackEntry(
                "Colourful Tropical Fish",
                "Colourful Tropical Fish.zip",
                "Colourful Tropical Fish Resourcepack"
        ));
        REMOTE_PACKS.add(new RemotePackEntry(
                "Redstone Tweaks",
                "Redstone Tweaks 2.5.6.zip",
                "v2.5.6 by RexxStone with RPO settings by KikuGie"
        ));
    }

    public static List<RemotePackEntry> getRemotePacks() {
        return Collections.unmodifiableList(REMOTE_PACKS);
    }

    public static RemotePackEntry getEntryById(String id) {
        if (id == null) return null;
        for (RemotePackEntry entry : REMOTE_PACKS) {
            if (entry.id.equals(id) || entry.id.equalsIgnoreCase(id)
                    || entry.fileName.equalsIgnoreCase(id)
                    || ("century:" + entry.fileName).equalsIgnoreCase(id)) {
                return entry;
            }
        }
        return null;
    }

    public static Path resolveResourcePacksDir() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.gameDirectory != null) {
                Path dir = mc.gameDirectory.toPath().resolve("resourcepacks");
                if (!Files.exists(dir)) {
                    Files.createDirectories(dir);
                }
                return dir;
            }
        } catch (Exception ignored) {}
        return Paths.get("resourcepacks");
    }

    public static Path resolvePackPath(String fileName) {
        return resolveResourcePacksDir().resolve(fileName);
    }

    public static boolean isPackDownloadedAndValid(String fileName) {
        Path path = resolvePackPath(fileName);
        if (!Files.exists(path) || Files.isDirectory(path)) {
            return false;
        }
        try {
            if (Files.size(path) < 100) {
                return false;
            }
            try (ZipFile zip = new ZipFile(path.toFile())) {
                return zip.size() > 0;
            }
        } catch (Exception e) {
            return false;
        }
    }

    public static Path ensureDownloaded(RemotePackEntry entry) {
        if (entry == null) return null;
        Object lock = DOWNLOAD_LOCKS.computeIfAbsent(entry.fileName, k -> new Object());
        synchronized (lock) {
            Path targetFile = resolvePackPath(entry.fileName);
            if (isPackDownloadedAndValid(entry.fileName)) {
                return targetFile;
            }

            LOGGER.info("[RemotePackManager] Downloading resource pack: " + entry.title + " (" + entry.fileName + ")...");
            try {
                Path dir = targetFile.getParent();
                if (!Files.exists(dir)) {
                    Files.createDirectories(dir);
                }

                String encodedName = URLEncoder.encode(entry.fileName, StandardCharsets.UTF_8).replace("+", "%20");
                String urlStr = GITHUB_RESOURCEPACKS_URL + encodedName;

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(urlStr))
                        .header("User-Agent", "CenturyCivilization-Client")
                        .GET()
                        .build();

                HttpResponse<InputStream> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
                if (response.statusCode() != 200) {
                    LOGGER.error("[RemotePackManager] Failed to download " + entry.fileName + ", HTTP status " + response.statusCode());
                    return targetFile;
                }

                Path tempFile = dir.resolve(entry.fileName + ".download.tmp");
                try (InputStream in = new BufferedInputStream(response.body());
                     FileOutputStream out = new FileOutputStream(tempFile.toFile())) {
                    byte[] buffer = new byte[8192];
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        out.write(buffer, 0, read);
                    }
                }

                // Verify zip before promoting
                try (ZipFile zip = new ZipFile(tempFile.toFile())) {
                    if (zip.size() <= 0) {
                        throw new IOException("Downloaded zip file is empty");
                    }
                }

                Files.move(tempFile, targetFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                LOGGER.info("[RemotePackManager] Successfully downloaded and verified: " + entry.fileName);
                return targetFile;
            } catch (Exception e) {
                LOGGER.error("[RemotePackManager] Error downloading " + entry.fileName, e);
                return targetFile;
            }
        }
    }

    public static void onPackSelected(String id) {
        RemotePackEntry entry = getEntryById(id);
        if (entry != null) {
            ensureDownloaded(entry);
        }
    }

    public static void checkAndPreloadSelectedPacks() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.options != null && mc.options.resourcePacks != null) {
                for (String selectedId : mc.options.resourcePacks) {
                    RemotePackEntry entry = getEntryById(selectedId);
                    if (entry != null && !isPackDownloadedAndValid(entry.fileName)) {
                        ensureDownloaded(entry);
                    }
                }
            }
        } catch (Throwable t) {
            LOGGER.warn("[RemotePackManager] Failed to check options.resourcePacks on boot: " + t.getMessage());
        }
    }

    public static Pack createPlaceholderPack(RemotePackEntry entry) {
        PackLocationInfo location = new PackLocationInfo(
                entry.id,
                Component.literal(entry.title),
                CENTURY_PACK_SOURCE,
                Optional.empty()
        );

        Pack.ResourcesSupplier supplier = new Pack.ResourcesSupplier() {
            @Override
            public PackMetadataResources openMetadata(PackLocationInfo loc) {
                Path path = resolvePackPath(entry.fileName);
                if (isPackDownloadedAndValid(entry.fileName)) {
                    return new FilePackResources.FileResourcesSupplier(path).openMetadata(loc);
                }
                return new PackMetadataResources() {
                    @Override
                    public PackLocationInfo location() {
                        return loc;
                    }
                    @Override
                    public IoSupplier<InputStream> getRootResource(String... paths) {
                        return null;
                    }
                    @Override
                    public <T> T getMetadataSection(MetadataSectionType<T> type) {
                        return null;
                    }
                    @Override
                    public void close() {}
                };
            }

            @Override
            public Stream<PackResources> openResources(PackLocationInfo loc, Pack.Metadata meta) {
                Path path = ensureDownloaded(entry);
                return new FilePackResources.FileResourcesSupplier(path).openResources(loc, meta);
            }
        };

        Pack.Metadata metadata = new Pack.Metadata(
                Component.literal(entry.description),
                PackCompatibility.COMPATIBLE,
                FeatureFlagSet.of(),
                List.of()
        );

        PackSelectionConfig selectionConfig = new PackSelectionConfig(
                false,
                Pack.Position.TOP,
                false
        );

        return new Pack(location, supplier, metadata, selectionConfig);
    }
}
