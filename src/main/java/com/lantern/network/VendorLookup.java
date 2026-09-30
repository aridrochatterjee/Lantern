package com.lantern.network;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class VendorLookup {

    private static final Pattern MAC_PREFIX =
            Pattern.compile(
                    "(?i)^([0-9A-F]{2}[:-][0-9A-F]{2}[:-][0-9A-F]{2})"
            );

    private static final String UNKNOWN_VENDOR =
            "Unknown";

    private final Map<String, String> oui =
            new HashMap<>();

    public VendorLookup(String explicitPath) {

        List<Path> possiblePaths =
                new ArrayList<>();

        if (explicitPath != null) {
            possiblePaths.add(
                    Paths.get(explicitPath)
            );
        }

        possiblePaths.add(
                Paths.get("data/oui.txt")
        );

        possiblePaths.add(
                Paths.get(
                        "/usr/share/ieee-data/oui.txt"
                )
        );

        possiblePaths.add(
                Paths.get(
                        "/usr/share/ieee-data/oui.txt.gz"
                )
        );

        loadFirstAvailableFile(
                possiblePaths
        );
    }

    /**
     * Loads the first available OUI database file.
     */
    private void loadFirstAvailableFile(
            List<Path> paths
    ) {

        for (Path path : paths) {

            if (!Files.isRegularFile(path)) {
                continue;
            }

            if (path.toString().endsWith(".gz")) {
                continue;
            }

            load(path);
            break;
        }
    }

    /**
     * Loads vendor information from an OUI database.
     */
    private void load(Path path) {

        try (
                BufferedReader reader =
                        Files.newBufferedReader(path)
        ) {

            String line;

            while ((line = reader.readLine()) != null) {
                parseLine(line);
            }

        } catch (IOException ignored) {
            // Vendor lookup is optional.
        }
    }

    /**
     * Parses a single OUI database line.
     */
    private void parseLine(String line) {

        String trimmed =
                line.trim();

        Matcher matcher =
                MAC_PREFIX.matcher(trimmed);

        if (!matcher.find()) {
            return;
        }

        int vendorEnd =
                trimmed.indexOf("(base 16)");

        if (vendorEnd < 0) {
            vendorEnd =
                    trimmed.indexOf("#");
        }

        String vendor;

        if (vendorEnd > 0) {

            vendor =
                    trimmed
                            .substring(
                                    vendorEnd + 10
                            )
                            .trim();

        } else {

            vendor =
                    trimmed
                            .substring(
                                    matcher.end()
                            )
                            .trim();
        }

        if (vendor.isBlank()) {
            return;
        }

        String prefix =
                normalizePrefix(
                        matcher.group(1)
                );

        oui.put(
                prefix,
                vendor
        );
    }

    /**
     * Looks up the vendor for a MAC address.
     */
    public String lookup(String mac) {

        if (mac == null || mac.length() < 8) {
            return UNKNOWN_VENDOR;
        }

        String prefix =
                normalizePrefix(
                        mac.substring(0, 8)
                );

        return oui.getOrDefault(
                prefix,
                UNKNOWN_VENDOR
        );
    }

    /**
     * Normalizes an OUI prefix into AA-BB-CC format.
     */
    private String normalizePrefix(
            String prefix
    ) {

        return prefix
                .replace(':', '-')
                .toUpperCase();
    }
}