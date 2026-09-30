package com.lantern.history;

import com.lantern.model.Device;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class NetworkMonitor {

    /**
     * Compares two network scans and detects:
     *
     * - Devices that appeared
     * - Devices that disappeared
     * - Devices whose information changed
     */
    public List<NetworkEvent> compare(
            List<Device> previousDevices,
            List<Device> currentDevices
    ) {

        Map<String, Device> previous =
                mapByIpAddress(previousDevices);

        Map<String, Device> current =
                mapByIpAddress(currentDevices);

        List<NetworkEvent> events =
                new ArrayList<>();

        // Find new and changed devices.
        for (Map.Entry<String, Device> entry :
                current.entrySet()) {

            String ipAddress = entry.getKey();
            Device currentDevice = entry.getValue();

            if (!previous.containsKey(ipAddress)) {

                events.add(
                        new NetworkEvent(
                                "APPEARED",
                                currentDevice
                        )
                );

                continue;
            }

            Device previousDevice =
                    previous.get(ipAddress);

            if (hasChanged(
                    previousDevice,
                    currentDevice
            )) {

                events.add(
                        new NetworkEvent(
                                "CHANGED",
                                currentDevice
                        )
                );
            }
        }

        // Find devices that disappeared.
        for (Map.Entry<String, Device> entry :
                previous.entrySet()) {

            String ipAddress = entry.getKey();

            if (!current.containsKey(ipAddress)) {

                events.add(
                        new NetworkEvent(
                                "DISAPPEARED",
                                entry.getValue()
                        )
                );
            }
        }

        return events;
    }

    /**
     * Converts a device list into a map keyed by IP address.
     */
    private Map<String, Device> mapByIpAddress(
            List<Device> devices
    ) {

        Map<String, Device> devicesByIp =
                new HashMap<>();

        for (Device device : devices) {
            devicesByIp.put(
                    device.getIpAddress(),
                    device
            );
        }

        return devicesByIp;
    }

    /**
     * Determines whether relevant device information
     * changed between two scans.
     */
    private boolean hasChanged(
            Device previous,
            Device current
    ) {

        return !Objects.equals(
                    previous.getMacAddress(),
                    current.getMacAddress()
                )
                || !Objects.equals(
                    previous.serviceSignature(),
                    current.serviceSignature()
                )
                || !Objects.equals(
                    previous.getHostname(),
                    current.getHostname()
                )
                || !Objects.equals(
                    previous.getType(),
                    current.getType()
                );
    }
}