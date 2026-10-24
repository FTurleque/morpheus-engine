package com.morpheus.api.remote;

import com.morpheus.api.AllowedWorkspaceRoots;
import com.morpheus.application.context.DisabledTechnicalContextProvider;
import com.morpheus.application.lifecycle.mutation.ChangeWriteCapabilityObservation;
import com.morpheus.application.operability.OperationalLogMode;
import com.morpheus.application.operability.OperationalRuntime;
import com.morpheus.application.reference.ExternalIntegrationStatus;
import com.morpheus.application.reference.ExternalIntegrationStatusProvider;
import com.morpheus.application.reference.ExternalReferenceResolverRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The remote facade keeps answering what does not need the database while backups wait on a busy one (AUD-PRF-19).
 *
 * <p>The backup route is the one route the facade serves with SQLite in its own hands: every other route is forwarded
 * to the local server over HTTP, where the request's thread parks. The SQLite driver waits for a busy database inside
 * native code, where a virtual thread stays mounted on its carrier, and the carriers are as many as the processors. The
 * privileged lane admits a quarter of the request budget, so a configuration that allows more backups than there are
 * carriers lets them hold every carrier, and the status route - which touches no database - has nowhere to run.</p>
 *
 * <p>The test holds the database with an exclusive writer, puts as many backups in flight as the privileged lane
 * admits, and asks for the status while they wait. It states what it measured when it fails.</p>
 */
class RemoteSchedulerAvailabilityUnderSqliteWaitTest {
    private static final Duration WRITER_HOLDS = Duration.ofSeconds(3);
    private static final Duration STATUS_BOUND = Duration.ofMillis(1_500);
    private static final int STATUS_PROBES = 8;

    @TempDir
    Path temp;

    @Test
    void statusIsAnsweredWhileMoreBackupsThanCarriersWaitOnABusyDatabase() throws Exception {
        int processors = Runtime.getRuntime().availableProcessors();
        int privilegedLane = processors + 4;
        int maxConcurrent = 4 * privilegedLane;
        assertEquals(privilegedLane, MorpheusRemoteHttpServer.privilegedConcurrencyLimit(maxConcurrent));

        Path database = temp.resolve("busy.db");
        Path auth = temp.resolve("remote-auth.txt");
        var admin = MorpheusRemoteIdentityFile.create(auth, "admin", MorpheusRemoteRole.ADMIN);
        Path keyStore = RemoteHttpTestSupport.createKeyStore(temp.resolve("server.p12"));
        HttpClient client = RemoteHttpTestSupport.trustedClient(keyStore);

        try (MorpheusRemoteHttpServer server = start(database, auth, keyStore, maxConcurrent)) {
            URI base = URI.create("https://127.0.0.1:" + server.port() + "/api/v1");
            assertEquals(200, RemoteHttpTestSupport.send(
                    client, URI.create(base + "/projects"), "GET", admin.token(), null).statusCode(),
                    "the database is ready");
            assertEquals(200, RemoteHttpTestSupport.send(
                    client, URI.create(base + "/server/status"), "GET", admin.token(), null).statusCode());

            List<CompletableFuture<HttpResponse<String>>> backups = new ArrayList<>();
            List<Long> statusMillis = new ArrayList<>();
            List<String> refused = new ArrayList<>();
            try (Connection writer = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
                 Statement statement = writer.createStatement()) {
                statement.execute("BEGIN EXCLUSIVE");
                long heldSince = System.nanoTime();
                for (int number = 0; number < privilegedLane; number++) {
                    backups.add(client.sendAsync(
                            HttpRequest.newBuilder(URI.create(base + "/server/backups"))
                                    .timeout(Duration.ofSeconds(60))
                                    .header("Authorization", "Bearer " + admin.token())
                                    .POST(HttpRequest.BodyPublishers.noBody())
                                    .build(),
                            HttpResponse.BodyHandlers.ofString()));
                }
                TimeUnit.MILLISECONDS.sleep(400);

                for (int probe = 0; probe < STATUS_PROBES; probe++) {
                    long started = System.nanoTime();
                    try {
                        HttpResponse<String> status = RemoteHttpTestSupport.send(
                                client, URI.create(base + "/server/status"), "GET", admin.token(), null);
                        assertEquals(200, status.statusCode(), status.body());
                    } catch (IOException unanswered) {
                        // A probe that cannot even connect is the failure being measured, not a reason to stop measuring.
                        refused.add(unanswered.getClass().getSimpleName());
                    }
                    statusMillis.add(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started));
                }

                long remaining = WRITER_HOLDS.toMillis() - TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - heldSince);
                if (remaining > 0) {
                    TimeUnit.MILLISECONDS.sleep(remaining);
                }
                statement.execute("ROLLBACK");
            }
            Map<Integer, Long> backupStatuses = new TreeMap<>();
            for (CompletableFuture<HttpResponse<String>> backup : backups) {
                backupStatuses.merge(backup.get(60, TimeUnit.SECONDS).statusCode(), 1L, Long::sum);
            }

            long slowest = statusMillis.stream().mapToLong(Long::longValue).max().orElseThrow();
            String measured = "processors=" + processors + ", backups in flight=" + privilegedLane
                    + ", backup statuses=" + backupStatuses + ", writer held the database "
                    + WRITER_HOLDS.toMillis() + " ms, status latencies (ms)=" + statusMillis + ", probes that failed=" + refused;
            System.out.println("[remote-scheduler-availability] " + measured);
            assertTrue(refused.isEmpty() && slowest <= STATUS_BOUND.toMillis(), "status must be answered within " + STATUS_BOUND.toMillis()
                    + " ms while backups wait on a busy database; " + measured);
        }
    }

    private MorpheusRemoteHttpServer start(Path database, Path auth, Path keyStore, int maxConcurrent)
            throws Exception {
        Path allowedWorkspaceRoot = Files.createDirectories(temp.resolve("allowed-workspaces"));
        ExternalIntegrationStatusProvider minos = () ->
                new ExternalIntegrationStatus("MINOS", "DISABLED", false, "availability", Map.of());
        return MorpheusRemoteHttpServer.start(
                database,
                temp.resolve("backups"),
                temp.resolve("provider-plugins"),
                AllowedWorkspaceRoots.of(List.of(allowedWorkspaceRoot)),
                "127.0.0.1",
                0,
                auth,
                keyStore,
                RemoteHttpTestSupport.KEYSTORE_PASSWORD.toCharArray(),
                maxConcurrent,
                new ExternalReferenceResolverRegistry(List.of()),
                minos,
                new DisabledTechnicalContextProvider("NEXUS", "availability"),
                project -> ChangeWriteCapabilityObservation.denied("availability"),
                OperationalRuntime.of(OperationalLogMode.OFF));
    }
}
