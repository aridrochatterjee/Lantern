package com.lantern.ui;

import com.lantern.agent.AgentClient;
import com.lantern.model.Device;
import com.lantern.model.Service;
import com.lantern.network.NetworkInterfaceInfo;

import java.time.Duration;
import java.util.*;

public class ConsoleUI {
    private final Scanner scanner = new Scanner(System.in);

    public void banner() {
        System.out.println("\n╔══════════════════════════════════════════════════════════════════════════════╗"
                + "\n║                                LANTERN v1.1                                 ║"
                + "\n║              Local Network Observatory + Host Intelligence                  ║"
                + "\n╚══════════════════════════════════════════════════════════════════════════════╝\n");
        System.out.printf("Platform : %s %s (%s)%nJava     : %s%n%n",
                System.getProperty("os.name", "Unknown"),
                System.getProperty("os.version", ""),
                System.getProperty("os.arch", "Unknown"),
                System.getProperty("java.version", "Unknown"));
    }

    public void network(NetworkInterfaceInfo n) {
        System.out.printf("Interface : %s%nLocal IP  : %s%nNetwork   : %s/%d%nGateway   : %s%nDNS       : %s%n%n",
                n.getInterfaceName(), n.getLocalAddress(), n.getNetworkAddress(), n.getPrefixLength(),
                nz(n.getDefaultGateway()), n.getDnsServers().isEmpty() ? "Unknown" : String.join(", ", n.getDnsServers()));
    }

    public void progress(int done, int total) {
        int width = 34;
        int filled = total == 0 ? width : done * width / total;
        String bar = "█".repeat(filled) + "░".repeat(width - filled);
        System.out.printf("\rDiscovering [%s] %3d%%", bar, total == 0 ? 100 : done * 100 / total);
        if (done == total) System.out.println();
    }

    public void results(List<Device> devices) {
        System.out.println("\nDevices discovered: " + devices.size());
        if (devices.isEmpty()) return;
        System.out.printf("%n%-4s %-15s %-23s %-18s %-18s %-13s %-9s %-10s%n",
                "#", "IP", "NAME", "VENDOR", "TYPE", "STATUS", "LATENCY", "SERVICES");
        System.out.println("----------------------------------------------------------------------------------------------------------------------");
        for (int i = 0; i < devices.size(); i++) {
            Device d = devices.get(i);
            System.out.printf("%-4d %-15s %-23s %-18s %-18s %-13s %-9s %-10d%n",
                    i + 1,
                    d.getIpAddress(),
                    cut(d.displayName(), 23),
                    cut(nz(d.getVendor()), 18),
                    cut(nz(d.getType()), 18),
                    d.isOnline() ? "ONLINE" : "OFFLINE",
                    d.getLatencyMs() >= 0 ? d.getLatencyMs() + " ms" : "-",
                    d.getServices().size());
        }
        System.out.println("\nTip: press 'd' to inspect network evidence and authorized Agent/Admin Mode telemetry.");
    }

    public Device select(List<Device> devices) {
        if (devices.isEmpty()) return null;
        while (true) {
            System.out.println("\nSelect a device:");
            for (int i = 0; i < devices.size(); i++) {
                Device d = devices.get(i);
                System.out.printf("[%d] %-17s %-30s %s%n", i + 1, d.getIpAddress(), d.displayName(), nz(d.getVendor()));
            }
            System.out.println("[0] Back");
            System.out.print("Enter number: ");
            String s = scanner.nextLine().trim();
            try {
                int n = Integer.parseInt(s);
                if (n == 0) return null;
                if (n >= 1 && n <= devices.size()) return devices.get(n - 1);
            } catch (Exception ignored) { }
            System.out.println("Invalid selection.");
        }
    }

    public void details(Device d) {
        if (d == null) return;
        System.out.println("\n╔══════════════════════════════════════════════════════════════════════════════╗"
                + "\n║                              DEVICE DETAILS                                ║"
                + "\n╚══════════════════════════════════════════════════════════════════════════════╝");
        System.out.printf("%nName          : %s%nIP Address    : %s%nHostname      : %s%nMAC           : %s%nVendor        : %s%nType          : %s%nLatency       : %s%nStatus        : %s%nDiscovered    : %s%n",
                d.displayName(), d.getIpAddress(), nz(d.getHostname()), nz(d.getMacAddress()), nz(d.getVendor()),
                nz(d.getType()), d.getLatencyMs() >= 0 ? d.getLatencyMs() + " ms" : "Unknown",
                d.isOnline() ? "ONLINE" : "OFFLINE", d.getDiscoveredAt());

        System.out.println("\nOpen TCP Services");
        System.out.println("-----------------");
        if (d.getServices().isEmpty()) {
            System.out.println("No open TCP services detected.");
        } else {
            for (Service s : d.getServices()) {
                String banner = s.banner();
                System.out.printf("%-7d %-18s%s%n", s.port(), s.name(),
                        banner == null || banner.isBlank() ? "" : "  |  " + clean(banner, 90));
            }
        }

        AgentClient.AgentData a = d.getAgentData();
        if (a != null) {
            System.out.println("\n════════════════ AUTHORIZED AGENT TELEMETRY ════════════════");
            System.out.printf("Agent Host      : %s%nOS              : %s%nKernel          : %s%nArchitecture    : %s%nHardware        : %s%nCPU             : %s%nCPU Cores       : %d%nCPU Load        : %s%nGPU             : %s%nRAM             : %s used / %s total%nRAM Available   : %s%nDisk            : %s used / %s total%nDisk Free       : %s%nUptime          : %s%nBattery         : %s%nLogged-in user  : %s%nJava            : %s%nAgent received  : %s%n",
                    nz(a.hostname()), nz(a.os()), nz(a.kernel()), nz(a.architecture()), nz(a.hardwareModel()), nz(a.cpuModel()),
                    a.cpuCores(), fmtPercent(a.cpuLoadPercent()), nz(a.gpu()),
                    bytes(a.memoryUsedBytes()), bytes(a.memoryTotalBytes()), bytes(a.memoryAvailableBytes()),
                    bytes(a.diskUsedBytes()), bytes(a.diskTotalBytes()), bytes(a.diskFreeBytes()),
                    uptime(a.uptimeSeconds()), a.batteryPercent() >= 0 ? a.batteryPercent() + "%" : "N/A",
                    nz(a.loggedInUser()), nz(a.javaVersion()), nz(String.valueOf(a.receivedAt())));

            System.out.println("\nNetwork Interfaces");
            System.out.println("------------------");
            if (a.networkInterfaces().isEmpty()) System.out.println("No interface data returned.");
            else a.networkInterfaces().forEach(x -> System.out.println("  " + x));

            if (!a.adminPermissions().isEmpty()) {
                System.out.println("\nAdmin Mode Permissions");
                System.out.println("-----------------------");
                a.adminPermissions().forEach(x -> System.out.println("  ✓ " + x));
            }

            System.out.println("\nTop Processes");
            System.out.println("--------------");
            if (a.processes().isEmpty()) System.out.println("No process data returned.");
            else a.processes().stream().limit(25).forEach(x -> System.out.println("  " + x));
        } else {
            System.out.println("\nAuthorized Agent : not paired");
            System.out.println("Network-only mode cannot reliably know private host facts such as CPU, RAM, GPU, disk or processes.");
        }
    }

    public void events(List<com.lantern.history.NetworkEvent> events) {
        if (events.isEmpty()) {
            System.out.println("No network changes detected.");
            return;
        }
        System.out.println("\nNetwork events:");
        for (var e : events) {
            System.out.printf("%-11s %-15s %s%n", e.type(), e.device().getIpAddress(), e.device().displayName());
        }
    }

    private String nz(String s) { return s == null || s.isBlank() ? "Unknown" : s; }
    private String cut(String s, int n) { if (s == null) return ""; return s.length() <= n ? s : s.substring(0, n - 3) + "..."; }
    private String clean(String s, int n) { return cut(s.replaceAll("\\s+", " "), n); }
    private String bytes(long v) { if (v < 1) return "0 B"; String[] u = {"B", "KB", "MB", "GB", "TB"}; int i = (int) Math.min(u.length - 1, Math.floor(Math.log(v) / Math.log(1024))); return String.format(Locale.ROOT, "%.1f %s", v / Math.pow(1024, i), u[i]); }
    private String uptime(long s) { Duration d = Duration.ofSeconds(Math.max(0, s)); return d.toDays() + "d " + (d.toHours() % 24) + "h " + (d.toMinutes() % 60) + "m"; }
    private String fmtPercent(double x) { return x < 0 ? "Unknown" : String.format(Locale.ROOT, "%.1f%%", x); }
}
