package com.morpheus.api;

import com.morpheus.store.sqlite.SqliteConnectionScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * One HTTP request opens one physical SQLite connection, as the same operation does through MCP and the CLI.
 *
 * <p>Counted at the JDBC driver on a served request, not on a runtime: a store built beside the runtime would open a
 * connection the runtime knows nothing about. The two operations are the ones {@code McpToolCallConnectionCountTest}
 * and {@code CliCommandConnectionCountTest} measure on their transport - listing the saved views of a scope, which
 * reads through the query store set, and listing the policy packs, which reads through the policy store set. The
 * database is initialised by a first request, so that the count is the one of an ordinary request.</p>
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
        try (MorpheusHttpServer server = MorpheusHttpServer.start(
                temporaryDirectory.resolve("morpheus.db"), "127.0.0.1", 0);
             HttpClient client = HttpClient.newHttpClient()) {
            URI uri = URI.create(server.baseUri() + route);
            assertEquals(200, get(client, uri));
            SqliteConnectionScope.Diagnostics before = SqliteConnectionScope.diagnostics();

            int opened = PhysicalSqliteConnections.openedDuring(() -> assertEquals(200, get(client, uri)));

            assertEquals(1, opened, "GET " + route + " opens one physical SQLite connection");
            assertEquals(1, SqliteConnectionScope.diagnostics().opened() - before.opened(),
                    "and that connection is the one of a scope, whose schema check runs once");
        }
    }

    private static int get(HttpClient client, URI uri) throws Exception {
        return client.send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofString())
                .statusCode();
    }
}
