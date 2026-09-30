package com.lantern.network;

import com.lantern.model.Device;
import com.lantern.model.Service;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class DeviceScanner {

    private static final int MAX_THREADS = 64;
    private static final int MIN_THREADS = 4;

    private static final int PING_TIMEOUT_MS = 1000;
    private static final int PORT_TIMEOUT_MS = 250;

    private final int[] ports;

    private final PingService pingService;
    private final MacAddressService macAddressService;
    private final PortScanner portScanner;
    private final VendorLookup vendorLookup;
    private final DeviceClassifier classifier;

    public DeviceScanner(int[] ports, String ouiPath) {
        this.ports = ports;

        this.pingService = new PingService();
        this.macAddressService = new MacAddressService();
        this.portScanner = new PortScanner();
        this.vendorLookup = new VendorLookup(ouiPath);
        this.classifier = new DeviceClassifier();
    }

    /**
     * Scans all hosts on the detected network.
     */
    public List<Device> scan(
            NetworkInterfaceInfo network,
            ScanListener listener
    ) throws InterruptedException {

        List<String> addresses = getHosts(
                network.getNetworkAddress(),
                network.getPrefixLength()
        );

        int threadCount = Math.min(
                MAX_THREADS,
                Math.max(MIN_THREADS, addresses.size())
        );

        ExecutorService executor =
                Executors.newFixedThreadPool(threadCount);

        try {
            return scanHosts(
                    addresses,
                    executor,
                    listener
            );
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * Scans the network without a progress listener.
     */
    public List<Device> scan(
            NetworkInterfaceInfo network
    ) throws InterruptedException {

        return scan(network, null);
    }

    /**
     * Scans all discovered IP addresses concurrently.
     */
    private List<Device> scanHosts(
            List<String> addresses,
            ExecutorService executor,
            ScanListener listener
    ) throws InterruptedException {

        List<Future<Device>> futures =
                new ArrayList<>();

        for (String ipAddress : addresses) {
            futures.add(
                    executor.submit(
                            () -> inspectDevice(ipAddress)
                    )
            );
        }

        List<Device> devices = new ArrayList<>();

        int completed = 0;
        int total = addresses.size();

        for (Future<Device> future : futures) {

            try {
                Device device = future.get();

                if (device != null) {
                    devices.add(device);
                }

            } catch (ExecutionException ignored) {
                // Ignore individual host scan failures.
            }

            completed++;

            if (listener != null) {
                listener.progress(completed, total);
            }
        }

        devices.sort(
                Comparator.comparing(
                        Device::getIpAddress,
                        DeviceScanner::compareIpAddresses
                )
        );

        return devices;
    }

    /**
     * Inspects a single IP address.
     */
    private Device inspectDevice(String ipAddress) {

        long latency =
                pingService.measure(
                        ipAddress,
                        PING_TIMEOUT_MS
                );

        String macAddress =
                macAddressService.getMacAddress(ipAddress);

        List<Service> services =
                portScanner.scan(
                        ipAddress,
                        ports,
                        PORT_TIMEOUT_MS
                );

        /*
         * If the host did not respond to ping,
         * has no MAC address, and has no open ports,
         * there is nothing useful to report.
         */
        if (isUnreachable(
                latency,
                macAddress,
                services
        )) {
            return null;
        }

        String hostname =
                resolveHostname(ipAddress);

        String vendor =
                vendorLookup.lookup(macAddress);

        String deviceType =
                classifier.classify(
                        hostname,
                        services
                );

        Device device = new Device(
                ipAddress,
                hostname,
                latency,
                macAddress,
                vendor,
                deviceType
        );

        for (Service service : services) {
            device.addService(service);
        }

        return device;
    }

    /**
     * Determines whether a host should be ignored.
     */
    private boolean isUnreachable(
            long latency,
            String macAddress,
            List<Service> services
    ) {

        return latency < 0
                && macAddress == null
                && services.isEmpty();
    }

    /**
     * Attempts to resolve an IP address to a hostname.
     */
    private String resolveHostname(String ipAddress) {

        try {
            String hostname =
                    InetAddress
                            .getByName(ipAddress)
                            .getCanonicalHostName();

            if (hostname != null
                    && !hostname.isBlank()
                    && !hostname.equals(ipAddress)) {

                return hostname;
            }

        } catch (Exception ignored) {
            // Hostname resolution is optional.
        }

        return ipAddress;
    }

    /**
     * Generates all usable host addresses
     * for a network and CIDR prefix.
     */
    private List<String> getHosts(
            String networkAddress,
            int prefixLength
    ) {

        List<String> addresses =
                new ArrayList<>();

        long baseAddress =
                NetworkDetector.ipv4ToLong(
                        networkAddress
                );

        long hostCount =
                1L << (32 - prefixLength);

        long firstHost =
                prefixLength >= 31
                        ? baseAddress
                        : baseAddress + 1;

        long lastHost =
                prefixLength >= 31
                        ? baseAddress + hostCount - 1
                        : baseAddress + hostCount - 2;

        for (long address = firstHost;
             address <= lastHost;
             address++) {

            addresses.add(
                    NetworkDetector.longToIpv4(address)
            );
        }

        return addresses;
    }

    /**
     * Compares two IPv4 addresses numerically.
     *
     * Example:
     * 192.168.1.9 comes before 192.168.1.100
     */
    private static int compareIpAddresses(
            String first,
            String second
    ) {

        String[] firstParts =
                first.split("\\.");

        String[] secondParts =
                second.split("\\.");

        for (int i = 0; i < 4; i++) {

            int comparison =
                    Integer.compare(
                            Integer.parseInt(firstParts[i]),
                            Integer.parseInt(secondParts[i])
                    );

            if (comparison != 0) {
                return comparison;
            }
        }

        return 0;
    }

    /**
     * Receives scan progress updates.
     */
    public interface ScanListener {

        void progress(
                int completed,
                int total
        );
    }
}