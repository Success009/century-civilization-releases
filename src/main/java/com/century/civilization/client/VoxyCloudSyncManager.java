package com.century.civilization.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class VoxyCloudSyncManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("century-voxy-sync");
    private static final String API_BASE_URL = "https://century.success0.com.np/api/voxy";
    private static volatile boolean syncInProgress = false;
    private static volatile long lastSyncTimestamp = 0L;

    public static boolean isVoxyActive() {
        return FabricLoader.getInstance().isModLoaded("voxy");
    }

    public static void init() {
        if (!isVoxyActive()) {
            LOGGER.info("[VOXY-SYNC] Voxy mod is not loaded in current session. Background sync disabled.");
            return;
        }

        LOGGER.info("[VOXY-SYNC] Voxy is active. Initializing zero-overhead background cloud sync...");
        downloadSharedPackageAsync();
    }

    private static Path getVoxySavesDirectory() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.gameDirectory != null) {
                return mc.gameDirectory.toPath().resolve(".voxy").resolve("saves");
            }
        } catch (Throwable t) {
            // fallback
        }
        return Paths.get(".voxy", "saves");
    }

    public static void downloadSharedPackageAsync() {
        if (!isVoxyActive() || syncInProgress) {
            return;
        }

        Thread thread = new Thread(() -> {
            syncInProgress = true;
            try {
                Path savesDir = getVoxySavesDirectory();
                if (!Files.exists(savesDir)) {
                    Files.createDirectories(savesDir);
                }

                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(12))
                        .followRedirects(HttpClient.Redirect.ALWAYS)
                        .build();

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(API_BASE_URL + "/manifest"))
                        .header("User-Agent", "Century-VoxySync")
                        .GET()
                        .build();

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() == 200) {
                    JsonObject manifest = JsonParser.parseString(response.body()).getAsJsonObject();
                    if (manifest.has("download_url") && manifest.has("version")) {
                        String downloadUrl = manifest.get("download_url").getAsString();
                        long remoteVersion = manifest.get("version").getAsLong();

                        Path localVersionFile = savesDir.resolve("voxy_cloud_version.txt");
                        long localVersion = 0L;
                        if (Files.exists(localVersionFile)) {
                            try {
                                localVersion = Long.parseLong(Files.readString(localVersionFile).trim());
                            } catch (Exception e) {
                                localVersion = 0L;
                            }
                        }

                        if (remoteVersion > localVersion) {
                            LOGGER.info("[VOXY-SYNC] Found updated world LOD package (v{} > v{}). Downloading...", remoteVersion, localVersion);
                            downloadAndExtractZip(client, downloadUrl, savesDir);
                            Files.writeString(localVersionFile, String.valueOf(remoteVersion));
                            lastSyncTimestamp = System.currentTimeMillis();
                            LOGGER.info("[VOXY-SYNC] World LOD data synchronized successfully.");
                        } else {
                            LOGGER.info("[VOXY-SYNC] Local Voxy LOD data is already up to date with cloud (v{}).", localVersion);
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.warn("[VOXY-SYNC] Manifest check skipped or server offline: " + e.getMessage());
            } finally {
                syncInProgress = false;
            }
        });
        thread.setName("Century-VoxyDownloadSync");
        thread.setDaemon(true);
        thread.start();
    }

    private static void downloadAndExtractZip(HttpClient client, String url, Path targetDir) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "Century-VoxySync")
                .GET()
                .build();

        HttpResponse<InputStream> res = client.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (res.statusCode() == 200) {
            try (ZipInputStream zis = new ZipInputStream(new BufferedInputStream(res.body()))) {
                ZipEntry entry;
                byte[] buffer = new byte[8192];
                while ((entry = zis.getNextEntry()) != null) {
                    if (entry.isDirectory()) {
                        continue;
                    }
                    Path outPath = targetDir.resolve(entry.getName()).normalize();
                    if (!outPath.startsWith(targetDir)) {
                        continue; // Protect against Zip Slip
                    }
                    if (outPath.getParent() != null) {
                        Files.createDirectories(outPath.getParent());
                    }
                    try (FileOutputStream fos = new FileOutputStream(outPath.toFile())) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
            }
        }
    }

    public static void onSessionEndAsync() {
        if (!isVoxyActive() || syncInProgress) {
            return;
        }

        Thread thread = new Thread(() -> {
            syncInProgress = true;
            try {
                Path savesDir = getVoxySavesDirectory();
                if (!Files.exists(savesDir)) {
                    return;
                }

                // Check if any storage files were updated during this gameplay session
                boolean hasNewData = false;
                try (var stream = Files.walk(savesDir)) {
                    hasNewData = stream.filter(Files::isRegularFile)
                            .anyMatch(p -> {
                                try {
                                    return Files.getLastModifiedTime(p).toMillis() > lastSyncTimestamp;
                                } catch (IOException e) {
                                    return false;
                                }
                            });
                }

                if (!hasNewData) {
                    LOGGER.info("[VOXY-SYNC] No new LOD data generated in this session. Upload skipped.");
                    return;
                }

                LOGGER.info("[VOXY-SYNC] Compressing updated Voxy LOD data for cloud backup...");
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                    byte[] buffer = new byte[8192];
                    try (var stream = Files.walk(savesDir)) {
                        for (Path file : (Iterable<Path>) stream.filter(Files::isRegularFile)::iterator) {
                            String relative = savesDir.relativize(file).toString();
                            if (relative.endsWith("voxy_cloud_version.txt") || relative.endsWith(".lock")) {
                                continue;
                            }
                            zos.putNextEntry(new ZipEntry(relative));
                            try (InputStream fis = new BufferedInputStream(Files.newInputStream(file))) {
                                int len;
                                while ((len = fis.read(buffer)) > 0) {
                                    zos.write(buffer, 0, len);
                                }
                            }
                            zos.closeEntry();
                        }
                    }
                }

                byte[] zipBytes = baos.toByteArray();
                if (zipBytes.length > 0) {
                    HttpClient client = HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(15))
                            .build();

                    HttpRequest uploadReq = HttpRequest.newBuilder()
                            .uri(URI.create(API_BASE_URL + "/upload"))
                            .header("Content-Type", "application/zip")
                            .header("User-Agent", "Century-VoxySync")
                            .header("X-Century-Token", CenturyModClient.getAuthToken())
                            .POST(HttpRequest.BodyPublishers.ofByteArray(zipBytes))
                            .build();

                    HttpResponse<String> resp = client.send(uploadReq, HttpResponse.BodyHandlers.ofString());
                    if (resp.statusCode() == 200) {
                        LOGGER.info("[VOXY-SYNC] Successfully pushed new Voxy LOD delta package to cloud.");
                        lastSyncTimestamp = System.currentTimeMillis();
                    }
                }
            } catch (Exception e) {
                LOGGER.warn("[VOXY-SYNC] Background delta upload skipped or offline: " + e.getMessage());
            } finally {
                syncInProgress = false;
            }
        });
        thread.setName("Century-VoxyUploadSync");
        thread.setDaemon(true);
        thread.start();
    }
}
