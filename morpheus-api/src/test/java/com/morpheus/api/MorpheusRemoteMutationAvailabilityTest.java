package com.morpheus.api;

import com.morpheus.application.context.DisabledTechnicalContextProvider;
import com.morpheus.application.lifecycle.mutation.ChangeWriteCapabilityObservation;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
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
     * <p>Status used to be admitted through the same semaphore as the traffic it describes, so a server at its
     * request ceiling answered 429 to the only question worth asking at that moment. It is now bounded on its
     * own lane, which is also why the {@code activeRequests} it reports is the work in flight and not itself.</p>
     */
    @Test
    void statusStaysAnswerableAndNamesTheMutationHoldingTheRequestSlot() throws Exception {
        Path database = temp.resolve("morpheus.db");
        seedSlowBackupPayload(database);
        Path auth = temp.resolve("remote-auth.txt");
        var admin = MorpheusRemoteIdentityFile.create(auth, "admin", MorpheusRemoteRole.ADMIN);
        HttpClient client = RemoteHttpTestSupport.trustedClient();

        try (MorpheusRemoteHttpServer server = start(database, auth);
             ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_MUTATIONS + 1)) {
            URI base = URI.create("https://127.0.0.1:" + server.port() + "/api/v1");

            Map<String, Object> idle = statusOf(client, base, admin.token());
            assertEquals(0, ((Number) idle.get("activeRequests")).intValue(),
                    "status must not count itself against the request budget it reports");
            assertEquals(0, ((Number) idle.get("activePrivilegedRequests")).intValue());
            assertEquals(MAX_CONCURRENT, ((Number) idle.get("maxConcurrentPrivilegedRequests")).intValue());

            Future<HttpResponse<String>> backup = pool.submit(() -> RemoteHttpTestSupport.send(
                    client, base.resolve("/api/v1/server/backups"), "POST", admin.token(), null));

            // Every one of these polls is itself the property under test: had status still taken a request slot,
            // the observation below could never be made, because the mutation is holding the only one.
            Map<String, Object> busy = awaitStatusWhere(
                    client, base, admin.token(), status -> ((Number) status.get("activePrivilegedRequests")).intValue() == 1);
            assertEquals(1, ((Number) busy.get("activeRequests")).intValue(),
                    "the mutation must be visible as the request holding the slot");
            assertTrue(((Number) busy.get("oldestActivePrivilegedRequestMillis")).longValue() >= 0L);

            assertEquals(201, backup.get(60, TimeUnit.SECONDS).statusCode());

            Map<String, Object> settled = statusOf(client, base, admin.token());
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
        seedSlowBackupPayload(database);
        Path auth = temp.resolve("remote-auth.txt");
        var admin = MorpheusRemoteIdentityFile.create(auth, "admin", MorpheusRemoteRole.ADMIN);
        var reader = MorpheusRemoteIdentityFile.create(auth, "reader", MorpheusRemoteRole.READ);
        HttpClient client = RemoteHttpTestSupport.trustedClient();

        try (MorpheusRemoteHttpServer server = start(database, auth);
             ExecutorService pool = Executors.newFixedThreadPool(CONCURRENT_MUTATIONS)) {
            URI base = URI.create("https://127.0.0.1:" + server.port() + "/api/v1");

            List<Callable<Integer>> mutations = new ArrayList<>();
            for (int index = 0; index < CONCURRENT_MUTATIONS; index++) {
                mutations.add(() -> RemoteHttpTestSupport.send(
                        client, base.resolve("/api/v1/server/backups"), "POST", admin.token(), null).statusCode());
            }
            List<Integer> statuses = pool.invokeAll(mutations).stream().map(future -> {
                try {
                    return future.get();
                } catch (Exception failure) {
                    throw new IllegalStateException(failure);
                }
            }).toList();

            assertTrue(statuses.contains(201), () -> "at least one mutation must succeed: " + statuses);
            assertTrue(statuses.contains(429), () -> "bounded privileged capacity must refuse the excess: " + statuses);
            assertTrue(statuses.stream().allMatch(code -> code == 201 || code == 429), () -> "unexpected: " + statuses);

            Map<String, Object> settled = statusOf(client, base, admin.token());
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

    private Map<String, Object> awaitStatusWhere(
            HttpClient client,
            URI base,
            String token,
            java.util.function.Predicate<Map<String, Object>> condition) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        Map<String, Object> last = Map.of();
        while (System.nanoTime() < deadline) {
            last = statusOf(client, base, token);
            if (condition.test(last)) return last;
        }
        throw new AssertionError("remote status never reached the expected state; last was " + last);
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

    /**
     * Makes a backup slow enough that it is observably in flight, using durable data rather than a sleep: the
     * backup copies whatever the database holds, so the payload size is what sets the duration.
     */
    private void seedSlowBackupPayload(Path database) throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE availability_load(id INTEGER PRIMARY KEY, payload BLOB NOT NULL)");
            statement.execute("INSERT INTO availability_load(payload) VALUES(randomblob(16000000))");
        }
    }

    private MorpheusRemoteHttpServer start(Path database, Path auth) throws Exception {
        Path allowedWorkspaceRoot = Files.createDirectories(temp.resolve("allowed-workspaces"));
        Path keyStore = RemoteHttpTestSupport.createKeyStore(temp.resolve("server.p12"));
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
                project -> ChangeWriteCapabilityObservation.denied("test"));
    }
}
