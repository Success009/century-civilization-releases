package com.century.civilization;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.entrypoint.PreLaunchEntrypoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
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

    // Strict list of allowed top/second-level package structures inside mod jars
    private static final Set<String> ALLOWED_PACKAGES = new HashSet<>(Arrays.asList(
        "ca/fxco",
        "com/logisticscraft",
        "com/misterpemodder",
        "com/spunkyinsaan",
        "de/johni0702",
        "de/maxhenkel",
        "dev/isxander",
        "dev/tr7zw",
        "fi/dy",
        "malte0811/ferritecore",
        "me/cortex",
        "me/shedaniel",
        "net/caffeinemc",
        "net/fabricmc"
    ));

    private static volatile boolean cachedIsClean = true;
    private static volatile long lastCleanCheckTime = 0;

    public static List<String> getIllegalMods() {
        return ILLEGAL_MODS_FOUND;
    }

    public static boolean isClean() {
        if (!ILLEGAL_MODS_FOUND.isEmpty()) {
            return false;
        }
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

    private static boolean isClassAllowed(String entryName) {
        if (!entryName.endsWith(".class")) {
            return true;
        }
        
        String path = entryName.replace('\\', '/');
        
        // Allow general JVM/Fabric boilerplate metadata classes
        if (path.startsWith("META-INF/") || path.equals("module-info.class")) {
            return true;
        }

        // Validate package path against whitelisted library namespaces
        for (String prefix : ALLOWED_PACKAGES) {
            if (path.startsWith(prefix + "/")) {
                return true;
            }
        }
        
        // Allow generated architectury dependencies
        if (path.startsWith("architectury_inject_")) {
            return true;
        }
        
        return false;
    }

    @Override
    public void onPreLaunch() {
        if (disabled) {
            LOGGER.info("[DUPLICATE-PREVENT] Older version detected. Disabling prelaunch sweeps.");
            return;
        }
        LOGGER.info("Century Janitor: Initializing strict package-based library sweeps...");

        ILLEGAL_MODS_FOUND.clear();

        // 1. Scan mods folder for package structures
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

                            // Verify every class inside the JAR against library whitelist
                            boolean jarAuthorized = true;
                            try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(canonicalFile)) {
                                java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = zip.entries();
                                while (entries.hasMoreElements()) {
                                    java.util.zip.ZipEntry entry = entries.nextElement();
                                    String entryName = entry.getName();
                                    if (entryName.endsWith(".class")) {
                                        if (!isClassAllowed(entryName)) {
                                            jarAuthorized = false;
                                            ILLEGAL_MODS_FOUND.add(f.getName() + " (violates whitelist: " + entryName + ")");
                                            LOGGER.warn("Century Security: Unauthorized class/package detected inside " + f.getName() + ": " + entryName);
                                            break;
                                        }
                                    }
                                }
                            } catch (Throwable t) {
                                ILLEGAL_MODS_FOUND.add(f.getName() + " (corrupted class structure)");
                            }
                        } catch (Throwable t) {
                            LOGGER.error("Error scanning jar: " + f.getName(), t);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            LOGGER.error("Security library sweep failed", t);
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
