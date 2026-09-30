package com.lantern.agent;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public class AgentClient {

    private static final int CONNECT_TIMEOUT_SECONDS = 2;
    private static final int REQUEST_TIMEOUT_SECONDS = 4;

    private final HttpClient client =
            HttpClient.newBuilder()
                    .connectTimeout(
                            Duration.ofSeconds(
                                    CONNECT_TIMEOUT_SECONDS
                            )
                    )
                    .build();

    /**
     * Information returned by a Lantern agent.
     */
    public record AgentData(
            String hostname,
            String os,
            String kernel,
            String architecture,
            String hardwareModel,
            String cpuModel,
            String gpu,
            String loggedInUser,
            String javaVersion,
            int cpuCores,
            double cpuLoadPercent,
            long memoryTotalBytes,
            long memoryUsedBytes,
            long memoryAvailableBytes,
            long diskTotalBytes,
            long diskUsedBytes,
            long diskFreeBytes,
            long uptimeSeconds,
            int batteryPercent,
            List<String> networkInterfaces,
            List<String> processes,
            String rawJson,
            Instant receivedAt,
            List<String> adminPermissions
    ) {
    }

    /**
     * Fetches system information from a Lantern agent.
     */
    public AgentData fetch(
            String host,
            int port,
            String token
    ) throws IOException, InterruptedException {

        String url =
                buildUrl(host, port);

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
                        token
                )
                .GET()
                .build();

        HttpResponse<String> response =
                client.send(
                        request,
                        HttpResponse.BodyHandlers
                                .ofString()
                );

        if (response.statusCode() != 200) {
            throw new IOException(
                    "Agent HTTP " +
                    response.statusCode()
            );
        }

        String json = response.body();

        return parseAgentData(json);
    }

    /**
     * Builds the system information endpoint URL.
     */
    private String buildUrl(
            String host,
            int port
    ) {

        return "http://"
                + host
                + ":"
                + port
                + "/api/v1/system";
    }

    /**
     * Converts the agent's JSON response into AgentData.
     */
    private AgentData parseAgentData(
            String json
    ) {

        return new AgentData(
                stringValue(json, "hostname"),
                stringValue(json, "os"),
                stringValue(json, "kernel"),
                stringValue(json, "architecture"),
                stringValue(json, "hardwareModel"),
                stringValue(json, "cpuModel"),
                stringValue(json, "gpu"),
                stringValue(json, "loggedInUser"),
                stringValue(json, "javaVersion"),

                (int) longValue(
                        json,
                        "cpuCores"
                ),

                numberValue(
                        json,
                        "cpuLoadPercent"
                ),

                longValue(
                        json,
                        "memoryTotalBytes"
                ),

                longValue(
                        json,
                        "memoryUsedBytes"
                ),

                longValue(
                        json,
                        "memoryAvailableBytes"
                ),

                longValue(
                        json,
                        "diskTotalBytes"
                ),

                longValue(
                        json,
                        "diskUsedBytes"
                ),

                longValue(
                        json,
                        "diskFreeBytes"
                ),

                longValue(
                        json,
                        "uptimeSeconds"
                ),

                (int) longValue(
                        json,
                        "batteryPercent"
                ),

                arrayValue(
                        json,
                        "networkInterfaces"
                ),

                arrayValue(
                        json,
                        "processes"
                ),

                json,
                Instant.now(),

                arrayValue(
                        json,
                        "adminPermissions"
                )
        );
    }

    /**
     * Extracts a string value from the JSON response.
     */
    private static String stringValue(
            String json,
            String key
    ) {

        String prefix =
                "\"" + key + "\":\"";

        int start =
                json.indexOf(prefix);

        if (start < 0) {
            return null;
        }

        start += prefix.length();

        StringBuilder value =
                new StringBuilder();

        boolean escaped = false;

        for (int i = start;
             i < json.length();
             i++) {

            char character =
                    json.charAt(i);

            if (escaped) {

                value.append(
                        decodeEscape(character)
                );

                escaped = false;

            } else if (character == '\\') {

                escaped = true;

            } else if (character == '"') {

                break;

            } else {

                value.append(character);
            }
        }

        return value.toString();
    }

    /**
     * Extracts a numeric value from the JSON response.
     */
    private static double numberValue(
            String json,
            String key
    ) {

        try {

            String prefix =
                    "\"" + key + "\":";

            int start =
                    json.indexOf(prefix);

            if (start < 0) {
                return -1;
            }

            start += prefix.length();

            while (start < json.length()
                    && Character.isWhitespace(
                            json.charAt(start)
                    )) {

                start++;
            }

            int end = start;

            while (end < json.length()
                    && isNumberCharacter(
                            json.charAt(end)
                    )) {

                end++;
            }

            return Double.parseDouble(
                    json.substring(start, end)
            );

        } catch (Exception ignored) {
            return -1;
        }
    }

    /**
     * Extracts a long value from the JSON response.
     */
    private static long longValue(
            String json,
            String key
    ) {

        return Math.round(
                numberValue(json, key)
        );
    }

    /**
     * Extracts a JSON string array.
     */
    private static List<String> arrayValue(
            String json,
            String key
    ) {

        String prefix =
                "\"" + key + "\":[";

        int start =
                json.indexOf(prefix);

        if (start < 0) {
            return List.of();
        }

        start += prefix.length();

        List<String> values =
                new ArrayList<>();

        StringBuilder current =
                new StringBuilder();

        boolean insideString = false;
        boolean escaped = false;

        for (int i = start;
             i < json.length();
             i++) {

            char character =
                    json.charAt(i);

            if (!insideString) {

                if (character == '"') {
                    insideString = true;
                    current.setLength(0);

                } else if (character == ']') {
                    break;
                }

                continue;
            }

            if (escaped) {

                current.append(
                        decodeEscape(character)
                );

                escaped = false;

            } else if (character == '\\') {

                escaped = true;

            } else if (character == '"') {

                values.add(
                        current.toString()
                );

                insideString = false;

            } else {

                current.append(character);
            }
        }

        return values;
    }

    /**
     * Converts a JSON escape sequence into its character.
     */
    private static char decodeEscape(
            char character
    ) {

        return switch (character) {
            case 'n' -> '\n';
            case 'r' -> '\r';
            case 't' -> '\t';
            case '"' -> '"';
            case '\\' -> '\\';
            default -> character;
        };
    }

    /**
     * Checks whether a character can be part of a number.
     */
    private static boolean isNumberCharacter(
            char character
    ) {

        return Character.isDigit(character)
                || character == '-'
                || character == '.';
    }
}