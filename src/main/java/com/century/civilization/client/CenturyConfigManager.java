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

    // Shared singleton HttpClient to prevent resource and descriptor leakage
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .build();

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
        MOD_ENTRIES.add(new ModEntry("sodium", "Sodium", "Recommended / Native", "sodium-fabric-0.9.2+mc26.3.jar", true, "Rendering engine", Collections.emptyList()));
        MOD_ENTRIES.add(new ModEntry("bobby", "Extended Render (Bobby)", "Recommended / Native", "bobby-5.2.16+mc26.3.jar", true, "Render distance extension", List.of("cloth-config")));
        MOD_ENTRIES.add(new ModEntry("pingdisplay", "Ping Display", "Recommended / Native", "ping-display-26.2.jar", true, "Render ping as milliseconds", Collections.emptyList()));
        MOD_ENTRIES.add(new ModEntry("shulkerboxtooltip", "Shulker Box Tooltip", "Recommended / Native", "shulkerboxtooltip-fabric-5.4.2+26.3.jar", true, "Shulker box contents preview", Collections.emptyList()));
        MOD_ENTRIES.add(new ModEntry("continuity", "Continuity", "Recommended / Native", "continuity-3.0.1+26.3.jar", true, "Connected textures support", Collections.emptyList()));

        // --- Additional (Disabled by default) ---
        MOD_ENTRIES.add(new ModEntry("voicechat", "Voice Chat", "Additional", "voicechat-fabric-2.6.24+26.3.jar", false, "Proximity voice chat", Collections.emptyList()));
        MOD_ENTRIES.add(new ModEntry("zoomify", "Zoomify", "Additional", "zoomify-2.17.0+mc26.3-universal.jar", false, "Camera zoom", List.of("cloth-config", "yacl", "kotlin")));
        MOD_ENTRIES.add(new ModEntry("entityculling", "Entity Culling", "Additional", "entityculling-fabric-1.11.3-mc26.3.jar", false, "Entity culling", Collections.emptyList()));
        MOD_ENTRIES.add(new ModEntry("ferritecore", "FerriteCore", "Additional", "ferritecore-9.0.0-fabric.jar", false, "Memory optimization", Collections.emptyList()));
        MOD_ENTRIES.add(new ModEntry("lithium", "Lithium", "Additional", "lithium-fabric-0.26.2+mc26.3.jar", false, "Physics optimizer", Collections.emptyList()));
        MOD_ENTRIES.add(new ModEntry("moreculling", "More Culling", "Additional", "moreculling-fabric-26.3-1.9.0.jar", false, "Block face culling", Collections.emptyList()));
        MOD_ENTRIES.add(new ModEntry("voxy", "Voxy", "Additional", "voxy-0.2.18-beta.jar", false, "Voxel LOD rendering engine", List.of("sodium")));
        MOD_ENTRIES.add(new ModEntry("iris", "Iris Shaders", "Additional", "iris-fabric-1.11.7+mc26.3.jar", false, "Shaders support", Collections.emptyList()));
        MOD_ENTRIES.add(new ModEntry("tlskincape", "TLauncher Skin Cape", "Additional", "tl_skin_cape_fabric_26.2-1.39.jar", false, "Custom skins and capes", Collections.emptyList()));
        MOD_ENTRIES.add(new ModEntry("voxyserver", "Voxy Server", "Additional", "VoxyServer-1.2.4-26.2.jar", false, "Voxy server-side LOD support", List.of("voxy")));
        MOD_ENTRIES.add(new ModEntry("doabarrelroll", "Do a Barrel Roll", "Additional", "do_a_barrel_roll-fabric-3.8.5+26.3.jar", false, "Flight simulator physics for elytra", List.of("cicada", "yacl")));
        MOD_ENTRIES.add(new ModEntry("scalablelux", "ScalableLux", "Additional", "ScalableLux-fabric-mc26.3-0.3.0-alpha.0.6-all.jar", false, "Light engine optimizer", Collections.emptyList()));
        MOD_ENTRIES.add(new ModEntry("krypton", "Krypton", "Additional", "krypton-0.3.2.jar", false, "Network stack optimizer", Collections.emptyList()));

        // --- Technical (Disabled by default) ---
        MOD_ENTRIES.add(new ModEntry("litematica", "Litematica", "Technical", "litematica-fabric-26.3-0.29.1.jar", false, "Schematic viewer", List.of("malilib")));
        MOD_ENTRIES.add(new ModEntry("syncmatica", "Syncmatica", "Technical", "syncmatica-fabric-26.3-0.3.20.jar", false, "Shared schematics", List.of("malilib")));
        MOD_ENTRIES.add(new ModEntry("minihud", "MiniHUD", "Technical", "minihud-fabric-26.3-0.41.2.jar", false, "F3 info overlays", List.of("malilib")));
        MOD_ENTRIES.add(new ModEntry("itemscroller", "Item Scroller", "Technical", "itemscroller-fabric-26.3-0.33.1.jar", false, "Inventory scrolling", List.of("malilib")));
        MOD_ENTRIES.add(new ModEntry("tweakermore", "TweakerMore", "Technical", "tweakermore-v3.33.3-beta.1-mc26.3.jar", false, "Technical tweaks", List.of("malilib")));
        MOD_ENTRIES.add(new ModEntry("devtools", "Dev Tweaks", "Technical", "devtools-without-debug.jar", false, "Developer utilities", Collections.emptyList()));
        MOD_ENTRIES.add(new ModEntry("xaerominimap", "Xaero's Minimap", "Technical", "xaerominimap-fabric-26.3-26.6.0.jar", false, "Minimap & radar", Collections.emptyList()));
        MOD_ENTRIES.add(new ModEntry("tweakeroo", "Tweakeroo", "Technical", "tweakeroo-fabric-26.3-0.30.1.jar", false, "Configurable gameplay tweaks", List.of("malilib")));

        DEPENDENCIES.put("cicada", new DependencyMod("cicada", "cicada-lib-0.15.3+26.3.jar"));
        DEPENDENCIES.put("cloth-config", new DependencyMod("cloth-config", "cloth-config-fabric-26.3.159.jar"));
        DEPENDENCIES.put("yacl", new DependencyMod("yacl", "yet_another_config_lib_v3-3.9.7+26.3-fabric.jar"));
        DEPENDENCIES.put("kotlin", new DependencyMod("kotlin", "fabric-language-kotlin-1.14.2+kotlin.2.4.21.jar"));
        DEPENDENCIES.put("malilib", new DependencyMod("malilib", "malilib-fabric-26.3-0.30.2.jar"));
    }

    public static List<String> getCategories() {
        return List.of("Recommended", "Additional", "Technical");
    }

    public static List<ModEntry> getModsByCategory(String category) {
        initIfNeeded();
        List<ModEntry> list = new ArrayList<>();
        for (ModEntry e : MOD_ENTRIES) {
            if ("Recommended".equalsIgnoreCase(category) && e.category.startsWith("Recommended")) {
                list.add(e);
            } else if (e.category.equalsIgnoreCase(category)) {
                list.add(e);
            }
        }
        return list;
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
            Path modsDir = resolveModsDir();
            Path bunkerDir = modsDir.resolve("Bunker");
            if (Files.exists(modsDir)) {
                File[] disabledFiles = modsDir.toFile().listFiles((d, name) -> name.endsWith(".disabled"));
                if (disabledFiles != null) {
                    for (File df : disabledFiles) {
                        String originalName = df.getName().substring(0, df.getName().length() - 9);
                        Path bunkerTarget = bunkerDir.resolve(originalName);
                        if (!Files.exists(bunkerTarget)) {
                            df.renameTo(bunkerTarget.toFile());
                        } else {
                            df.delete();
                        }
                    }
                }
            }
        } catch (Throwable t) {}

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
                            if (safeMoveToMods(modPathInBunker, modPathInMods, modsDir, entry.jarName)) {
                                changesMade = true;
                            }
                        } else if (!Files.exists(modPathInMods)) {
                            filesToDownload.add(entry.jarName);
                        }
                    } else {
                        if (Files.exists(modPathInMods)) {
                            if (safeMoveToBunker(modPathInMods, modPathInBunker, modsDir, entry.jarName)) {
                                changesMade = true;
                            }
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
                            if (safeMoveToMods(depInBunker, depInMods, modsDir, dep.jarName)) {
                                changesMade = true;
                            }
                        } else if (!Files.exists(depInMods)) {
                            filesToDownload.add(dep.jarName);
                        }
                    } else {
                        if (Files.exists(depInMods)) {
                            if (safeMoveToBunker(depInMods, depInBunker, modsDir, dep.jarName)) {
                                changesMade = true;
                            }
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

    private static boolean safeMoveToBunker(Path modPathInMods, Path modPathInBunker, Path modsDir, String jarName) {
        if (!Files.exists(modPathInMods)) return false;
        try {
            Files.move(modPathInMods, modPathInBunker, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("Moved " + jarName + " from mods folder to Bunker.");
            return true;
        } catch (Throwable t1) {
            LOGGER.warn("Direct move to Bunker failed for " + jarName + " (Windows file lock). Applying lock-resilient fallback: " + t1.getMessage());
            try {
                if (!Files.exists(modPathInBunker)) {
                    try {
                        Files.copy(modPathInMods, modPathInBunker, StandardCopyOption.REPLACE_EXISTING);
                    } catch (Throwable t2) {}
                }
                Path disabledPath = modsDir.resolve(jarName + ".disabled");
                try {
                    Files.move(modPathInMods, disabledPath, StandardCopyOption.REPLACE_EXISTING);
                    LOGGER.info("Staged " + jarName + " as .disabled due to active file lock.");
                    return true;
                } catch (Throwable t3) {
                    modPathInMods.toFile().deleteOnExit();
                    return true;
                }
            } catch (Throwable t4) {
                LOGGER.error("Fallback staging failed for " + jarName, t4);
            }
        }
        return false;
    }

    private static boolean safeMoveToMods(Path modPathInBunker, Path modPathInMods, Path modsDir, String jarName) {
        if (!Files.exists(modPathInBunker)) return false;
        try {
            Path disabledPath = modsDir.resolve(jarName + ".disabled");
            if (Files.exists(disabledPath)) {
                try { Files.delete(disabledPath); } catch (Throwable t) {}
            }
            Files.move(modPathInBunker, modPathInMods, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("Moved " + jarName + " from Bunker to mods folder.");
            return true;
        } catch (Throwable t1) {
            LOGGER.warn("Failed moving " + jarName + " to mods: " + t1.getMessage());
            try {
                Files.copy(modPathInBunker, modPathInMods, StandardCopyOption.REPLACE_EXISTING);
                return true;
            } catch (Throwable t2) {}
        }
        return false;
    }

    private static void downloadModFile(String jarName, Path targetFile) throws Exception {
                String urlStr = GITHUB_MODS_BASE_URL + jarName;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(urlStr))
                .header("User-Agent", "CenturyCivilization-Client")
                .GET()
                .build();

        HttpResponse<InputStream> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofInputStream());
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
