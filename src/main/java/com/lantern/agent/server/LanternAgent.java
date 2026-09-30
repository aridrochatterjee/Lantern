package com.lantern.agent.server;

import com.lantern.agent.AgentConfig;
import com.lantern.agent.SystemCollector;
import com.lantern.agent.admin.AdminCollector;
import com.lantern.agent.model.SystemSnapshot;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.Executors;

public class LanternAgent {

    private static final String VERSION = "1.1.0";

    private static final String JSON_CONTENT_TYPE =
            "application/json; charset=utf-8";

    private static final String API_PREFIX =
            "/api/v1";

    public static void main(String[] args) throws Exception {

        AgentConfig config =
                AgentConfig.load(args);

        AdminCollector adminCollector =
                new AdminCollector(
                        config.adminPolicy()
                );

        printStartupInfo(
                config,
                adminCollector
        );

        HttpServer server =
                HttpServer.create(
                        new InetSocketAddress(
                                config.bind(),
                                config.port()
                        ),
                        0
                );

        SystemCollector systemCollector =
                new SystemCollector();

        registerRoutes(
                server,
                config,
                adminCollector,
                systemCollector
        );

        server.setExecutor(
                Executors.newCachedThreadPool()
        );

        server.start();
    }

    /**
     * Prints basic agent startup information.
     */
    private static void printStartupInfo(
            AgentConfig config,
            AdminCollector adminCollector
    ) {

        System.out.println(
                "Lantern Agent v" + VERSION
        );

        System.out.println(
                "Listening on http://"
                        + config.bind()
                        + ":"
                        + config.port()
        );

        System.out.println(
                "Token stored in data/agent.token"
        );

        String permissions =
                adminCollector.permissions().isEmpty()
                        ? "none"
                        : String.join(
                                ", ",
                                adminCollector.permissions()
                        );

        System.out.println(
                "Admin permissions: "
                        + permissions
        );
    }

    /**
     * Registers all HTTP API endpoints.
     */
    private static void registerRoutes(
            HttpServer server,
            AgentConfig config,
            AdminCollector adminCollector,
            SystemCollector systemCollector
    ) {

        server.createContext(
                API_PREFIX + "/health",
                exchange ->
                        respond(
                                exchange,
                                config,
                                health(adminCollector)
                        )
        );

        server.createContext(
                API_PREFIX + "/system",
                exchange ->
                        respond(
                                exchange,
                                config,
                                json(
                                        systemCollector.collect(),
                                        adminCollector
                                )
                        )
        );

        server.createContext(
                API_PREFIX + "/resources",
                exchange ->
                        respond(
                                exchange,
                                config,
                                json(
                                        systemCollector.collect(),
                                        adminCollector
                                )
                        )
        );

        server.createContext(
                API_PREFIX + "/network",
                exchange ->
                        respond(
                                exchange,
                                config,
                                json(
                                        systemCollector.collect(),
                                        adminCollector
                                )
                        )
        );

        server.createContext(
                API_PREFIX + "/processes",
                exchange ->
                        respond(
                                exchange,
                                config,
                                json(
                                        systemCollector.collect(),
                                        adminCollector
                                )
                        )
        );

        server.createContext(
                API_PREFIX + "/admin/permissions",
                exchange ->
                        respond(
                                exchange,
                                config,
                                permissions(adminCollector)
                        )
        );

        server.createContext(
                API_PREFIX + "/admin/files",
                exchange ->
                        files(
                                exchange,
                                config,
                                adminCollector
                        )
        );

        server.createContext(
                API_PREFIX + "/admin/screen",
                exchange ->
                        screen(
                                exchange,
                                config,
                                adminCollector
                        )
        );

        server.createContext(
                API_PREFIX + "/admin/clipboard",
                exchange ->
                        clipboard(
                                exchange,
                                config,
                                adminCollector
                        )
        );

        server.createContext(
                API_PREFIX + "/admin/browser-history",
                exchange ->
                        browserHistory(
                                exchange,
                                config,
                                adminCollector
                        )
        );
    }

    /**
     * Returns the agent health information.
     */
    private static String health(
            AdminCollector adminCollector
    ) {

        boolean adminEnabled =
                !adminCollector.permissions().isEmpty();

        return "{"
                + q("agent", "lantern")
                + ","
                + q("version", VERSION)
                + ","
                + q("status", "ok")
                + ","
                + "\"adminEnabled\":"
                + adminEnabled
                + ","
                + "\"permissions\":"
                + arr(adminCollector.permissions())
                + ","
                + q(
                        "timestamp",
                        Instant.now().toString()
                )
                + "}";
    }

    /**
     * Handles file listing and file reading.
     */
    private static void files(
            HttpExchange exchange,
            AgentConfig config,
            AdminCollector adminCollector
    ) throws IOException {

        if (!authorized(exchange, config)) {
            unauthorized(exchange);
            return;
        }

        try {

            String path =
                    query(
                            exchange.getRequestURI(),
                            "path"
                    );

            String mode =
                    query(
                            exchange.getRequestURI(),
                            "mode"
                    );

            String body;

            if ("read".equalsIgnoreCase(mode)) {

                body = jsonText(
                        adminCollector.readText(
                                path,
                                1_000_000
                        )
                );

            } else {

                body = lines(
                        adminCollector.listFiles(path)
                );
            }

            respondAuthorized(
                    exchange,
                    body,
                    JSON_CONTENT_TYPE
            );

        } catch (Exception exception) {

            error(
                    exchange,
                    400,
                    exception.getMessage()
            );
        }
    }

    /**
     * Returns the current clipboard contents.
     */
    private static void clipboard(
            HttpExchange exchange,
            AgentConfig config,
            AdminCollector adminCollector
    ) throws IOException {

        if (!authorized(exchange, config)) {
            unauthorized(exchange);
            return;
        }

        try {

            respondAuthorized(
                    exchange,
                    jsonText(
                            adminCollector.clipboardText()
                    ),
                    JSON_CONTENT_TYPE
            );

        } catch (Exception exception) {

            error(
                    exchange,
                    400,
                    exception.getMessage()
            );
        }
    }

    /**
     * Returns browser history entries.
     */
    private static void browserHistory(
            HttpExchange exchange,
            AgentConfig config,
            AdminCollector adminCollector
    ) throws IOException {

        if (!authorized(exchange, config)) {
            unauthorized(exchange);
            return;
        }

        try {

            respondAuthorized(
                    exchange,
                    lines(
                            adminCollector.browserHistory(100)
                    ),
                    JSON_CONTENT_TYPE
            );

        } catch (Exception exception) {

            error(
                    exchange,
                    400,
                    exception.getMessage()
            );
        }
    }

    /**
     * Returns a PNG screenshot from the agent machine.
     */
    private static void screen(
            HttpExchange exchange,
            AgentConfig config,
            AdminCollector adminCollector
    ) throws IOException {

        if (!authorized(exchange, config)) {
            unauthorized(exchange);
            return;
        }

        try {

            byte[] png =
                    adminCollector.screenPng();

            exchange.getResponseHeaders().set(
                    "Content-Type",
                    "image/png"
            );

            exchange.getResponseHeaders().set(
                    "Cache-Control",
                    "no-store"
            );

            exchange.sendResponseHeaders(
                    200,
                    png.length
            );

            try (OutputStream output =
                         exchange.getResponseBody()) {

                output.write(png);
            }

        } catch (Exception exception) {

            error(
                    exchange,
                    400,
                    exception.getMessage()
            );
        }
    }

    /**
     * Checks whether the request contains the configured token.
     */
    private static boolean authorized(
            HttpExchange exchange,
            AgentConfig config
    ) {

        String authorization =
                exchange.getRequestHeaders()
                        .getFirst("Authorization");

        return config.token().equals(
                authorization
        );
    }

    /**
     * Sends an unauthorized response.
     */
    private static void unauthorized(
            HttpExchange exchange
    ) throws IOException {

        exchange.getResponseHeaders().set(
                "WWW-Authenticate",
                "Bearer"
        );

        exchange.sendResponseHeaders(
                401,
                -1
        );

        exchange.close();
    }

    /**
     * Sends an authenticated JSON response.
     */
    private static void respond(
            HttpExchange exchange,
            AgentConfig config,
            String body
    ) throws IOException {

        if (!authorized(exchange, config)) {
            unauthorized(exchange);
            return;
        }

        respondAuthorized(
                exchange,
                body,
                JSON_CONTENT_TYPE
        );
    }

    /**
     * Sends a normal HTTP response.
     */
    private static void respondAuthorized(
            HttpExchange exchange,
            String body,
            String contentType
    ) throws IOException {

        if (!"GET".equals(
                exchange.getRequestMethod()
        )) {

            exchange.sendResponseHeaders(
                    405,
                    -1
            );

            exchange.close();
            return;
        }

        byte[] data =
                body.getBytes(
                        StandardCharsets.UTF_8
                );

        exchange.getResponseHeaders().set(
                "Content-Type",
                contentType
        );

        exchange.getResponseHeaders().set(
                "Cache-Control",
                "no-store"
        );

        exchange.sendResponseHeaders(
                200,
                data.length
        );

        try (OutputStream output =
                     exchange.getResponseBody()) {

            output.write(data);
        }
    }

    /**
     * Sends a JSON error response.
     */
    private static void error(
            HttpExchange exchange,
            int statusCode,
            String message
    ) throws IOException {

        String body =
                jsonText(
                        message == null
                                ? "error"
                                : message
                );

        byte[] data =
                body.getBytes(
                        StandardCharsets.UTF_8
                );

        exchange.getResponseHeaders().set(
                "Content-Type",
                JSON_CONTENT_TYPE
        );

        exchange.sendResponseHeaders(
                statusCode,
                data.length
        );

        try (OutputStream output =
                     exchange.getResponseBody()) {

            output.write(data);
        }
    }

    /**
     * Converts a system snapshot into JSON.
     */
    private static String json(
            SystemSnapshot snapshot,
            AdminCollector adminCollector
    ) {

        return "{"
                + q(
                        "timestamp",
                        snapshot.timestamp().toString()
                )
                + ","
                + q(
                        "hostname",
                        snapshot.hostname()
                )
                + ","
                + q(
                        "os",
                        snapshot.os()
                )
                + ","
                + q(
                        "kernel",
                        snapshot.kernel()
                )
                + ","
                + q(
                        "architecture",
                        snapshot.architecture()
                )
                + ","
                + q(
                        "hardwareModel",
                        snapshot.hardwareModel()
                )
                + ","
                + q(
                        "cpuModel",
                        snapshot.cpuModel()
                )
                + ","
                + q(
                        "gpu",
                        snapshot.gpu()
                )
                + ","
                + q(
                        "loggedInUser",
                        snapshot.loggedInUser()
                )
                + ","
                + q(
                        "javaVersion",
                        snapshot.javaVersion()
                )
                + ","
                + "\"cpuCores\":"
                + snapshot.cpuCores()
                + ","
                + "\"cpuLoadPercent\":"
                + snapshot.cpuLoadPercent()
                + ","
                + "\"memoryTotalBytes\":"
                + snapshot.memoryTotalBytes()
                + ","
                + "\"memoryUsedBytes\":"
                + snapshot.memoryUsedBytes()
                + ","
                + "\"memoryAvailableBytes\":"
                + snapshot.memoryAvailableBytes()
                + ","
                + "\"diskTotalBytes\":"
                + snapshot.diskTotalBytes()
                + ","
                + "\"diskUsedBytes\":"
                + snapshot.diskUsedBytes()
                + ","
                + "\"diskFreeBytes\":"
                + snapshot.diskFreeBytes()
                + ","
                + "\"uptimeSeconds\":"
                + snapshot.uptimeSeconds()
                + ","
                + "\"batteryPresent\":"
                + snapshot.batteryPresent()
                + ","
                + "\"batteryPercent\":"
                + snapshot.batteryPercent()
                + ","
                + "\"networkInterfaces\":"
                + arr(snapshot.networkInterfaces())
                + ","
                + "\"processes\":"
                + arr(snapshot.processes())
                + ","
                + "\"adminEnabled\":"
                + !adminCollector.permissions().isEmpty()
                + ","
                + "\"adminPermissions\":"
                + arr(adminCollector.permissions())
                + "}";
    }

    /**
     * Returns the configured admin permissions and allowed roots.
     */
    private static String permissions(
            AdminCollector adminCollector
    ) {

        List<String> roots =
                adminCollector.policy()
                        .allowedRoots()
                        .stream()
                        .map(Object::toString)
                        .toList();

        return "{"
                + "\"enabled\":"
                + !adminCollector.permissions().isEmpty()
                + ","
                + "\"permissions\":"
                + arr(adminCollector.permissions())
                + ","
                + "\"roots\":"
                + arr(roots)
                + "}";
    }

    private static String lines(
            List<String> values
    ) {

        return arr(values);
    }

    private static String jsonText(
            String value
    ) {

        return quote(value);
    }

    private static String q(
            String key,
            String value
    ) {

        return "\""
                + key
                + "\":"
                + quote(value);
    }

    /**
     * Escapes a Java string for JSON.
     */
    private static String quote(
            String value
    ) {

        if (value == null) {
            return "null";
        }

        StringBuilder result =
                new StringBuilder("\"");

        for (char character :
                value.toCharArray()) {

            switch (character) {

                case '\\' ->
                        result.append("\\\\");

                case '"' ->
                        result.append("\\\"");

                case '\n' ->
                        result.append("\\n");

                case '\r' ->
                        result.append("\\r");

                case '\t' ->
                        result.append("\\t");

                case '\b' ->
                        result.append("\\b");

                case '\f' ->
                        result.append("\\f");

                default -> {

                    if (character < 0x20) {

                        result.append(
                                String.format(
                                        "\\u%04x",
                                        (int) character
                                )
                        );

                    } else {

                        result.append(character);
                    }
                }
            }
        }

        return result
                .append('"')
                .toString();
    }

    /**
     * Converts a list of strings into a JSON array.
     */
    private static String arr(
            List<String> values
    ) {

        StringBuilder result =
                new StringBuilder("[");

        for (int i = 0;
             i < values.size();
             i++) {

            if (i > 0) {
                result.append(',');
            }

            result.append(
                    quote(values.get(i))
            );
        }

        return result
                .append(']')
                .toString();
    }

    /**
     * Reads a query parameter from the request URI.
     */
    private static String query(
            URI uri,
            String key
    ) {

        String rawQuery =
                uri.getRawQuery();

        if (rawQuery == null) {
            return null;
        }

        for (String parameter :
                rawQuery.split("&")) {

            String[] parts =
                    parameter.split("=", 2);

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
}