package com.lantern.agent.admin;

import java.awt.*;
import java.awt.datatransfer.DataFlavor;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.util.List;
import javax.imageio.ImageIO;

/** Optional, explicitly authorized local-admin collectors. No credentials or keystrokes are collected. */
public final class AdminCollector {
    private final AdminPolicy policy;

    public AdminCollector(AdminPolicy policy) { this.policy = policy; }

    public AdminPolicy policy() { return policy; }

    public List<String> permissions() {
        return policy.permissions().stream().map(Enum::name).sorted().toList();
    }

    public List<String> listFiles(String requested) throws IOException {
        require(AdminPermission.FILESYSTEM);
        Path root = safePath(requested);
        if (!Files.isDirectory(root)) throw new IOException("Not a directory: " + requested);
        try (var stream = Files.list(root)) {
            return stream.limit(200).map(p -> {
                try {
                    String kind = Files.isDirectory(p) ? "DIR" : "FILE";
                    long size = Files.isRegularFile(p) ? Files.size(p) : 0;
                    return kind + "\t" + p.getFileName() + "\t" + size + "\t" + Files.getLastModifiedTime(p).toInstant();
                } catch (Exception e) { return "UNKNOWN\t" + p.getFileName(); }
            }).sorted().toList();
        }
    }

    public String readText(String requested, int maxBytes) throws IOException {
        require(AdminPermission.FILESYSTEM);
        Path file = safePath(requested);
        if (!Files.isRegularFile(file)) throw new IOException("Not a file: " + requested);
        long size = Files.size(file);
        if (size > Math.min(Math.max(maxBytes, 1), 1_000_000)) throw new IOException("File exceeds read limit");
        byte[] bytes = Files.readAllBytes(file);
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    public byte[] screenPng() throws Exception {
        require(AdminPermission.SCREEN);
        if (GraphicsEnvironment.isHeadless()) throw new IOException("Screen capture unavailable on a headless machine");
        Robot robot = new Robot();
        Rectangle bounds = new Rectangle(0, 0, 0, 0);
        for (GraphicsDevice device : GraphicsEnvironment.getLocalGraphicsEnvironment().getScreenDevices()) {
            Rectangle b = device.getDefaultConfiguration().getBounds();
            bounds = bounds.union(b);
        }
        if (bounds.width <= 0 || bounds.height <= 0) throw new IOException("No display detected");
        BufferedImage image = robot.createScreenCapture(bounds);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    public String clipboardText() throws Exception {
        require(AdminPermission.CLIPBOARD);
        if (!Toolkit.getDefaultToolkit().getSystemClipboard().isDataFlavorAvailable(DataFlavor.stringFlavor)) return "";
        Object value = Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.stringFlavor);
        return value == null ? "" : value.toString();
    }

    public List<String> browserHistory(int limit) throws Exception {
        require(AdminPermission.BROWSER_HISTORY);
        Path db = findBrowserHistoryDb();
        if (db == null) throw new IOException("No supported browser history database found");
        Path copy = Files.createTempFile("lantern-history-", ".db");
        try {
            Files.copy(db, copy, StandardCopyOption.REPLACE_EXISTING);
            String sqlite = findExecutable("sqlite3");
            if (sqlite == null) throw new IOException("sqlite3 executable not found; browser history is unavailable on this machine");
            String sql = "SELECT url, title, datetime(last_visit_time/1000000-11644473600,'unixepoch') FROM urls ORDER BY last_visit_time DESC LIMIT " + Math.max(1, Math.min(limit, 200));
            Process p = new ProcessBuilder(sqlite, "-separator", "\t", copy.toString(), sql).redirectErrorStream(true).start();
            List<String> lines = new ArrayList<>();
            try (BufferedReader r = p.inputReader()) { String line; while ((line = r.readLine()) != null && lines.size() < 200) lines.add(line); }
            p.waitFor();
            return lines;
        } finally { try { Files.deleteIfExists(copy); } catch (Exception ignored) {} }
    }

    private Path findBrowserHistoryDb() {
        String home = System.getProperty("user.home", "");
        boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
        boolean mac = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
        List<Path> candidates = new ArrayList<>();
        if (windows) {
            String local = System.getenv("LOCALAPPDATA");
            if (local != null) candidates.add(Path.of(local, "Google", "Chrome", "User Data", "Default", "History"));
            if (local != null) candidates.add(Path.of(local, "Microsoft", "Edge", "User Data", "Default", "History"));
        } else if (mac) {
            candidates.add(Path.of(home, "Library/Application Support/Google/Chrome/Default/History"));
            candidates.add(Path.of(home, "Library/Application Support/Microsoft Edge/Default/History"));
        } else {
            candidates.add(Path.of(home, ".config/google-chrome/Default/History"));
            candidates.add(Path.of(home, ".config/chromium/Default/History"));
            candidates.add(Path.of(home, ".config/microsoft-edge/Default/History"));
        }
        return candidates.stream().filter(Files::isRegularFile).findFirst().orElse(null);
    }

    private String findExecutable(String name) {
        try {
            boolean windows = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
            Process p = windows
                    ? new ProcessBuilder("where", name).redirectErrorStream(true).start()
                    : new ProcessBuilder("sh", "-c", "command -v " + name).redirectErrorStream(true).start();
            String v = new String(p.getInputStream().readAllBytes()).lines().findFirst().orElse("").trim(); p.waitFor();
            return v.isBlank() ? null : v;
        } catch (Exception e) { return null; }
    }

    private Path safePath(String requested) throws IOException {
        if (requested == null || requested.isBlank()) throw new IOException("Path is required");
        Path candidate = Path.of(requested).toAbsolutePath().normalize();
        for (Path root : policy.allowedRoots()) if (candidate.startsWith(root)) return candidate;
        throw new IOException("Path is outside configured admin roots");
    }

    private void require(AdminPermission permission) throws IOException {
        if (!policy.allows(permission)) throw new IOException("Permission disabled: " + permission);
    }
}
