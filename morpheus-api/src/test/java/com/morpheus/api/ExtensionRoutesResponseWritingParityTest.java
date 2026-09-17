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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What the four extension routers -- query, policy, policy management, reasoning -- <em>write</em>, pinned byte for
 * byte: status, the three response headers, {@code Allow}, and the whole envelope.
 *
 * <p>{@link ExtensionRoutesRequestBoundaryParityTest} pins how these routers refuse a request. It says nothing about
 * what they write when they accept one: it asserts {@code Content-Type} alone, never {@code Cache-Control} or
 * {@code X-Content-Type-Options}, and no success response at all. That gap is this test, and the two together are the
 * contract the shared response writer has to reproduce.</p>
 *
 * <p>The values below were captured from the four routers' own {@code send}/{@code sendJson}/{@code sendRaw} on
 * 16/09/2026, each with its own {@code CanonicalJsonSerializer} and its own private {@code ApiSuccess},
 * {@code ApiError} and {@code ApiErrorEnvelope}, before any of it moved onto {@link MorpheusHttpResponseWriter}. A
 * different status, header or byte after the move is a regression, not a refinement.</p>
 *
 * <p>Identifiers and timestamps are generated per run, so they are normalized to {@code <uuid>} and
 * {@code <instant>}; everything else is compared literally. Normalizing them is what makes the rest comparable --
 * the envelope's shape, field order and punctuation are pinned exactly.</p>
 *
 * <p><strong>What is deliberately not here.</strong> Each router ends with
 * {@code catch (RuntimeException) -> 500 INTERNAL_ERROR}, and none of those four lines is exercised. They are
 * catch-alls for a failure no request can provoke: every exception these services raise on a reachable path is
 * already caught by name above them. Reaching one would mean injecting a fault below the service, which would pin
 * the behaviour of the injection rather than of the contract. Two more stay out for the same reason -- policy's and
 * policy-management's {@code STATE_CONFLICT}, whose services validate the request before any state conflict can
 * arise, so every attempt lands on {@code BAD_REQUEST} instead -- plus query's {@code QUERY_BUDGET_EXCEEDED}, which
 * needs an export larger than the bounded budget. The changed-line gate is met without them; they are named here so
 * the gap is a recorded decision rather than an oversight.</p>
 */
class ExtensionRoutesResponseWritingParityTest {
    private static final Pattern UUID = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");
    private static final Pattern INSTANT = Pattern.compile("\\d{4}-\\d{2}-\\d{2}T[0-9:.]+Z");

    private static final String JSON = "application/json; charset=utf-8";
    private static final String PROJECT = "11111111-1111-7111-8111-111111111111";
    private static final String RULE = "{\"description\":\"No findings\",\"kind\":\"QUALITY_THRESHOLD\","
            + "\"severity\":\"BLOCKER\",\"qualityMetric\":\"FINDINGS\",\"comparison\":\"LTE\",\"threshold\":0}";

    @TempDir
    Path temporaryDirectory;

    /**
     * Reasoning writes a success envelope and its own {@code REASONING_VALIDATION} code through the same method; both
     * are pinned, because the migration replaces that method for both.
     */
    @Test
    void reasoningRoutesKeepTheirResponses() throws Exception {
        run("reasoning.db", (client, base) -> List.of(
                expect(client, "GET", base + "/reasoning/adapters", null, null, 200, JSON, null,
                        "{\"apiVersion\":\"v1\",\"data\":[{\"id\":\"builtin-evidence-synthesis-v1\",\"description\":"
                                + "\"Deterministic evidence coverage synthesis without network or LLM dependency\"}]}"),
                expect(client, "POST", base + "/reasoning/analyze", JSON,
                        "{\"question\":\"q\",\"evidence\":[{\"id\":\"f1\",\"kind\":\"PUBLISHED_FACT\","
                                + "\"subject\":\"s\",\"statement\":\"st\"}],\"adapterIds\":[]}",
                        200, JSON, null,
                        "{\"apiVersion\":\"v1\",\"data\":{\"question\":\"q\",\"evidence\":[{\"id\":\"f1\",\"kind\":"
                                + "\"PUBLISHED_FACT\",\"subject\":\"s\",\"statement\":\"st\",\"provenance\":{}}],"
                                + "\"facts\":[{\"id\":\"f1\",\"kind\":\"PUBLISHED_FACT\",\"subject\":\"s\","
                                + "\"statement\":\"st\",\"provenance\":{}}],\"inferences\":[],\"heuristics\":[],"
                                + "\"suggestions\":[],\"executions\":[],\"assisted\":false,\"mutated\":false}}"),
                expect(client, "POST", base + "/reasoning/analyze", JSON,
                        "{\"question\":\"\",\"evidence\":[],\"adapterIds\":[]}", 400, JSON, null,
                        "{\"apiVersion\":\"v1\",\"error\":{\"code\":\"REASONING_VALIDATION\",\"message\":"
                                + "\"reasoning question must not be blank\",\"details\":{}}}")));
    }

    /**
     * Query is the only router that writes a body that is not an envelope: a CSV export, with its own media type and
     * bytes the serializer never sees. It carries the same two other headers as every JSON response, which is why the
     * shared writer can own it as a parameter rather than a special case.
     */
    @Test
    void queryRoutesKeepTheirResponsesIncludingTheRawExport() throws Exception {
        run("query.db", (client, base) -> {
            List<Recorded> recorded = new ArrayList<>();
            recorded.add(expect(client, "GET", base + "/saved-views?scopeKind=PROJECT&scopeId=" + PROJECT, null, null,
                    200, JSON, null, "{\"apiVersion\":\"v1\",\"data\":[]}"));
            Recorded created = expect(client, "POST", base + "/saved-views", JSON,
                    "{\"name\":\"Report\",\"scopeKind\":\"PROJECT\",\"scopeId\":\"" + PROJECT
                            + "\",\"query\":{\"entity\":\"change\",\"fields\":\"id,title\"}}",
                    201, JSON, null,
                    "{\"apiVersion\":\"v1\",\"data\":{\"id\":\"<uuid>\",\"name\":\"Report\",\"query\":{\"scope\":"
                            + "{\"kind\":\"PROJECT\",\"id\":\"<uuid>\"},\"entityType\":\"CHANGE\",\"filter\":null,"
                            + "\"sort\":[],\"projection\":[\"id\",\"title\"],\"page\":{\"offset\":0,\"limit\":100}},"
                            + "\"revision\":1,\"status\":\"ACTIVE\",\"createdAt\":\"<instant>\",\"updatedAt\":"
                            + "\"<instant>\"}}");
            recorded.add(created);
            recorded.add(expect(client, "POST", base + "/saved-views/" + firstUuid(created.rawBody()) + "/export", JSON,
                    "{\"format\":\"CSV\"}", 200, "text/csv; charset=utf-8", null,
                    "\"id\",\"projectId\",\"title\"\n"));
            recorded.add(expect(client, "POST", base + "/queries/execute", JSON,
                    "{\"scopeKind\":\"PROJECT\",\"scopeId\":\"" + PROJECT
                            + "\",\"query\":{\"entity\":\"change\",\"fields\":\"id,title\"}}",
                    200, JSON, null,
                    "{\"apiVersion\":\"v1\",\"data\":{\"query\":{\"scope\":{\"kind\":\"PROJECT\",\"id\":\"<uuid>\"},"
                            + "\"entityType\":\"CHANGE\",\"filter\":null,\"sort\":[],\"projection\":[\"id\",\"title\"],"
                            + "\"page\":{\"offset\":0,\"limit\":100}},\"columns\":[\"id\",\"projectId\",\"title\"],"
                            + "\"items\":[],\"totalMatches\":0,\"hasMore\":false}}"));
            recorded.add(expect(client, "POST", base + "/queries/execute", JSON,
                    "{\"scopeKind\":\"PROJECT\",\"scopeId\":\"" + PROJECT
                            + "\",\"query\":{\"entity\":\"nope\",\"fields\":\"id\"}}",
                    400, JSON, null,
                    "{\"apiVersion\":\"v1\",\"error\":{\"code\":\"BAD_REQUEST\",\"message\":"
                            + "\"unknown query entity: nope\",\"details\":{}}}"));
            recorded.add(expect(client, "DELETE", base + "/saved-views", null, null, 405, JSON, "GET, POST",
                    "{\"apiVersion\":\"v1\",\"error\":{\"code\":\"METHOD_NOT_ALLOWED\",\"message\":"
                            + "\"saved-views supports GET and POST\",\"details\":{}}}"));
            recorded.add(expect(client, "POST", base + "/queries/execute", JSON,
                    "{\"scopeKind\":\"PROJECT\",\"scopeId\":\"" + PROJECT
                            + "\",\"query\":{\"entity\":\"change\",\"fields\":\"nope\"}}",
                    400, JSON, null,
                    "{\"apiVersion\":\"v1\",\"error\":{\"code\":\"QUERY_VALIDATION\",\"message\":"
                            + "\"QUERY_FIELD_UNKNOWN at $.projection[0]: unknown field: nope\",\"details\":{}}}"));
            recorded.add(expect(client, "PUT", base + "/saved-views/" + firstUuid(created.rawBody()), JSON,
                    "{\"name\":\"Report2\",\"query\":{\"entity\":\"change\",\"fields\":\"id\"},"
                            + "\"expectedRevision\":99}",
                    409, JSON, null,
                    "{\"apiVersion\":\"v1\",\"error\":{\"code\":\"REVISION_CONFLICT\",\"message\":"
                            + "\"stale saved view revision: expected 99 but current is 1\",\"details\":{}}}"));

            String archived = firstUuid(send(client, "POST", base + "/saved-views", JSON,
                    "{\"name\":\"Archived\",\"scopeKind\":\"PROJECT\",\"scopeId\":\"" + PROJECT
                            + "\",\"query\":{\"entity\":\"change\",\"fields\":\"id\"}}"));
            send(client, "POST", base + "/saved-views/" + archived + "/archive", JSON, "{\"expectedRevision\":1}");
            recorded.add(expect(client, "POST", base + "/saved-views/" + archived + "/execute", JSON, null,
                    409, JSON, null,
                    "{\"apiVersion\":\"v1\",\"error\":{\"code\":\"STATE_CONFLICT\",\"message\":"
                            + "\"saved view is archived: <uuid>\",\"details\":{}}}"));
            return recorded;
        });
    }

    /**
     * Policy writes a 201 on creation and a {@code REVISION_CONFLICT} from a distinct catch branch; both go through
     * the method being replaced, and 201 is the one success status in these four routers that is not 200.
     */
    @Test
    void policyRoutesKeepTheirResponses() throws Exception {
        run("policy.db", (client, base) -> {
            List<Recorded> recorded = new ArrayList<>();
            Recorded created = expect(client, "POST", base + "/policy-packs", JSON,
                    "{\"name\":\"Governance\",\"rules\":[" + RULE + "],\"actor\":\"alice\",\"reason\":\"baseline\"}",
                    201, JSON, null,
                    "{\"apiVersion\":\"v1\",\"data\":{\"id\":\"<uuid>\",\"name\":\"Governance\",\"revision\":1,"
                            + "\"latestVersionNumber\":1,\"createdAt\":\"<instant>\",\"updatedAt\":\"<instant>\"}}");
            recorded.add(created);
            recorded.add(expect(client, "PUT", base + "/policy-packs/" + firstUuid(created.rawBody()), JSON,
                    "{\"name\":\"Stale\",\"rules\":[" + RULE + "],\"expectedRevision\":99,\"actor\":\"alice\","
                            + "\"reason\":\"stale\"}",
                    409, JSON, null,
                    "{\"apiVersion\":\"v1\",\"error\":{\"code\":\"REVISION_CONFLICT\",\"message\":"
                            + "\"stale policy pack revision: expected 99 but current is 1\",\"details\":{}}}"));
            recorded.add(expect(client, "DELETE", base + "/policy-packs", null, null, 405, JSON, "GET, POST",
                    "{\"apiVersion\":\"v1\",\"error\":{\"code\":\"METHOD_NOT_ALLOWED\",\"message\":"
                            + "\"policy-packs supports GET and POST\",\"details\":{}}}"));
            return recorded;
        });
    }

    /**
     * Policy management writes an empty list, an {@code Allow} header, and the two business codes that reach the
     * client from its own catch branches. The {@code REVISION_CONFLICT} needs the whole override lifecycle in front
     * of it -- activate a version, force-block a rule, then remove with a stale revision -- because a conflict is
     * only observable once there is something to conflict with.
     */
    @Test
    void policyManagementRoutesKeepTheirResponses() throws Exception {
        run("policy-management.db", (client, base) -> {
            List<Recorded> recorded = new ArrayList<>();
            recorded.add(expect(client, "GET", base + "/policy-activations?scopeKind=PROJECT&scopeId=" + PROJECT,
                    null, null, 200, JSON, null, "{\"apiVersion\":\"v1\",\"data\":[]}"));
            recorded.add(expect(client, "POST", base + "/policy-activations", null, null, 405, JSON, "GET",
                    "{\"apiVersion\":\"v1\",\"error\":{\"code\":\"METHOD_NOT_ALLOWED\",\"message\":"
                            + "\"expected HTTP GET but received POST\",\"details\":{}}}"));
            recorded.add(expect(client, "GET", base + "/policy-activations?scopeKind=BOGUS&scopeId=" + PROJECT,
                    null, null, 400, JSON, null,
                    "{\"apiVersion\":\"v1\",\"error\":{\"code\":\"BAD_REQUEST\",\"message\":"
                            + "\"scopeKind must be PROJECT or PORTFOLIO\",\"details\":{}}}"));

            String pack = send(client, "POST", base + "/policy-packs", JSON,
                    "{\"name\":\"Governance\",\"rules\":[" + RULE + "],\"actor\":\"alice\",\"reason\":\"baseline\"}");
            String packId = uuids(pack).get(0);
            List<String> versionIds = uuids(send(client, "GET", base + "/policy-packs/" + packId + "/versions",
                    null, null));
            String versionId = versionIds.get(1);
            String ruleId = versionIds.get(2);
            send(client, "POST", base + "/policy-packs/" + packId + "/activate", JSON,
                    "{\"versionId\":\"" + versionId + "\",\"scopeKind\":\"PROJECT\",\"scopeId\":\"" + PROJECT
                            + "\",\"expectedRevision\":0,\"actor\":\"alice\",\"reason\":\"enable\"}");
            send(client, "PUT", base + "/policy-packs/" + packId + "/overrides/" + ruleId, JSON,
                    "{\"scopeKind\":\"PROJECT\",\"scopeId\":\"" + PROJECT + "\",\"mode\":\"FORCE_BLOCK\","
                            + "\"expectedRevision\":0,\"actor\":\"security\",\"reason\":\"temporary\"}");
            recorded.add(expect(client, "POST", base + "/policy-overrides/remove", JSON,
                    "{\"id\":\"" + packId + "\",\"ruleId\":\"" + ruleId + "\",\"scopeKind\":\"PROJECT\","
                            + "\"scopeId\":\"" + PROJECT + "\",\"expectedRevision\":99,\"actor\":\"security\","
                            + "\"reason\":\"waiver expired\"}",
                    409, JSON, null,
                    "{\"apiVersion\":\"v1\",\"error\":{\"code\":\"REVISION_CONFLICT\",\"message\":"
                            + "\"stale policy override revision: expected 99 but current is 1\",\"details\":{}}}"));
            return recorded;
        });
    }

    /**
     * Every response of these four routers carries {@code Cache-Control: no-store} and
     * {@code X-Content-Type-Options: nosniff}, success or failure, JSON or CSV. The four routers set them in their own
     * copies today; the shared writer must keep setting both on every response, which is why this is asserted over
     * every case above rather than per route.
     */
    private static void assertInvariantHeaders(List<Recorded> recorded, List<Executable> assertions) {
        for (Recorded response : recorded) {
            assertions.add(() -> assertEquals(Optional.of("no-store"), response.cacheControl(), response.label()));
            assertions.add(() -> assertEquals(Optional.of("nosniff"), response.contentTypeOptions(), response.label()));
        }
    }

    private void run(String database, Scenario scenario) throws Exception {
        List<Executable> assertions = new ArrayList<>();
        try (MorpheusHttpServer server = MorpheusHttpServer.start(
                temporaryDirectory.resolve(database), "127.0.0.1", 0);
             HttpClient client = HttpClient.newHttpClient()) {
            List<Recorded> recorded = scenario.play(client, server.baseUri().toString());
            for (Recorded response : recorded) {
                assertions.add(() -> assertEquals(response.expectedStatus(), response.status(), response.label()));
                assertions.add(() -> assertEquals(Optional.of(response.expectedContentType()),
                        response.contentType(), response.label()));
                assertions.add(() -> assertEquals(Optional.ofNullable(response.expectedAllow()),
                        response.allow(), response.label()));
                assertions.add(() -> assertEquals(response.expectedBody(), response.body(), response.label()));
            }
            assertInvariantHeaders(recorded, assertions);
        }
        assertAll(assertions);
    }

    private static Recorded expect(HttpClient client, String method, String uri, String contentType, String body,
            int expectedStatus, String expectedContentType, String expectedAllow, String expectedBody)
            throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(uri))
                .method(method, body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body));
        if (contentType != null) {
            request.header("Content-Type", contentType);
        }
        HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        return new Recorded(method + " " + uri.substring(uri.indexOf("/api/v1")), response,
                expectedStatus, expectedContentType, expectedAllow, expectedBody);
    }

    private static String firstUuid(String body) {
        Matcher matcher = UUID.matcher(body);
        if (!matcher.find()) {
            throw new IllegalStateException("no identifier in " + body);
        }
        return matcher.group();
    }

    /** A setup request whose own response is not the subject: only its identifiers are needed. */
    private static String send(HttpClient client, String method, String uri, String contentType, String body)
            throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(uri))
                .method(method, body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body));
        if (contentType != null) {
            request.header("Content-Type", contentType);
        }
        HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new IllegalStateException(method + " " + uri + " failed the setup: " + response.body());
        }
        return response.body();
    }

    private static List<String> uuids(String body) {
        List<String> identifiers = new ArrayList<>();
        Matcher matcher = UUID.matcher(body);
        while (matcher.find()) {
            identifiers.add(matcher.group());
        }
        return identifiers;
    }

    private static String normalize(String body) {
        return INSTANT.matcher(UUID.matcher(body).replaceAll("<uuid>")).replaceAll("<instant>");
    }

    @FunctionalInterface
    private interface Scenario {
        List<Recorded> play(HttpClient client, String base) throws Exception;
    }

    /** One response as observed, beside what it must be. */
    private record Recorded(String label, HttpResponse<String> response, int expectedStatus,
            String expectedContentType, String expectedAllow, String expectedBody) {
        int status() {
            return response.statusCode();
        }

        Optional<String> contentType() {
            return response.headers().firstValue("Content-Type");
        }

        Optional<String> cacheControl() {
            return response.headers().firstValue("Cache-Control");
        }

        Optional<String> contentTypeOptions() {
            return response.headers().firstValue("X-Content-Type-Options");
        }

        Optional<String> allow() {
            return response.headers().firstValue("Allow");
        }

        String rawBody() {
            return response.body();
        }

        String body() {
            return normalize(response.body());
        }
    }
}
