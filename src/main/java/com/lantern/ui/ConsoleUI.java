package com.lantern.ui;

import com.lantern.agent.AgentClient;
import com.lantern.history.NetworkEvent;
import com.lantern.model.Device;
import com.lantern.model.Service;
import com.lantern.network.NetworkInterfaceInfo;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Scanner;

public class ConsoleUI {

    private static final int PROGRESS_BAR_WIDTH = 34;
    private static final int MAX_PROCESS_DISPLAY = 25;

    private final Scanner scanner =
            new Scanner(System.in);

    public void banner() {

        System.out.println(
                "\n╔══════════════════════════════════════════════════════════════════════════════╗"
                        + "\n║                                LANTERN v1.1                                  ║"
                        + "\n║              Local Network Observatory + Host Intelligence                   ║"
                        + "\n╚══════════════════════════════════════════════════════════════════════════════╝\n"
        );

        System.out.printf(
                "Platform : %s %s (%s)%n"
                        + "Java     : %s%n%n",
                System.getProperty(
                        "os.name",
                        "Unknown"
                ),
                System.getProperty(
                        "os.version",
                        ""
                ),
                System.getProperty(
                        "os.arch",
                        "Unknown"
                ),
                System.getProperty(
                        "java.version",
                        "Unknown"
                )
        );
    }

    public void network(
            NetworkInterfaceInfo network
    ) {

        String dns =
                network.getDnsServers().isEmpty()
                        ? "Unknown"
                        : String.join(
                                ", ",
                                network.getDnsServers()
                        );

        System.out.printf(
                "Interface : %s%n"
                        + "Local IP  : %s%n"
                        + "Network   : %s/%d%n"
                        + "Gateway   : %s%n"
                        + "DNS       : %s%n%n",
                network.getInterfaceName(),
                network.getLocalAddress(),
                network.getNetworkAddress(),
                network.getPrefixLength(),
                nz(network.getDefaultGateway()),
                dns
        );
    }

    public void progress(
            int done,
            int total
    ) {

        int filled =
                total == 0
                        ? PROGRESS_BAR_WIDTH
                        : done * PROGRESS_BAR_WIDTH / total;

        String bar =
                "█".repeat(filled)
                        + "░".repeat(
                                PROGRESS_BAR_WIDTH - filled
                        );

        int percent =
                total == 0
                        ? 100
                        : done * 100 / total;

        System.out.printf(
                "\rDiscovering [%s] %3d%%",
                bar,
                percent
        );

        if (done == total) {
            System.out.println();
        }
    }

    public void results(
            List<Device> devices
    ) {

        System.out.println(
                "\nDevices discovered: "
                        + devices.size()
        );

        if (devices.isEmpty()) {
            return;
        }

        printResultHeader();

        for (int i = 0; i < devices.size(); i++) {
            printDeviceRow(
                    i + 1,
                    devices.get(i)
            );
        }

        System.out.println(
                "\nTip: press 'd' to inspect network evidence "
                        + "and authorized Agent/Admin Mode telemetry."
        );
    }

    private void printResultHeader() {

        System.out.printf(
                "%-4s %-15s %-23s %-18s %-18s %-13s %-9s %-10s%n",
                "#",
                "IP",
                "NAME",
                "VENDOR",
                "TYPE",
                "STATUS",
                "LATENCY",
                "SERVICES"
        );

        System.out.println(
                "----------------------------------------------------------------------------------------------------------------------"
        );
    }

    private void printDeviceRow(
            int number,
            Device device
    ) {

        String latency =
                device.getLatencyMs() >= 0
                        ? device.getLatencyMs() + " ms"
                        : "-";

        System.out.printf(
                "%-4d %-15s %-23s %-18s %-18s %-13s %-9s %-10d%n",
                number,
                device.getIpAddress(),
                cut(device.displayName(), 23),
                cut(nz(device.getVendor()), 18),
                cut(nz(device.getType()), 18),
                device.isOnline()
                        ? "ONLINE"
                        : "OFFLINE",
                latency,
                device.getServices().size()
        );
    }

    public Device select(
            List<Device> devices
    ) {

        if (devices.isEmpty()) {
            return null;
        }

        while (true) {

            printDeviceSelection(devices);

            System.out.println("[0] Back");
            System.out.print("Enter number: ");

            String input =
                    scanner.nextLine().trim();

            try {

                int selection =
                        Integer.parseInt(input);

                if (selection == 0) {
                    return null;
                }

                if (selection >= 1
                        && selection <= devices.size()) {

                    return devices.get(
                            selection - 1
                    );
                }

            } catch (Exception ignored) {
                // Invalid input is handled below.
            }

            System.out.println(
                    "Invalid selection."
            );
        }
    }

    private void printDeviceSelection(
            List<Device> devices
    ) {

        System.out.println(
                "\nSelect a device:"
        );

        for (int i = 0; i < devices.size(); i++) {

            Device device =
                    devices.get(i);

            System.out.printf(
                    "[%d] %-17s %-30s %s%n",
                    i + 1,
                    device.getIpAddress(),
                    device.displayName(),
                    nz(device.getVendor())
            );
        }
    }

    public void details(
            Device device
    ) {

        if (device == null) {
            return;
        }

        printDetailsHeader();
        printBasicDetails(device);
        printServices(device);

        AgentClient.AgentData agentData =
                device.getAgentData();

        if (agentData != null) {
            printAgentTelemetry(agentData);
        } else {
            printAgentUnavailable();
        }
    }

    private void printDetailsHeader() {

        System.out.println(
                "\n╔══════════════════════════════════════════════════════════════════════════════╗"
                        + "\n║                              DEVICE DETAILS                                  ║"
                        + "\n╚══════════════════════════════════════════════════════════════════════════════╝"
        );
    }

    private void printBasicDetails(
            Device device
    ) {

        String latency =
                device.getLatencyMs() >= 0
                        ? device.getLatencyMs() + " ms"
                        : "Unknown";

        System.out.printf(
                "%nName          : %s%n"
                        + "IP Address    : %s%n"
                        + "Hostname      : %s%n"
                        + "MAC           : %s%n"
                        + "Vendor        : %s%n"
                        + "Type          : %s%n"
                        + "Latency       : %s%n"
                        + "Status        : %s%n"
                        + "Discovered    : %s%n",
                device.displayName(),
                device.getIpAddress(),
                nz(device.getHostname()),
                nz(device.getMacAddress()),
                nz(device.getVendor()),
                nz(device.getType()),
                latency,
                device.isOnline()
                        ? "ONLINE"
                        : "OFFLINE",
                device.getDiscoveredAt()
        );
    }

    private void printServices(
            Device device
    ) {

        System.out.println(
                "\nOpen TCP Services"
        );

        System.out.println(
                "-----------------"
        );

        if (device.getServices().isEmpty()) {

            System.out.println(
                    "No open TCP services detected."
            );

            return;
        }

        for (Service service :
                device.getServices()) {

            String banner =
                    service.banner();

            String bannerText =
                    banner == null
                            || banner.isBlank()
                            ? ""
                            : "  |  "
                                    + clean(
                                            banner,
                                            90
                                    );

            System.out.printf(
                    "%-7d %-18s%s%n",
                    service.port(),
                    service.name(),
                    bannerText
            );
        }
    }

    private void printAgentTelemetry(
            AgentClient.AgentData agent
    ) {

        System.out.println(
                "\n════════════════ AUTHORIZED AGENT TELEMETRY ════════════════"
        );

        printAgentSystemInfo(agent);
        printNetworkInterfaces(agent);
        printAdminPermissions(agent);
        printProcesses(agent);
    }

    private void printAgentSystemInfo(
            AgentClient.AgentData agent
    ) {

        String battery =
                agent.batteryPercent() >= 0
                        ? agent.batteryPercent() + "%"
                        : "N/A";

        System.out.printf(
                "Agent Host      : %s%n"
                        + "OS              : %s%n"
                        + "Kernel          : %s%n"
                        + "Architecture    : %s%n"
                        + "Hardware        : %s%n"
                        + "CPU             : %s%n"
                        + "CPU Cores       : %d%n"
                        + "CPU Load        : %s%n"
                        + "GPU             : %s%n"
                        + "RAM             : %s used / %s total%n"
                        + "RAM Available   : %s%n"
                        + "Disk            : %s used / %s total%n"
                        + "Disk Free       : %s%n"
                        + "Uptime          : %s%n"
                        + "Battery         : %s%n"
                        + "Logged-in user  : %s%n"
                        + "Java            : %s%n"
                        + "Agent received  : %s%n",
                nz(agent.hostname()),
                nz(agent.os()),
                nz(agent.kernel()),
                nz(agent.architecture()),
                nz(agent.hardwareModel()),
                nz(agent.cpuModel()),
                agent.cpuCores(),
                fmtPercent(
                        agent.cpuLoadPercent()
                ),
                nz(agent.gpu()),
                bytes(agent.memoryUsedBytes()),
                bytes(agent.memoryTotalBytes()),
                bytes(agent.memoryAvailableBytes()),
                bytes(agent.diskUsedBytes()),
                bytes(agent.diskTotalBytes()),
                bytes(agent.diskFreeBytes()),
                uptime(agent.uptimeSeconds()),
                battery,
                nz(agent.loggedInUser()),
                nz(agent.javaVersion()),
                nz(String.valueOf(
                        agent.receivedAt()
                ))
        );
    }

    private void printNetworkInterfaces(
            AgentClient.AgentData agent
    ) {

        System.out.println(
                "\nNetwork Interfaces"
        );

        System.out.println(
                "------------------"
        );

        if (agent.networkInterfaces().isEmpty()) {

            System.out.println(
                    "No interface data returned."
            );

            return;
        }

        agent.networkInterfaces()
                .forEach(
                        value ->
                                System.out.println(
                                        "  " + value
                                )
                );
    }

    private void printAdminPermissions(
            AgentClient.AgentData agent
    ) {

        if (agent.adminPermissions().isEmpty()) {
            return;
        }

        System.out.println(
                "\nAdmin Mode Permissions"
        );

        System.out.println(
                "-----------------------"
        );

        agent.adminPermissions()
                .forEach(
                        permission ->
                                System.out.println(
                                        "  ✓ " + permission
                                )
                );
    }

    private void printProcesses(
            AgentClient.AgentData agent
    ) {

        System.out.println(
                "\nTop Processes"
        );

        System.out.println(
                "--------------"
        );

        if (agent.processes().isEmpty()) {

            System.out.println(
                    "No process data returned."
            );

            return;
        }

        agent.processes()
                .stream()
                .limit(MAX_PROCESS_DISPLAY)
                .forEach(
                        process ->
                                System.out.println(
                                        "  " + process
                                )
                );
    }

    private void printAgentUnavailable() {

        System.out.println(
                "\nAuthorized Agent : not paired"
        );

        System.out.println(
                "Network-only mode cannot reliably know private host facts "
                        + "such as CPU, RAM, GPU, disk or processes."
        );
    }

    public void events(
            List<NetworkEvent> events
    ) {

        if (events.isEmpty()) {

            System.out.println(
                    "No network changes detected."
            );

            return;
        }

        System.out.println(
                "\nNetwork events:"
        );

        for (NetworkEvent event : events) {

            System.out.printf(
                    "%-11s %-15s %s%n",
                    event.type(),
                    event.device().getIpAddress(),
                    event.device().displayName()
            );
        }
    }

    private String nz(
            String value
    ) {

        return value == null
                || value.isBlank()
                ? "Unknown"
                : value;
    }

    private String cut(
            String value,
            int maxLength
    ) {

        if (value == null) {
            return "";
        }

        return value.length() <= maxLength
                ? value
                : value.substring(
                        0,
                        maxLength - 3
                ) + "...";
    }

    private String clean(
            String value,
            int maxLength
    ) {

        return cut(
                value.replaceAll(
                        "\\s+",
                        " "
                ),
                maxLength
        );
    }

    private String bytes(
            long value
    ) {

        if (value < 1) {
            return "0 B";
        }

        String[] units = {
                "B",
                "KB",
                "MB",
                "GB",
                "TB"
        };

        int unitIndex =
                (int) Math.min(
                        units.length - 1,
                        Math.floor(
                                Math.log(value)
                                        / Math.log(1024)
                        )
                );

        return String.format(
                Locale.ROOT,
                "%.1f %s",
                value
                        / Math.pow(
                                1024,
                                unitIndex
                        ),
                units[unitIndex]
        );
    }

    private String uptime(
            long seconds
    ) {

        Duration duration =
                Duration.ofSeconds(
                        Math.max(0, seconds)
                );

        return duration.toDays()
                + "d "
                + (duration.toHours() % 24)
                + "h "
                + (duration.toMinutes() % 60)
                + "m";
    }

    private String fmtPercent(
            double value
    ) {

        return value < 0
                ? "Unknown"
                : String.format(
                        Locale.ROOT,
                        "%.1f%%",
                        value
                );
    }
}