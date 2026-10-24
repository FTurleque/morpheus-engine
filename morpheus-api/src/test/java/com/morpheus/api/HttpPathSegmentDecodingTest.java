package com.morpheus.api;

import com.morpheus.api.local.MorpheusHttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What a route receives as a path parameter is the segment the caller sent, decoded once.
 *
 * <p>The routes below refuse an identifier that is not a UUID and say which value they refused, so the answer shows
 * the segment the router was given. It used to be decoded twice, and with a form decoder: a doubly encoded dot arrived
 * as a dot and a plus sign as a space.</p>
 */
class HttpPathSegmentDecodingTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void aPathParameterIsDecodedOnceAndAPlusSignStaysAPlusSign() throws Exception {
        try (MorpheusHttpServer server = MorpheusHttpServer.start(
                temporaryDirectory.resolve("segments.db"), "127.0.0.1", 0);
             HttpClient client = HttpClient.newHttpClient()) {
            assertRefusedIdentifier(client, server, "/saved-views/a+b/execute", "a+b");
            assertRefusedIdentifier(client, server, "/saved-views/a%2Bb/execute", "a+b");
            assertRefusedIdentifier(client, server, "/saved-views/a%20b/execute", "a b");
            assertRefusedIdentifier(client, server, "/saved-views/%252e%252e/execute", "%2e%2e");
            assertRefusedIdentifier(client, server, "/saved-views/view%2D1/execute", "view-1");
            assertRefusedIdentifier(client, server, "/%73aved-views/view-1/%65xecute", "view-1");
        }
    }

    /**
     * The action that runs is the one the decoded segment names, observed by what each action alone refuses: archiving
     * needs a body, executing forbids one. The methods a route accepts would not tell them apart -- both take POST.
     */
    @Test
    void anEncodedActionRunsTheActionItNamesAndNoOther() throws Exception {
        try (MorpheusHttpServer server = MorpheusHttpServer.start(
                temporaryDirectory.resolve("actions.db"), "127.0.0.1", 0);
             HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<String> archive = send(client, server, "POST", "/saved-views/view-1/%61rchive");
            HttpResponse<String> execute = client.send(HttpRequest.newBuilder(
                            URI.create(server.baseUri() + "/saved-views/view-1/%65xecute"))
                    .header("Content-Type", "application/json; charset=utf-8")
                    .POST(HttpRequest.BodyPublishers.ofString("{}"))
                    .build(), HttpResponse.BodyHandlers.ofString());
            HttpResponse<String> doublyEncoded = send(client, server, "POST", "/saved-views/view-1/%2561rchive");

            assertEquals(400, archive.statusCode(), archive::body);
            assertTrue(archive.body().contains("\"message\":\"JSON request body is required\""), archive::body);
            assertEquals(400, execute.statusCode(), execute::body);
            assertTrue(execute.body().contains("\"message\":\"request body must be empty\""), execute::body);
            assertEquals(404, doublyEncoded.statusCode(), doublyEncoded::body);
            assertTrue(doublyEncoded.body().contains("\"message\":\"unknown saved-view action: %61rchive\""),
                    doublyEncoded::body);
        }
    }

    @Test
    void aContextIsNotEnteredByAPathThatOnlyStartsWithItsName() throws Exception {
        try (MorpheusHttpServer server = MorpheusHttpServer.start(
                temporaryDirectory.resolve("contexts.db"), "127.0.0.1", 0);
             HttpClient client = HttpClient.newHttpClient()) {
            for (String path : new String[] {"/saved-viewsx", "/policy-packsx", "/policy-overridesx",
                    "/saved-views%2Fx", "/policy-packs%2Fx"}) {
                HttpResponse<String> response = send(client, server, "GET", path);

                assertEquals(404, response.statusCode(), path);
                assertTrue(response.body().contains("\"message\":\"unknown API route\""),
                        () -> path + " -> " + response.body());
            }
            for (String path : new String[] {"/queries%2Fexecute", "/policy-overrides%2Fremove",
                    "/reasoning%2Fanalyze"}) {
                HttpResponse<String> response = send(client, server, "POST", path);

                assertEquals(404, response.statusCode(), path);
                assertTrue(response.body().contains("\"code\":\"NOT_FOUND\""), () -> path + " -> " + response.body());
            }
        }
    }

    private static void assertRefusedIdentifier(HttpClient client, MorpheusHttpServer server, String path,
            String identifier) throws Exception {
        HttpResponse<String> response = send(client, server, "POST", path);

        assertEquals(400, response.statusCode(), () -> path + " -> " + response.body());
        assertTrue(response.body().contains("\"message\":\"Invalid UUID string: " + identifier + "\""),
                () -> path + " -> " + response.body());
    }

    private static HttpResponse<String> send(HttpClient client, MorpheusHttpServer server, String method, String path)
            throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(server.baseUri() + path))
                        .method(method, HttpRequest.BodyPublishers.noBody())
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }
}
