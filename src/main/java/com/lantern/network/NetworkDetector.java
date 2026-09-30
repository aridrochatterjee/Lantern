package com.lantern.network;


import java.io.BufferedReader;
import java.io.IOException;
import java.net.Inet4Address;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class NetworkDetector {

    /**
     * Detects the first active IPv4 network interface.
     */
    public NetworkInterfaceInfo detect() throws Exception {

        Enumeration<NetworkInterface> interfaces =
                NetworkInterface.getNetworkInterfaces();

        while (interfaces.hasMoreElements()) {

            NetworkInterface networkInterface =
                    interfaces.nextElement();

            if (!isUsableInterface(networkInterface)) {
                continue;
            }

            for (InterfaceAddress interfaceAddress :
                    networkInterface.getInterfaceAddresses()) {

                if (!(interfaceAddress.getAddress() instanceof Inet4Address)) {
                    continue;
                }

                String ipAddress =
                        interfaceAddress.getAddress().getHostAddress();

                int prefixLength =
                        interfaceAddress.getNetworkPrefixLength();

                if (prefixLength < 1 || prefixLength > 32) {
                    continue;
                }

                String networkAddress =
                        calculateNetwork(ipAddress, prefixLength);

                return new NetworkInterfaceInfo(
                        networkInterface.getName(),
                        ipAddress,
                        networkAddress,
                        prefixLength,
                        defaultGateway(),
                        dnsServers()
                );
            }
        }

        throw new IllegalStateException(
                "No active IPv4 network interface found."
        );
    }

    /**
     * Checks whether a network interface can be used.
     */
    private boolean isUsableInterface(
            NetworkInterface networkInterface
    ) throws IOException {

        return networkInterface.isUp()
                && !networkInterface.isLoopback()
                && !networkInterface.isVirtual();
    }

    /**
     * Calculates the network address from an IPv4 address
     * and its CIDR prefix length.
     *
     * Example:
     * 192.168.1.25/24 -> 192.168.1.0
     */
    private String calculateNetwork(
            String ipAddress,
            int prefixLength
    ) {

        long ipValue = ipv4ToLong(ipAddress);

        long mask = prefixLength == 0
                ? 0
                : (0xFFFFFFFFL << (32 - prefixLength))
                    & 0xFFFFFFFFL;

        long networkValue = ipValue & mask;

        return longToIpv4(networkValue);
    }

    /**
     * Attempts to detect the system's default gateway.
     *
     * Supports:
     * - Windows
     * - macOS
     * - Linux
     */
    private String defaultGateway() {

        String operatingSystem =
                System.getProperty(
                        "os.name",
                        ""
                ).toLowerCase(Locale.ROOT);

        try {

            if (operatingSystem.contains("win")) {
                return detectWindowsGateway();
            }

            if (operatingSystem.contains("mac")) {
                return detectMacGateway();
            }

            return detectLinuxGateway();

        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Gets the default gateway on Windows.
     */
    private String detectWindowsGateway() {

        String[] command = {
                "powershell",
                "-NoProfile",
                "-Command",
                "(Get-NetRoute -DestinationPrefix " +
                        "'0.0.0.0/0' | " +
                        "Sort-Object RouteMetric | " +
                        "Select-Object -First 1 " +
                        "-ExpandProperty NextHop)"
        };

        for (String line : command(command)) {

            String value = line.trim();

            if (isIpv4(value)) {
                return value;
            }
        }

        return null;
    }

    /**
     * Gets the default gateway on macOS.
     */
    private String detectMacGateway() {

        for (String line :
                command("route", "-n", "get", "default")) {

            String value = line.trim();

            if (value.startsWith("gateway:")) {
                return value
                        .substring(value.indexOf(':') + 1)
                        .trim();
            }
        }

        return null;
    }

    /**
     * Gets the default gateway on Linux.
     */
    private String detectLinuxGateway() {

        String[] command = {
                "sh",
                "-c",
                "ip route show default 2>/dev/null | head -1"
        };

        for (String line : command(command)) {

            String[] parts =
                    line.trim().split("\\s+");

            for (int i = 0; i < parts.length - 1; i++) {

                if (parts[i].equals("via")) {
                    return parts[i + 1];
                }
            }
        }

        return null;
    }

    /**
     * Attempts to find configured IPv4 DNS servers.
     *
     * Supports:
     * - Windows
     * - macOS
     * - Linux
     */
    private List<String> dnsServers() {

        String operatingSystem =
                System.getProperty(
                        "os.name",
                        ""
                ).toLowerCase(Locale.ROOT);

        Set<String> servers =
                new LinkedHashSet<>();

        try {

            if (operatingSystem.contains("win")) {
                detectWindowsDns(servers);

            } else if (operatingSystem.contains("mac")) {
                detectMacDns(servers);

            } else {
                detectLinuxDns(servers);
            }

        } catch (Exception ignored) {
            // DNS detection is optional.
        }

        return List.copyOf(servers);
    }

    /**
     * Gets DNS servers on Windows.
     */
    private void detectWindowsDns(
            Set<String> servers
    ) {

        String[] command = {
                "powershell",
                "-NoProfile",
                "-Command",
                "Get-DnsClientServerAddress " +
                        "-AddressFamily IPv4 | " +
                        "ForEach-Object {$_.ServerAddresses} | " +
                        "Where-Object {$_}"
        };

        for (String line : command(command)) {

            String value = line.trim();

            if (isIpv4(value)) {
                servers.add(value);
            }
        }
    }

    /**
     * Gets DNS servers on macOS.
     */
    private void detectMacDns(
            Set<String> servers
    ) {

        String[] command = {
                "sh",
                "-c",
                "scutil --dns 2>/dev/null | " +
                        "grep 'nameserver\\[' | head -6"
        };

        for (String line : command(command)) {

            String[] parts =
                    line.trim().split("\\s+");

            if (parts.length > 2 && isIpv4(parts[2])) {
                servers.add(parts[2]);
            }
        }
    }

    /**
     * Gets DNS servers from /etc/resolv.conf on Linux.
     */
    private void detectLinuxDns(
            Set<String> servers
    ) {

        try {

            List<String> lines =
                    Files.readAllLines(
                            Path.of("/etc/resolv.conf")
                    );

            for (String line : lines) {

                String value = line.trim();

                if (!value.startsWith("nameserver ")) {
                    continue;
                }

                String server =
                        value.substring("nameserver ".length())
                                .trim();

                if (isIpv4(server)) {
                    servers.add(server);
                }
            }

        } catch (Exception ignored) {
            // /etc/resolv.conf may not exist.
        }
    }

    /**
     * Executes a system command and returns its output.
     */
    private List<String> command(
            String... command
    ) {

        try {

            Process process =
                    new ProcessBuilder(command)
                            .redirectErrorStream(true)
                            .start();

            List<String> output =
                    new ArrayList<>();

            try (BufferedReader reader =
                         process.inputReader()) {

                String line;

                while ((line = reader.readLine()) != null) {
                    output.add(line);
                }
            }

            process.waitFor();

            return output;

        } catch (Exception ignored) {
            return List.of();
        }
    }

    /**
     * Checks whether a string is a valid IPv4 address.
     */
    private boolean isIpv4(String value) {

        if (value == null) {
            return false;
        }

        return value.matches(
                "\\d+(\\.\\d+){3}"
        );
    }

    /**
     * Converts an IPv4 address into a 32-bit long value.
     *
     * Example:
     * 192.168.1.1 -> 3232235777
     */
    public static long ipv4ToLong(String ipAddress) {

        String[] parts =
                ipAddress.split("\\.");

        long value = 0;

        for (String part : parts) {
            value = (value << 8)
                    | Integer.parseInt(part);
        }

        return value & 0xFFFFFFFFL;
    }

    /**
     * Converts a 32-bit long value back into an IPv4 address.
     *
     * Example:
     * 3232235777 -> 192.168.1.1
     */
    public static String longToIpv4(long value) {

        return String.format(
                "%d.%d.%d.%d",
                (value >>> 24) & 255,
                (value >>> 16) & 255,
                (value >>> 8) & 255,
                value & 255
        );
    }
}