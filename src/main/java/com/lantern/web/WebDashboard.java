package com.lantern.web;

import com.lantern.agent.AgentClient;
import com.lantern.agent.AgentRegistry;
import com.lantern.history.NetworkEvent;
import com.lantern.model.Device;
import com.lantern.model.Service;
import com.lantern.network.NetworkInterfaceInfo;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executors;

/** Local-only dashboard. Host telemetry is only exposed from explicitly paired agents. */
public class WebDashboard {
    private final HttpServer server;
    private volatile List<Device> devices = List.of();
    private volatile NetworkInterfaceInfo network;
    private volatile List<NetworkEvent> events = List.of();
    private final AgentRegistry agents;

    public WebDashboard(int port, AgentRegistry agents) throws IOException {
        this.agents = agents;
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/", this::home);
        server.createContext("/api/devices", this::apiDevices);
        server.createContext("/api/network", this::apiNetwork);
        server.createContext("/api/events", this::apiEvents);
        server.createContext("/api/admin", this::apiAdmin);
        server.setExecutor(Executors.newCachedThreadPool());
    }

    public void start() {
        server.start();
        System.out.println("Dashboard: http://127.0.0.1:" + server.getAddress().getPort());
    }

    public void update(NetworkInterfaceInfo network, List<Device> devices, List<NetworkEvent> events) {
        this.network = network;
        this.devices = List.copyOf(devices);
        this.events = List.copyOf(events);
    }

    private void home(HttpExchange exchange) throws IOException {
        Path page = Path.of("web", "index.html");
        if (!Files.exists(page)) {
            send(exchange,
                    "<!doctype html><html><body style='font-family:monospace;background:#0a0d0a;color:#b6ff3c;padding:30px'>"
                            + "<h1>Lantern v1.1</h1><p>Dashboard file not found: web/index.html</p>"
                            + "<p>Start Lantern from the extracted project root.</p></body></html>",
                    "text/html; charset=utf-8");
            return;
        }
        send(exchange, Files.readString(page, StandardCharsets.UTF_8), "text/html; charset=utf-8");
    }

    private void apiDevices(HttpExchange exchange) throws IOException {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < devices.size(); i++) {
            if (i > 0) json.append(',');
            Device d = devices.get(i);
            json.append('{')
                    .append("\"ip\":").append(q(d.getIpAddress()))
                    .append(",\"name\":").append(q(d.displayName()))
                    .append(",\"hostname\":").append(q(d.getHostname()))
                    .append(",\"mac\":").append(q(d.getMacAddress()))
                    .append(",\"vendor\":").append(q(d.getVendor()))
                    .append(",\"type\":").append(q(d.getType()))
                    .append(",\"latency\":").append(d.getLatencyMs())
                    .append(",\"online\":").append(d.isOnline())
                    .append(",\"discoveredAt\":").append(q(d.getDiscoveredAt().toString()))
                    .append(",\"services\":[");

            List<Service> services = d.getServices();
            for (int k = 0; k < services.size(); k++) {
                if (k > 0) json.append(',');
                Service service = services.get(k);
                json.append('{')
                        .append("\"port\":").append(service.port())
                        .append(",\"name\":").append(q(service.name()))
                        .append(",\"banner\":").append(q(service.banner()))
                        .append('}');
            }
            json.append(']');

            AgentClient.AgentData agent = d.getAgentData();
            if (agent != null) {
                json.append(",\"agent\":{")
                        .append("\"hostname\":").append(q(agent.hostname()))
                        .append(",\"os\":").append(q(agent.os()))
                        .append(",\"kernel\":").append(q(agent.kernel()))
                        .append(",\"architecture\":").append(q(agent.architecture()))
                        .append(",\"hardwareModel\":").append(q(agent.hardwareModel()))
                        .append(",\"cpuModel\":").append(q(agent.cpuModel()))
                        .append(",\"gpu\":").append(q(agent.gpu()))
                        .append(",\"loggedInUser\":").append(q(agent.loggedInUser()))
                        .append(",\"javaVersion\":").append(q(agent.javaVersion()))
                        .append(",\"cpuCores\":").append(agent.cpuCores())
                        .append(",\"cpuLoadPercent\":").append(agent.cpuLoadPercent())
                        .append(",\"memoryTotalBytes\":").append(agent.memoryTotalBytes())
                        .append(",\"memoryUsedBytes\":").append(agent.memoryUsedBytes())
                        .append(",\"memoryAvailableBytes\":").append(agent.memoryAvailableBytes())
                        .append(",\"diskTotalBytes\":").append(agent.diskTotalBytes())
                        .append(",\"diskUsedBytes\":").append(agent.diskUsedBytes())
                        .append(",\"diskFreeBytes\":").append(agent.diskFreeBytes())
                        .append(",\"uptimeSeconds\":").append(agent.uptimeSeconds())
                        .append(",\"batteryPercent\":").append(agent.batteryPercent())
                        .append(",\"receivedAt\":").append(q(agent.receivedAt().toString()))
                        .append(",\"networkInterfaces\":").append(arr(agent.networkInterfaces()))
                        .append(",\"processes\":").append(arr(agent.processes()))
                        .append(",\"adminPermissions\":").append(arr(agent.adminPermissions()))
                        .append('}');
            }
            json.append('}');
        }
        json.append(']');
        send(exchange, json.toString(), "application/json; charset=utf-8");
    }

    private void apiAdmin(HttpExchange exchange) throws IOException {
        try {
            String ip = query(exchange.getRequestURI(), "ip");
            String path = query(exchange.getRequestURI(), "path");
            String q = query(exchange.getRequestURI(), "query");
            if (ip == null || path == null) { send(exchange, "{\"error\":\"ip and path are required\"}", "application/json; charset=utf-8", 400); return; }
            AgentRegistry.ProxyResponse r = agents.proxy(ip, path, q);
            send(exchange, new String(r.body(), StandardCharsets.UTF_8), r.contentType(), r.status());
        } catch (Exception e) { send(exchange, q(e.getMessage()), "application/json; charset=utf-8", 400); }
    }

    private static String query(java.net.URI uri, String key) {
        String raw = uri.getRawQuery(); if (raw == null) return null;
        for (String pair : raw.split("&")) { String[] p = pair.split("=", 2); if (p.length == 2 && p[0].equals(key)) try { return java.net.URLDecoder.decode(p[1], StandardCharsets.UTF_8); } catch (Exception ignored) {} }
        return null;
    }

    private void apiNetwork(HttpExchange exchange) throws IOException {
        String json = network == null
                ? "{}"
                : "{"
                + "\"interface\":" + q(network.getInterfaceName())
                + ",\"ip\":" + q(network.getLocalAddress())
                + ",\"network\":" + q(network.getNetworkAddress() + "/" + network.getPrefixLength())
                + ",\"gateway\":" + q(network.getDefaultGateway())
                + ",\"dns\":" + arr(network.getDnsServers())
                + "}";
        send(exchange, json, "application/json; charset=utf-8");
    }

    private void apiEvents(HttpExchange exchange) throws IOException {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < events.size(); i++) {
            if (i > 0) json.append(',');
            NetworkEvent event = events.get(i);
            Device device = event.device();
            json.append('{')
                    .append("\"type\":").append(q(event.type()))
                    .append(",\"ip\":").append(q(device.getIpAddress()))
                    .append(",\"name\":").append(q(device.displayName()))
                    .append(",\"hostname\":").append(q(device.getHostname()))
                    .append(",\"timestamp\":").append(q(device.getDiscoveredAt().toString()))
                    .append('}');
        }
        json.append(']');
        send(exchange, json.toString(), "application/json; charset=utf-8");
    }

    private static String arr(List<String> values) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) json.append(',');
            json.append(q(values.get(i)));
        }
        return json.append(']').toString();
    }

    private static String q(String value) {
        if (value == null) return "null";
        StringBuilder out = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '\\' -> out.append("\\\\");
                case '"' -> out.append("\\\"");
                case '\b' -> out.append("\\b");
                case '\f' -> out.append("\\f");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) out.append(String.format("\\u%04x", (int) c));
                    else out.append(c);
                }
            }
        }
        return out.append('"').toString();
    }

    private static void send(HttpExchange exchange, String body, String type) throws IOException { send(exchange, body, type, 200); }

    private static void send(HttpExchange exchange, String body, String type, int status) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
