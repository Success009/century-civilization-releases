package com.century.civilization.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.century.civilization.JanitorPreLaunch;
import net.fabricmc.api.ClientModInitializer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.User;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ConnectScreen;
import net.minecraft.client.multiplayer.resolver.ServerAddress;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

public class CenturyModClient implements ClientModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("century-mod-client");
    public static final String SERVER_IP = "127.0.0.1";
    public static final int SERVER_PORT = 25565;
    public static final String API_BASE_URL = "https://century.success0.com.np";

        private static boolean useDirectLink = false;
    private static boolean loggedIn = false;
    private static String authenticatedUsername = "";
    private static String authToken = "";
    private static String userRole = "";
    private static User cachedUser = null;
    private static boolean sessionLoaded = false;
    private static Identifier loadedSkinIdentifier = null;
    private static boolean skinDownloadStarted = false;

        @Override
    public void onInitializeClient() {
        if (JanitorPreLaunch.isDisabled()) {
            LOGGER.info("Century Civilization Client (Older Version) is disabled and inactive.");
            return;
        }
        LOGGER.info("Century UI Client Initialized.");
        loadNetworkConfig();
        AutoUpdater.checkForUpdatesAsync();
        configureVoiceChatPeriodically();
        VoxyCloudSyncManager.init();
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.registerGlobalReceiver(
            com.century.civilization.network.WorldSeedPayload.TYPE,
            (payload, context) -> {
                long seed = payload.seed();
                context.client().execute(() -> {
                    if (context.client().level != null && context.client().level.getBiomeManager() != null) {
                        try {
                            ((com.century.civilization.duck.BiomeManagerDuck) context.client().level.getBiomeManager()).century$setBiomeZoomSeed(seed);
                            LOGGER.info("[LOD-SEED] Successfully injected world seed " + seed + " into client BiomeManager.");
                        } catch (Throwable t) {
                            LOGGER.error("Failed to inject received seed into client", t);
                        }
                    }
                });
            }
        );
    }

    /**
     * Simple Voice Chat generates its property file upon loading or server join and might override 
     * our initial settings. We check and re-apply settings (300% volume, unmuted mic, voice activation) 
     * multiple times over the first 30 seconds of launch to guarantee configuration persistence.
     */
    private static void configureVoiceChatPeriodically() {
        Thread thread = new Thread(() -> {
            for (int i = 0; i < 5; i++) {
                configureVoiceChat();
                try {
                    Thread.sleep(6000);
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    private static void configureVoiceChat() {
        try {
            java.nio.file.Path configDir = java.nio.file.Paths.get("config", "voicechat");
            if (!java.nio.file.Files.exists(configDir)) {
                java.nio.file.Files.createDirectories(configDir);
            }
            java.nio.file.Path configFile = configDir.resolve("voicechat-client.properties");
            
            java.util.List<String> lines;
            if (java.nio.file.Files.exists(configFile)) {
                lines = java.nio.file.Files.readAllLines(configFile, java.nio.charset.StandardCharsets.UTF_8);
            } else {
                lines = new java.util.ArrayList<>();
            }

            // Map variables and variations to be ultra-robust across different Simple Voice Chat versions
            java.util.Map<String, String> desiredSettings = new java.util.HashMap<>();
            desiredSettings.put("voice_chat_volume", "3.0");
            desiredSettings.put("voicechat_volume", "3.0");
            desiredSettings.put("volume", "3.0");
            desiredSettings.put("muted", "false");
            desiredSettings.put("microphone_muted", "false");
            desiredSettings.put("mic_muted", "false");
            desiredSettings.put("input_mode", "ACTIVATION");
            desiredSettings.put("voice_activation", "true");

            java.util.List<String> updatedLines = new java.util.ArrayList<>();
            java.util.Set<String> processedKeys = new java.util.HashSet<>();

            // Parse existing lines and update values
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                    updatedLines.add(line);
                    continue;
                }
                
                int eqIdx = trimmed.indexOf('=');
                if (eqIdx > 0) {
                    String key = trimmed.substring(0, eqIdx).trim();
                    if (desiredSettings.containsKey(key)) {
                        updatedLines.add(key + "=" + desiredSettings.get(key));
                        processedKeys.add(key);
                    } else {
                        updatedLines.add(line);
                    }
                } else {
                    updatedLines.add(line);
                }
            }

            // Append any missing keys that were not found in the original file
            for (java.util.Map.Entry<String, String> entry : desiredSettings.entrySet()) {
                if (!processedKeys.contains(entry.getKey())) {
                    updatedLines.add(entry.getKey() + "=" + entry.getValue());
                }
            }

            java.nio.file.Files.write(configFile, updatedLines, java.nio.charset.StandardCharsets.UTF_8);
            LOGGER.info("[Century Mod] Auto-configured Simple Voice Chat Client settings (300% volume, unmuted mic, voice activation).");
        } catch (Exception e) {
            LOGGER.error("[Century Mod] Failed to configure Simple Voice Chat settings: " + e.getMessage());
        }
    }
    public static boolean isLoggedIn() {
        loadSessionIfNeeded();
        return loggedIn;
    }

    public static String getAuthenticatedUsername() {
        loadSessionIfNeeded();
        return authenticatedUsername;
    }

    public static String getUserRole() {
        loadSessionIfNeeded();
        return userRole;
    }

    public static String getAuthToken() {
        loadSessionIfNeeded();
        return authToken;
    }

    private static java.io.File explicitGameDirectory = null;

    public static void loadSessionWithDirectory(java.io.File dir) {
        if (sessionLoaded) return;
        explicitGameDirectory = dir;
        sessionLoaded = true;
        loadSession();
    }

    private static Path resolveSessionPath() {
        if (explicitGameDirectory != null) {
            return explicitGameDirectory.toPath().resolve("century_session.json");
        }
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.gameDirectory != null) {
                return mc.gameDirectory.toPath().resolve("century_session.json");
            }
        } catch (Exception e) {
            // Fallback
        }
        return Paths.get("century_session.json");
    }

    public static void loadSessionIfNeeded() {
        if (sessionLoaded) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.gameDirectory == null) {
            return;
        }
        sessionLoaded = true;
        loadSession();
    }

    private static void loadSession() {
        try {
            Path sessionPath = resolveSessionPath();
            File file = sessionPath.toFile();
            if (file.exists()) {
                String content = Files.readString(sessionPath, StandardCharsets.UTF_8);
                JsonObject obj = JsonParser.parseString(content).getAsJsonObject();
                if (obj.has("username") && obj.has("token")) {
                    authenticatedUsername = obj.get("username").getAsString();
                    authToken = obj.get("token").getAsString();
                    userRole = obj.has("role") ? obj.get("role").getAsString() : "player";
                    loggedIn = true;
                    LOGGER.info("Loaded active persistent session for player: " + authenticatedUsername);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load local session file", e);
        }
    }

    public static void saveSession(String username, String token, String role) {
        try {
            authenticatedUsername = username;
            authToken = token;
            userRole = role;
            loggedIn = true;
            cachedUser = null;
            loadedSkinIdentifier = null; // Reset skin on login change
            skinDownloadStarted = false; // Reset thread-lock flag

            JsonObject obj = new JsonObject();
            obj.addProperty("username", username);
            obj.addProperty("token", token);
            obj.addProperty("role", role);

            Path sessionPath = resolveSessionPath();
            Files.writeString(sessionPath, obj.toString(), StandardCharsets.UTF_8);
            LOGGER.info("Session saved successfully to game directory for player: " + username);
        } catch (Exception e) {
            LOGGER.error("Failed to write local session file", e);
        }
    }

    public static void logout() {
        loggedIn = false;
        authenticatedUsername = "";
        authToken = "";
        userRole = "";
        cachedUser = null;
        loadedSkinIdentifier = null;
        skinDownloadStarted = false; // Reset thread-lock flag
        try {
            Path sessionPath = resolveSessionPath();
            Files.deleteIfExists(sessionPath);
            LOGGER.info("Logged out successfully. Persistent session file deleted.");
        } catch (Exception e) {
            LOGGER.error("Failed to delete session file", e);
        }
    }

    public static String login(String username, String password) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(12))
                    .build();

            String jsonPayload = String.format("{\"username\":\"%s\",\"password\":\"%s\"}",
                    username.replace("\"", "\\\""),
                    password.replace("\"", "\\\""));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_BASE_URL + "/api/auth/login"))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "CenturyMod-Client")
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonObject obj = JsonParser.parseString(response.body()).getAsJsonObject();
                String token = obj.get("token").getAsString();
                String apiUsername = obj.get("username").getAsString();
                String role = obj.get("role").getAsString();

                saveSession(apiUsername, token, role);
                return null; // success
            } else {
                JsonObject obj = JsonParser.parseString(response.body()).getAsJsonObject();
                return obj.has("error") ? obj.get("error").getAsString() : "Invalid username or password";
            }
        } catch (Exception e) {
            LOGGER.error("Authentication request failed", e);
            return "Connection failed: " + e.getMessage();
        }
    }

    public static boolean verifySession() {
        loadSessionIfNeeded();
        if (!loggedIn || authToken.isEmpty()) return false;
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(8))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_BASE_URL + "/api/user/profile"))
                    .header("Authorization", "Bearer " + authToken)
                    .header("User-Agent", "CenturyMod-Client")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                JsonObject obj = JsonParser.parseString(response.body()).getAsJsonObject();
                if (obj.has("username")) {
                    String role = obj.has("role") ? obj.get("role").getAsString() : "player";
                    userRole = role; // Sync role
                    return true;
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Session verification request failed, assuming offline cache valid: " + e.getMessage());
            return true; // Fallback
        }
        logout();
        return false;
    }

    public static User getMinecraftUser() {
        loadSessionIfNeeded();
        if (!loggedIn) return null;
        if (cachedUser == null || !cachedUser.getName().equalsIgnoreCase(authenticatedUsername)) {
            java.util.UUID offlineUuid = UUID.nameUUIDFromBytes(("OfflinePlayer:" + authenticatedUsername).getBytes(StandardCharsets.UTF_8));
            cachedUser = new User(
                    authenticatedUsername,
                    offlineUuid,
                    authToken,
                    Optional.empty(),
                    Optional.empty()
            );
        }
        return cachedUser;
    }

    public static Identifier getPlayerSkinIdentifier() {
        if (!loggedIn) return null;
        if (loadedSkinIdentifier != null) return loadedSkinIdentifier;

        // Thread lock to prevent spawning multiple threads per frame
        if (skinDownloadStarted) {
            return Identifier.fromNamespaceAndPath("minecraft", "textures/entity/steve.png");
        }
        skinDownloadStarted = true;

        String username = authenticatedUsername.toLowerCase();
        Identifier id = Identifier.fromNamespaceAndPath("century", "skins/" + username);

        // Fetch skin from web server asynchronously
        Thread downloadThread = new Thread(() -> {
            try {
                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(10))
                        .build();

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(API_BASE_URL + "/skins/" + username + ".png"))
                        .GET()
                        .build();

                HttpResponse<java.io.InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                
                // Verify content type to prevent reading fallback HTML React pages (index.html) as PNG
                Optional<String> contentTypeOpt = response.headers().firstValue("Content-Type");
                String contentType = contentTypeOpt.orElse("").toLowerCase();

                if (response.statusCode() == 200 && contentType.contains("image/png")) {
                    try (java.io.InputStream is = response.body()) {
                        com.mojang.blaze3d.platform.NativeImage nativeImage = com.mojang.blaze3d.platform.NativeImage.read(is);
                        Minecraft.getInstance().execute(() -> {
                            try {
                                net.minecraft.client.renderer.texture.DynamicTexture dynamicTexture = new net.minecraft.client.renderer.texture.DynamicTexture(() -> "century_skin_" + username, nativeImage);
                                Minecraft.getInstance().getTextureManager().register(id, dynamicTexture);
                                loadedSkinIdentifier = id;
                                LOGGER.info("Successfully loaded and registered server skin for: " + username);
                            } catch (Exception ex) {
                                LOGGER.error("Failed to register dynamic skin texture", ex);
                            }
                        });
                    }
                } else {
                    LOGGER.info("No custom server skin uploaded for player: " + username);
                }
            } catch (Exception e) {
                LOGGER.warn("Server skin download omitted or failed for: " + username + " (" + e.getMessage() + ")");
            }
        });
        downloadThread.setDaemon(true);
        downloadThread.start();

                // Fallback to Steve texture
        return Identifier.fromNamespaceAndPath("minecraft", "textures/entity/steve.png");
    }

    public static boolean isUseDirectLink() {
        return useDirectLink;
    }

    public static void setUseDirectLink(boolean val) {
        useDirectLink = val;
        saveNetworkConfig();
    }

    public static void loadNetworkConfig() {
        try {
            Path path = resolveNetworkConfigPath();
            if (Files.exists(path)) {
                String content = Files.readString(path, StandardCharsets.UTF_8);
                JsonObject obj = JsonParser.parseString(content).getAsJsonObject();
                if (obj.has("useDirectLink")) {
                    useDirectLink = obj.get("useDirectLink").getAsBoolean();
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to load network config", e);
        }
    }

    private static void saveNetworkConfig() {
        try {
            Path path = resolveNetworkConfigPath();
            JsonObject obj = new JsonObject();
            obj.addProperty("useDirectLink", useDirectLink);
            Files.writeString(path, obj.toString(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            LOGGER.error("Failed to save network config", e);
        }
    }

    private static Path resolveNetworkConfigPath() {
        if (explicitGameDirectory != null) {
            return explicitGameDirectory.toPath().resolve("century_network.json");
        }
        try {
            Minecraft mc = Minecraft.getInstance();
            if (mc != null && mc.gameDirectory != null) {
                return mc.gameDirectory.toPath().resolve("century_network.json");
            }
        } catch (Exception e) {
            // Fallback
        }
        return Paths.get("century_network.json");
    }

    public static void joinServer(Screen parent) {
        String host = SERVER_IP;
        int port = SERVER_PORT;

        if (useDirectLink) {
            host = "mauritania-allied.tun.ply.gg";
            port = 25565;
            LOGGER.info("Direct Link selected. Routing connection directly to " + host + ":" + port);
        } else {
            LOGGER.info("Secure Tunnel selected. Routing connection via local bridge at " + host + ":" + port);
        }

        Minecraft client = Minecraft.getInstance();
        ServerAddress serverAddress = new ServerAddress(host, port);
        ServerData serverData = new ServerData("Century Server", "century.internal.gateway", ServerData.Type.REALM);
        LOGGER.info("Created ServerData with dummy address for secure routing.");
        ConnectScreen.startConnecting(parent, client, serverAddress, serverData, true, null);
    }
}
