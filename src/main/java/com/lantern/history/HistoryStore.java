package com.lantern.history;

import com.lantern.model.Device;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.List;

public class HistoryStore {

    private static final String HEADER =
            "timestamp,ip,hostname,mac,vendor,type,latency,services";

    private final Path file;

    public HistoryStore(Path file) {
        this.file = file;
    }

    /**
     * Saves the results of a network scan to the history file.
     */
    public synchronized void saveScan(
            List<Device> devices
    ) {

        try {
            createParentDirectory();

            boolean fileExists =
                    Files.exists(file);

            try (BufferedWriter writer =
                         Files.newBufferedWriter(
                                 file,
                                 StandardOpenOption.CREATE,
                                 StandardOpenOption.APPEND
                         )) {

                if (!fileExists) {
                    writer.write(HEADER);
                    writer.newLine();
                }

                String timestamp =
                        LocalDateTime.now().toString();

                for (Device device : devices) {
                    writer.write(
                            toCsvRow(
                                    timestamp,
                                    device
                            )
                    );

                    writer.newLine();
                }
            }

        } catch (IOException ignored) {
            // History storage should not stop the scanner.
        }
    }

    /**
     * Returns the most recent history entries.
     *
     * The CSV header is excluded from the result.
     */
    public List<String> recentLines(int maxLines) {

        if (!Files.exists(file)) {
            return List.of();
        }

        try {
            List<String> lines =
                    Files.readAllLines(file);

            // No data besides the header.
            if (lines.size() <= 1) {
                return List.of();
            }

            // Return everything except the header
            // when there are fewer entries than requested.
            if (lines.size() <= maxLines + 1) {
                return lines.subList(1, lines.size());
            }

            int startIndex =
                    Math.max(
                            1,
                            lines.size() - maxLines
                    );

            return lines.subList(
                    startIndex,
                    lines.size()
            );

        } catch (IOException ignored) {
            return List.of();
        }
    }

    /**
     * Returns the location of the history file.
     */
    public Path path() {
        return file;
    }

    /**
     * Creates the parent directory if necessary.
     */
    private void createParentDirectory()
            throws IOException {

        Path parent = file.getParent();

        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    /**
     * Converts a device into a CSV row.
     */
    private String toCsvRow(
            String timestamp,
            Device device
    ) {

        return String.join(
                ",",
                csv(timestamp),
                csv(device.getIpAddress()),
                csv(device.getHostname()),
                csv(device.getMacAddress()),
                csv(device.getVendor()),
                csv(device.getType()),
                Long.toString(device.getLatencyMs()),
                csv(device.serviceSignature())
        );
    }

    /**
     * Escapes a value for CSV output.
     */
    private String csv(String value) {

        if (value == null) {
            return "";
        }

        return "\""
                + value.replace("\"", "\"\"")
                + "\"";
    }
}