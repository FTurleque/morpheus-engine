package com.morpheus.api;

import com.morpheus.application.context.DisabledTechnicalContextProvider;
import com.morpheus.application.lifecycle.mutation.ChangeWriteCapabilityObservation;
import com.morpheus.application.reference.ExternalIntegrationStatus;
import com.morpheus.application.reference.ExternalIntegrationStatusProvider;
import com.morpheus.application.reference.ExternalReferenceResolverRegistry;
import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.source.SourceLocator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Clients that stop reading, read slowly, or vanish -- driven from a raw TLS socket against the real facade.
 *
 * <p>These replace a scenario that could not fail. It sent a request with
 * {@code HttpResponse.BodyHandlers.discarding()} and called that "the client walking away mid-exchange", but
 * {@code discarding()} consumes the response body exactly like any other handler and only throws the bytes away
 * afterwards. The server saw a perfectly well-behaved client, so the assertion that no slot was left behind was
 * true for a reason that had nothing to do with abandonment.</p>
 *
 * <p>Simulating abandonment needs a socket the test controls, so these open one, complete the TLS handshake,
 * authenticate, and then misbehave at the transport level: read the headers and stop, read a few bytes at a
 * time, or disappear without a close. The response has to be large enough to fill the socket buffers before the
 * facade can finish writing it -- otherwise the whole response lands in kernel memory, the handler returns, and
 * a stalled client costs the server nothing. That is why the fixture seeds a project registry big enough to
 * produce a multi-megabyte listing.</p>
 */
class MorpheusRemoteAdversarialClientTest {
    private static final int MAX_CONCURRENT = 4;
    private static final int SEEDED_PROJECTS = 8_000;
    private static final int STALLED_CLIENTS = MAX_CONCURRENT;
    private static final int SMALL_RECEIVE_BUFFER_BYTES = 4096;
    /**
     * A slow reader's pacing, derived from the budget it must stay inside rather than written next to it.
     *
     * <p>Six pauses of a fifth of the stall budget outlast that budget in aggregate while leaving each
     * individual gap five times inside it. The first version used a third of the budget per pause and was cut
     * off on Linux but not on Windows -- close enough to the limit that platform differences in socket
     * back-pressure decided the outcome. A test of a 15-second budget should not be sensitive to a second.</p>
     */
    private static final int PAUSES = 6;
    private static final Duration PAUSE = TimedBoundedResponseWriter.RESPONSE_STALL_TIMEOUT.dividedBy(5);
    private static final int SIP_BYTES = 256 * 1024;

    @TempDir
    static Path temp;

    private static Path keyStore;
    private static MorpheusRemoteHttpServer server;
    private static MorpheusRemoteIdentityFile.GeneratedCredential admin;
    private static HttpClient client;
    private static URI base;

    @BeforeAll
    static void startFacade() throws Exception {
        Path database = temp.resolve("morpheus.db");
        seedLargeProjectListing(database);
        Path auth = temp.resolve("remote-auth.txt");
        admin = MorpheusRemoteIdentityFile.create(auth, "admin", MorpheusRemoteRole.ADMIN);
        keyStore = RemoteHttpTestSupport.createKeyStore(temp.resolve("server.p12"));
        client = RemoteHttpTestSupport.trustedClient(keyStore);

        Path allowedWorkspaceRoot = Files.createDirectories(temp.resolve("allowed-workspaces"));
        ExternalIntegrationStatusProvider minos = () ->
                new ExternalIntegrationStatus("MINOS", "DISABLED", false, "adversarial", Map.of());
        server = MorpheusRemoteHttpServer.start(
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
                new DisabledTechnicalContextProvider("NEXUS", "adversarial"),
                project -> ChangeWriteCapabilityObservation.denied("adversarial"));
        base = URI.create("https://127.0.0.1:" + server.port() + "/api/v1");
    }

    @AfterAll
    static void stopFacade() {
        if (server != null) server.close();
    }

    /**
     * Clients that authenticate, take the headers and then stop reading do not own the facade.
     *
     * <p>Every permit they hold is taken before the response is written and released after it, so without a
     * deadline on the write they hold all of them for as long as they care to stay connected. The evidence that
     * the deadline is what ended it is the facade's own counter: it is the one outcome no other failure
     * produces.</p>
     */
    @Test
    @Timeout(240)
    void clientsThatStopReadingAreTimedOutAndGiveBackEverySlot() throws Exception {
        long timeoutsBefore = counter("responseWriteTimeouts");

        List<StalledClient> stalled = new ArrayList<>();
        try {
            for (int index = 0; index < STALLED_CLIENTS; index++) {
                stalled.add(StalledClient.readHeadersThenStop("/api/v1/projects"));
            }
            for (StalledClient abandoned : stalled) {
                assertTrue(abandoned.responseStarted(), "each adversarial client must have a response to abandon");
            }

            // Every one of them, not just the first: the counter is incremented after the handler's finally has
            // already released the slots, so activeRequests can reach zero while increments are still pending.
            // Leaving those in flight would let one land inside whichever test runs next.
            awaitCondition(
                    Duration.ofSeconds(150),
                    () -> counter("responseWriteTimeouts") >= timeoutsBefore + STALLED_CLIENTS);
            awaitCondition(Duration.ofSeconds(60), () -> counter("activeRequests") == 0);
        } finally {
            for (StalledClient abandoned : stalled) {
                abandoned.close();
            }
        }

        assertEquals(0, counter("activePrivilegedRequests"));
        assertEquals(200, RemoteHttpTestSupport
                .send(client, base.resolve("/api/v1/health"), "GET", admin.token(), null).statusCode(),
                "the facade must be fully usable once the abandoned responses are reclaimed");
    }

    /**
     * A slow client that keeps draining is served in full, however slowly it reads.
     *
     * <p>Without this the previous test would be satisfied by a deadline that simply cut off anyone below a
     * bandwidth threshold, which would break every honest client on a poor link.</p>
     */
    @Test
    @Timeout(240)
    void aSlowButProgressingClientIsServedInFull() throws Exception {
        assertTrue(PAUSE.compareTo(TimedBoundedResponseWriter.RESPONSE_STALL_TIMEOUT) < 0,
                "each pause must stay inside the stall budget, or this would only be a stalled client again");
        assertTrue(PAUSE.multipliedBy(PAUSES).compareTo(TimedBoundedResponseWriter.RESPONSE_STALL_TIMEOUT) > 0,
                "the response must outlast the stall budget, or a deadline that never rearmed would pass too");

        long declared;
        long received;
        try (StalledClient slow = StalledClient.readHeadersThenStop("/api/v1/projects")) {
            assertTrue(slow.responseStarted());
            declared = slow.declaredBodyBytes();
            received = slow.drainWithPauses(PAUSES, PAUSE);
        }

        assertTrue(declared > 1024L * 1024L,
                () -> "the fixture must produce a response too large to be absorbed by socket buffers, got "
                        + declared + " bytes");
        assertEquals(declared, received,
                "a client that keeps making progress must be served in full, not truncated by the deadline");
        awaitCondition(Duration.ofSeconds(60), () -> counter("activeRequests") == 0);
    }

    /**
     * A client that disappears mid-response leaves nothing behind either.
     *
     * <p>This is the other half of abandonment, and it does not go through the deadline at all: the write fails
     * outright, which is the path that has to release the same permits without reporting a timeout that did not
     * happen.</p>
     */
    @Test
    @Timeout(240)
    void anAbruptDisconnectMidResponseLeavesNoSlotBehind() throws Exception {
        for (int attempt = 0; attempt < STALLED_CLIENTS; attempt++) {
            StalledClient abandoned = StalledClient.readHeadersThenStop("/api/v1/projects");
            assertTrue(abandoned.responseStarted());
            abandoned.abort();
        }

        awaitCondition(Duration.ofSeconds(90), () -> counter("activeRequests") == 0);
        assertEquals(0, counter("activePrivilegedRequests"));
        assertEquals(200, RemoteHttpTestSupport
                .send(client, base.resolve("/api/v1/health"), "GET", admin.token(), null).statusCode());
    }

    /**
     * A registry large enough that listing it cannot fit in a socket buffer.
     *
     * <p>Registering these through the API would mean eight thousand real directories and eight thousand round
     * trips for a fixture whose only job is to be big; the store is written directly instead, before the facade
     * takes its exclusive lease on the database.</p>
     */
    private static void seedLargeProjectListing(Path database) {
        String longWorkspaceName = "adversarial-workspace-fixture-".repeat(6);
        try (ApiRuntime runtime = new ApiRuntime(database)) {
            for (int index = 0; index < SEEDED_PROJECTS; index++) {
                runtime.snapshots.putProject(new ProjectStoreEntry(
                        ProjectSpecificationId.generate(),
                        SourceLocator.file("seeded/" + index + "-" + longWorkspaceName)));
            }
        }
    }

    private static long counter(String name) {
        try {
            HttpResponse<String> response = RemoteHttpTestSupport.send(
                    client, base.resolve("/api/v1/server/status"), "GET", admin.token(), null);
            assertEquals(200, response.statusCode(), response.body());
            @SuppressWarnings("unchecked")
            Map<String, Object> envelope = JsonMapper.builder().build().readValue(response.body(), Map.class);
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) envelope.get("data");
            return ((Number) data.get(name)).longValue();
        } catch (Exception unreachable) {
            throw new IllegalStateException("remote status must stay answerable under adversarial load", unreachable);
        }
    }

    private static void awaitCondition(Duration timeout, BooleanSupplier condition) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime() >= deadline) {
                throw new AssertionError("condition was not satisfied within " + timeout);
            }
            TimeUnit.MILLISECONDS.sleep(200);
        }
    }

    /** An authenticated TLS client that reads the response headers and then behaves badly on purpose. */
    private static final class StalledClient implements AutoCloseable {
        private final Socket plain;
        private final SSLSocket tls;
        private final InputStream input;
        private final long declaredBodyBytes;

        private StalledClient(Socket plain, SSLSocket tls, InputStream input, long declaredBodyBytes) {
            this.plain = plain;
            this.tls = tls;
            this.input = input;
            this.declaredBodyBytes = declaredBodyBytes;
        }

        private static StalledClient readHeadersThenStop(String path) throws Exception {
            SSLContext context = RemoteHttpTestSupport.trustedContext(keyStore);
            Socket plain = new Socket();
            // Set before connect, so the advertised receive window really is small: the point is to make the
            // facade block on a full socket quickly rather than to wait out a platform's buffer autotuning.
            plain.setReceiveBufferSize(SMALL_RECEIVE_BUFFER_BYTES);
            plain.connect(new InetSocketAddress("127.0.0.1", server.port()), 10_000);
            SSLSocket tls = (SSLSocket) context.getSocketFactory()
                    .createSocket(plain, "127.0.0.1", server.port(), true);
            tls.setSoTimeout(30_000);
            tls.startHandshake();

            OutputStream output = tls.getOutputStream();
            output.write(("GET " + path + " HTTP/1.1\r\n"
                    + "Host: 127.0.0.1:" + server.port() + "\r\n"
                    + "Authorization: Bearer " + admin.token() + "\r\n"
                    + "Accept: application/json\r\n"
                    + "Connection: close\r\n"
                    + "\r\n").getBytes(StandardCharsets.US_ASCII));
            output.flush();

            InputStream input = tls.getInputStream();
            return new StalledClient(plain, tls, input, readStatusLineAndHeaders(input));
        }

        /**
         * Reads exactly up to the blank line ending the headers, and not one byte of the body.
         *
         * <p>Returns the declared body length, or -1 when the response is not one this test can use. The length
         * is what makes truncation detectable: the facade sends a bounded Content-Length on every proxied
         * response, so a client that received fewer bytes than that was cut off -- which is a property of this
         * one exchange, not of a counter the whole class shares.</p>
         */
        private static long readStatusLineAndHeaders(InputStream input) throws IOException {
            StringBuilder headers = new StringBuilder();
            int next;
            while ((next = input.read()) != -1) {
                headers.append((char) next);
                if (headers.length() >= 4 && headers.lastIndexOf("\r\n\r\n") == headers.length() - 4) {
                    return headers.indexOf("HTTP/1.1 200") == 0 ? declaredLength(headers.toString()) : -1L;
                }
                if (headers.length() > 16 * 1024) return -1L;
            }
            return -1L;
        }

        private static long declaredLength(String headers) {
            String name = "Content-Length:";
            for (String line : headers.split("\r\n")) {
                if (line.regionMatches(true, 0, name, 0, name.length())) {
                    return Long.parseLong(line.substring(name.length()).trim());
                }
            }
            return -1L;
        }

        private boolean responseStarted() {
            return declaredBodyBytes >= 0;
        }

        private long declaredBodyBytes() {
            return declaredBodyBytes;
        }

        /**
         * Reads the body with a few long pauses in it, then drains the rest. Returns the bytes read.
         *
         * <p>The pauses are what make this a slow reader rather than merely a small one: each is long enough to
         * be a stall on any platform and short enough to stay inside the stall budget, and together they last
         * longer than that budget. A deadline that did not rearm on progress would end this response; one that
         * merely throttled by bandwidth would too.</p>
         */
        private long drainWithPauses(int pauses, Duration pause) throws IOException, InterruptedException {
            byte[] sip = new byte[8192];
            long total = 0;
            for (int index = 0; index < pauses; index++) {
                long before = total;
                // Enough to let the facade finish whole chunks and record progress. Freeing less than one chunk
                // of window would leave the facade blocked mid-write, which is a stalled client with extra steps.
                while (total - before < SIP_BYTES) {
                    int read = input.read(sip);
                    if (read < 0) return total;
                    total += read;
                }
                TimeUnit.NANOSECONDS.sleep(pause.toNanos());
            }
            int read;
            while ((read = input.read(sip)) != -1) {
                total += read;
            }
            return total;
        }

        /** Disappears without a graceful close, the way a killed client or a dropped link does. */
        private void abort() throws IOException {
            plain.setSoLinger(true, 0);
            close();
        }

        @Override
        public void close() {
            try {
                tls.close();
            } catch (IOException alreadyGone) {
                // Closing a connection the facade already reclaimed is the expected path here.
            }
        }
    }
}
