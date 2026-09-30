package com.lantern.network;

import com.lantern.model.Service;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class PortScanner {

    private static final int MAX_THREADS = 20;
    private static final int BANNER_TIMEOUT_MS = 500;
    private static final int MAX_BANNER_LENGTH = 300;

    public List<Service> scan(
            String ip,
            int[] ports,
            int timeoutMs
    ) {

        if (ports == null || ports.length == 0) {
            return List.of();
        }

        int threadCount = Math.min(
                MAX_THREADS,
                Math.max(1, ports.length)
        );

        ExecutorService pool =
                Executors.newFixedThreadPool(threadCount);

        try {
            List<Future<Service>> futures =
                    submitScanTasks(
                            pool,
                            ip,
                            ports,
                            timeoutMs
                    );

            return collectResults(futures);

        } catch (InterruptedException e) {

            Thread.currentThread().interrupt();
            return List.of();

        } finally {

            pool.shutdownNow();
        }
    }

    private List<Future<Service>> submitScanTasks(
            ExecutorService pool,
            String ip,
            int[] ports,
            int timeoutMs
    ) {

        List<Future<Service>> futures =
                new ArrayList<>();

        for (int port : ports) {
            futures.add(
                    pool.submit(
                            () -> check(
                                    ip,
                                    port,
                                    timeoutMs
                            )
                    )
            );
        }

        return futures;
    }

    private List<Service> collectResults(
            List<Future<Service>> futures
    ) throws InterruptedException {

        List<Service> found =
                new ArrayList<>();

        for (Future<Service> future : futures) {

            try {
                Service service = future.get();

                if (service != null) {
                    found.add(service);
                }

            } catch (ExecutionException ignored) {
                // Ignore individual port failures.
            }
        }

        found.sort(
                Comparator.comparingInt(
                        Service::port
                )
        );

        return found;
    }

    private Service check(
            String ip,
            int port,
            int timeoutMs
    ) {

        try (Socket socket = new Socket()) {

            socket.connect(
                    new InetSocketAddress(ip, port),
                    timeoutMs
            );

            socket.setSoTimeout(
                    Math.min(
                            BANNER_TIMEOUT_MS,
                            timeoutMs
                    )
            );

            String banner =
                    probeBanner(socket, port);

            return new Service(
                    port,
                    serviceName(port),
                    banner
            );

        } catch (Exception ignored) {
            return null;
        }
    }

    private String probeBanner(
            Socket socket,
            int port
    ) {

        try {

            if (supportsHttpProbe(port)) {
                sendHttpProbe(socket);
            }

            byte[] buffer = new byte[1024];

            int bytesRead =
                    socket.getInputStream()
                            .read(buffer);

            if (bytesRead <= 0) {
                return null;
            }

            String banner =
                    new String(
                            buffer,
                            0,
                            bytesRead,
                            StandardCharsets.ISO_8859_1
                    )
                    .replaceAll(
                            "[\\r\\n]+",
                            " | "
                    )
                    .trim();

            if (banner.length() > MAX_BANNER_LENGTH) {
                return banner.substring(
                        0,
                        MAX_BANNER_LENGTH
                );
            }

            return banner;

        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean supportsHttpProbe(int port) {

        return switch (port) {
            case 80,
                 8000,
                 8080,
                 8096,
                 3000,
                 5000,
                 8443 -> true;

            default -> false;
        };
    }

    private void sendHttpProbe(
            Socket socket
    ) throws IOException {

        String request =
                "HEAD / HTTP/1.0\r\n"
                        + "Host: lantern.local\r\n"
                        + "Connection: close\r\n"
                        + "\r\n";

        socket.getOutputStream().write(
                request.getBytes(
                        StandardCharsets.US_ASCII
                )
        );

        socket.getOutputStream().flush();
    }

    private String serviceName(int port) {

        return switch (port) {

            case 20 -> "FTP-DATA";
            case 21 -> "FTP";
            case 22 -> "SSH";
            case 23 -> "Telnet";
            case 25 -> "SMTP";
            case 53 -> "DNS";
            case 67, 68 -> "DHCP";
            case 80 -> "HTTP";
            case 110 -> "POP3";
            case 123 -> "NTP";
            case 135 -> "MS-RPC";
            case 139 -> "NetBIOS";
            case 143 -> "IMAP";
            case 443 -> "HTTPS";
            case 445 -> "SMB";
            case 631 -> "IPP";
            case 1433 -> "MSSQL";
            case 1883 -> "MQTT";
            case 3000 -> "HTTP-ALT";
            case 3306 -> "MySQL";
            case 3389 -> "RDP";
            case 5000 -> "HTTP-ALT";
            case 5432 -> "PostgreSQL";
            case 5900 -> "VNC";
            case 6379 -> "Redis";
            case 8000, 8080 -> "HTTP-ALT";
            case 8096 -> "Jellyfin";
            case 8443 -> "HTTPS-ALT";

            default -> "TCP";
        };
    }
}