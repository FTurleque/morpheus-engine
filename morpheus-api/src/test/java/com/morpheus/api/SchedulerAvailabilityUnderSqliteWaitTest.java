package com.morpheus.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A database that makes its callers wait does not stop the server from answering what does not need it.
 *
 * <p>Every request runs on a virtual thread, and the SQLite driver waits for a busy database inside native code, where
 * a virtual thread stays mounted on its carrier. The carriers are as many as the processors: with more requests
 * waiting on the database than there are carriers, a request that touches no database - the health probe an
 * orchestrator uses to decide whether to restart the server - has nowhere to run.</p>
 *
 * <p>The test holds the database with an exclusive writer, puts four times as many reads in flight as there are
 * processors, and asks for health while they wait. It states what it measured when it fails.</p>
 */
class SchedulerAvailabilityUnderSqliteWaitTest {
    private static final Duration WRITER_HOLDS = Duration.ofSeconds(3);
    private static final Duration HEALTH_BOUND = Duration.ofMillis(1_500);
    private static final int HEALTH_PROBES = 8;

    @TempDir
    Path temporaryDirectory;

    @Test
    void healthIsAnsweredWhileMoreReadsThanCarriersWaitOnABusyDatabase() throws Exception {
        Path database = temporaryDirectory.resolve("busy.db");
        int processors = Runtime.getRuntime().availableProcessors();
        int waitingReads = 4 * processors;

        try (MorpheusHttpServer server = MorpheusHttpServer.start(database, "127.0.0.1", 0);
             HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            assertEquals(200, get(client, server, "/api/v1/projects").statusCode(), "the database is ready");
            assertEquals(200, get(client, server, "/api/v1/health").statusCode());

            List<CompletableFuture<HttpResponse<String>>> reads = new ArrayList<>();
            List<Long> healthMillis = new ArrayList<>();
            try (Connection writer = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
                 Statement statement = writer.createStatement()) {
                statement.execute("BEGIN EXCLUSIVE");
                long heldSince = System.nanoTime();
                for (int number = 0; number < waitingReads; number++) {
                    reads.add(client.sendAsync(request(server, "/api/v1/projects"),
                            HttpResponse.BodyHandlers.ofString()));
                }
                TimeUnit.MILLISECONDS.sleep(400);

                for (int probe = 0; probe < HEALTH_PROBES; probe++) {
                    long started = System.nanoTime();
                    HttpResponse<String> health = client.send(
                            request(server, "/api/v1/health"), HttpResponse.BodyHandlers.ofString());
                    healthMillis.add(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
                    assertEquals(200, health.statusCode(), health.body());
                }

                long remaining = WRITER_HOLDS.toMillis() - TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - heldSince);
                if (remaining > 0) {
                    TimeUnit.MILLISECONDS.sleep(remaining);
                }
                statement.execute("ROLLBACK");
            }
            long stillWaitingWhenProbed = reads.stream().filter(read -> !read.isDone()).count();
            java.util.Map<Integer, Long> readStatuses = new java.util.TreeMap<>();
            for (CompletableFuture<HttpResponse<String>> read : reads) {
                readStatuses.merge(read.get(60, TimeUnit.SECONDS).statusCode(), 1L, Long::sum);
            }

            long slowest = healthMillis.stream().mapToLong(Long::longValue).max().orElseThrow();
            String measured = "processors=" + processors + ", reads in flight=" + waitingReads
                    + ", reads still waiting after the writer left=" + stillWaitingWhenProbed
                    + ", read statuses=" + readStatuses + ", writer held the database " + WRITER_HOLDS.toMillis() + " ms, health latencies (ms)="
                    + healthMillis;
            System.out.println("[scheduler-availability] " + measured);
            assertTrue(slowest <= HEALTH_BOUND.toMillis(), "health must be answered within " + HEALTH_BOUND.toMillis()
                    + " ms while reads wait on a busy database; " + measured);
        }
    }

    private static HttpResponse<String> get(HttpClient client, MorpheusHttpServer server, String path)
            throws Exception {
        return client.send(request(server, path), HttpResponse.BodyHandlers.ofString());
    }

    private static HttpRequest request(MorpheusHttpServer server, String path) {
        return HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + server.port() + path))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();
    }
}
