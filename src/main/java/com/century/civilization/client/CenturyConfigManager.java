package com.century.civilization.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
public class CenturyConfigManager {
    private static final Logger LOGGER = LoggerFactory.getLogger("century-config");
    private static final String GITHUB_MODS_BASE_URL = "https://github.com/Success009/century-civilization-releases/raw/main/mods/";

    public static class ModEntry {
        public final String id;
        public final String name;
        public final String category;
        public final String jarName;
        public final boolean defaultState;
        public final String description;
        public final List<String> dependencies;
        public boolean enabled;

        public ModEntry(String id, String name, String category, String jarName, boolean defaultState, String description, List<String> dependencies) {
            this.id = id;
            this.name = name;
            this.category = category;
            this.jarName = jarName;
            this.defaultState = defaultState;
            this.description = description;
            this.dependencies = dependencies;
            this.enabled = defaultState;
        }
    }

    public static class DependencyMod {
        public final String id;
        public final String jarName;

        public DependencyMod(String id, String jarName) {
            this.id = id;
            this.jarName = jarName;
        }
    }

    private static final List<ModEntry> MOD_ENTRIES = new ArrayList<>();
    private static final Map<String, DependencyMod> DEPENDENCIES = new HashMap<>();
    private static final Map<String, Boolean> USER_TOGGLES = new ConcurrentHashMap<>();
    private static boolean restartRequired = false;
    private static boolean initialized = false;

    // Progress tracking for UI animation
    private static volatile String currentDownloadName = "";
    private static volatile float downloadProgress = 0.0f; // 0.0 to 1.0
    private static volatile boolean isDownloading = false;

    static {
        // --- Recommended / Native (Enabled by default) ---
        MOD_ENTRIES.add(new ModEntry("sodium", "Sodium", "Recommended / Native", "sodium-fabric-0.9.1+mc26.2.jar", true, "Rendering engine", Collections.emptyList()));
        MOD_ENTRIES.add(new ModEntry("bobby", "Extended Render (Bobby)", "Recommended / Native", "bobby-5.2.15+mc26.2.jar", true, "Render distance extension", List.of("cloth-config")));

        // --- Additional (Disabled by default) ---
        MOD_ENTRIES.add(new ModEntry("voicechat", "Voice Chat", "Additional", "voicechat-fabric-2.6.21+26.2.jar", false, "Proximity voice chat", Collections.emptyList()));
        MOD_ENTRIES.add(new ModEntry("zoomify", "Zoomify", "Additional", "zoomify-2.16.1+26.2.jar", false, "Camera zoom", List.of("cloth-config", "yacl", "kotlin")));
        MOD_ENTRIES.add(new ModEntry("entityculling", "Entity Culling", "Additional", "entityculling-fabric-1.10.5-mc26.2.jar", false, "Entity culling", Collections.emptyList()));
        MOD_ENTRIES.add(new ModEntry("ferritecore", "FerriteCore", "Additional", "ferritecore-9.0.0-fabric.jar", false, "Memory optimization", Collections.emptyList()));
        MOD_ENTRIES.add(new ModEntry("lithium", "Lithium", "Additional", "lithium-fabric-0.25.2+mc26.2.jar", false, "Physics optimizer", Collections.emptyList()));
        MOD_ENTRIES.add(new ModEntry("moreculling", "More Culling", "Additional", "moreculling-fabric-26.2-1.8.1.jar", false, "Block face culling", Collections.emptyList()));
        MOD_ENTRIES.add(new ModEntry("litematica", "Litematica", "Additional", "litematica-fabric-26.2-0.28.4.jar", false, "Schematic viewer", List.of("malilib")));
        MOD_ENTRIES.add(new ModEntry("voxy", "Voxy", "Additional", "voxy-0.2.18-beta.jar", false, "Voxel LOD rendering engine", List.of("sodium")));
        DEPENDENCIES.put("cloth-config", new DependencyMod("cloth-config", "cloth-config-26.2.155.jar"));
        DEPENDENCIES.put("yacl", new DependencyMod("yacl", "yet_another_config_lib_v3-3.9.5+26.2-fabric.jar"));
        DEPENDENCIES.put("kotlin", new DependencyMod("kotlin", "fabric-language-kotlin-1.13.13+kotlin.2.4.10.jar"));
        DEPENDENCIES.put("malilib", new DependencyMod("malilib", "malilib-fabric-26.2-0.29.3.jar"));
    }

    public static List<ModEntry> getModEntries() {
        initIfNeeded();
        return MOD_ENTRIES;
    }

    public static boolean isRestartRequired() {
        return restartRequired;
    }

    public static void setRestartRequired(boolean required) {
        restartRequired = required;
    }

    public static String getCurrentDownloadName() {
        return currentDownloadName;
    }

    public static float getDownloadProgress() {
        return downloadProgress;
    }

    public static boolean isDownloading() {
        return isDownloading;
    }

    public static synchronized void initIfNeeded() {
        if (initialized) return;
        initialized = true;
        loadConfig();
    }

    private static Path resolveModsDir() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.gameDirectory != null) {
                return mc.gameDirectory.toPath().resolve("mods");
            }
        } catch (Exception e) {}
        return Paths.get("mods");
    }

    private static Path resolveConfigFile() {
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.gameDirectory != null) {
                return mc.gameDirectory.toPath().resolve("century_config.json");
            }
        } catch (Exception e) {}
        return Paths.get("century_config.json");
    }

    public static void loadConfig() {
        restartRequired = false; // Reset restartRequired on boot since game has restarted
        try {
            Path configFile = resolveConfigFile();
            if (Files.exists(configFile)) {
                String jsonStr = Files.readString(configFile, StandardCharsets.UTF_8);
                JsonObject json = JsonParser.parseString(jsonStr).getAsJsonObject();
                if (json.has("toggles")) {
                    JsonObject toggles = json.getAsJsonObject("toggles");
                    for (ModEntry entry : MOD_ENTRIES) {
                        if (toggles.has(entry.id)) {
                            entry.enabled = toggles.get(entry.id).getAsBoolean();
                            USER_TOGGLES.put(entry.id, entry.enabled);
                        } else {
                            USER_TOGGLES.put(entry.id, entry.defaultState);
                        }
                    }
                }
            } else {
                for (ModEntry entry : MOD_ENTRIES) {
                    USER_TOGGLES.put(entry.id, entry.defaultState);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load century_config.json", e);
        }
    }

    public static void saveConfig() {
        try {
            JsonObject json = new JsonObject();
            json.addProperty("restartRequired", restartRequired);
            JsonObject toggles = new JsonObject();
            for (ModEntry entry : MOD_ENTRIES) {
                toggles.addProperty(entry.id, entry.enabled);
            }
            json.add("toggles", toggles);

            Path configFile = resolveConfigFile();
            Files.writeString(configFile, json.toString(), StandardCharsets.UTF_8);
            LOGGER.info("Successfully saved Century config.");
        } catch (Exception e) {
            LOGGER.error("Failed to save century_config.json", e);
        }
    }

    public interface DownloadCallback {
        void onProgress(String modName, float progress);
        void onComplete();
        void onError(String error);
    }

    public static void applyAndSyncAsync(DownloadCallback callback) {
        Thread thread = new Thread(() -> {
            try {
                Path modsDir = resolveModsDir();
                Path bunkerDir = modsDir.resolve("Bunker");
                if (!Files.exists(bunkerDir)) {
                    Files.createDirectories(bunkerDir);
                }

                Set<String> activeDependencyIds = new HashSet<>();
                for (ModEntry entry : MOD_ENTRIES) {
                    if (entry.enabled) {
                        activeDependencyIds.addAll(entry.dependencies);
                    }
                }

                List<String> filesToDownload = new ArrayList<>();
                boolean changesMade = false;

                for (ModEntry entry : MOD_ENTRIES) {
                    Path modPathInMods = modsDir.resolve(entry.jarName);
                    Path modPathInBunker = bunkerDir.resolve(entry.jarName);

                    if (entry.enabled) {
                        if (Files.exists(modPathInBunker) && !Files.exists(modPathInMods)) {
                            Files.move(modPathInBunker, modPathInMods, StandardCopyOption.REPLACE_EXISTING);
                            changesMade = true;
                            LOGGER.info("Moved " + entry.jarName + " from Bunker to mods folder.");
                        } else if (!Files.exists(modPathInMods)) {
                            filesToDownload.add(entry.jarName);
                        }
                    } else {
                        if (Files.exists(modPathInMods)) {
                            Files.move(modPathInMods, modPathInBunker, StandardCopyOption.REPLACE_EXISTING);
                            changesMade = true;
                            LOGGER.info("Moved " + entry.jarName + " from mods folder to Bunker.");
                        }
                    }
                }

                for (Map.Entry<String, DependencyMod> depEntry : DEPENDENCIES.entrySet()) {
                    String depId = depEntry.getKey();
                    DependencyMod dep = depEntry.getValue();
                    Path depInMods = modsDir.resolve(dep.jarName);
                    Path depInBunker = bunkerDir.resolve(dep.jarName);

                    boolean shouldBeActive = activeDependencyIds.contains(depId);
                    if (shouldBeActive) {
                        if (Files.exists(depInBunker) && !Files.exists(depInMods)) {
                            Files.move(depInBunker, depInMods, StandardCopyOption.REPLACE_EXISTING);
                            changesMade = true;
                        } else if (!Files.exists(depInMods)) {
                            filesToDownload.add(dep.jarName);
                        }
                    } else {
                        if (Files.exists(depInMods)) {
                            Files.move(depInMods, depInBunker, StandardCopyOption.REPLACE_EXISTING);
                            changesMade = true;
                        }
                    }
                }

                if (!filesToDownload.isEmpty()) {
                    changesMade = true;
                    isDownloading = true;
                    int total = filesToDownload.size();
                    for (int i = 0; i < total; i++) {
                        String jarName = filesToDownload.get(i);
                        currentDownloadName = jarName;
                        downloadProgress = (float) i / total;
                        if (callback != null) callback.onProgress(jarName, downloadProgress);

                        Path targetFile = modsDir.resolve(jarName);
                        downloadModFile(jarName, targetFile);

                        downloadProgress = (float) (i + 1) / total;
                        if (callback != null) callback.onProgress(jarName, downloadProgress);
                    }
                    isDownloading = false;
                }

                if (changesMade) {
                    restartRequired = true;
                }
                saveConfig();

                if (callback != null) {
                    callback.onComplete();
                }

            } catch (Exception e) {
                isDownloading = false;
                LOGGER.error("Failed applying mod configuration", e);
                if (callback != null) {
                    callback.onError(e.getMessage());
                }
            }
        });
        thread.setDaemon(true);
        thread.start();
    }
    private static void downloadModFile(String jarName, Path targetFile) throws Exception {
        String urlStr = GITHUB_MODS_BASE_URL + jarName;
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(urlStr))
                .header("User-Agent", "CenturyCivilization-Client")
                .GET()
                .build();

        HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        if (response.statusCode() != 200) {
            throw new java.io.IOException("HTTP error " + response.statusCode() + " downloading " + jarName + " from " + urlStr);
        }

        try (InputStream in = new BufferedInputStream(response.body());
             FileOutputStream out = new FileOutputStream(targetFile.toFile())) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
            }
        }
    }
}
