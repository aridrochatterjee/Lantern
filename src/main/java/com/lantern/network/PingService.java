package com.lantern.network;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class PingService {
    public long measure(String ip, int timeoutMs) {
        long start = System.nanoTime();
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        try {
            ProcessBuilder pb;
            if (os.contains("win")) {
                pb = new ProcessBuilder("ping", "-n", "1", "-w", String.valueOf(timeoutMs), ip);
            } else if (os.contains("mac")) {
                pb = new ProcessBuilder("ping", "-c", "1", "-W", String.valueOf(timeoutMs), ip);
            } else {
                int seconds = Math.max(1, (int) Math.ceil(timeoutMs / 1000.0));
                pb = new ProcessBuilder("ping", "-c", "1", "-W", String.valueOf(seconds), ip);
            }
            Process p = pb.redirectErrorStream(true).start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) { while (r.readLine() != null) {} }
            if (!p.waitFor(timeoutMs + 800L, TimeUnit.MILLISECONDS)) { p.destroyForcibly(); return -1; }
            return p.exitValue() == 0 ? Math.max(1, (System.nanoTime() - start) / 1_000_000L) : -1;
        } catch (Exception e) { return -1; }
    }
}
