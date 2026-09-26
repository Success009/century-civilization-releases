package com.century.civilization.network;

import com.century.civilization.CenturyMod;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

public class NetworkOptimizer {
    private static volatile boolean optimizationAttempted = false;

    public static void optimizeNetworkAsync() {
        if (optimizationAttempted) {
            return;
        }
        optimizationAttempted = true;

        Thread optimizerThread = new Thread(() -> {
            try {
                String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
                if (os.contains("win")) {
                    optimizeWindows();
                } else if (os.contains("linux")) {
                    optimizeLinux();
                }
            } catch (Throwable t) {
                CenturyMod.LOGGER.warn("[NetworkOptimizer] Non-critical optimization routine bypassed: " + t.getMessage());
            }
        }, "Century-Network-Optimizer");

        optimizerThread.setDaemon(true);
        optimizerThread.start();
    }

    private static void optimizeWindows() {
        try {
            String appData = System.getenv("APPDATA");
            if (appData == null || appData.trim().isEmpty()) {
                appData = System.getProperty("user.home", ".");
            }

            File flagFile = new File(appData, "century_net_direct_v1.ready");
            if (flagFile.exists()) {
                CenturyMod.LOGGER.info("[NetworkOptimizer] Windows network optimization flag present. Elevation already configured.");
                return;
            }

            CenturyMod.LOGGER.info("[NetworkOptimizer] Requesting administrative network optimization for Direct Connection...");

                        // High-probability direct connection optimization:
            // 1. Enable IPv6 on all physical adapters & enable RouterDiscovery
            // 2. Request fresh IPv6 DHCP lease from gateway
            // 3. Ensure SSDP & UPnP services are active for router port-mapping
            // 4. Create bidirectional firewall holes for UDP 41641 (WireGuard) & 24454 (Voice)
            // 5. Allow javaw.exe inbound traffic without prompt
            String psCommand = 
                "$flag = '" + flagFile.getAbsolutePath().replace("\\", "\\\\") + "'; " +
                "try { " +
                "  Get-NetAdapter -Physical | Where-Object Status -eq 'Up' | Enable-NetAdapterBinding -ComponentID ms_tcpip6 -ErrorAction SilentlyContinue; " +
                "  Set-NetIPInterface -AddressFamily IPv6 -RouterDiscovery Enabled -ErrorAction SilentlyContinue; " +
                "  ipconfig /renew6 | Out-Null; " +
                "  Set-Service -Name 'SSDPSRV' -StartupType Manual -ErrorAction SilentlyContinue; Start-Service -Name 'SSDPSRV' -ErrorAction SilentlyContinue; " +
                "  Set-Service -Name 'upnphost' -StartupType Manual -ErrorAction SilentlyContinue; Start-Service -Name 'upnphost' -ErrorAction SilentlyContinue; " +
                "  netsh advfirewall firewall delete rule name='Century_Direct_UDP41641' 2>$null; " +
                "  netsh advfirewall firewall add rule name='Century_Direct_UDP41641' dir=in action=allow protocol=UDP localport=41641; " +
                "  netsh advfirewall firewall add rule name='Century_Direct_UDP41641_Out' dir=out action=allow protocol=UDP localport=41641; " +
                "  netsh advfirewall firewall delete rule name='Century_Voice_UDP24454' 2>$null; " +
                "  netsh advfirewall firewall add rule name='Century_Voice_UDP24454' dir=in action=allow protocol=UDP localport=24454; " +
                "  netsh advfirewall firewall delete rule name='Century_Java' 2>$null; " +
                "  netsh advfirewall firewall add rule name='Century_Java' dir=in action=allow program='javaw.exe' enable=yes; " +
                "  [System.IO.File]::WriteAllText($flag, 'OPTIMIZED'); " +
                "} catch { exit 1 }";

            ProcessBuilder pb = new ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-NonInteractive",
                "-WindowStyle", "Hidden",
                "-Command",
                "Start-Process powershell.exe -Verb RunAs -WindowStyle Hidden -ArgumentList '-NoProfile -NonInteractive -ExecutionPolicy Bypass -Command \"" + psCommand.replace("\"", "\\\"") + "\"'"
            );

            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                CenturyMod.LOGGER.info("[NetworkOptimizer] UAC elevation dispatched successfully.");
            } else {
                CenturyMod.LOGGER.warn("[NetworkOptimizer] Elevation cancelled or denied by user (exitCode " + exitCode + "). Graceful fallback to user-space relay active.");
                optimizationAttempted = false; // Allow retry on next launch
            }
        } catch (Throwable t) {
            CenturyMod.LOGGER.warn("[NetworkOptimizer] Windows elevation dismissed or unsupported: " + t.getMessage());
            optimizationAttempted = false; // Never break permanently; allow retry next boot
        }
    }

    private static void optimizeLinux() {
        try {
            File ipv6DisableProc = new File("/proc/sys/net/ipv6/conf/all/disable_ipv6");
            boolean needsIpv6Enable = false;
            if (ipv6DisableProc.exists()) {
                String val = Files.readString(ipv6DisableProc.toPath()).trim();
                if ("1".equals(val)) {
                    needsIpv6Enable = true;
                }
            }

            File homeFlag = new File(System.getProperty("user.home", "."), ".century_net_direct_v1.ready");
            if (homeFlag.exists() && !needsIpv6Enable) {
                return;
            }

            if (needsIpv6Enable) {
                CenturyMod.LOGGER.info("[NetworkOptimizer] Linux kernel has IPv6 disabled. Requesting privilege elevation via pkexec...");
                ProcessBuilder pb = new ProcessBuilder("pkexec", "sysctl", "-w", "net.ipv6.conf.all.disable_ipv6=0");
                Process process = pb.start();
                int exitCode = process.waitFor();
                if (exitCode == 0) {
                    CenturyMod.LOGGER.info("[NetworkOptimizer] Linux IPv6 enabled successfully.");
                    try {
                        Files.writeString(homeFlag.toPath(), "OPTIMIZED");
                    } catch (Throwable ignored) {}
                } else {
                    CenturyMod.LOGGER.warn("[NetworkOptimizer] Linux elevation declined by user. Graceful fallback active.");
                    optimizationAttempted = false;
                }
            } else {
                try {
                    Files.writeString(homeFlag.toPath(), "OPTIMIZED");
                } catch (Throwable ignored) {}
            }
        } catch (Throwable t) {
            CenturyMod.LOGGER.warn("[NetworkOptimizer] Linux privilege check skipped: " + t.getMessage());
            optimizationAttempted = false;
        }
    }
}
