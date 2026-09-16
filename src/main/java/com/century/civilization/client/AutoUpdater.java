package com.century.civilization.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.century.civilization.CenturyMod;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class AutoUpdater {
    private static final String REPO = "Success009/century-civilization-releases";
    private static final String API_URL = "https://api.github.com/repos/" + REPO + "/releases/latest";
    private static boolean updateCompleted = false;

    
        private static final String[] REQUIRED_MODS = {
        "sodium-fabric-0.9.1+mc26.2.jar"
    };

    public static boolean isUpdateCompleted() {
        return updateCompleted;
    }

        public static void checkForUpdatesAsync() {
        Thread thread = new Thread(() -> {
            while (true) {
                try {
                    URL clientLocation = AutoUpdater.class.getProtectionDomain().getCodeSource().getLocation();
                    File currentJarFile = new File(clientLocation.toURI());

                    if (!currentJarFile.isFile() || !currentJarFile.getName().endsWith(".jar") || currentJarFile.getName().contains("1.0.0") || new java.io.File(currentJarFile.getParentFile(), "noupdate.txt").exists()) {
                        CenturyMod.LOGGER.info("[AUTO-UPDATER] Local dev build or bypass detected. Skipping update check.");
                        return;
                    }

                    cleanOldVersions(currentJarFile);
                    syncRequiredMods(currentJarFile);

                    String currentJarName = currentJarFile.getName();
                    CenturyMod.LOGGER.info("[AUTO-UPDATER] Checking for updates. Current active JAR: " + currentJarName);

                    HttpClient client = HttpClient.newBuilder()
                            .connectTimeout(Duration.ofSeconds(10))
                            .followRedirects(HttpClient.Redirect.ALWAYS)
                            .build();

                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(API_URL))
                            .header("Accept", "application/vnd.github.v3+json")
                            .header("User-Agent", "Century-AutoUpdater")
                            .GET()
                            .build();

                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                    if (response.statusCode() == 200) {
                        JsonObject release = JsonParser.parseString(response.body()).getAsJsonObject();

                        String os = System.getProperty("os.name").toLowerCase();
                        String platformSuffix;
                        if (os.contains("win")) {
                            platformSuffix = "-windows.jar";
                        } else if (os.contains("mac") || os.contains("darwin")) {
                            platformSuffix = "-mac.jar";
                        } else {
                            platformSuffix = "-linux.jar";
                        }

                        if (release.has("assets")) {
                            JsonArray assets = release.getAsJsonArray("assets");
                            String downloadUrl = null;
                            String assetName = null;

                            for (JsonElement assetEl : assets) {
                                JsonObject asset = assetEl.getAsJsonObject();
                                if (asset.has("name") && asset.has("browser_download_url")) {
                                    String name = asset.get("name").getAsString();
                                    if (name.endsWith(platformSuffix)) {
                                        assetName = name;
                                        downloadUrl = asset.get("browser_download_url").getAsString();
                                        break;
                                    }
                                }
                            }

                            if (downloadUrl != null && assetName != null) {
                                int currentVer = parseVersionFromFilename(currentJarName);
                                int remoteVer = parseVersionFromFilename(assetName);

                                if (remoteVer > currentVer || (!currentJarName.equalsIgnoreCase(assetName) && currentVer == 0)) {
                                    CenturyMod.LOGGER.info("[AUTO-UPDATER] Newer remote JAR found: " + assetName + " (v" + remoteVer + " > v" + currentVer + ") | Initiating silent download...");
                                    applyUpdate(currentJarFile, downloadUrl, assetName);
                                    if (updateCompleted) {
                                        CenturyMod.LOGGER.info("[AUTO-UPDATER] Update downloaded successfully. Stopping periodic checks as restart is required.");
                                        break;
                                    }
                                } else {
                                    CenturyMod.LOGGER.info("[AUTO-UPDATER] Mod is up to date (current build: " + currentVer + ", remote build: " + remoteVer + ").");
                                }
                            } else {
                                CenturyMod.LOGGER.warn("[AUTO-UPDATER] No platform-specific asset found matching suffix: " + platformSuffix);
                            }
                        }
                    } else {
                        CenturyMod.LOGGER.warn("[AUTO-UPDATER] GitHub API request failed with status: " + response.statusCode());
                    }
                } catch (Exception e) {
                    CenturyMod.LOGGER.error("[AUTO-UPDATER] Error checking for updates: " + e.getMessage());
                }

                try {
                    Thread.sleep(60000);
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    private static int parseVersionFromFilename(String verStr) {
        if (verStr == null) return 0;
        try {
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("1\\.0\\.(\\d+)").matcher(verStr);
            if (matcher.find()) {
                return Integer.parseInt(matcher.group(1));
            }
        } catch (Throwable e) {}
        try {
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\d+").matcher(verStr);
            int lastNum = 0;
            while (matcher.find()) {
                lastNum = Integer.parseInt(matcher.group());
            }
            return lastNum;
        } catch (Throwable e) {}
        return 0;
    }

    private static void cleanOldVersions(File currentJarFile) {
        try {
            File modsDirectory = currentJarFile.getParentFile();
            if (modsDirectory == null || !modsDirectory.exists()) return;

            File[] files = modsDirectory.listFiles();
            if (files == null) return;

            String activeName = currentJarFile.getName();

            for (File file : files) {
                if (!file.isFile()) continue;

                String name = file.getName();
                // Clean up any left-over .bak and .tmp fragments
                if (name.startsWith("CenturyCivilization") && (name.endsWith(".bak") || name.endsWith(".tmp"))) {
                    CenturyMod.LOGGER.info("[AUTO-UPDATER] Self-healing: deleting old temporary/backup fragment: " + name);
                    file.delete();
                }
                // Clean up duplicate inactive .jar files starting with CenturyCivilization
                else if (name.startsWith("CenturyCivilization") && name.endsWith(".jar") && !name.equalsIgnoreCase(activeName)) {
                    CenturyMod.LOGGER.info("[AUTO-UPDATER] Self-healing: deleting old duplicate inactive jar: " + name);
                    file.delete();
                }
            }
        } catch (Exception e) {
            CenturyMod.LOGGER.error("[AUTO-UPDATER] Error in self-healing routine: " + e.getMessage());
        }
    }

    private static void applyUpdate(File currentJarFile, String downloadUrl, String assetName) {
        File modsDirectory = currentJarFile.getParentFile();
        File targetNewJar = new File(modsDirectory, assetName);
        File tempDownloadFile = new File(modsDirectory, assetName + ".tmp");

        try {
            CenturyMod.LOGGER.info("[AUTO-UPDATER] Downloading update to temporary file: " + tempDownloadFile.getAbsolutePath());
            downloadFile(downloadUrl, tempDownloadFile);

            // Strict integrity check: verify it is a valid, readable JAR with fabric.mod.json
            boolean valid = false;
            try (java.util.jar.JarFile jar = new java.util.jar.JarFile(tempDownloadFile)) {
                if (jar.getJarEntry("fabric.mod.json") != null) {
                    valid = true;
                }
            } catch (Throwable t) {
                valid = false;
            }

            if (!valid) {
                if (tempDownloadFile.exists()) tempDownloadFile.delete();
                throw new java.io.IOException("Downloaded archive failed integrity verification (corrupt or incomplete jar).");
            }

            // Atomic file replace using modern standard Files.move
            java.nio.file.Files.move(
                tempDownloadFile.toPath(),
                targetNewJar.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING
            );

            CenturyMod.LOGGER.info("[AUTO-UPDATER] Silently downloaded new update and finalized JAR: " + assetName);
            updateCompleted = true;

            // Immediate Deletion or Renaming of Self to prevent duplicate mod crashes on next boot
            try {
                // On Linux and macOS, we can delete the running jar immediately!
                if (currentJarFile.delete()) {
                    CenturyMod.LOGGER.info("[AUTO-UPDATER] Deleted running JAR file immediately after download: " + currentJarFile.getName());
                } else {
                    // On Windows, the file is locked, so we rename it to .bak which stops Fabric from loading it as a mod
                    File backupFile = new File(modsDirectory, currentJarFile.getName() + ".bak");
                    if (currentJarFile.renameTo(backupFile)) {
                        CenturyMod.LOGGER.info("[AUTO-UPDATER] Windows lock active. Successfully renamed running JAR to safe backup: " + backupFile.getName());
                    } else {
                        CenturyMod.LOGGER.warn("[AUTO-UPDATER] Could not rename running JAR, registering deleteOnExit.");
                        currentJarFile.deleteOnExit();
                    }
                }
            } catch (Exception ex) {
                CenturyMod.LOGGER.error("[AUTO-UPDATER] Failed to immediately rename/delete old file, utilizing fallback: " + ex.getMessage());
                currentJarFile.deleteOnExit();
            }
        } catch (Exception e) {
            CenturyMod.LOGGER.error("[AUTO-UPDATER] Error applying update: " + e.getMessage());
            if (tempDownloadFile.exists()) {
                try { tempDownloadFile.delete(); } catch (Throwable ignored) {}
            }
        }
    }

    private static void downloadFile(String fileUrl, File targetFile) throws Exception {
        URI uri = URI.create(fileUrl);
        URL url = uri.toURL();
        int maxBytesPerSecond = 500 * 1024; // Throttled to 500 KB/s to balance high-speed download with zero gameplay jitter
        try (InputStream in = new BufferedInputStream(url.openStream());
             FileOutputStream out = new FileOutputStream(targetFile, false)) { // false ensures truncation from scratch
            byte[] buffer = new byte[8192];
            int bytesRead;
            long startTime = System.currentTimeMillis();
            long totalBytesRead = 0;

            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                totalBytesRead += bytesRead;

                long elapsedTime = System.currentTimeMillis() - startTime;
                if (elapsedTime > 0) {
                    double expectedMs = (double) totalBytesRead / ((double) maxBytesPerSecond / 1000.0);
                    long sleepTime = (long) (expectedMs - elapsedTime);
                    if (sleepTime > 10) {
                        Thread.sleep(sleepTime);
                    }
                }
            }
            out.flush();
        } catch (Throwable t) {
            // Guarantee no corrupt partial file is left on disk if connection drops
            if (targetFile.exists()) {
                try { targetFile.delete(); } catch (Throwable ignored) {}
            }
            throw t;
        }
    }

    private static void syncRequiredMods(File currentJarFile) {
        File modsDirectory = currentJarFile.getParentFile();
        if (modsDirectory == null || !modsDirectory.exists()) return;

        for (String modName : REQUIRED_MODS) {
            File targetFile = new File(modsDirectory, modName);
            if (!targetFile.exists()) {
                CenturyMod.LOGGER.info("[AUTO-UPDATER] Required mod is missing: " + modName);
                
                // Securely check if local source exists first (prevents unnecessary bandwidth usage)
                File localSource = new File("/home/success0/.minecraft/instances/fabric_26.2/mods/" + modName);
                if (localSource.exists()) {
                    try {
                        CenturyMod.LOGGER.info("[AUTO-UPDATER] Found local copy of required mod at: " + localSource.getAbsolutePath() + " | Copying...");
                        java.nio.file.Files.copy(localSource.toPath(), targetFile.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                        CenturyMod.LOGGER.info("[AUTO-UPDATER] Successfully copied required mod locally: " + modName);
                        continue;
                    } catch (Exception e) {
                        CenturyMod.LOGGER.error("[AUTO-UPDATER] Failed to copy local required mod: " + e.getMessage());
                    }
                }

                // Fallback to downloading
                CenturyMod.LOGGER.info("[AUTO-UPDATER] Remote download fallback triggered: " + modName);
                String downloadUrl = "https://century.success0.com.np/mods/" + modName;
                try {
                    File tempDownload = new File(modsDirectory, modName + ".tmp");
                    downloadFile(downloadUrl, tempDownload);
                    if (tempDownload.renameTo(targetFile)) {
                        CenturyMod.LOGGER.info("[AUTO-UPDATER] Successfully downloaded and installed required mod: " + modName);
                    } else {
                        CenturyMod.LOGGER.error("[AUTO-UPDATER] Failed to rename temp download for: " + modName);
                    }
                } catch (Exception e) {
                    CenturyMod.LOGGER.error("[AUTO-UPDATER] Failed to download required mod: " + modName + " | Error: " + e.getMessage());
                }
            }
        }
    }
}
