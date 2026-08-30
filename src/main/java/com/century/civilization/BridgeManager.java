package com.century.civilization;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import java.io.*;
import java.nio.file.*;
import java.util.Locale;

public class BridgeManager {
    private static BridgeLib bridge;
    private static String loadError = null;
    private static boolean isWindows = false;

        private static String getDecryptedKey() {
        String envKey = System.getenv("TS_AUTHKEY");
        if (envKey != null && !envKey.trim().isEmpty()) {
            return envKey.trim();
        }
        String propKey = System.getProperty("tskey");
        if (propKey != null && !propKey.trim().isEmpty()) {
            return propKey.trim();
        }
                return "tskey-auth-kshK6MUtUs11CNTRL-TncJmyFgxyhbhuRhMn111i7jsGrmD2DWX";
    }

    
    private static String cachedStatus = "NOT_LOADED";
    private static long lastStatusCheckTime = 0;
    private static boolean cachedIsReady = false;
    private static long lastReadyCheckTime = 0;

    public interface BridgeLib extends Library {
        void StartProxy(String authKey); 
        int IsReady();
        Pointer GetStatus();
        void FreeStatus(Pointer s);
    }

    public static String getStatus() {
        if (loadError != null) return loadError;
        if (bridge == null) return "NOT_LOADED";

        long now = System.currentTimeMillis();
        if (now - lastStatusCheckTime < 1000) {
            return cachedStatus;
        }

        lastStatusCheckTime = now;
        try {
            Pointer p = bridge.GetStatus();
            String s = p.getString(0);
            bridge.FreeStatus(p);
            cachedStatus = s;
        } catch (Throwable e) {
            cachedStatus = "ERR_COMM";
        }
        return cachedStatus;
    }

    public static boolean isReady() {
        if (bridge == null) return false;

        long now = System.currentTimeMillis();
        if (now - lastReadyCheckTime < 1000) {
            return cachedIsReady;
        }

        lastReadyCheckTime = now;
        try {
            cachedIsReady = bridge.IsReady() == 1;
        } catch (Throwable e) {
            cachedIsReady = false;
        }
        return cachedIsReady;
    }


    public static void start() {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        isWindows = os.contains("win");
        
        String libName = isWindows ? "bridge-windows-amd64" : (os.contains("mac") ? "libbridge-darwin-amd64" : "libbridge-linux-amd64");
        String extension = isWindows ? ".dll" : (os.contains("mac") ? ".dylib" : ".so");

        
        try {
            Path tempDir = Paths.get(System.getProperty("java.io.tmpdir"), "century_bridge_native");
            Files.createDirectories(tempDir);
            File libFile = tempDir.resolve(libName + extension).toFile();

            // Break the inode link by deleting the file first, preventing SIGBUS on Linux if mapped/in use
            if (libFile.exists()) {
                try {
                    Files.delete(libFile.toPath());
                } catch (Throwable t) {
                    // Fallback to unique filename if delete fails (e.g. file lock on Windows)
                    libFile = tempDir.resolve(libName + "_" + System.currentTimeMillis() + extension).toFile();
                }
            }

            // Always extract to ensure we have the latest version
            try (InputStream is = BridgeManager.class.getResourceAsStream("/assets/century/bin/" + libName + extension)) {
                if (is == null) {
                    loadError = "RES_MISSING";
                    return;
                }
                Files.copy(is, libFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            try {
                bridge = Native.load(libFile.getAbsolutePath(), BridgeLib.class);

                bridge.StartProxy(getDecryptedKey());
                CenturyMod.LOGGER.info("Century Network Layer (Native) initialized successfully.");
            } catch (Throwable t) {
                String msg = t.getMessage();
                if (msg != null && msg.length() > 20) msg = msg.substring(0, 17) + "...";
                loadError = "NAT_ERR: " + (msg != null ? msg : "UNKNOWN");
                CenturyMod.LOGGER.error("JNA Native Load Error", t);
            }

        } catch (IOException e) {
            loadError = "EXTRACT_ERR";
            CenturyMod.LOGGER.error("Failed to extract Century Network Layer", e);
        } catch (Throwable e) {
            loadError = "LOAD_FAILED";
            CenturyMod.LOGGER.error("General Load Failure", e);
        }
    }

    public static void stop() {}
}
