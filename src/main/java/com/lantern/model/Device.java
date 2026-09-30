package com.lantern.model;

import com.lantern.agent.AgentClient;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Device {

    private final String ipAddress;
    private final String hostname;
    private final String macAddress;
    private final String vendor;
    private final String type;

    private final long latencyMs;
    private final LocalDateTime discoveredAt;

    private final List<Service> services =
            new ArrayList<>();

    private AgentClient.AgentData agentData;

    public Device(
            String ipAddress,
            String hostname,
            long latencyMs,
            String macAddress,
            String vendor,
            String type
    ) {
        this.ipAddress = ipAddress;
        this.hostname = hostname;
        this.latencyMs = latencyMs;
        this.macAddress = macAddress;
        this.vendor = vendor;
        this.type = type;
        this.discoveredAt = LocalDateTime.now();
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public String getHostname() {
        return hostname;
    }

    public long getLatencyMs() {
        return latencyMs;
    }

    public String getMacAddress() {
        return macAddress;
    }

    public String getVendor() {
        return vendor;
    }

    public String getType() {
        return type;
    }

    public LocalDateTime getDiscoveredAt() {
        return discoveredAt;
    }

    /**
     * Determines whether the device appears to be online.
     */
    public boolean isOnline() {

        return latencyMs >= 0
                || macAddress != null
                || !services.isEmpty()
                || agentData != null;
    }

    /**
     * Adds a service if it has not already been discovered.
     *
     * Services are considered duplicates when they use
     * the same port.
     */
    public void addService(Service service) {

        if (service == null) {
            return;
        }

        boolean alreadyExists =
                services.stream()
                        .anyMatch(
                                existing ->
                                        existing.port()
                                                == service.port()
                        );

        if (!alreadyExists) {
            services.add(service);
        }
    }

    /**
     * Returns discovered services sorted by port number.
     */
    public List<Service> getServices() {

        return services.stream()
                .sorted(
                        Comparator.comparingInt(
                                Service::port
                        )
                )
                .toList();
    }

    /**
     * Creates a comma-separated list of open ports.
     *
     * Example:
     * 22,80,443,8080
     */
    public String serviceSignature() {

        return getServices()
                .stream()
                .map(service ->
                        Integer.toString(service.port())
                )
                .reduce(
                        (first, second) ->
                                first + "," + second
                )
                .orElse("");
    }

    /**
     * Returns the most useful human-readable name
     * for this device.
     */
    public String displayName() {

        // Prefer agent-provided hostname.
        if (agentData != null
                && agentData.hostname() != null
                && !agentData.hostname().isBlank()) {

            return agentData.hostname();
        }

        // Then use the discovered hostname.
        if (hostname != null
                && !hostname.isBlank()
                && !hostname.equals(ipAddress)) {

            return hostname;
        }

        // Then fall back to the vendor.
        if (vendor != null
                && !vendor.isBlank()
                && !vendor.equalsIgnoreCase("Unknown")) {

            return vendor;
        }

        // Finally, use the IP address.
        return ipAddress;
    }

    public AgentClient.AgentData getAgentData() {
        return agentData;
    }

    public void setAgentData(
            AgentClient.AgentData agentData
    ) {
        this.agentData = agentData;
    }
}