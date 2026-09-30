package com.lantern.agent;

import com.lantern.model.Device;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AgentRegistry {

    private static final int CONNECT_TIMEOUT_SECONDS = 2;
    private static final int REQUEST_TIMEOUT_SECONDS = 10;

    private final Map<String, Target> targets =
            new HashMap<>();

    /**
     * Represents a paired Lantern agent.
     */
    public record Target(
            String ip,
            int port,
            String token
    ) {
    }

    /**
     * Represents the response received when proxying
     * a request through an agent.
     */
    public record ProxyResponse(
            int status,
            String contentType,
            byte[] body
    ) {
    }

    /**
     * Creates an AgentRegistry from command-line arguments.
     *
     * Expected format:
     *
     * --agent=192.168.1.20:9000:token
     */
    public static AgentRegistry fromArgs(
            String[] args
    ) {

        AgentRegistry registry =
                new AgentRegistry();

        for (String argument : args) {

            if (!argument.startsWith("--agent=")) {
                continue;
            }

            parseAgent(
                    registry,
                    argument.substring(8)
            );
        }

        return registry;
    }

    /**
     * Parses a single agent configuration.
     */
    private static void parseAgent(
            AgentRegistry registry,
            String value
    ) {

        String[] parts =
                value.split(":", 3);

        if (parts.length != 3) {
            return;
        }

        try {

            String ipAddress = parts[0];
            int port = Integer.parseInt(parts[1]);
            String token = parts[2];

            registry.targets.put(
                    ipAddress,
                    new Target(
                            ipAddress,
                            port,
                            token
                    )
            );

        } catch (NumberFormatException ignored) {
            // Ignore invalid agent configuration.
        }
    }

    /**
     * Enriches discovered devices with information
     * from their paired agents.
     */
    public void enrich(
            List<Device> devices
    ) {

        AgentClient client =
                new AgentClient();

        for (Device device : devices) {

            Target target =
                    targets.get(
                            device.getIpAddress()
                    );

            if (target == null) {
                continue;
            }

            try {

                AgentClient.AgentData agentData =
                        client.fetch(
                                target.ip(),
                                target.port(),
                                target.token()
                        );

                device.setAgentData(agentData);

            } catch (Exception ignored) {
                // Agent may be offline or unreachable.
            }
        }
    }

    /**
     * Checks whether any agents are configured.
     */
    public boolean hasTargets() {
        return !targets.isEmpty();
    }

    /**
     * Proxies an HTTP GET request through a paired agent.
     */
    public ProxyResponse proxy(
            String ip,
            String agentPath,
            String query
    ) throws IOException, InterruptedException {

        Target target = targets.get(ip);

        if (target == null) {
            throw new IOException(
                    "No paired agent for " + ip
            );
        }

        String path =
                normalizePath(agentPath);

        String url =
                buildUrl(
                        target,
                        path,
                        query
                );

        HttpRequest request =
                HttpRequest.newBuilder(
                        URI.create(url)
                )
                .timeout(
                        Duration.ofSeconds(
                                REQUEST_TIMEOUT_SECONDS
                        )
                )
                .header(
                        "Authorization",
                        target.token()
                )
                .GET()
                .build();

        HttpClient client =
                HttpClient.newBuilder()
                        .connectTimeout(
                                Duration.ofSeconds(
                                        CONNECT_TIMEOUT_SECONDS
                                )
                        )
                        .build();

        HttpResponse<byte[]> response =
                client.send(
                        request,
                        HttpResponse.BodyHandlers
                                .ofByteArray()
                );

        String contentType =
                response.headers()
                        .firstValue("Content-Type")
                        .orElse(
                                "application/octet-stream"
                        );

        return new ProxyResponse(
                response.statusCode(),
                contentType,
                response.body()
        );
    }

    /**
     * Ensures an agent path starts with "/".
     */
    private String normalizePath(
            String path
    ) {

        if (path == null || path.isBlank()) {
            return "/";
        }

        return path.startsWith("/")
                ? path
                : "/" + path;
    }

    /**
     * Builds the URL used to contact an agent.
     */
    private String buildUrl(
            Target target,
            String path,
            String query
    ) {

        StringBuilder url =
                new StringBuilder();

        url.append("http://")
                .append(target.ip())
                .append(":")
                .append(target.port())
                .append(path);

        if (query != null && !query.isBlank()) {
            url.append("?")
                    .append(query);
        }

        return url.toString();
    }
}