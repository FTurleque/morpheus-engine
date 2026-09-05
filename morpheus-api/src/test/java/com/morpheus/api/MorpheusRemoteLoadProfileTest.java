package com.morpheus.api;

import com.morpheus.application.context.DisabledTechnicalContextProvider;
import com.morpheus.application.lifecycle.mutation.ChangeWriteCapabilityObservation;
import com.morpheus.application.reference.ExternalIntegrationStatus;
import com.morpheus.application.reference.ExternalIntegrationStatusProvider;
import com.morpheus.application.reference.ExternalReferenceResolverRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A reproducible load profile for the {@code jdk.httpserver} remote facade.
 *
 * <p>RT-01 and RT-03 both invite the same shortcut: replace the JDK server with a real one because it "might
 * not scale". This suite exists so that decision is made on evidence. It drives concurrent reads, concurrent
 * mutations and oversized bodies against the real HTTPS facade, and writes what it measured to
 * {@code target/remote-load-profile.txt}. Transport-level abandonment needs a socket the test controls rather
 * than an HTTP client, and lives in {@code MorpheusRemoteAdversarialClientTest}.</p>
 *
 * <p>What it <em>asserts</em> is deliberately not a latency number. Absolute timings on a CI runner are not a
 * property of MORPHEUS, and a build that fails when a shared machine is busy teaches people to ignore it. The
 * assertions are the properties that must hold at any speed: every request gets an answer, that answer is never
 * a 5xx, saturation is expressed as an explicit refusal rather than a drop, observability survives it, and no
 * slot is left behind afterwards. The timings are recorded as evidence for the replacement decision documented
 * in {@code docs/user/TEAM_REMOTE_SERVER.md}.</p>
 */
class MorpheusRemoteLoadProfileTest {
    private static final int MAX_CONCURRENT = 8;
    private static final int CLIENT_THREADS = 24;
    private static final int READ_REQUESTS = 240;

    @TempDir
    Path temp;

    private final List<String> measurements = new ArrayList<>();

    /**
     * A read storm larger than the facade admits, and every request still gets a real answer.
     *
     * <p>The property that matters under saturation is that refusal is explicit. A server that drops, hangs or
     * answers 500 under load is a server whose capacity bound does not work, whatever its throughput.</p>
     */
    @Test
    void aReadStormBeyondCapacityIsAnsweredEntirelyIn200sAnd429s() throws Exception {
        Path database = temp.resolve("morpheus.db");
        Path auth = temp.resolve("remote-auth.txt");
        var admin = MorpheusRemoteIdentityFile.create(auth, "admin", MorpheusRemoteRole.ADMIN);
        HttpClient client = RemoteHttpTestSupport.trustedClient(keyStore());

        try (MorpheusRemoteHttpServer server = start(database, auth);
             ExecutorService callers = Executors.newFixedThreadPool(CLIENT_THREADS)) {
            URI base = URI.create("https://127.0.0.1:" + server.port() + "/api/v1");

            List<Callable<long[]>> reads = new ArrayList<>();
            for (int index = 0; index < READ_REQUESTS; index++) {
                reads.add(() -> {
                    long startedNanos = System.nanoTime();
                    int status = RemoteHttpTestSupport
                            .send(client, base.resolve("/api/v1/health"), "GET", admin.token(), null)
                            .statusCode();
                    return new long[]{status, System.nanoTime() - startedNanos};
                });
            }

            long wallStartNanos = System.nanoTime();
            List<long[]> outcomes = invokeAll(callers, reads);
            long wallNanos = System.nanoTime() - wallStartNanos;

            assertEquals(READ_REQUESTS, outcomes.size(), "every request must produce an answer");
            for (long[] outcome : outcomes) {
                assertTrue(outcome[0] == 200 || outcome[0] == 429,
                        () -> "saturation must be an explicit refusal, got HTTP " + outcome[0]);
            }
            long served = outcomes.stream().filter(outcome -> outcome[0] == 200).count();
            assertTrue(served > 0, "the facade must serve reads while refusing the excess");

            recordLatency("read.storm", outcomes, wallNanos);
            measure("read.storm.served", served);
            measure("read.storm.refused", outcomes.size() - served);
            measure("read.storm.maxConcurrentRequests", MAX_CONCURRENT);

            Map<String, Object> settled = statusOf(client, base, admin.token());
            assertEquals(0, ((Number) settled.get("activeRequests")).intValue(),
                    "no request slot may be left behind after the storm");
            measure("read.storm.throttledRequests", ((Number) settled.get("throttledRequests")).longValue());
        }
        writeProfile();
    }

    /**
     * Reads and mutations under load at once, with observability holding throughout.
     *
     * <p>Mutations have their own smaller budget, so this is where a facade that let write pressure eat the
     * read capacity, or that stopped answering status, would show it.</p>
     */
    @Test
    void mixedReadAndMutationLoadKeepsStatusAnswerableThroughout() throws Exception {
        Path database = temp.resolve("morpheus.db");
        seedBackupPayload(database);
        Path auth = temp.resolve("remote-auth.txt");
        var admin = MorpheusRemoteIdentityFile.create(auth, "admin", MorpheusRemoteRole.ADMIN);
        HttpClient client = RemoteHttpTestSupport.trustedClient(keyStore());

        try (MorpheusRemoteHttpServer server = start(database, auth);
             ExecutorService callers = Executors.newFixedThreadPool(CLIENT_THREADS)) {
            URI base = URI.create("https://127.0.0.1:" + server.port() + "/api/v1");

            List<Callable<long[]>> mixed = new ArrayList<>();
            for (int index = 0; index < 12; index++) {
                mixed.add(() -> {
                    long startedNanos = System.nanoTime();
                    int status = RemoteHttpTestSupport.send(
                            client, base.resolve("/api/v1/server/backups"), "POST", admin.token(), null).statusCode();
                    return new long[]{status, System.nanoTime() - startedNanos};
                });
            }
            for (int index = 0; index < 60; index++) {
                mixed.add(() -> {
                    long startedNanos = System.nanoTime();
                    int status = RemoteHttpTestSupport.send(
                            client, base.resolve("/api/v1/health"), "GET", admin.token(), null).statusCode();
                    return new long[]{status, System.nanoTime() - startedNanos};
                });
            }
            Collections.shuffle(mixed);

            Future<Integer> statusPolls = callers.submit(() -> {
                int answered = 0;
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
                while (System.nanoTime() < deadline && answered < 40) {
                    if (RemoteHttpTestSupport.send(
                            client, base.resolve("/api/v1/server/status"), "GET", admin.token(), null)
                            .statusCode() == 200) {
                        answered++;
                    }
                }
                return answered;
            });

            long wallStartNanos = System.nanoTime();
            List<long[]> outcomes = invokeAll(callers, mixed);
            long wallNanos = System.nanoTime() - wallStartNanos;

            for (long[] outcome : outcomes) {
                assertTrue(outcome[0] == 200 || outcome[0] == 201 || outcome[0] == 429,
                        () -> "mixed load must never produce a server error, got HTTP " + outcome[0]);
            }
            assertEquals(40, statusPolls.get(30, TimeUnit.SECONDS).intValue(),
                    "status must stay answerable for the whole of a mixed load");

            recordLatency("mixed.load", outcomes, wallNanos);

            Map<String, Object> settled = statusOf(client, base, admin.token());
            assertEquals(0, ((Number) settled.get("activeRequests")).intValue());
            assertEquals(0, ((Number) settled.get("activePrivilegedRequests")).intValue());
            measure("mixed.load.throttledPrivileged",
                    ((Number) settled.get("throttledPrivilegedRequests")).longValue());
        }
        writeProfile();
    }

    /**
     * An oversized body is refused and forgotten, not accumulated.
     *
     * <p>The assertion is not that it fails -- it is that after it fails, the facade is exactly as large as it
     * was before it.</p>
     *
     * <p>This used to also claim to cover an abandoned client, using a request sent with a discarding body
     * handler. That handler consumes the response body exactly like any other and only throws the bytes away
     * afterwards, so the facade saw an entirely well-behaved client and the no-slot-left-behind assertion held
     * for a reason unrelated to abandonment. Abandonment is exercised where it can actually be produced -- from
     * a raw TLS socket -- in {@code MorpheusRemoteAdversarialClientTest}.</p>
     */
    @Test
    void oversizedBodiesLeaveNoSlotBehind() throws Exception {
        Path database = temp.resolve("morpheus.db");
        Path auth = temp.resolve("remote-auth.txt");
        var admin = MorpheusRemoteIdentityFile.create(auth, "admin", MorpheusRemoteRole.ADMIN);
        HttpClient client = RemoteHttpTestSupport.trustedClient(keyStore());

        try (MorpheusRemoteHttpServer server = start(database, auth)) {
            URI base = URI.create("https://127.0.0.1:" + server.port() + "/api/v1");
            String oversized = "x".repeat(MorpheusHttpServer.MAX_REQUEST_BODY_BYTES + 1024);

            for (int attempt = 0; attempt < 8; attempt++) {
                HttpResponse<String> refused = RemoteHttpTestSupport.send(
                        client, base.resolve("/api/v1/projects"), "POST", admin.token(), oversized);
                assertEquals(413, refused.statusCode(), refused.body());
                assertTrue(refused.body().contains("PAYLOAD_TOO_LARGE"), refused.body());
            }

            Map<String, Object> settled = statusOf(client, base, admin.token());
            assertEquals(0, ((Number) settled.get("activeRequests")).intValue(),
                    "a refused oversized body must not hold a request slot");
            assertEquals(0, ((Number) settled.get("activePrivilegedRequests")).intValue(),
                    "a refused oversized mutation must not hold a privileged slot");
            measure("hostile.oversizedRefused", 8);

            assertEquals(200, RemoteHttpTestSupport.send(
                    client, base.resolve("/api/v1/health"), "GET", admin.token(), null).statusCode(),
                    "the facade must remain fully usable afterwards");
        }
        writeProfile();
    }

    /** Closing the facade stops accepting connections, so its executor and socket really went with it. */
    @Test
    void closingTheFacadeReleasesItsListeningSocketAndExecutor() throws Exception {
        Path database = temp.resolve("morpheus.db");
        Path auth = temp.resolve("remote-auth.txt");
        var admin = MorpheusRemoteIdentityFile.create(auth, "admin", MorpheusRemoteRole.ADMIN);
        HttpClient client = RemoteHttpTestSupport.trustedClient(keyStore());

        URI base;
        try (MorpheusRemoteHttpServer server = start(database, auth)) {
            base = URI.create("https://127.0.0.1:" + server.port() + "/api/v1");
            assertEquals(200, RemoteHttpTestSupport.send(
                    client, base.resolve("/api/v1/health"), "GET", admin.token(), null).statusCode());
        }

        URI closed = base;
        assertThrows(IOException.class, () -> RemoteHttpTestSupport.send(
                client, closed.resolve("/api/v1/health"), "GET", admin.token(), null),
                "a closed facade must refuse connections");
    }

    private <T> List<T> invokeAll(ExecutorService pool, List<Callable<T>> work) throws InterruptedException {
        return pool.invokeAll(work).stream().map(future -> {
            try {
                return future.get();
            } catch (Exception failure) {
                throw new IllegalStateException(failure);
            }
        }).toList();
    }

    private void recordLatency(String prefix, List<long[]> outcomes, long wallNanos) {
        List<Long> latencies = new ArrayList<>(outcomes.stream().map(outcome -> outcome[1]).toList());
        Collections.sort(latencies);
        measure(prefix + ".requests", latencies.size());
        measure(prefix + ".p50Millis", TimeUnit.NANOSECONDS.toMillis(latencies.get(latencies.size() / 2)));
        measure(prefix + ".p95Millis", TimeUnit.NANOSECONDS.toMillis(latencies.get(latencies.size() * 95 / 100)));
        measure(prefix + ".maxMillis", TimeUnit.NANOSECONDS.toMillis(latencies.getLast()));
        measure(prefix + ".wallMillis", TimeUnit.NANOSECONDS.toMillis(wallNanos));
    }

    private void measure(String name, long value) {
        measurements.add(name + "=" + value);
    }

    /**
     * Writes what this run measured next to the build output.
     *
     * <p>Evidence rather than a gate: these numbers are the input to the replacement criteria in the operator
     * guide, and asserting them on a shared CI runner would only teach people to ignore the failure.</p>
     */
    private void writeProfile() throws IOException {
        Path profile = Path.of("target", "remote-load-profile.txt");
        Files.createDirectories(profile.getParent());
        List<String> lines = new ArrayList<>();
        lines.add("# MORPHEUS remote facade load profile (jdk.httpserver)");
        lines.add("os=" + System.getProperty("os.name"));
        lines.add("availableProcessors=" + Runtime.getRuntime().availableProcessors());
        lines.addAll(measurements);
        Files.write(
                profile,
                String.join(System.lineSeparator(), lines).concat(System.lineSeparator())
                        .getBytes(StandardCharsets.UTF_8),
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.APPEND);
        assertFalse(measurements.isEmpty(), "a load profile run must record what it measured");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> statusOf(HttpClient client, URI base, String token) throws Exception {
        HttpResponse<String> response = RemoteHttpTestSupport.send(
                client, base.resolve("/api/v1/server/status"), "GET", token, null);
        assertEquals(200, response.statusCode(), response.body());
        Map<String, Object> envelope = JsonMapper.builder().build().readValue(response.body(), Map.class);
        return (Map<String, Object>) envelope.get("data");
    }

    private void seedBackupPayload(Path database) throws Exception {
        try (var connection = java.sql.DriverManager.getConnection("jdbc:sqlite:" + database);
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE load_profile(id INTEGER PRIMARY KEY, payload BLOB NOT NULL)");
            statement.execute("INSERT INTO load_profile(payload) VALUES(randomblob(2000000))");
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
                new ExternalIntegrationStatus("MINOS", "DISABLED", false, "load", Map.of());
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
                new DisabledTechnicalContextProvider("NEXUS", "load"),
                project -> ChangeWriteCapabilityObservation.denied("load"));
    }
}
