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
        String arch = System.getProperty("os.arch").toLowerCase(Locale.ROOT);
        isWindows = os.contains("win");
        boolean isMac = os.contains("mac") || os.contains("darwin");

        String[] candidateNames;
        String extension;

        if (isWindows) {
            extension = ".dll";
            candidateNames = new String[] { "bridge-windows-amd64", "bridge-windows-x86_64", "bridge-windows" };
        } else if (isMac) {
            extension = ".dylib";
            if (arch.contains("aarch64") || arch.contains("arm")) {
                candidateNames = new String[] { "libbridge-darwin-arm64", "libbridge-darwin-amd64", "libbridge-darwin" };
            } else {
                candidateNames = new String[] { "libbridge-darwin-amd64", "libbridge-darwin-arm64", "libbridge-darwin" };
            }
        } else {
            extension = ".so";
            if (arch.contains("aarch64") || arch.contains("arm")) {
                candidateNames = new String[] { "libbridge-linux-arm64", "libbridge-linux-amd64", "libbridge-linux" };
            } else {
                candidateNames = new String[] { "libbridge-linux-amd64", "libbridge-linux-x86_64", "libbridge-linux" };
            }
        }

        try {
            Path tempDir = Paths.get(System.getProperty("java.io.tmpdir"), "century_bridge_native");
            Files.createDirectories(tempDir);

            // Find matching bundled resource
            InputStream is = null;
            String matchedName = null;
            for (String cand : candidateNames) {
                InputStream stream = BridgeManager.class.getResourceAsStream("/assets/century/bin/" + cand + extension);
                if (stream != null) {
                    is = stream;
                    matchedName = cand;
                    break;
                }
            }

            if (is == null) {
                loadError = "RES_MISSING";
                CenturyMod.LOGGER.warn("Native bridge binary not found in mod resources for OS: " + os + " (" + arch + ")");
                return;
            }

            File libFile = tempDir.resolve(matchedName + extension).toFile();

            // Break inode link to prevent SIGBUS on Linux if mapped or locked
            if (libFile.exists()) {
                try {
                    Files.delete(libFile.toPath());
                } catch (Throwable t) {
                    libFile = tempDir.resolve(matchedName + "_" + System.currentTimeMillis() + extension).toFile();
                }
            }

            try (InputStream inStream = is) {
                Files.copy(inStream, libFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            }

            try {
                bridge = Native.load(libFile.getAbsolutePath(), BridgeLib.class);
                bridge.StartProxy(getDecryptedKey());
                CenturyMod.LOGGER.info("Century Network Layer (Native) initialized successfully from " + matchedName + extension);
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
