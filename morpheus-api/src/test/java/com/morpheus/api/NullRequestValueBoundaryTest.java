package com.morpheus.api;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A JSON {@code null} where the policy and portfolio routes need a value is refused at the boundary, by name.
 *
 * <p>The request records of these routes are plain records: an absent member and an explicit {@code null} both
 * decode to {@code null}. Where the service handed that on, the first {@code Objects.requireNonNull} below it
 * raised a {@code NullPointerException}, which no route maps -- the caller received {@code 500 INTERNAL_ERROR} for a
 * request it could have corrected, and nothing told it which member was at fault.</p>
 */
class NullRequestValueBoundaryTest {
    private static final String PROJECT = "11111111-1111-7111-8111-111111111111";
    private static final String OTHER_PROJECT = "22222222-2222-7222-8222-222222222222";
    private static final String ENTITY = "33333333-3333-7333-8333-333333333333";

    @TempDir
    Path temporaryDirectory;

    private MorpheusHttpServer server;
    private HttpClient client;

    @BeforeEach
    void startServer() {
        server = MorpheusHttpServer.start(temporaryDirectory.resolve("nulls.db"), "127.0.0.1", 0);
        client = HttpClient.newHttpClient();
    }

    @AfterEach
    void stopServer() {
        client.close();
        server.close();
    }

    @Test
    void aPolicyPackWhoseRulesHoldANullIsRefusedByName() throws Exception {
        assertRefused("/policy-packs", "{\"name\":\"n\",\"rules\":[null],\"actor\":\"a\",\"reason\":\"r\"}", "rules[0]");
        assertRefused("/policy-packs", "{\"name\":\"n\",\"rules\":null,\"actor\":\"a\",\"reason\":\"r\"}", "rules");
        assertRefused("/policy-packs", "{\"name\":\"n\",\"actor\":\"a\",\"reason\":\"r\"}", "rules");
    }

    @Test
    void aPolicyPackUpdateWhoseRulesHoldANullIsRefusedByName() throws Exception {
        String pack = "/policy-packs/44444444-4444-7444-8444-444444444444";
        String head = "{\"expectedRevision\":1,\"name\":\"n\",\"actor\":\"a\",\"reason\":\"r\"";

        assertRefused("PUT", pack, head + ",\"rules\":[null]}", "rules[0]");
        assertRefused("PUT", pack, head + ",\"rules\":null}", "rules");
        assertRefused("PUT", pack, head + "}", "rules");
    }

    @Test
    void aPortfolioWithoutANameIsRefusedByName() throws Exception {
        assertRefused("/portfolios", "{\"name\":null}", "name");
        assertRefused("/portfolios", "{}", "name");
    }

    @Test
    void aProjectRegistrationMissingARequiredMemberIsRefusedByName() throws Exception {
        String route = "/portfolios/" + createPortfolio() + "/projects";
        Map<String, String> valid = members("projectId", PROJECT, "name", "member");

        assertEachRequiredMemberIsRefused(route, valid, List.of("projectId", "name"));
    }

    @Test
    void aFreshnessObservationWithoutAStateIsRefusedByName() throws Exception {
        String route = "/portfolios/" + createPortfolio() + "/projects/" + PROJECT + "/freshness";

        assertRefused(route, "{\"state\":null}", "state");
        assertRefused(route, "{\"revision\":\"r1\"}", "state");
    }

    @Test
    void aCrossProjectReferenceMissingARequiredMemberIsRefusedByName() throws Exception {
        String route = "/portfolios/" + createPortfolio() + "/references";
        Map<String, String> valid = members(
                "sourceProjectId", PROJECT, "sourceType", "requirement", "sourceId", ENTITY,
                "targetProjectId", OTHER_PROJECT, "targetType", "requirement", "targetId", ENTITY,
                "relation", "depends-on", "providerId", "openspec");

        assertEachRequiredMemberIsRefused(route, valid, List.copyOf(valid.keySet()));
    }

    @Test
    void aTraversalMissingARequiredMemberIsRefusedByName() throws Exception {
        String route = "/portfolios/" + createPortfolio() + "/traverse";
        Map<String, String> valid = members("startProjectId", PROJECT, "startType", "requirement", "startId", ENTITY);

        assertEachRequiredMemberIsRefused(route, valid, List.copyOf(valid.keySet()));
    }

    @Test
    void aBodyThatIsTheJsonNullIsRefusedAsABody() throws Exception {
        for (String route : List.of("/policy-packs", "/portfolios", "/policies/evaluate", "/policy-overrides/remove")) {
            assertRefused(route, "null", "JSON request body");
        }
    }

    private void assertEachRequiredMemberIsRefused(String route, Map<String, String> valid, List<String> required)
            throws Exception {
        for (String member : required) {
            Map<String, String> explicitNull = new LinkedHashMap<>(valid);
            explicitNull.put(member, null);
            assertRefused(route, json(explicitNull), member);

            Map<String, String> absent = new LinkedHashMap<>(valid);
            absent.remove(member);
            assertRefused(route, json(absent), member);
        }
    }

    private void assertRefused(String route, String body, String named) throws Exception {
        assertRefused("POST", route, body, named);
    }

    private void assertRefused(String method, String route, String body, String named) throws Exception {
        HttpResponse<String> response = send(method, route, body);
        String context = method + " " + route + " " + body + " -> " + response.body();

        assertEquals(400, response.statusCode(), context);
        assertTrue(response.body().startsWith("{\"apiVersion\":\"v1\",\"error\":{\"code\":\"BAD_REQUEST\",\"message\":\""),
                context);
        String message = response.body().substring(response.body().indexOf("\"message\":\"") + 11,
                response.body().indexOf("\",\"details\""));
        assertTrue(message.contains(named), () -> "the refusal does not name " + named + ": " + context);
    }

    private String createPortfolio() throws Exception {
        HttpResponse<String> response = send("POST", "/portfolios", "{\"name\":\"portfolio\"}");
        assertEquals(201, response.statusCode(), response.body());
        int start = response.body().indexOf("\"id\":\"") + 6;
        return response.body().substring(start, response.body().indexOf('"', start));
    }

    private HttpResponse<String> send(String method, String route, String body) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create(server.baseUri() + route))
                        .header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private static Map<String, String> members(String... namesAndValues) {
        Map<String, String> members = new LinkedHashMap<>();
        for (int index = 0; index < namesAndValues.length; index += 2) {
            members.put(namesAndValues[index], namesAndValues[index + 1]);
        }
        return members;
    }

    private static String json(Map<String, String> members) {
        return members.entrySet().stream()
                .map(member -> "\"" + member.getKey() + "\":"
                        + (member.getValue() == null ? "null" : "\"" + member.getValue() + "\""))
                .collect(Collectors.joining(",", "{", "}"));
    }
}
