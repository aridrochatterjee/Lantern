package com.lantern.agent;

import com.lantern.agent.admin.AdminPolicy;
import java.nio.file.*;
import java.util.*;

public record AgentConfig(String bind, int port, String token, int intervalSeconds, AdminPolicy adminPolicy) {
    public static AgentConfig load(String[] args) {
        String bind = "0.0.0.0"; int port = 8786, interval = 5; String token = null;
        for (String a : args) {
            if (a.startsWith("--bind=")) bind = a.substring(7);
            if (a.startsWith("--port=")) try { port = Integer.parseInt(a.substring(7)); } catch (Exception ignored) {}
            if (a.startsWith("--token=")) token = a.substring(8);
            if (a.startsWith("--interval=")) try { interval = Math.max(1, Integer.parseInt(a.substring(11))); } catch (Exception ignored) {}
        }
        if (token == null || token.isBlank()) token = loadOrCreateToken();
        return new AgentConfig(bind, port, token, interval, AdminPolicy.fromArgs(args));
    }
    private static String loadOrCreateToken() {
        Path p = Path.of("data", "agent.token");
        try {
            Files.createDirectories(p.getParent());
            if (Files.exists(p)) return Files.readString(p).trim();
            String t = UUID.randomUUID() + "-" + UUID.randomUUID();
            Files.writeString(p, t, StandardOpenOption.CREATE_NEW);
            return t;
        } catch (Exception e) { return UUID.randomUUID().toString(); }
    }
}
