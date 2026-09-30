package com.lantern.agent.admin;

import java.awt.datatransfer.DataFlavor;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.datatransfer.Transferable;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.imageio.ImageIO;

/**
 * Optional, explicitly authorized local-admin collectors.
 *
 * No credentials or keystrokes are collected.
 */
public final class AdminCollector {

    private static final int MAX_FILE_LIST_ENTRIES = 200;
    private static final int MAX_BROWSER_HISTORY_ENTRIES = 200;
    private static final int MAX_FILE_READ_BYTES = 1_000_000;

    private final AdminPolicy policy;

    public AdminCollector(
            AdminPolicy policy
    ) {
        this.policy = policy;
    }

    public AdminPolicy policy() {
        return policy;
    }

    public List<String> permissions() {

        return policy.permissions()
                .stream()
                .map(Enum::name)
                .sorted()
                .toList();
    }

    public List<String> listFiles(
            String requested
    ) throws IOException {

        require(AdminPermission.FILESYSTEM);

        Path root =
                safePath(requested);

        if (!Files.isDirectory(root)) {
            throw new IOException(
                    "Not a directory: " + requested
            );
        }

        try (var stream = Files.list(root)) {

            return stream
                    .limit(MAX_FILE_LIST_ENTRIES)
                    .map(this::describeFile)
                    .sorted()
                    .toList();
        }
    }

    private String describeFile(
            Path path
    ) {

        try {

            String kind =
                    Files.isDirectory(path)
                            ? "DIR"
                            : "FILE";

            long size =
                    Files.isRegularFile(path)
                            ? Files.size(path)
                            : 0;

            String modified =
                    Files.getLastModifiedTime(path)
                            .toInstant()
                            .toString();

            return kind
                    + "\t"
                    + path.getFileName()
                    + "\t"
                    + size
                    + "\t"
                    + modified;

        } catch (Exception ignored) {

            return "UNKNOWN\t"
                    + path.getFileName();
        }
    }

    public String readText(
            String requested,
            int maxBytes
    ) throws IOException {

        require(AdminPermission.FILESYSTEM);

        Path file =
                safePath(requested);

        if (!Files.isRegularFile(file)) {
            throw new IOException(
                    "Not a file: " + requested
            );
        }

        int readLimit =
                Math.min(
                        Math.max(maxBytes, 1),
                        MAX_FILE_READ_BYTES
                );

        long size =
                Files.size(file);

        if (size > readLimit) {
            throw new IOException(
                    "File exceeds read limit"
            );
        }

        byte[] bytes =
                Files.readAllBytes(file);

        return new String(
                bytes,
                StandardCharsets.UTF_8
        );
    }

    public byte[] screenPng()
            throws Exception {

        require(AdminPermission.SCREEN);

        if (GraphicsEnvironment.isHeadless()) {
            throw new IOException(
                    "Screen capture unavailable "
                            + "on a headless machine"
            );
        }

        Rectangle screenBounds =
                getScreenBounds();

        if (screenBounds.width <= 0
                || screenBounds.height <= 0) {

            throw new IOException(
                    "No display detected"
            );
        }

        Robot robot =
                new Robot();

        BufferedImage image =
                robot.createScreenCapture(
                        screenBounds
                );

        ByteArrayOutputStream output =
                new ByteArrayOutputStream();

        ImageIO.write(
                image,
                "png",
                output
        );

        return output.toByteArray();
    }

    private Rectangle getScreenBounds() {

        Rectangle bounds =
                new Rectangle(
                        0,
                        0,
                        0,
                        0
                );

        GraphicsEnvironment environment =
                GraphicsEnvironment
                        .getLocalGraphicsEnvironment();

        for (
                GraphicsDevice device :
                environment.getScreenDevices()
        ) {

            Rectangle screen =
                    device
                            .getDefaultConfiguration()
                            .getBounds();

            bounds =
                    bounds.union(screen);
        }

        return bounds;
    }

    public String clipboardText()
            throws Exception {

        require(AdminPermission.CLIPBOARD);

        var clipboard =
                Toolkit
                        .getDefaultToolkit()
                        .getSystemClipboard();

        if (!clipboard.isDataFlavorAvailable(
                DataFlavor.stringFlavor
        )) {
            return "";
        }

        Object value =
                clipboard.getData(
                        DataFlavor.stringFlavor
                );

        return value == null
                ? ""
                : value.toString();
    }

    public List<String> browserHistory(
            int limit
    ) throws Exception {

        require(
                AdminPermission.BROWSER_HISTORY
        );

        Path database =
                findBrowserHistoryDb();

        if (database == null) {
            throw new IOException(
                    "No supported browser history "
                            + "database found"
            );
        }

        Path copy =
                Files.createTempFile(
                        "lantern-history-",
                        ".db"
                );

        try {

            Files.copy(
                    database,
                    copy,
                    StandardCopyOption.REPLACE_EXISTING
            );

            String sqlite =
                    findExecutable("sqlite3");

            if (sqlite == null) {
                throw new IOException(
                        "sqlite3 executable not found; "
                                + "browser history is unavailable "
                                + "on this machine"
                );
            }

            String sql =
                    buildHistoryQuery(limit);

            Process process =
                    new ProcessBuilder(
                            sqlite,
                            "-separator",
                            "\t",
                            copy.toString(),
                            sql
                    )
                            .redirectErrorStream(true)
                            .start();

            return readHistoryOutput(
                    process
            );

        } finally {

            try {
                Files.deleteIfExists(copy);
            } catch (Exception ignored) {
                // Temporary-file cleanup is best effort.
            }
        }
    }

    private String buildHistoryQuery(
            int limit
    ) {

        int safeLimit =
                Math.max(
                        1,
                        Math.min(
                                limit,
                                MAX_BROWSER_HISTORY_ENTRIES
                        )
                );

        return "SELECT url, title, "
                + "datetime("
                + "last_visit_time/1000000-11644473600,"
                + "'unixepoch'"
                + ") "
                + "FROM urls "
                + "ORDER BY last_visit_time DESC "
                + "LIMIT "
                + safeLimit;
    }

    private List<String> readHistoryOutput(
            Process process
    ) throws IOException, InterruptedException {

        List<String> lines =
                new ArrayList<>();

        try (
                BufferedReader reader =
                        new BufferedReader(
                                new InputStreamReader(
                                        process.getInputStream()
                                )
                        )
        ) {

            String line;

            while (
                    (line = reader.readLine()) != null
                    && lines.size()
                    < MAX_BROWSER_HISTORY_ENTRIES
            ) {

                lines.add(line);
            }
        }

        process.waitFor();

        return lines;
    }

    private Path findBrowserHistoryDb() {

        String home =
                System.getProperty(
                        "user.home",
                        ""
                );

        String operatingSystem =
                System.getProperty(
                        "os.name",
                        ""
                ).toLowerCase(Locale.ROOT);

        boolean windows =
                operatingSystem.contains("win");

        boolean mac =
                operatingSystem.contains("mac");

        List<Path> candidates =
                new ArrayList<>();

        if (windows) {

            addWindowsBrowserPaths(
                    candidates
            );

        } else if (mac) {

            addMacBrowserPaths(
                    candidates,
                    home
            );

        } else {

            addLinuxBrowserPaths(
                    candidates,
                    home
            );
        }

        return candidates.stream()
                .filter(Files::isRegularFile)
                .findFirst()
                .orElse(null);
    }

    private void addWindowsBrowserPaths(
            List<Path> candidates
    ) {

        String localAppData =
                System.getenv(
                        "LOCALAPPDATA"
                );

        if (localAppData == null) {
            return;
        }

        candidates.add(
                Path.of(
                        localAppData,
                        "Google",
                        "Chrome",
                        "User Data",
                        "Default",
                        "History"
                )
        );

        candidates.add(
                Path.of(
                        localAppData,
                        "Microsoft",
                        "Edge",
                        "User Data",
                        "Default",
                        "History"
                )
        );
    }

    private void addMacBrowserPaths(
            List<Path> candidates,
            String home
    ) {

        candidates.add(
                Path.of(
                        home,
                        "Library/Application Support/"
                                + "Google/Chrome/Default/History"
                )
        );

        candidates.add(
                Path.of(
                        home,
                        "Library/Application Support/"
                                + "Microsoft Edge/Default/History"
                )
        );
    }

    private void addLinuxBrowserPaths(
            List<Path> candidates,
            String home
    ) {

        candidates.add(
                Path.of(
                        home,
                        ".config/google-chrome/Default/History"
                )
        );

        candidates.add(
                Path.of(
                        home,
                        ".config/chromium/Default/History"
                )
        );

        candidates.add(
                Path.of(
                        home,
                        ".config/microsoft-edge/Default/History"
                )
        );
    }

    private String findExecutable(
            String name
    ) {

        try {

            String operatingSystem =
                    System.getProperty(
                            "os.name",
                            ""
                    ).toLowerCase(Locale.ROOT);

            boolean windows =
                    operatingSystem.contains("win");

            Process process =
                    windows
                            ? new ProcessBuilder(
                                    "where",
                                    name
                            )
                            .redirectErrorStream(true)
                            .start()
                            : new ProcessBuilder(
                                    "sh",
                                    "-c",
                                    "command -v " + name
                            )
                            .redirectErrorStream(true)
                            .start();

            String path =
                    new String(
                            process.getInputStream()
                                    .readAllBytes(),
                            StandardCharsets.UTF_8
                    )
                    .lines()
                    .findFirst()
                    .orElse("")
                    .trim();

            process.waitFor();

            return path.isBlank()
                    ? null
                    : path;

        } catch (Exception ignored) {
            return null;
        }
    }

    private Path safePath(
            String requested
    ) throws IOException {

        if (requested == null
                || requested.isBlank()) {

            throw new IOException(
                    "Path is required"
            );
        }

        Path candidate =
                Path.of(requested)
                        .toAbsolutePath()
                        .normalize();

        for (Path root :
                policy.allowedRoots()) {

            if (candidate.startsWith(root)) {
                return candidate;
            }
        }

        throw new IOException(
                "Path is outside configured admin roots"
        );
    }

    private void require(
            AdminPermission permission
    ) throws IOException {

        if (!policy.allows(permission)) {

            throw new IOException(
                    "Permission disabled: "
                            + permission
            );
        }
    }
}