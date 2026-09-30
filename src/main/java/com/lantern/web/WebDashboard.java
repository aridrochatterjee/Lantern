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
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executors;

public class WebDashboard {

    private static final String JSON_CONTENT_TYPE =
            "application/json; charset=utf-8";

    private static final String HTML_CONTENT_TYPE =
            "text/html; charset=utf-8";

    private final HttpServer server;
    private final AgentRegistry agents;

    private volatile List<Device> devices =
            List.of();

    private volatile NetworkInterfaceInfo network;

    private volatile List<NetworkEvent> events =
            List.of();

    public WebDashboard(
            int port,
            AgentRegistry agents
    ) throws IOException {

        this.agents = agents;

        server = HttpServer.create(
                new InetSocketAddress(
                        "127.0.0.1",
                        port
                ),
                0
        );

        registerRoutes();

        server.setExecutor(
                Executors.newCachedThreadPool()
        );
    }

    private void registerRoutes() {

        server.createContext(
                "/",
                this::home
        );

        server.createContext(
                "/api/devices",
                this::apiDevices
        );

        server.createContext(
                "/api/network",
                this::apiNetwork
        );

        server.createContext(
                "/api/events",
                this::apiEvents
        );

        server.createContext(
                "/api/admin",
                this::apiAdmin
        );
    }

    public void start() {

        server.start();

        System.out.println(
                "Dashboard: http://127.0.0.1:"
                        + server.getAddress().getPort()
        );
    }

    public void update(
            NetworkInterfaceInfo network,
            List<Device> devices,
            List<NetworkEvent> events
    ) {

        this.network = network;
        this.devices = List.copyOf(devices);
        this.events = List.copyOf(events);
    }

    private void home(
            HttpExchange exchange
    ) throws IOException {

        Path page =
                Path.of(
                        "web",
                        "index.html"
                );

        if (!Files.exists(page)) {

            String fallback =
                    "<!doctype html>"
                            + "<html>"
                            + "<body style='font-family:monospace;"
                            + "background:#0a0d0a;"
                            + "color:#b6ff3c;"
                            + "padding:30px'>"
                            + "<h1>Lantern v1.1</h1>"
                            + "<p>Dashboard file not found: "
                            + "web/index.html</p>"
                            + "<p>Start Lantern from the "
                            + "extracted project root.</p>"
                            + "</body>"
                            + "</html>";

            send(
                    exchange,
                    fallback,
                    HTML_CONTENT_TYPE
            );

            return;
        }

        send(
                exchange,
                Files.readString(
                        page,
                        StandardCharsets.UTF_8
                ),
                HTML_CONTENT_TYPE
        );
    }

    private void apiDevices(
            HttpExchange exchange
    ) throws IOException {

        StringBuilder json =
                new StringBuilder("[");

        for (int i = 0; i < devices.size(); i++) {

            if (i > 0) {
                json.append(',');
            }

            appendDevice(
                    json,
                    devices.get(i)
            );
        }

        json.append(']');

        send(
                exchange,
                json.toString(),
                JSON_CONTENT_TYPE
        );
    }

    private void appendDevice(
            StringBuilder json,
            Device device
    ) {

        json.append('{')
                .append("\"ip\":")
                .append(q(device.getIpAddress()))
                .append(",\"name\":")
                .append(q(device.displayName()))
                .append(",\"hostname\":")
                .append(q(device.getHostname()))
                .append(",\"mac\":")
                .append(q(device.getMacAddress()))
                .append(",\"vendor\":")
                .append(q(device.getVendor()))
                .append(",\"type\":")
                .append(q(device.getType()))
                .append(",\"latency\":")
                .append(device.getLatencyMs())
                .append(",\"online\":")
                .append(device.isOnline())
                .append(",\"discoveredAt\":")
                .append(q(
                        device.getDiscoveredAt()
                                .toString()
                ))
                .append(",\"services\":");

        appendServices(
                json,
                device.getServices()
        );

        AgentClient.AgentData agent =
                device.getAgentData();

        if (agent != null) {
            appendAgentData(
                    json,
                    agent
            );
        }

        json.append('}');
    }

    private void appendServices(
            StringBuilder json,
            List<Service> services
    ) {

        json.append('[');

        for (int i = 0; i < services.size(); i++) {

            if (i > 0) {
                json.append(',');
            }

            Service service =
                    services.get(i);

            json.append('{')
                    .append("\"port\":")
                    .append(service.port())
                    .append(",\"name\":")
                    .append(q(service.name()))
                    .append(",\"banner\":")
                    .append(q(service.banner()))
                    .append('}');
        }

        json.append(']');
    }

    private void appendAgentData(
            StringBuilder json,
            AgentClient.AgentData agent
    ) {

        json.append(",\"agent\":{")
                .append("\"hostname\":")
                .append(q(agent.hostname()))
                .append(",\"os\":")
                .append(q(agent.os()))
                .append(",\"kernel\":")
                .append(q(agent.kernel()))
                .append(",\"architecture\":")
                .append(q(agent.architecture()))
                .append(",\"hardwareModel\":")
                .append(q(agent.hardwareModel()))
                .append(",\"cpuModel\":")
                .append(q(agent.cpuModel()))
                .append(",\"gpu\":")
                .append(q(agent.gpu()))
                .append(",\"loggedInUser\":")
                .append(q(agent.loggedInUser()))
                .append(",\"javaVersion\":")
                .append(q(agent.javaVersion()))
                .append(",\"cpuCores\":")
                .append(agent.cpuCores())
                .append(",\"cpuLoadPercent\":")
                .append(agent.cpuLoadPercent())
                .append(",\"memoryTotalBytes\":")
                .append(agent.memoryTotalBytes())
                .append(",\"memoryUsedBytes\":")
                .append(agent.memoryUsedBytes())
                .append(",\"memoryAvailableBytes\":")
                .append(agent.memoryAvailableBytes())
                .append(",\"diskTotalBytes\":")
                .append(agent.diskTotalBytes())
                .append(",\"diskUsedBytes\":")
                .append(agent.diskUsedBytes())
                .append(",\"diskFreeBytes\":")
                .append(agent.diskFreeBytes())
                .append(",\"uptimeSeconds\":")
                .append(agent.uptimeSeconds())
                .append(",\"batteryPercent\":")
                .append(agent.batteryPercent())
                .append(",\"receivedAt\":")
                .append(q(
                        agent.receivedAt()
                                .toString()
                ))
                .append(",\"networkInterfaces\":")
                .append(arr(agent.networkInterfaces()))
                .append(",\"processes\":")
                .append(arr(agent.processes()))
                .append(",\"adminPermissions\":")
                .append(arr(agent.adminPermissions()))
                .append('}');
    }

    private void apiAdmin(
            HttpExchange exchange
    ) throws IOException {

        try {

            URI uri =
                    exchange.getRequestURI();

            String ip =
                    query(uri, "ip");

            String path =
                    query(uri, "path");

            String query =
                    query(uri, "query");

            if (ip == null || path == null) {

                send(
                        exchange,
                        "{\"error\":\"ip and path are required\"}",
                        JSON_CONTENT_TYPE,
                        400
                );

                return;
            }

            AgentRegistry.ProxyResponse response =
                    agents.proxy(
                            ip,
                            path,
                            query
                    );

            send(
                    exchange,
                    new String(
                            response.body(),
                            StandardCharsets.UTF_8
                    ),
                    response.contentType(),
                    response.status()
            );

        } catch (Exception exception) {

            send(
                    exchange,
                    q(exception.getMessage()),
                    JSON_CONTENT_TYPE,
                    400
            );
        }
    }

    private static String query(
            URI uri,
            String key
    ) {

        String rawQuery =
                uri.getRawQuery();

        if (rawQuery == null) {
            return null;
        }

        for (String pair :
                rawQuery.split("&")) {

            String[] parts =
                    pair.split("=", 2);

            if (parts.length != 2
                    || !parts[0].equals(key)) {
                continue;
            }

            try {

                return URLDecoder.decode(
                        parts[1],
                        StandardCharsets.UTF_8
                );

            } catch (Exception ignored) {
                return null;
            }
        }

        return null;
    }

    private void apiNetwork(
            HttpExchange exchange
    ) throws IOException {

        String json;

        if (network == null) {
            json = "{}";
        } else {

            json =
                    "{"
                            + "\"interface\":"
                            + q(network.getInterfaceName())
                            + ",\"ip\":"
                            + q(network.getLocalAddress())
                            + ",\"network\":"
                            + q(
                                    network.getNetworkAddress()
                                            + "/"
                                            + network.getPrefixLength()
                            )
                            + ",\"gateway\":"
                            + q(network.getDefaultGateway())
                            + ",\"dns\":"
                            + arr(network.getDnsServers())
                            + "}";
        }

        send(
                exchange,
                json,
                JSON_CONTENT_TYPE
        );
    }

    private void apiEvents(
            HttpExchange exchange
    ) throws IOException {

        StringBuilder json =
                new StringBuilder("[");

        for (int i = 0; i < events.size(); i++) {

            if (i > 0) {
                json.append(',');
            }

            appendEvent(
                    json,
                    events.get(i)
            );
        }

        json.append(']');

        send(
                exchange,
                json.toString(),
                JSON_CONTENT_TYPE
        );
    }

    private void appendEvent(
            StringBuilder json,
            NetworkEvent event
    ) {

        Device device =
                event.device();

        json.append('{')
                .append("\"type\":")
                .append(q(event.type()))
                .append(",\"ip\":")
                .append(q(device.getIpAddress()))
                .append(",\"name\":")
                .append(q(device.displayName()))
                .append(",\"hostname\":")
                .append(q(device.getHostname()))
                .append(",\"timestamp\":")
                .append(q(
                        device.getDiscoveredAt()
                                .toString()
                ))
                .append('}');
    }

    private static String arr(
            List<String> values
    ) {

        StringBuilder json =
                new StringBuilder("[");

        for (int i = 0; i < values.size(); i++) {

            if (i > 0) {
                json.append(',');
            }

            json.append(
                    q(values.get(i))
            );
        }

        return json
                .append(']')
                .toString();
    }

    private static String q(
            String value
    ) {

        if (value == null) {
            return "null";
        }

        StringBuilder output =
                new StringBuilder("\"");

        for (char character :
                value.toCharArray()) {

            switch (character) {

                case '\\' ->
                        output.append("\\\\");

                case '"' ->
                        output.append("\\\"");

                case '\b' ->
                        output.append("\\b");

                case '\f' ->
                        output.append("\\f");

                case '\n' ->
                        output.append("\\n");

                case '\r' ->
                        output.append("\\r");

                case '\t' ->
                        output.append("\\t");

                default -> {

                    if (character < 0x20) {

                        output.append(
                                String.format(
                                        "\\u%04x",
                                        (int) character
                                )
                        );

                    } else {

                        output.append(character);
                    }
                }
            }
        }

        return output
                .append('"')
                .toString();
    }

    private static void send(
            HttpExchange exchange,
            String body,
            String type
    ) throws IOException {

        send(
                exchange,
                body,
                type,
                200
        );
    }

    private static void send(
            HttpExchange exchange,
            String body,
            String type,
            int status
    ) throws IOException {

        byte[] bytes =
                body.getBytes(
                        StandardCharsets.UTF_8
                );

        exchange.getResponseHeaders()
                .set(
                        "Content-Type",
                        type
                );

        exchange.getResponseHeaders()
                .set(
                        "Cache-Control",
                        "no-store"
                );

        exchange.sendResponseHeaders(
                status,
                bytes.length
        );

        try (
                OutputStream output =
                        exchange.getResponseBody()
        ) {

            output.write(bytes);
        }
    }
}