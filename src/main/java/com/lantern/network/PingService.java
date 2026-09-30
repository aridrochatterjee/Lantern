package com.lantern.network;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public class PingService {

    private static final long PROCESS_GRACE_PERIOD_MS = 800L;

    public long measure(
            String ip,
            int timeoutMs
    ) {

        long startTime =
                System.nanoTime();

        String operatingSystem =
                System.getProperty(
                        "os.name",
                        ""
                ).toLowerCase(Locale.ROOT);

        try {

            ProcessBuilder processBuilder =
                    buildPingCommand(
                            operatingSystem,
                            ip,
                            timeoutMs
                    );

            Process process =
                    processBuilder
                            .redirectErrorStream(true)
                            .start();

            drainOutput(process);

            if (!waitForProcess(
                    process,
                    timeoutMs
            )) {

                process.destroyForcibly();
                return -1;
            }

            if (process.exitValue() != 0) {
                return -1;
            }

            return calculateLatency(startTime);

        } catch (Exception ignored) {
            return -1;
        }
    }

    /**
     * Builds the platform-specific ping command.
     */
    private ProcessBuilder buildPingCommand(
            String operatingSystem,
            String ip,
            int timeoutMs
    ) {

        if (operatingSystem.contains("win")) {

            return new ProcessBuilder(
                    "ping",
                    "-n",
                    "1",
                    "-w",
                    String.valueOf(timeoutMs),
                    ip
            );
        }

        if (operatingSystem.contains("mac")) {

            return new ProcessBuilder(
                    "ping",
                    "-c",
                    "1",
                    "-W",
                    String.valueOf(timeoutMs),
                    ip
            );
        }

        int timeoutSeconds =
                Math.max(
                        1,
                        (int) Math.ceil(
                                timeoutMs / 1000.0
                        )
                );

        return new ProcessBuilder(
                "ping",
                "-c",
                "1",
                "-W",
                String.valueOf(timeoutSeconds),
                ip
        );
    }

    /**
     * Consumes process output so the process
     * cannot block because of a full output buffer.
     */
    private void drainOutput(
            Process process
    ) throws Exception {

        try (
                BufferedReader reader =
                        new BufferedReader(
                                new InputStreamReader(
                                        process.getInputStream()
                                )
                        )
        ) {

            while (reader.readLine() != null) {
                // Output is intentionally ignored.
            }
        }
    }

    /**
     * Waits for the ping process to finish.
     */
    private boolean waitForProcess(
            Process process,
            int timeoutMs
    ) throws InterruptedException {

        return process.waitFor(
                timeoutMs + PROCESS_GRACE_PERIOD_MS,
                TimeUnit.MILLISECONDS
        );
    }

    /**
     * Calculates the elapsed time in milliseconds.
     */
    private long calculateLatency(
            long startTime
    ) {

        long elapsedMs =
                (System.nanoTime() - startTime)
                        / 1_000_000L;

        return Math.max(1, elapsedMs);
    }
}