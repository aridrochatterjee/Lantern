package com.lantern.network;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Locale;

public class MacAddressService {
    public String getMacAddress(String ip) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        try {
            String[] commands = os.contains("win")
                    ? new String[]{"arp", "-a", ip}
                    : os.contains("mac")
                    ? new String[]{"arp", "-n", ip}
                    : new String[]{"ip", "neigh", "show", ip};
            Process p = new ProcessBuilder(commands).redirectErrorStream(true).start();
            StringBuilder all = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line; while ((line = r.readLine()) != null) all.append(line).append('\n');
            }
            p.waitFor();
            return extract(all.toString(), os.contains("win"));
        } catch (Exception ignored) { return null; }
    }

    private String extract(String text, boolean windows) {
        String[] parts = text.trim().split("\\s+");
        for (String part : parts) {
            String candidate = part.replace('-', ':');
            if (isMac(candidate)) return candidate.toLowerCase(Locale.ROOT);
        }
        return null;
    }

    private boolean isMac(String s) { return s.matches("(?i)^([0-9a-f]{2}:){5}[0-9a-f]{2}$"); }
}
