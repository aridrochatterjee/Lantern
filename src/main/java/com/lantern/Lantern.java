package com.lantern;

import com.lantern.agent.AgentRegistry;
import com.lantern.history.HistoryStore;
import com.lantern.model.Device;
import com.lantern.network.DeviceScanner;
import com.lantern.network.NetworkDetector;
import com.lantern.network.NetworkInterfaceInfo;
import com.lantern.history.NetworkMonitor;
import com.lantern.history.NetworkEvent;
import com.lantern.ui.ConsoleUI;
import com.lantern.web.WebDashboard;

import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

public class Lantern {

    private static final int[] DEFAULT_PORTS = {
            20, 21, 22, 23, 25, 53, 80, 110, 123, 135,
            139, 143, 443, 445, 631, 1433, 1883, 3000,
            3306, 3389, 5000, 5432, 5900, 6379, 8000,
            8080, 8096, 8443
    };

    private static final int DEFAULT_WEB_PORT = 8765;
    private static final int MONITOR_INTERVAL_SECONDS = 10;
    private static final int HISTORY_LINES = 20;

    public static void main(
            String[] args
    ) {

        ConsoleUI ui =
                new ConsoleUI();

        ui.banner();

        try {

            run(
                    args,
                    ui
            );

        } catch (InterruptedException exception) {

            Thread.currentThread().interrupt();

            System.err.println(
                    "Scan interrupted."
            );

        } catch (Exception exception) {

            System.err.println(
                    "Lantern error: "
                            + exception.getMessage()
            );

            exception.printStackTrace();
        }
    }

    private static void run(
            String[] args,
            ConsoleUI ui
    ) throws Exception {

        NetworkInterfaceInfo network =
                detectNetwork();

        ui.network(network);

        int[] ports =
                parsePorts(args);

        String ouiPath =
                parseOui(args);

        int webPort =
                parseWebPort(args);

        DeviceScanner scanner =
                new DeviceScanner(
                        ports,
                        ouiPath
                );

        AgentRegistry agents =
                AgentRegistry.fromArgs(args);

        HistoryStore history =
                new HistoryStore(
                        Paths.get(
                                "data",
                                "history.csv"
                        )
                );

        WebDashboard dashboard =
                new WebDashboard(
                        webPort,
                        agents
                );

        dashboard.start();

        List<Device> currentDevices =
                scan(
                        ui,
                        scanner,
                        network
                );

        agents.enrich(
                currentDevices
        );

        history.saveScan(
                currentDevices
        );

        ui.results(
                currentDevices
        );

        dashboard.update(
                network,
                currentDevices,
                List.of()
        );

        commandLoop(
                ui,
                scanner,
                network,
                currentDevices,
                history,
                dashboard,
                agents,
                webPort
        );
    }

    private static NetworkInterfaceInfo detectNetwork()
            throws Exception {

        return new NetworkDetector()
                .detect();
    }

    private static void commandLoop(
            ConsoleUI ui,
            DeviceScanner scanner,
            NetworkInterfaceInfo network,
            List<Device> currentDevices,
            HistoryStore history,
            WebDashboard dashboard,
            AgentRegistry agents,
            int webPort
    ) throws InterruptedException {

        Scanner input =
                new Scanner(System.in);

        while (true) {

            printCommands();

            System.out.print(
                    "lantern> "
            );

            String command =
                    input.nextLine()
                            .trim()
                            .toLowerCase();

            switch (command) {

                case "d" ->
                        showDeviceDetails(
                                ui,
                                currentDevices
                        );

                case "r" -> {

                    currentDevices =
                            rescan(
                                    ui,
                                    scanner,
                                    network,
                                    currentDevices,
                                    history,
                                    dashboard,
                                    agents
                            );
                }

                case "m" ->
                        monitor(
                                ui,
                                scanner,
                                network,
                                currentDevices,
                                history,
                                dashboard,
                                agents
                        );

                case "h" ->
                        showHistory(history);

                case "w" ->
                        showDashboardUrl(webPort);

                case "q", "exit" -> {

                    System.out.println(
                            "Goodbye."
                    );

                    return;
                }

                default ->
                        System.out.println(
                                "Unknown command."
                        );
            }
        }
    }

    private static void printCommands() {

        System.out.println(
                "\nCommands: "
                        + "[d] details  "
                        + "[r] rescan  "
                        + "[m] monitor  "
                        + "[h] history  "
                        + "[w] dashboard  "
                        + "[q] quit"
        );
    }

    private static void showDeviceDetails(
            ConsoleUI ui,
            List<Device> devices
    ) {

        Device device =
                ui.select(devices);

        if (device != null) {
            ui.details(device);
        }
    }

    private static List<Device> rescan(
            ConsoleUI ui,
            DeviceScanner scanner,
            NetworkInterfaceInfo network,
            List<Device> previousDevices,
            HistoryStore history,
            WebDashboard dashboard,
            AgentRegistry agents
    ) throws InterruptedException {

        List<Device> newDevices =
                scan(
                        ui,
                        scanner,
                        network
                );

        agents.enrich(
                newDevices
        );

        history.saveScan(
                newDevices
        );

        List<NetworkEvent> events =
                new NetworkMonitor()
                        .compare(
                                previousDevices,
                                newDevices
                        );

        ui.results(
                newDevices
        );

        ui.events(events);

        dashboard.update(
                network,
                newDevices,
                events
        );

        return newDevices;
    }

    private static void monitor(
            ConsoleUI ui,
            DeviceScanner scanner,
            NetworkInterfaceInfo network,
            List<Device> currentDevices,
            HistoryStore history,
            WebDashboard dashboard,
            AgentRegistry agents
    ) throws InterruptedException {

        System.out.println(
                "Monitor mode. Rescanning every "
                        + MONITOR_INTERVAL_SECONDS
                        + " seconds. Press Ctrl+C to stop."
        );

        List<Device> previousDevices =
                currentDevices;

        NetworkMonitor networkMonitor =
                new NetworkMonitor();

        while (true) {

            Thread.sleep(
                    MONITOR_INTERVAL_SECONDS
                            * 1000L
            );

            List<Device> newDevices =
                    scan(
                            ui,
                            scanner,
                            network
                    );

            agents.enrich(
                    newDevices
            );

            history.saveScan(
                    newDevices
            );

            List<NetworkEvent> events =
                    networkMonitor.compare(
                            previousDevices,
                            newDevices
                    );

            ui.events(events);
            ui.results(newDevices);

            dashboard.update(
                    network,
                    newDevices,
                    events
            );

            previousDevices =
                    newDevices;
        }
    }

    private static List<Device> scan(
            ConsoleUI ui,
            DeviceScanner scanner,
            NetworkInterfaceInfo network
    ) throws InterruptedException {

        System.out.println(
                "Scanning "
                        + network.getNetworkAddress()
                        + "/"
                        + network.getPrefixLength()
                        + "..."
        );

        return scanner.scan(
                network,
                ui::progress
        );
    }

    private static void showHistory(
            HistoryStore history
    ) {

        history
                .recentLines(HISTORY_LINES)
                .forEach(System.out::println);
    }

    private static void showDashboardUrl(
            int webPort
    ) {

        System.out.println(
                "Dashboard: http://127.0.0.1:"
                        + webPort
        );
    }

    private static int[] parsePorts(
            String[] args
    ) {

        for (String argument : args) {

            if (!argument.startsWith("--ports=")) {
                continue;
            }

            String[] values =
                    argument
                            .substring(8)
                            .split(",");

            int[] ports =
                    new int[values.length];

            int count = 0;

            for (String value : values) {

                try {

                    int port =
                            Integer.parseInt(
                                    value.trim()
                            );

                    if (port > 0
                            && port < 65536) {

                        ports[count++] =
                                port;
                    }

                } catch (NumberFormatException ignored) {
                    // Ignore invalid port values.
                }
            }

            if (count == 0) {
                return DEFAULT_PORTS;
            }

            return Arrays.copyOf(
                    ports,
                    count
            );
        }

        return DEFAULT_PORTS;
    }

    private static String parseOui(
            String[] args
    ) {

        for (String argument : args) {

            if (argument.startsWith("--oui=")) {
                return argument.substring(6);
            }
        }

        return null;
    }

    private static int parseWebPort(
            String[] args
    ) {

        for (String argument : args) {

            if (!argument.startsWith("--web=")) {
                continue;
            }

            try {

                return Integer.parseInt(
                        argument.substring(6)
                );

            } catch (NumberFormatException ignored) {
                // Fall back to the default web port.
            }
        }

        return DEFAULT_WEB_PORT;
    }
}