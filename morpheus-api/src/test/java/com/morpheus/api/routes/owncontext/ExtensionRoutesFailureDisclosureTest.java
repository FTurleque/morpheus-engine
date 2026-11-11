package com.morpheus.api.routes.owncontext;

import com.morpheus.api.local.MorpheusHttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The four extension routers -- query, policy, policy management, reasoning -- relay the text of a failure through
 * the same filter as the rest of the API.
 *
 * <p>Each of them carried its own {@code safeMessage}, which replaced a message only when it was empty. A failure
 * whose message names a server location therefore reached the caller verbatim, and through the remote facade, a
 * caller outside the machine. A request is enough to show it: several refusals echo a value of the request, so a
 * value shaped like a pathname comes back in the message exactly as a pathname raised from below a service would.</p>
 *
 * <p>The filter replaces, it does not scrub: the message becomes the simple name of the failure, and the status and
 * the error code -- which already say what went wrong -- do not move.</p>
 */
class ExtensionRoutesFailureDisclosureTest {
    private static final String JSON = "application/json; charset=utf-8";
    private static final String LOCATION = "/srv/morpheus/data";
    private static final String PROJECT = "11111111-1111-7111-8111-111111111111";

    @TempDir
    Path temporaryDirectory;

    @Test
    void aReasoningRefusalNamesNoLocation() throws Exception {
        assertFiltered("/reasoning/analyze",
                "{\"question\":\"q\",\"evidence\":[{\"id\":\"f1\",\"kind\":\"PUBLISHED_FACT\",\"subject\":\"s\","
                        + "\"statement\":\"st\"}],\"adapterIds\":[\"" + LOCATION + "\"]}",
                400, "REASONING_VALIDATION", "IllegalArgumentException");
    }

    @Test
    void aQueryRefusalNamesNoLocationUnderEitherOfItsErrorCodes() throws Exception {
        assertFiltered("/queries/execute",
                "{\"scopeKind\":\"PROJECT\",\"scopeId\":\"" + PROJECT + "\",\"query\":{\"entity\":\"" + LOCATION + "\"}}",
                400, "BAD_REQUEST", "IllegalArgumentException");
        assertFiltered("/queries/execute",
                "{\"scopeKind\":\"PROJECT\",\"scopeId\":\"" + PROJECT + "\",\"query\":{\"entity\":\"change\","
                        + "\"fields\":\"id," + LOCATION + "\"}}",
                400, "QUERY_VALIDATION", "QueryValidationException");
    }

    @Test
    void aPolicyRefusalNamesNoLocation() throws Exception {
        assertFiltered("/policies/evaluate",
                "{\"scopeKind\":\"PROJECT\",\"scopeId\":\"" + LOCATION + "\"}",
                400, "BAD_REQUEST", "IllegalArgumentException");
    }

    @Test
    void aPolicyManagementRefusalNamesNoLocation() throws Exception {
        assertFiltered("/policy-overrides/remove",
                "{\"scopeKind\":\"PROJECT\",\"scopeId\":\"" + LOCATION + "\",\"expectedRevision\":0,\"actor\":\"a\","
                        + "\"reason\":\"r\"}",
                400, "BAD_REQUEST", "IllegalArgumentException");
    }

    @Test
    void aRefusalThatNamesNoLocationKeepsItsMessage() throws Exception {
        HttpResponse<String> response = post("/reasoning/analyze", "{\"question\":\"\",\"evidence\":[],\"adapterIds\":[]}");

        assertEquals(400, response.statusCode());
        assertEquals("{\"apiVersion\":\"v1\",\"error\":{\"code\":\"REASONING_VALIDATION\",\"message\":"
                + "\"reasoning question must not be blank\",\"details\":{}}}", response.body());
    }

    /**
     * A failure raised below a service, not an echo of the request: the database is replaced by a file that is not one
     * while the server runs, so every store the routes open fails. Whatever the platform puts in that failure, the
     * answer names neither the database nor its directory.
     */
    @Test
    void aStorageFailureUnderEachFamilyNamesNoLocation() throws Exception {
        Path database = temporaryDirectory.resolve("unreadable.db");
        try (MorpheusHttpServer server = MorpheusHttpServer.start(database, "127.0.0.1", 0);
             HttpClient client = HttpClient.newHttpClient()) {
            Files.write(database, "this is not a SQLite database, and every store opened on it fails".repeat(64)
                    .getBytes(StandardCharsets.UTF_8));

            for (String[] request : new String[][] {
                    {"/queries/execute", "{\"scopeKind\":\"PROJECT\",\"scopeId\":\"" + PROJECT
                            + "\",\"query\":{\"entity\":\"change\"}}"},
                    {"/policies/evaluate", "{\"scopeKind\":\"PROJECT\",\"scopeId\":\"" + PROJECT + "\"}"},
                    {"/policy-overrides/remove", "{\"scopeKind\":\"PROJECT\",\"scopeId\":\"" + PROJECT
                            + "\",\"expectedRevision\":0,\"actor\":\"a\",\"reason\":\"r\"}"}}) {
                HttpResponse<String> response = client.send(HttpRequest.newBuilder(
                                URI.create(server.baseUri() + request[0]))
                        .header("Content-Type", JSON)
                        .POST(HttpRequest.BodyPublishers.ofString(request[1]))
                        .build(), HttpResponse.BodyHandlers.ofString());

                assertEquals(409, response.statusCode(), () -> request[0] + " -> " + response.body());
                assertTrue(response.body().contains("\"code\":\"STATE_CONFLICT\""), response::body);
                assertFalse(response.body().contains("unreadable.db"), response::body);
                assertFalse(response.body().contains(temporaryDirectory.getFileName().toString()), response::body);
            }
        }
    }

    private void assertFiltered(String route, String body, int status, String code, String failureType) throws Exception {
        HttpResponse<String> response = post(route, body);

        assertEquals(status, response.statusCode(), route);
        assertFalse(response.body().contains(LOCATION),
                () -> route + " relays a value that names a location: " + response.body());
        assertEquals("{\"apiVersion\":\"v1\",\"error\":{\"code\":\"" + code + "\",\"message\":\"" + failureType
                + "\",\"details\":{}}}", response.body(), route);
        assertTrue(response.headers().firstValue("Content-Type").orElse("").startsWith("application/json"), route);
    }

    private HttpResponse<String> post(String route, String body) throws Exception {
        try (MorpheusHttpServer server = MorpheusHttpServer.start(
                temporaryDirectory.resolve("disclosure.db"), "127.0.0.1", 0);
             HttpClient client = HttpClient.newHttpClient()) {
            return client.send(HttpRequest.newBuilder(URI.create(server.baseUri() + route))
                            .header("Content-Type", JSON)
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
        }
    }
}
