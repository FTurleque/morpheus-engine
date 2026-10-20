package com.morpheus.api;

import com.morpheus.application.context.DisabledTechnicalContextProvider;
import com.morpheus.application.lifecycle.mutation.ChangeWriteCapabilityObservation;
import com.morpheus.application.operability.OperationalLogMode;
import com.morpheus.application.operability.OperationalRuntime;
import com.morpheus.application.reference.ExternalIntegrationStatus;
import com.morpheus.application.reference.ExternalIntegrationStatusProvider;
import com.morpheus.application.reference.ExternalReferenceResolverRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Availability of the remote facade while its privileged capacity is occupied.
 *
 * <p>A remote mutation deliberately carries no upstream deadline: answering 504 for a commit that may already be
 * durable is worse than answering late. The consequence is that a genuinely blocked mutation occupies a request
 * slot and a privileged slot for as long as it takes, so the facade must stay observable and must give every
 * slot back on every exit path -- including the paths where it refused the request.</p>
 */
class MorpheusRemoteMutationAvailabilityTest {
    private static final int MAX_CONCURRENT = 1;
    private static final int CONCURRENT_MUTATIONS = 6;

    @TempDir
    Path temp;

    /**
     * Status answers while the single request slot is held by a mutation, and reports the mutation.
     *
     * <p>Status is bounded on its own lane, independent of the request budget it describes, so a server at its
     * request ceiling still answers it; the {@code activeRequests} it reports is the work in flight and not
     * itself.</p>
     */
    @Test
    void statusStaysAnswerableAndNamesTheMutationHoldingTheRequestSlot() throws Exception {
        Path database = temp.resolve("morpheus.db");
        seedDatabase(database);
        Path auth = temp.resolve("remote-auth.txt");
        var admin = MorpheusRemoteIdentityFile.create(auth, "admin", MorpheusRemoteRole.ADMIN);
        HttpClient client = RemoteHttpTestSupport.trustedClient(keyStore());

        try (MorpheusRemoteHttpServer server = start(database, auth);
             ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_MUTATIONS + 1)) {
            URI base = URI.create("https://127.0.0.1:" + server.port() + "/api/v1");

            Map<String, Object> idle = statusOf(client, base, admin.token());
            assertEquals(0, ((Number) idle.get("activeRequests")).intValue(),
                    "status must not count itself against the request budget it reports");
            assertEquals(0, ((Number) idle.get("activePrivilegedRequests")).intValue());
            assertEquals(MAX_CONCURRENT, ((Number) idle.get("maxConcurrentPrivilegedRequests")).intValue());

            try (HeldDatabase held = HeldDatabase.take(database)) {
                Future<HttpResponse<String>> backup = pool.submit(() -> RemoteHttpTestSupport.send(
                        client, base.resolve("/api/v1/server/backups"), "POST", admin.token(), null));

                // Every one of these polls is itself the property under test: had status still taken a request
                // slot, the observation below could never be made, because the mutation is holding the only one.
                Map<String, Object> busy = awaitStatusWhere(client, base, admin.token(),
                        status -> ((Number) status.get("activePrivilegedRequests")).intValue() == 1);
                assertEquals(1, ((Number) busy.get("activeRequests")).intValue(),
                        "the mutation must be visible as the request holding the slot");
                assertTrue(((Number) busy.get("oldestActivePrivilegedRequestMillis")).longValue() >= 0L);

                held.release();
                HttpResponse<String> created = backup.get(60, TimeUnit.SECONDS);
                assertEquals(201, created.statusCode(), created::body);
            }

            Map<String, Object> settled = awaitSettledStatus(client, base, admin.token());
            assertEquals(0, ((Number) settled.get("activeRequests")).intValue());
            assertEquals(0, ((Number) settled.get("activePrivilegedRequests")).intValue());
            assertEquals(0L, ((Number) settled.get("oldestActivePrivilegedRequestMillis")).longValue());
            assertEquals(1L, ((Number) settled.get("totalPrivilegedRequests")).longValue());
        }
    }

    /**
     * Saturating the privileged lane refuses the excess and leaks nothing.
     *
     * <p>The assertion that matters is the one made after everything settles: a refusal takes both a privileged
     * slot decision and a request slot decision, and a slot released on the success path only would leave the
     * facade permanently smaller than it reports.</p>
     */
    @Test
    void concurrentMutationsAreRefusedFairlyAndGiveEverySlotBack() throws Exception {
        Path database = temp.resolve("morpheus.db");
        seedDatabase(database);
        Path auth = temp.resolve("remote-auth.txt");
        var admin = MorpheusRemoteIdentityFile.create(auth, "admin", MorpheusRemoteRole.ADMIN);
        var reader = MorpheusRemoteIdentityFile.create(auth, "reader", MorpheusRemoteRole.READ);
        HttpClient client = RemoteHttpTestSupport.trustedClient(keyStore());

        try (MorpheusRemoteHttpServer server = start(database, auth);
             ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_MUTATIONS)) {
            URI base = URI.create("https://127.0.0.1:" + server.port() + "/api/v1");

            List<Integer> statuses;
            try (HeldDatabase held = HeldDatabase.take(database)) {
                List<Future<Integer>> mutations = new ArrayList<>();
                mutations.add(pool.submit(() -> backup(client, base, admin.token())));
                awaitStatusWhere(client, base, admin.token(),
                        status -> ((Number) status.get("activePrivilegedRequests")).intValue() == 1);

                // The only privileged slot is taken and stays taken until the database is released, so every one
                // of these is refused whatever the scheduling. A refusal is either at the privileged lane or, when
                // several arrive at once, at the authentication lane before it: the aggregate counts both.
                for (int index = 1; index < CONCURRENT_MUTATIONS; index++) {
                    mutations.add(pool.submit(() -> backup(client, base, admin.token())));
                }
                awaitStatusWhere(client, base, admin.token(), status ->
                        ((Number) status.get("throttledRequests")).longValue() == CONCURRENT_MUTATIONS - 1);

                held.release();
                statuses = new ArrayList<>();
                for (Future<Integer> mutation : mutations) {
                    statuses.add(mutation.get(60, TimeUnit.SECONDS));
                }
            }

            assertTrue(statuses.contains(201), () -> "at least one mutation must succeed: " + statuses);
            assertTrue(statuses.contains(429), () -> "bounded privileged capacity must refuse the excess: " + statuses);
            assertTrue(statuses.stream().allMatch(code -> code == 201 || code == 429), () -> "unexpected: " + statuses);

            Map<String, Object> settled = awaitSettledStatus(client, base, admin.token());
            assertEquals(0, ((Number) settled.get("activeRequests")).intValue(),
                    "a refused request must give its request slot back");
            assertEquals(0, ((Number) settled.get("activePrivilegedRequests")).intValue(),
                    "a refused mutation must give its privileged slot back");
            long throttledPrivileged = ((Number) settled.get("throttledPrivilegedRequests")).longValue();
            assertTrue(throttledPrivileged > 0L, "privileged refusals must be attributed to write/admin pressure");
            assertTrue(((Number) settled.get("throttledRequests")).longValue() >= throttledPrivileged,
                    "the aggregate must still contain every privileged refusal");
            // The whole capacity is free again, so an ordinary read is served normally.
            assertEquals(200, RemoteHttpTestSupport.send(
                    client, base.resolve("/api/v1/health"), "GET", reader.token(), null).statusCode());
        }
    }

    /**
     * Waits for the request gauges to become quiescent once the client holds every response.
     *
     * <p>The facade releases its request slot and privileged permit in the handler's {@code finally}, after the
     * response writer can already have made the final bytes visible to the client. A single status read taken
     * straight after {@code Future#get()} therefore races that bookkeeping, so the gauges are awaited rather than
     * read once.</p>
     *
     * <p>This waits for a state rather than retrying until green: the bound is real, and a slot that is genuinely
     * leaked still fails the assertions that follow once the deadline expires.</p>
     */
    private Map<String, Object> awaitSettledStatus(HttpClient client, URI base, String token) throws Exception {
        return awaitStatusWhere("the remote facade to release every request slot", client, base, token,
                status -> ((Number) status.get("activeRequests")).intValue() == 0
                        && ((Number) status.get("activePrivilegedRequests")).intValue() == 0);
    }

    private Map<String, Object> awaitStatusWhere(
            HttpClient client,
            URI base,
            String token,
            java.util.function.Predicate<Map<String, Object>> condition) throws Exception {
        return awaitStatusWhere("the remote status to reach the expected state", client, base, token, condition);
    }

    private Map<String, Object> awaitStatusWhere(
            String what,
            HttpClient client,
            URI base,
            String token,
            java.util.function.Predicate<Map<String, Object>> condition) throws Exception {
        return BoundedWait.untilObserved(
                what,
                Duration.ofSeconds(60),
                BoundedWait.HTTPS_STATUS_POLL,
                () -> statusOf(client, base, token),
                condition);
    }

    private Map<String, Object> statusOf(HttpClient client, URI base, String token) throws Exception {
        HttpResponse<String> response = RemoteHttpTestSupport.send(
                client, base.resolve("/api/v1/server/status"), "GET", token, null);
        assertEquals(200, response.statusCode(),
                () -> "remote status must stay answerable under saturation: " + response.body());
        return statusData(response.body());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> statusData(String body) {
        Map<String, Object> envelope = JsonMapper.builder().build().readValue(body, Map.class);
        return (Map<String, Object>) envelope.get("data");
    }

    private static int backup(HttpClient client, URI base, String token) throws Exception {
        return RemoteHttpTestSupport.send(client, base.resolve("/api/v1/server/backups"), "POST", token, null)
                .statusCode();
    }

    /** A database file with a table of its own, which the server brings up to the current schema on first use. */
    private void seedDatabase(Path database) throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE availability_load(id INTEGER PRIMARY KEY, payload BLOB NOT NULL)");
            statement.execute("INSERT INTO availability_load(payload) VALUES(randomblob(1024))");
        }
    }

    /**
     * Holds the database in a transaction of its own, so a backup request is parked inside the handler for as long
     * as the test says and not for as long as the copy happens to take.
     *
     * <p>The server opens the database with a busy wait, and an exclusive transaction taken here makes that open
     * wait: the request sits in the mutation, holding its request slot and its privileged permit, until
     * {@link #release()}. The state the test observes is therefore stable for as long as it needs to look at it,
     * and the speed of the disk plays no part in whether the mutation is in flight when the status is read.</p>
     */
    private static final class HeldDatabase implements AutoCloseable {
        private final java.sql.Connection connection;
        private boolean released;

        private HeldDatabase(java.sql.Connection connection) {
            this.connection = connection;
        }

        static HeldDatabase take(Path database) throws Exception {
            java.sql.Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
            try (var statement = connection.createStatement()) {
                statement.execute("BEGIN EXCLUSIVE");
            } catch (Exception | Error failure) {
                connection.close();
                throw failure;
            }
            return new HeldDatabase(connection);
        }

        void release() throws Exception {
            if (released) {
                return;
            }
            released = true;
            try (connection; var statement = connection.createStatement()) {
                statement.execute("ROLLBACK");
            }
        }

        @Override
        public void close() throws Exception {
            release();
        }
    }

    /** Generated once per test so the client can be pinned to the very certificate the server will serve. */
    private Path keyStore() throws Exception {
        Path keyStore = temp.resolve("server.p12");
        if (!Files.isRegularFile(keyStore)) {
            RemoteHttpTestSupport.createKeyStore(keyStore);
        }
        return keyStore;
    }

    private MorpheusRemoteHttpServer start(Path database, Path auth) throws Exception {
        Path allowedWorkspaceRoot = Files.createDirectories(temp.resolve("allowed-workspaces"));
        Path keyStore = keyStore();
        ExternalIntegrationStatusProvider minos = () ->
                new ExternalIntegrationStatus("MINOS", "DISABLED", false, "test", Map.of());
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
                MAX_CONCURRENT,
                new ExternalReferenceResolverRegistry(List.of()),
                minos,
                new DisabledTechnicalContextProvider("NEXUS", "test"),
                project -> ChangeWriteCapabilityObservation.denied("test"), OperationalRuntime.of(OperationalLogMode.OFF));
    }
}
