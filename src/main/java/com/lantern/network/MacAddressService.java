package com.lantern.network;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Locale;

public class MacAddressService {

    public String getMacAddress(String ip) {

        String operatingSystem =
                System.getProperty(
                        "os.name",
                        ""
                ).toLowerCase(Locale.ROOT);

        try {

            String[] command =
                    buildCommand(
                            operatingSystem,
                            ip
                    );

            Process process =
                    new ProcessBuilder(command)
                            .redirectErrorStream(true)
                            .start();

            String output =
                    readOutput(process);

            process.waitFor();

            return extractMacAddress(
                    output,
                    operatingSystem.contains("win")
            );

        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Builds the platform-specific command used
     * to resolve a MAC address from an IP address.
     */
    private String[] buildCommand(
            String operatingSystem,
            String ip
    ) {

        if (operatingSystem.contains("win")) {
            return new String[]{
                    "arp",
                    "-a",
                    ip
            };
        }

        if (operatingSystem.contains("mac")) {
            return new String[]{
                    "arp",
                    "-n",
                    ip
            };
        }

        return new String[]{
                "ip",
                "neigh",
                "show",
                ip
        };
    }

    /**
     * Reads all output produced by the system command.
     */
    private String readOutput(
            Process process
    ) throws Exception {

        StringBuilder output =
                new StringBuilder();

        try (
                BufferedReader reader =
                        new BufferedReader(
                                new InputStreamReader(
                                        process.getInputStream()
                                )
                        )
        ) {

            String line;

            while ((line = reader.readLine()) != null) {
                output.append(line).append('\n');
            }
        }

        return output.toString();
    }

    /**
     * Extracts the first valid MAC address from command output.
     */
    private String extractMacAddress(
            String text,
            boolean windows
    ) {

        String[] parts =
                text.trim().split("\\s+");

        for (String part : parts) {

            String candidate =
                    part.replace('-', ':');

            if (isMacAddress(candidate)) {
                return candidate.toLowerCase(
                        Locale.ROOT
                );
            }
        }

        return null;
    }

    /**
     * Checks whether a string matches the standard
     * six-byte MAC address format.
     */
    private boolean isMacAddress(
            String value
    ) {

        return value.matches(
                "(?i)^([0-9a-f]{2}:){5}[0-9a-f]{2}$"
        );
    }
}