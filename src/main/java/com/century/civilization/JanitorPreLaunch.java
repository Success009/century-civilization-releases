package com.century.civilization;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class JanitorPreLaunch implements PreLaunchEntrypoint {
    private static final Logger LOGGER = LoggerFactory.getLogger("century-janitor");
    private static final List<String> ILLEGAL_MODS_FOUND = java.util.Collections.synchronizedList(new ArrayList<>());

    private static volatile boolean disabled = false;

    public static boolean isDisabled() {
        return disabled;
    }

    static {
        try {
            disabled = checkAndCleanDuplicates();
        } catch (Throwable t) {
            // Prevent boot crashes
        }
    }

    private static boolean checkAndCleanDuplicates() {
        try {
            java.net.URL location = JanitorPreLaunch.class.getProtectionDomain().getCodeSource().getLocation();
            java.io.File ourJar = new java.io.File(location.toURI());

            if (!ourJar.isFile() || !ourJar.getName().endsWith(".jar")) {
                return false;
            }

            java.io.File modsDir = ourJar.getParentFile();
            if (modsDir == null || !modsDir.exists()) {
                return false;
            }

            int ourVersion = getModVersionFromJar(ourJar);

            java.io.File[] files = modsDir.listFiles((dir, name) -> 
                name.endsWith(".jar") && 
                (name.startsWith("CenturyCivilization") || name.startsWith("CentoryCivilization"))
            );

            if (files == null || files.length <= 1) {
                return false;
            }

            boolean weAreLesser = false;

            for (java.io.File otherJar : files) {
                if (otherJar.getCanonicalPath().equals(ourJar.getCanonicalPath())) {
                    continue;
                }

                int otherVersion = getModVersionFromJar(otherJar);

                if (ourVersion < otherVersion) {
                    weAreLesser = true;
                } else if (ourVersion > otherVersion) {
                    try {
                        if (otherJar.delete()) {
                            LOGGER.info("[DUPLICATE-CLEANER] Successfully deleted older version jar: " + otherJar.getName());
                        } else {
                            java.io.File bakFile = new java.io.File(modsDir, otherJar.getName() + ".bak");
                            if (otherJar.renameTo(bakFile)) {
                                LOGGER.info("[DUPLICATE-CLEANER] Renamed locked older version to: " + bakFile.getName());
                            } else {
                                otherJar.deleteOnExit();
                            }
                        }
                    } catch (Throwable t) {
                        otherJar.deleteOnExit();
                    }
                } else {
                    if (ourJar.getName().compareTo(otherJar.getName()) > 0) {
                        weAreLesser = true;
                    } else {
                        try {
                            otherJar.delete();
                        } catch (Throwable t) {}
                    }
                }
            }

            return weAreLesser;

        } catch (Throwable t) {
            // Safe fallback
        }
        return false;
    }

    private static int getModVersionFromJar(java.io.File jarFile) {
        try (java.util.jar.JarFile jar = new java.util.jar.JarFile(jarFile)) {
            java.util.jar.JarEntry entry = jar.getJarEntry("fabric.mod.json");
            if (entry != null) {
                try (java.io.InputStream is = jar.getInputStream(entry)) {
                    String content = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                    com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(content).getAsJsonObject();
                    if (json.has("version")) {
                        return parseVersionToBuildNumber(json.get("version").getAsString());
                    }
                }
            }
        } catch (Throwable e) {}
        return parseVersionToBuildNumber(jarFile.getName());
    }

    private static int parseVersionToBuildNumber(String verStr) {
        if (verStr == null) return 0;
        try {
            String[] parts = verStr.replaceAll("[^0-9.]", "").split("\\.");
            if (parts.length > 0) {
                return Integer.parseInt(parts[parts.length - 1]);
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

    // Signatures of known cheat/hacked client classes for dynamic classpath verification
    private static final String[] CHEAT_INDICATOR_CLASSES = {
        "net.wurstclient.WurstClient",
        "com.meteorclient.MeteorClient",
        "net.ccbluex.liquidbounce.LiquidBounce",
        "me.deftware.client.framework.main.bootstrap.Bootstrap",
        "bleach.hack.BleachHack",
        "thunder.hack.ThunderHack",
        "info.sigmaclient.sigma.Sigma",
        "com.inertia.Inertia",
        "client.impact.Impact"
    };

    // Strict SHA-256 hash whitelist of allowed client mods
    private static final Set<String> ALLOWED_HASHES = new HashSet<>(Arrays.asList(
        "02723bb3322bb45c5f75b1d83b3708ebf0fd59d1717076c9892dd22b0f1c69b8", // bobby-5.2.15+mc26.2.jar
        "def4be7639cd66704f7e304d658ea0f6bf490fb4a6eaa2dbf18ec2c3999d6349", // cloth-config-26.2.155.jar
        "05a36decbb1bbb0f785d974068c0d9c191975e0d8d637e1eb1f2f6bb84eab05a", // entityculling-fabric-1.10.5-mc26.2.jar
        "d6518c770024cbe8a556248f16fcdbb91c6a62f50227a6c3bae8190511e2c1b8", // fabric-api-0.155.2+26.2.jar
        "34ccdacf13bb9351fe43ce61912c2e09b72364e43e787d36ba3d2d04dec75a52", // fabric-language-kotlin-1.13.13+kotlin.2.4.10.jar
        "213966c72ed967acc7392beb28a866fba301ff56b9976c2e7801c2db7de6bf22", // ferritecore-9.0.0-fabric.jar
        "268fa149cc493ac997d7c8ec3dc3a232d90797d5251dbe19909e7a54cd529ee3", // litematica-fabric-26.2-0.28.4.jar
        "7588d4a76989498f56e11e63a2b1170fff476e8d9c4aac94e310b3e7db469e0f", // lithium-fabric-0.25.2+mc26.2.jar
        "5b95edce341d74351e656e35bdb51e9716596b86575d3d3d3cbf60b0940c207e", // malilib-fabric-26.2-0.29.3.jar
        "ea04505496e4d35a8c94199884b6fafa69057efe50f2096d2988c11163d49122", // moreculling-fabric-26.2-1.8.1.jar
        "517373bd057672c89243b4ee64c7610bfeeb1ec9057e153e40c45fb1ea37a3f8", // ping-display-26.2.jar
        "f5c1e570e22511a40a762ef68642c9ae57ec8a85fcf9ea309f05622904eda36d", // shulkerboxtooltip-fabric-5.4.0+26.2.jar
        "de406c7a0ca5e748dfbe44740278400882a44e3109e2584b243ec02d4003344b", // sodium-fabric-0.9.1+mc26.2.jar
        "ed5fa1a117fa26bfbc8463c27f88a8dffc53da277781f266ed1112281f7f65f7", // voicechat-fabric-2.6.21+26.2.jar
        "dd9e10d2110879f07c1ce9bdf1945f3470b32e83977f094005ee8c642e8128d5", // voxy-0.2.18-beta.jar
        "0aaedb63e398ab2e8d1b61439dac0ed6edf913fd10fc47a6cd56f01811db7283", // yet_another_config_lib_v3-3.9.5+26.2-fabric.jar
        "e014d36408e3d957bfce4d02d55d3703041902fa88bf96062694d69234b2d9e9"  // zoomify-2.16.1+26.2.jar
    ));

    private static volatile boolean cachedIsClean = true;
    private static volatile long lastCleanCheckTime = 0;

    public static List<String> getIllegalMods() {
        return ILLEGAL_MODS_FOUND;
    }

    public static boolean isClean() {
        long now = System.currentTimeMillis();
        if (now - lastCleanCheckTime < 2500) {
            return cachedIsClean;
        }
        lastCleanCheckTime = now;

        // Reflection-proof check: re-run the dynamic class signature check on the fly
        ClassLoader cl = JanitorPreLaunch.class.getClassLoader();
        for (String cls : CHEAT_INDICATOR_CLASSES) {
            try {
                Class.forName(cls, false, cl);
                cachedIsClean = false;
                return false; // Found cheat class!
            } catch (ClassNotFoundException e) {
                // Safe
            }
        }
        cachedIsClean = true;
        return true;
    }

    private static String calculateSHA256(File file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = fis.read(buffer)) != -1) {
                    digest.update(buffer, 0, read);
                }
            }
            byte[] hash = digest.digest();
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Throwable e) {
            return "";
        }
    }

    @Override
    public void onPreLaunch() {
        if (disabled) {
            LOGGER.info("[DUPLICATE-PREVENT] Older version detected. Disabling prelaunch sweeps.");
            return;
        }
        LOGGER.info("Century Janitor: Initializing strict SHA-256 hash-based security sweeps...");

        ILLEGAL_MODS_FOUND.clear();

        // 1. Scan mods folder for file hashes
        try {
            File modsDir = null;
            File ourJarFile = null;
            try {
                java.net.URL location = JanitorPreLaunch.class.getProtectionDomain().getCodeSource().getLocation();
                ourJarFile = new File(location.toURI()).getCanonicalFile();
                modsDir = ourJarFile.getParentFile();
            } catch (Throwable t) {}

            if (modsDir == null || !modsDir.exists()) {
                try {
                    modsDir = FabricLoader.getInstance().getGameDir().resolve("mods").toFile();
                } catch (Throwable t) {}
            }

            if (modsDir != null && modsDir.exists() && modsDir.isDirectory()) {
                File[] files = modsDir.listFiles((dir, name) -> name.endsWith(".jar"));
                if (files != null) {
                    for (File f : files) {
                        try {
                            File canonicalFile = f.getCanonicalFile();
                            if (ourJarFile != null && canonicalFile.equals(ourJarFile)) {
                                continue; // Skip our own mod jar
                            }
                            String nameLower = f.getName().toLowerCase();
                            if (nameLower.startsWith("centurycivilization") || nameLower.startsWith("centorycivilization")) {
                                continue; // Skip our own mod regardless of filename
                            }

                            // Calculate SHA-256
                            String hash = calculateSHA256(canonicalFile);
                            if (!ALLOWED_HASHES.contains(hash)) {
                                ILLEGAL_MODS_FOUND.add(f.getName() + " (unknown hash: " + hash + ")");
                                LOGGER.warn("Century Security: Unauthorized mod detected! File: " + f.getName() + " (SHA-256: " + hash + ")");
                            } else {
                                LOGGER.info("Century Janitor: Verified secure mod: " + f.getName());
                            }
                        } catch (Throwable t) {
                            LOGGER.error("Error verifying file: " + f.getName(), t);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            LOGGER.error("Strict security sweep failed", t);
        }

        // 2. Scan Classpath for cheat client packages (stops spoofed mod IDs)
        ClassLoader cl = JanitorPreLaunch.class.getClassLoader();
        for (String cls : CHEAT_INDICATOR_CLASSES) {
            try {
                Class.forName(cls, false, cl);
                String simpleName = cls.substring(cls.lastIndexOf('.') + 1);
                ILLEGAL_MODS_FOUND.add(simpleName);
                LOGGER.warn("Century Security: Detected active cheat package signature in memory: " + cls);
            } catch (ClassNotFoundException e) {
                // Clean
            }
        }

        if (!ILLEGAL_MODS_FOUND.isEmpty()) {
            LOGGER.warn("Century Security: Found " + ILLEGAL_MODS_FOUND.size() + " unauthorized mods/signatures. Access will be locked.");
        } else {
            LOGGER.info("Century Janitor: Client verified secure.");
        }
    }
}
