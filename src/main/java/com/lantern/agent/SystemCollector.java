package com.lantern.agent;

import com.lantern.agent.model.SystemSnapshot;
import com.sun.management.OperatingSystemMXBean;

import java.io.*;
import java.lang.management.ManagementFactory;
import java.net.*;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** Cross-platform, read-only host telemetry for explicitly authorized Lantern Agents. */
public class SystemCollector {
    private final OperatingSystemMXBean osBean = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);

    public SystemSnapshot collect() {
        String osName = System.getProperty("os.name", "unknown");
        boolean windows = osName.toLowerCase(Locale.ROOT).contains("win");
        boolean mac = osName.toLowerCase(Locale.ROOT).contains("mac");

        long memTotal = safePhysicalMemoryTotal();
        long memFree = safePhysicalMemoryFree();
        if (memTotal == 0 && !windows && !mac) {
            memTotal = readMem("MemTotal:");
            memFree = readMem("MemAvailable:");
        }

        File root = windows ? new File(System.getenv().getOrDefault("SystemDrive", "C:") + "\\") : new File("/");
        long diskTotal = safe(root::getTotalSpace);
        long diskFree = safe(root::getFreeSpace);
        long diskUsed = Math.max(0, diskTotal - diskFree);

        long uptime = safeUptime(windows, mac);
        double cpu = safeCpuLoad();
        String kernel = windows ? commandFirst("powershell", "-NoProfile", "-Command", "[Environment]::OSVersion.Version.ToString()")
                : mac ? commandFirst("uname", "-sr") : commandFirst("uname", "-sr");

        List<String> networks = networkInterfaces();
        List<String> processes = processList(windows, mac);
        boolean batteryPresent = batteryPresent(windows, mac);
        int batteryPercent = batteryPercent(windows, mac);

        return new SystemSnapshot(
                Instant.now(),
                host(),
                osName + " " + System.getProperty("os.version", ""),
                blankToUnknown(kernel),
                System.getProperty("os.arch", "unknown"),
                hardwareModel(windows, mac),
                cpuModel(windows, mac),
                gpu(windows, mac),
                loggedInUser(windows, mac),
                System.getProperty("java.version", "unknown"),
                Runtime.getRuntime().availableProcessors(),
                cpu,
                memTotal,
                Math.max(0, memTotal - memFree),
                memFree,
                diskTotal,
                diskUsed,
                diskFree,
                uptime,
                batteryPresent,
                batteryPercent,
                networks,
                processes
        );
    }

    private String host() {
        try { return InetAddress.getLocalHost().getHostName(); }
        catch (Exception e) { return "unknown"; }
    }

    private long safePhysicalMemoryTotal() {
        try { return osBean == null ? 0 : osBean.getTotalMemorySize(); }
        catch (Exception e) { return 0; }
    }

    private long safePhysicalMemoryFree() {
        try { return osBean == null ? 0 : osBean.getFreeMemorySize(); }
        catch (Exception e) { return 0; }
    }

    private double safeCpuLoad() {
        try {
            double load = osBean == null ? -1 : osBean.getCpuLoad() * 100.0;
            return Double.isFinite(load) && load >= 0 ? Math.min(100, load) : -1;
        } catch (Exception e) { return -1; }
    }

    private long readMem(String key) {
        try {
            for (String line : Files.readAllLines(Path.of("/proc/meminfo"))) {
                if (line.startsWith(key)) {
                    String[] parts = line.trim().split("\\s+");
                    return Long.parseLong(parts[1]) * 1024L;
                }
            }
        } catch (Exception ignored) {}
        return 0;
    }

    private long safeUptime(boolean windows, boolean mac) {
        if (windows) {
            String v = commandFirst("powershell", "-NoProfile", "-Command", "[Environment]::TickCount64");
            try { return Long.parseLong(v.trim()) / 1000L; } catch (Exception ignored) {}
        }
        if (mac) {
            String v = commandFirst("sysctl", "-n", "kern.boottime");
            long boot = parseBootEpoch(v);
            if (boot > 0) return Math.max(0, Instant.now().getEpochSecond() - boot);
        }
        try { return (long) Double.parseDouble(Files.readString(Path.of("/proc/uptime")).split("\\s+")[0]); }
        catch (Exception e) { return 0; }
    }

    private long parseBootEpoch(String value) {
        try {
            int sec = value.indexOf("sec =");
            if (sec >= 0) {
                String n = value.substring(sec + 5).replaceAll("[^0-9].*", "");
                return Long.parseLong(n);
            }
        } catch (Exception ignored) {}
        return 0;
    }

    private String hardwareModel(boolean windows, boolean mac) {
        if (mac) return blankToUnknown(commandFirst("sysctl", "-n", "hw.model"));
        if (windows) return blankToUnknown(commandFirst("powershell", "-NoProfile", "-Command", "(Get-CimInstance Win32_ComputerSystem).Model"));
        String dmi = readFirstExisting("/sys/devices/virtual/dmi/id/product_name", "/sys/class/dmi/id/product_name");
        return blankToUnknown(dmi);
    }

    private String cpuModel(boolean windows, boolean mac) {
        if (mac) return blankToUnknown(commandFirst("sysctl", "-n", "machdep.cpu.brand_string"));
        if (windows) return blankToUnknown(commandFirst("powershell", "-NoProfile", "-Command", "(Get-CimInstance Win32_Processor | Select-Object -First 1 -ExpandProperty Name)"));
        String l = readFirstLineContaining("/proc/cpuinfo", "model name");
        return blankToUnknown(l == null ? null : l.replaceFirst("^.*?:\\s*", ""));
    }

    private String gpu(boolean windows, boolean mac) {
        if (mac) return blankToUnknown(commandFirst("system_profiler", "SPDisplaysDataType"));
        if (windows) return blankToUnknown(commandFirst("powershell", "-NoProfile", "-Command", "(Get-CimInstance Win32_VideoController | Select-Object -ExpandProperty Name) -join '; '"));
        List<String> l = commandLines(new String[]{"sh", "-c", "command -v lspci >/dev/null 2>&1 && lspci | grep -Ei 'vga|3d|display' | head -4 || true"}, 4);
        return blankToUnknown(String.join(" | ", l));
    }

    private String loggedInUser(boolean windows, boolean mac) {
        if (windows) return blankToUnknown(System.getenv("USERNAME"));
        return blankToUnknown(System.getenv("USER") != null ? System.getenv("USER") : commandFirst("id", "-un"));
    }

    private List<String> networkInterfaces() {
        List<String> out = new ArrayList<>();
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback()) continue;
                String mac = formatMac(ni.getHardwareAddress());
                String addresses = Collections.list(ni.getInetAddresses()).stream().map(InetAddress::getHostAddress).toList().toString();
                out.add(ni.getName() + " | MAC " + (mac == null ? "unknown" : mac) + " | " + addresses);
            }
        } catch (Exception ignored) {}
        return out;
    }

    private List<String> processList(boolean windows, boolean mac) {
        if (windows) {
            return commandLines(new String[]{"powershell", "-NoProfile", "-Command", "Get-Process | Sort-Object CPU -Descending | Select-Object -First 25 Id,ProcessName,CPU | Format-Table -AutoSize | Out-String"}, 32);
        }
        return commandLines(new String[]{"sh", "-c", "ps -eo pid,comm,%cpu,%mem --sort=-%cpu | head -26"}, 32);
    }

    private boolean batteryPresent(boolean windows, boolean mac) {
        return batteryPercent(windows, mac) >= 0 || (mac && !commandLines(new String[]{"pmset", "-g", "batt"}, 5).isEmpty());
    }

    private int batteryPercent(boolean windows, boolean mac) {
        if (windows) {
            String v = commandFirst("powershell", "-NoProfile", "-Command", "(Get-CimInstance Win32_Battery | Select-Object -First 1 -ExpandProperty EstimatedChargeRemaining)");
            try { return Integer.parseInt(v.trim()); } catch (Exception ignored) { return -1; }
        }
        if (mac) {
            String v = commandFirst("sh", "-c", "pmset -g batt | grep -Eo '[0-9]+%' | head -1 | tr -d '%' || true");
            try { return Integer.parseInt(v.trim()); } catch (Exception ignored) { return -1; }
        }
        try {
            try (var s = Files.list(Path.of("/sys/class/power_supply"))) {
                for (Path p : s.filter(x -> x.getFileName().toString().startsWith("BAT")).toList()) {
                    try { return Integer.parseInt(Files.readString(p.resolve("capacity")).trim()); } catch (Exception ignored) {}
                }
            }
        } catch (Exception ignored) {}
        return -1;
    }

    private String readFirstExisting(String... paths) {
        for (String p : paths) try { if (Files.exists(Path.of(p))) return Files.readString(Path.of(p)).trim(); } catch (Exception ignored) {}
        return null;
    }

    private String readFirstLineContaining(String path, String key) {
        try {
            for (String line : Files.readAllLines(Path.of(path))) if (line.toLowerCase(Locale.ROOT).startsWith(key.toLowerCase(Locale.ROOT))) return line;
        } catch (Exception ignored) {}
        return null;
    }

    private String commandFirst(String... cmd) {
        List<String> lines = commandLines(cmd, 1);
        return lines.isEmpty() ? null : lines.get(0).trim();
    }

    private List<String> commandLines(String... cmd) { return commandLines(cmd, 50); }

    private List<String> commandLines(String[] cmd, int max) {
        try {
            Process p = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            List<String> out = new ArrayList<>();
            try (BufferedReader r = p.inputReader()) {
                String line;
                while ((line = r.readLine()) != null && out.size() < max) out.add(line);
            }
            p.waitFor(3, TimeUnit.SECONDS);
            return out;
        } catch (Exception e) { return List.of(); }
    }

    private long safe(Supplier supplier) { try { return supplier.get(); } catch (Exception e) { return 0; } }
    private interface Supplier { long get(); }
    private String blankToUnknown(String value) { return value == null || value.isBlank() ? "Unknown" : value.trim(); }

    private String formatMac(byte[] mac) {
        if (mac == null || mac.length == 0) return null;
        StringBuilder b = new StringBuilder();
        for (byte x : mac) { if (b.length() > 0) b.append(':'); b.append(String.format("%02x", x & 0xff)); }
        return b.toString();
    }
}
