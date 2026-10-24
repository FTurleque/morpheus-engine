package com.morpheus.api;

import com.morpheus.application.operability.OperationalLogMode;
import com.morpheus.application.operability.OperationalRuntime;
import com.morpheus.store.sqlite.runtime.SqliteConnectionScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * One HTTP request opens one physical SQLite connection, as the same operation does through MCP and the CLI.
 *
 * <p>The two operations are the ones {@code McpToolCallConnectionCountTest}
 * and {@code CliCommandConnectionCountTest} measure on their transport - listing the saved views of a scope, which
 * reads through the query store set, and listing the policy packs, which reads through the policy store set. The
 * database is initialised by a first request, so that the count is the one of an ordinary request.
 * The count is the scope's own, read on the recorder this test hands in, so it shares nothing with another
 * test of the JVM. A store built outside a scope opens a connection that no scope counts; that is refused
 * where it is written, by {@code SqliteConnectionScopeUseArchitectureTest}, and no longer observed at the JDBC
 * driver, which meant swapping the driver of the whole JVM (AUD-QUA-17).</p>
 */
class HttpRequestConnectionCountTest {
    private static final String SAVED_VIEWS =
            "/saved-views?scopeKind=PROJECT&scopeId=01920000-0000-7000-8000-0000000000ff";
    private static final String POLICY_PACKS = "/policy-packs";

    @TempDir
    Path temporaryDirectory;

    @Test
    void listingSavedViewsOpensOnePhysicalConnection() throws Exception {
        assertOneConnection(SAVED_VIEWS);
    }

    @Test
    void listingPolicyPacksOpensOnePhysicalConnection() throws Exception {
        assertOneConnection(POLICY_PACKS);
    }

    private void assertOneConnection(String route) throws Exception {
        OperationalRuntime observed = OperationalRuntime.of(OperationalLogMode.OFF);
        try (MorpheusHttpServer server = MorpheusHttpServer.start(
                temporaryDirectory.resolve("morpheus.db"), "127.0.0.1", 0, observed);
             HttpClient client = HttpClient.newHttpClient()) {
            URI uri = URI.create(server.baseUri() + route);
            assertEquals(200, get(client, uri));
            Map<String, Long> before = observed.metrics().snapshot().counters();

            assertEquals(200, get(client, uri));

            Map<String, Long> after = observed.metrics().snapshot().counters();
            assertEquals(1L, delta(before, after, SqliteConnectionScope.CONNECTIONS_OPENED),
                    "GET " + route + " opens one physical SQLite connection, the one of a scope, counted on the "
                            + "runtime the server was handed");
            assertEquals(1L, delta(before, after, SqliteConnectionScope.SCHEMA_CHECKS),
                    "whose schema check runs once");
        }
    }

    private static long delta(Map<String, Long> before, Map<String, Long> after, String counter) {
        return after.getOrDefault(counter, 0L) - before.getOrDefault(counter, 0L);
    }

    private static int get(HttpClient client, URI uri) throws Exception {
        return client.send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofString())
                .statusCode();
    }
}
