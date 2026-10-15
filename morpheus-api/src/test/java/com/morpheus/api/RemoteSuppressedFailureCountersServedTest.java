package com.morpheus.api;

import com.morpheus.application.context.DisabledTechnicalContextProvider;
import com.morpheus.application.lifecycle.mutation.ChangeWriteCapabilityObservation;
import com.morpheus.application.operability.OperationalLogMode;
import com.morpheus.application.operability.OperationalRuntime;
import com.morpheus.application.reference.ExternalIntegrationStatusProvider;
import com.morpheus.application.reference.ExternalReferenceResolverRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.morpheus.api.RemoteHttpTestSupport.send;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The counters of failures the product sets aside are served through the remote facade, to the role allowed to read
 * the metrics, as {@code docs/developer/OPERABILITY.md} promises.
 *
 * <p>Every test that read a {@code failure.suppressed.*} counter read it from a local server or from the recorder
 * itself. The remote facade forwards {@code /metrics} to the local server it owns, behind its own authorization; a
 * facade that answered the route from somewhere else, or filtered the counters out, would have passed every test
 * while the page said an operator could read them remotely. The counter is moved here by a request through the facade
 * and read back through the facade.</p>
 */
class RemoteSuppressedFailureCountersServedTest {
    private static final String COUNTER = "failure.suppressed.http-internal-error";
    private static final Pattern VALUE = Pattern.compile("\"" + Pattern.quote(COUNTER) + "\":(\\d+)");

    @TempDir
    Path temporaryDirectory;

    @Test
    void aFailureSetAsideBehindTheFacadeIsCountedInTheMetricsTheFacadeServesToAnAdministrator() throws Exception {
        Path auth = temporaryDirectory.resolve("remote-auth.txt");
        var reader = MorpheusRemoteIdentityFile.create(auth, "reader", MorpheusRemoteRole.READ);
        var admin = MorpheusRemoteIdentityFile.create(auth, "admin", MorpheusRemoteRole.ADMIN);
        Path keyStore = RemoteHttpTestSupport.createKeyStore(temporaryDirectory.resolve("remote.p12"));
        Path workspaces = Files.createDirectory(temporaryDirectory.resolve("workspaces"));
        ExternalIntegrationStatusProvider brokenMinosStatus = () -> {
            throw new UnsupportedOperationException("status source at /srv/morpheus/minos is gone");
        };

        try (MorpheusRemoteHttpServer server = MorpheusRemoteHttpServer.start(
                temporaryDirectory.resolve("morpheus.db"),
                temporaryDirectory.resolve("backups"),
                temporaryDirectory.resolve("provider-plugins"),
                AllowedWorkspaceRoots.of(List.of(workspaces)),
                "127.0.0.1",
                0,
                auth,
                keyStore,
                RemoteHttpTestSupport.KEYSTORE_PASSWORD.toCharArray(),
                4,
                new ExternalReferenceResolverRegistry(List.of()),
                brokenMinosStatus,
                new DisabledTechnicalContextProvider("NEXUS", "test"),
                project -> ChangeWriteCapabilityObservation.denied("test"),
                OperationalRuntime.of(OperationalLogMode.OFF));
             HttpClient client = RemoteHttpTestSupport.trustedClient(keyStore)) {
            URI base = URI.create("https://127.0.0.1:" + server.port());
            long before = counter(client, base, admin.token());

            HttpResponse<String> failed = send(
                    client, base.resolve("/api/v1/integrations/minos/status"), "GET", reader.token(), null);

            assertEquals(500, failed.statusCode(), failed::body);
            assertFalse(failed.body().contains("/srv/"), failed::body);
            assertEquals(before + 1, counter(client, base, admin.token()),
                    "the failure set aside behind the facade must be counted where the facade serves the metrics");

            HttpResponse<String> readerMetrics = send(client, base.resolve("/api/v1/metrics"), "GET", reader.token(), null);
            assertEquals(403, readerMetrics.statusCode(), readerMetrics::body);
            assertFalse(readerMetrics.body().contains(COUNTER), readerMetrics::body);

            // The outcome of a request is journalled after its exchange is closed; leaving before the four records
            // are written lets the journal recreate its file under the temporary directory being deleted.
            Path journal = temporaryDirectory.resolve(RemoteAccessJournal.FILE_NAME);
            BoundedWait.until("the four requests journalled", Duration.ofSeconds(10), BoundedWait.LOCAL_COUNTER_POLL,
                    () -> completedRecords(journal) == 4, () -> completedRecords(journal) + " completed records");
        }
    }

    private static long completedRecords(Path journal) {
        try {
            return Files.readAllLines(journal).stream().filter(line -> line.contains("\"completed\"")).count();
        } catch (IOException missing) {
            return 0L;
        }
    }

    /** The counter as the facade serves it to an administrator; absent means it never moved on the runtime the server was handed. */
    private static long counter(HttpClient client, URI base, String token) throws Exception {
        HttpResponse<String> metrics = send(client, base.resolve("/api/v1/metrics"), "GET", token, null);
        assertEquals(200, metrics.statusCode(), metrics::body);
        assertTrue(metrics.body().contains("\"counters\""), metrics::body);
        Matcher value = VALUE.matcher(metrics.body());
        return value.find() ? Long.parseLong(value.group(1)) : 0L;
    }
}
