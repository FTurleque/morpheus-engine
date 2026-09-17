package com.morpheus.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The failures of the four extension routers -- query, policy, policy management, reasoning -- pinned byte for
 * byte: status, {@code Content-Type}, {@code Allow} and the whole error envelope.
 *
 * <p>These routers registered their own HTTP contexts and carried their own copy of the request boundary: a
 * {@code JsonMapper}, a body reader, a private failure type. The values below were captured from that code on
 * 15/09/2026, before it moved onto {@code MorpheusHttpRequestDecoder}, so they are the contract a client already
 * observes. A different message, code or status after the move is a regression, not a refinement.</p>
 *
 * <p>The private failure type carried routing, method and query-string refusals too, not only body refusals, so
 * those are pinned here as well: they changed exception type in the move just as the body failures did.</p>
 */
class ExtensionRoutesRequestBoundaryParityTest {
    private static final String OVERSIZED_JSON = "{\"x\":\"" + "a".repeat(MorpheusHttpServer.MAX_REQUEST_BODY_BYTES) + "\"}";
    private static final String REDACTED_SOURCE =
            " at [Source: REDACTED (`StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION` disabled); byte offset: #";
    private static final String UNRECOGNIZED_TOKEN = "invalid JSON request body: Unrecognized token 'not': was expecting "
            + "(JSON String, Number, Array, Object or token 'null', 'true' or 'false')\n" + REDACTED_SOURCE + "0]";

    @TempDir
    Path temporaryDirectory;

    @Test
    void queryRoutesKeepTheirRequestBoundaryFailures() throws Exception {
        String execute = "/queries/execute";
        List<Case> cases = new ArrayList<>(jsonBodyCases("POST", execute));
        cases.add(new Case("POST", execute, "application/json", "{\"unknownField\":1}", 400, null, "BAD_REQUEST",
                "invalid JSON request body: Cannot construct instance of "
                        + "`com.morpheus.api.MorpheusQueryApiService$ScopedQueryRequest`, problem: query\n"
                        + REDACTED_SOURCE + "17]"));
        cases.add(new Case("GET", execute, null, null, 405, "POST", "METHOD_NOT_ALLOWED",
                "expected HTTP POST but received GET"));
        cases.add(new Case("POST", "/saved-views/view-1/execute", "application/json", "{}", 400, null, "BAD_REQUEST",
                "request body must be empty"));
        cases.add(new Case("DELETE", "/saved-views", null, null, 405, "GET, POST", "METHOD_NOT_ALLOWED",
                "saved-views supports GET and POST"));
        cases.add(new Case("POST", execute + "?x=1", "application/json", "{}", 400, null, "BAD_REQUEST",
                "query parameters are not supported on this route"));
        cases.add(failure("POST", "/queries/other", 404, null, "NOT_FOUND", "unknown API route"));
        cases.add(failure("DELETE", "/saved-views/view-1", 405, "GET, PUT", "METHOD_NOT_ALLOWED",
                "saved view supports GET and PUT"));
        cases.add(failure("GET", "/saved-views/a/b/c", 404, null, "NOT_FOUND", "unknown saved-view route"));
        cases.add(failure("GET", "/saved-views/view-1/unknown", 404, null, "NOT_FOUND",
                "unknown saved-view action: unknown"));
        cases.add(failure("GET", "/saved-views//versions", 404, null, "NOT_FOUND", "invalid API path"));
        cases.addAll(queryParameterCases("/saved-views", "invalid or duplicate query parameter: scopeKind",
                "query parameter is required: scopeId"));
        cases.add(failure("POST", "/saved-views/view-1/execute", 400, null, "BAD_REQUEST",
                "Invalid UUID string: view-1"));
        cases.add(new Case("POST", "/saved-views/view-1/archive", "application/json", "{\"expectedRevision\":1}", 400,
                null, "BAD_REQUEST", "Invalid UUID string: view-1"));
        assertBoundary("query.db", cases);
    }

    @Test
    void policyRoutesKeepTheirRequestBoundaryFailures() throws Exception {
        String packs = "/policy-packs";
        String createRequest = "com.morpheus.api.MorpheusPolicyApiService$CreateRequest";
        List<Case> cases = new ArrayList<>(jsonBodyCases("POST", packs));
        cases.add(unknownProperty("POST", packs, createRequest,
                "4 known properties: \\\"name\\\", \\\"rules\\\", \\\"actor\\\", \\\"reason\\\""));
        cases.add(trailingToken("POST", packs, createRequest));
        cases.add(oversized("POST", "/policies/evaluate"));
        cases.add(new Case("GET", packs, "application/json", "{}", 400, null, "BAD_REQUEST",
                "request body must be empty"));
        cases.add(new Case("DELETE", packs, null, null, 405, "GET, POST", "METHOD_NOT_ALLOWED",
                "policy-packs supports GET and POST"));
        cases.add(new Case("GET", "/policies/evaluate", null, null, 405, "POST", "METHOD_NOT_ALLOWED",
                "expected HTTP POST but received GET"));
        cases.add(failure("DELETE", "/policy-packs/pack-1", 405, "GET, PUT", "METHOD_NOT_ALLOWED",
                "policy pack supports GET and PUT"));
        cases.add(failure("GET", "/policy-packs/pack-1/unknown", 404, null, "NOT_FOUND",
                "unknown policy-pack action: unknown"));
        cases.add(failure("GET", "/policy-packs/pack-1/a/b", 404, null, "NOT_FOUND", "unknown policy-pack route"));
        cases.add(new Case("POST", "/policies/a/b", "application/json", "{}", 404, null, "NOT_FOUND",
                "unknown policies route"));
        cases.add(new Case("POST", "/policies/unknown", "application/json", "{}", 404, null, "NOT_FOUND",
                "unknown policies action"));
        cases.add(new Case("POST", packs + "?x=1", "application/json", "{}", 400, null, "BAD_REQUEST",
                "query parameters are not supported on this route"));
        cases.add(failure("GET", "/policy-packs//versions", 404, null, "NOT_FOUND", "invalid API path"));
        cases.addAll(queryParameterCases("/policy-overrides", "invalid or duplicate query parameter",
                "missing query parameter: scopeId"));
        cases.add(failure("GET", "/policy-packs/pack-1", 400, null, "BAD_REQUEST", "Invalid UUID string: pack-1"));
        cases.add(new Case("POST", "/policy-packs/pack-1/deactivate", "application/json",
                "{\"scopeKind\":\"PROJECT\",\"scopeId\":\"x\",\"expectedRevision\":1,\"actor\":\"a\",\"reason\":\"r\"}",
                400, null, "BAD_REQUEST", "Invalid UUID string: x"));
        assertBoundary("policy.db", cases);
    }

    @Test
    void policyManagementRoutesKeepTheirRequestBoundaryFailures() throws Exception {
        String remove = "/policy-overrides/remove";
        String removeRequest = "com.morpheus.api.MorpheusPolicyManagementHttpRoutes$RemoveOverrideRequest";
        List<Case> cases = new ArrayList<>(jsonBodyCases("POST", remove));
        cases.add(unknownProperty("POST", remove, removeRequest,
                "7 known properties: \\\"id\\\", \\\"ruleId\\\", \\\"scopeKind\\\", \\\"scopeId\\\", "
                        + "\\\"expectedRevision\\\", \\\"actor\\\", \\\"reason\\\""));
        cases.add(trailingToken("POST", remove, removeRequest));
        cases.add(oversized("POST", remove));
        cases.add(new Case("GET", remove, null, null, 405, "POST", "METHOD_NOT_ALLOWED",
                "expected HTTP POST but received GET"));
        cases.add(new Case("GET", "/policy-activations", "application/json", "{}", 400, null, "BAD_REQUEST",
                "request body must be empty"));
        cases.add(new Case("POST", "/policy-activations", null, null, 405, "GET", "METHOD_NOT_ALLOWED",
                "expected HTTP GET but received POST"));
        cases.add(failure("GET", "/policy-activations/x", 404, null, "NOT_FOUND", "unknown API route"));
        cases.add(new Case("POST", remove + "?x=1", "application/json", "{}", 400, null, "BAD_REQUEST",
                "query parameters are not supported on this route"));
        cases.addAll(queryParameterCases("/policy-activations", "invalid or duplicate query parameter: scopeKind",
                "query parameter is required: scopeId"));
        assertBoundary("policy-management.db", cases);
    }

    @Test
    void reasoningRoutesKeepTheirRequestBoundaryFailures() throws Exception {
        String analyze = "/reasoning/analyze";
        String reasoningRequest = "com.morpheus.api.MorpheusReasoningApiService$ReasoningRequest";
        List<Case> cases = new ArrayList<>(jsonBodyCases("POST", analyze));
        cases.add(unknownProperty("POST", analyze, reasoningRequest,
                "5 known properties: \\\"question\\\", \\\"evidence\\\", \\\"adapterIds\\\", \\\"parameters\\\", "
                        + "\\\"maxClaims\\\""));
        cases.add(trailingToken("POST", analyze, reasoningRequest));
        cases.add(oversized("POST", analyze));
        cases.add(new Case("GET", analyze, null, null, 405, "POST", "METHOD_NOT_ALLOWED",
                "expected HTTP POST but received GET"));
        cases.add(new Case("POST", "/reasoning/adapters", null, null, 405, "GET", "METHOD_NOT_ALLOWED",
                "expected HTTP GET but received POST"));
        cases.add(new Case("GET", "/reasoning/adapters", "application/json", "{}", 400, null, "BAD_REQUEST",
                "request body must be empty"));
        cases.add(failure("GET", "/reasoning/other", 404, null, "NOT_FOUND", "unknown reasoning route"));
        cases.add(failure("GET", "/reasoning/adapters?x=1", 400, null, "BAD_REQUEST",
                "query parameters are not supported on reasoning routes"));
        assertBoundary("reasoning.db", cases);
    }

    /** A request without a body whose failure comes from routing, a method check or the service behind it. */
    private static Case failure(String method, String path, int status, String allow, String code, String message) {
        return new Case(method, path, null, null, status, allow, code, message);
    }

    /** The three ways a GET listing refuses its query string: a repeated key, a missing key, an unknown key. */
    private static List<Case> queryParameterCases(String path, String duplicateMessage, String missingMessage) {
        return List.of(
                failure("GET", path + "?scopeKind=a&scopeKind=b", 400, null, "BAD_REQUEST", duplicateMessage),
                failure("GET", path + "?scopeKind=PROJECT", 400, null, "BAD_REQUEST", missingMessage),
                failure("GET", path + "?foo=1", 400, null, "BAD_REQUEST", "unknown query parameter: foo"));
    }

    /** The failures every JSON body route shares, in the order the boundary checks them: presence, type, syntax. */
    private static List<Case> jsonBodyCases(String method, String path) {
        return List.of(
                new Case(method, path, null, null, 400, null, "BAD_REQUEST", "JSON request body is required"),
                new Case(method, path, null, "{}", 415, null, "UNSUPPORTED_MEDIA_TYPE",
                        "Content-Type application/json is required"),
                new Case(method, path, "text/plain", "{}", 415, null, "UNSUPPORTED_MEDIA_TYPE",
                        "Content-Type application/json is required"),
                new Case(method, path, "application/json; charset=iso-8859-1", "{}", 415, null,
                        "UNSUPPORTED_MEDIA_TYPE", "Content-Type application/json is required"),
                new Case(method, path, "application/json", "not json", 400, null, "BAD_REQUEST", UNRECOGNIZED_TOKEN));
    }

    private static Case unknownProperty(String method, String path, String type, String knownProperties) {
        return new Case(method, path, "application/json", "{\"unknownField\":1}", 400, null, "BAD_REQUEST",
                "invalid JSON request body: Unrecognized property \\\"unknownField\\\" (class " + type
                        + "), not marked as ignorable (" + knownProperties + ")\n at [No location information] "
                        + "(through reference chain: " + type + "[\\\"unknownField\\\"])");
    }

    private static Case trailingToken(String method, String path, String type) {
        return new Case(method, path, "application/json", "{} {}", 400, null, "BAD_REQUEST",
                "invalid JSON request body: Trailing token (`JsonToken.START_OBJECT`) found after value (bound as `"
                        + type + "`): not allowed as per `DeserializationFeature.FAIL_ON_TRAILING_TOKENS`\n"
                        + REDACTED_SOURCE + "3]");
    }

    private static Case oversized(String method, String path) {
        return new Case(method, path, "application/json", OVERSIZED_JSON, 400, null, "BAD_REQUEST",
                "request body exceeds " + MorpheusHttpServer.MAX_REQUEST_BODY_BYTES + " bytes");
    }

    private void assertBoundary(String database, List<Case> cases) throws Exception {
        List<Executable> assertions = new ArrayList<>();
        try (MorpheusHttpServer server = MorpheusHttpServer.start(
                temporaryDirectory.resolve(database), "127.0.0.1", 0);
             HttpClient client = HttpClient.newHttpClient()) {
            for (Case expected : cases) {
                HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(server.baseUri() + expected.path()))
                        .method(expected.method(), expected.body() == null
                                ? HttpRequest.BodyPublishers.noBody()
                                : HttpRequest.BodyPublishers.ofString(expected.body()));
                if (expected.contentType() != null) {
                    request.header("Content-Type", expected.contentType());
                }
                HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
                String label = expected.method() + " " + expected.path() + " content-type=" + expected.contentType()
                        + " body=" + (expected.body() == null ? "none" : expected.body().length() + " chars");
                assertions.add(() -> assertEquals(expected.status(), response.statusCode(), label));
                assertions.add(() -> assertEquals(Optional.of("application/json; charset=utf-8"),
                        response.headers().firstValue("Content-Type"), label));
                assertions.add(() -> assertEquals(Optional.ofNullable(expected.allow()),
                        response.headers().firstValue("Allow"), label));
                assertions.add(() -> assertEquals(expected.envelope(), response.body(), label));
            }
        }
        assertAll(assertions);
    }

    /**
     * One request and the failure it must produce. {@code message} is written as it appears inside the JSON string,
     * quotes escaped, with a real line feed where Jackson puts one.
     */
    private record Case(String method, String path, String contentType, String body, int status, String allow,
            String code, String message) {
        String envelope() {
            return "{\"apiVersion\":\"v1\",\"error\":{\"code\":\"" + code + "\",\"message\":\""
                    + message.replace("\n", "\\n") + "\",\"details\":{}}}";
        }
    }
}
