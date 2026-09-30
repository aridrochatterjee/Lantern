package com.lantern.agent.model;

import java.time.Instant;
import java.util.List;

public record SystemSnapshot(
        Instant timestamp,
        String hostname,
        String os,
        String kernel,
        String architecture,
        String hardwareModel,
        String cpuModel,
        String gpu,
        String loggedInUser,
        String javaVersion,
        int cpuCores,
        double cpuLoadPercent,
        long memoryTotalBytes,
        long memoryUsedBytes,
        long memoryAvailableBytes,
        long diskTotalBytes,
        long diskUsedBytes,
        long diskFreeBytes,
        long uptimeSeconds,
        boolean batteryPresent,
        int batteryPercent,
        List<String> networkInterfaces,
        List<String> processes
) {}
