package com.morpheus.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The audit of a policy pack answers with a page, not a bare array (AUD-PRF-04, ADR-0131): a journal only grows, and a
 * list cut without saying so is what ADR-0108 forbids.
 */
class MorpheusPolicyAuditPagingHttpTest {
    private static final Pattern UUID_V7 = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @TempDir
    Path temporaryDirectory;

    @Test
    void theAuditIsAPageThatSaysHowManyRecordsFollow() throws Exception {
        try (MorpheusHttpServer server = MorpheusHttpServer.start(
                temporaryDirectory.resolve("audit-page.db"), "127.0.0.1", 0)) {
            URI packs = URI.create(server.baseUri() + "/policy-packs");
            String packId = uuids(send("POST", packs, createBody("Governance")).body()).getFirst();
            for (int revision = 1; revision <= 4; revision++) {
                assertEquals(200, send("PUT", URI.create(packs + "/" + packId), updateBody(revision)).statusCode());
            }
            URI audit = URI.create(packs + "/" + packId + "/audit");

            JsonNode everything = data(send("GET", audit, null));
            JsonNode firstTwo = data(send("GET", URI.create(audit + "?limit=2"), null));
            JsonNode next = data(send("GET", URI.create(audit + "?offset=2&limit=2"), null));
            JsonNode last = data(send("GET", URI.create(audit + "?offset=4&limit=2"), null));

            assertEquals(java.util.Set.of("offset", "limit", "totalMatches", "hasMore", "items"), fieldNames(everything));
            assertEquals(5, everything.get("totalMatches").asInt());
            assertEquals(5, everything.get("items").size());
            assertFalse(everything.get("hasMore").asBoolean());
            assertEquals(2, firstTwo.get("items").size());
            assertTrue(firstTwo.get("hasMore").asBoolean());
            assertEquals(2, next.get("offset").asInt());
            assertTrue(next.get("hasMore").asBoolean());
            assertEquals(1, last.get("items").size());
            assertFalse(last.get("hasMore").asBoolean());
            assertEquals(everything.get("items").get(2), next.get("items").get(0),
                    "a page starts where the previous one stopped");
            assertEquals("CREATE", everything.get("items").get(0).get("action").asString());
        }
    }

    @Test
    void aPageOutsideTheBoundsAnUnknownParameterAndAnUnknownPackAreRefused() throws Exception {
        try (MorpheusHttpServer server = MorpheusHttpServer.start(
                temporaryDirectory.resolve("audit-refusals.db"), "127.0.0.1", 0)) {
            URI packs = URI.create(server.baseUri() + "/policy-packs");
            String packId = uuids(send("POST", packs, createBody("Governance")).body()).getFirst();
            URI audit = URI.create(packs + "/" + packId + "/audit");

            assertEquals(400, send("GET", URI.create(audit + "?limit=0"), null).statusCode());
            assertEquals(400, send("GET", URI.create(audit + "?limit=" + (MorpheusApiService.MAX_LIMIT + 1)), null)
                    .statusCode());
            assertEquals(400, send("GET", URI.create(audit + "?offset=-1"), null).statusCode());
            HttpResponse<String> unknown = send("GET", URI.create(audit + "?cursor=x"), null);
            assertEquals(400, unknown.statusCode(), unknown.body());
            assertTrue(unknown.body().contains("cursor"), unknown.body());
            HttpResponse<String> missing = send("GET", URI.create(
                    packs + "/00000000-0000-7000-8000-000000000000/audit"), null);
            assertEquals(400, missing.statusCode(), "an unknown pack is refused as the rest of the pack tree refuses it");
            assertTrue(missing.body().contains("unknown policy pack"), missing.body());
            assertEquals(405, send("POST", audit, "{}").statusCode());
            assertEquals(400, send("GET", URI.create(packs + "/" + packId + "?limit=2"), null).statusCode(),
                    "the rest of the pack tree still takes no query");
        }
    }

    private static java.util.Set<String> fieldNames(JsonNode node) {
        java.util.Set<String> names = new java.util.TreeSet<>();
        node.propertyNames().forEach(names::add);
        return names;
    }

    private static JsonNode data(HttpResponse<String> response) throws Exception {
        assertEquals(200, response.statusCode(), response.body());
        return JSON.readTree(response.body()).get("data");
    }

    private static String createBody(String name) {
        return """
                {"name":"%s","rules":[
                  {"description":"No findings","kind":"QUALITY_THRESHOLD","severity":"BLOCKER",
                   "qualityMetric":"FINDINGS","comparison":"LTE","threshold":0}
                ],"actor":"alice","reason":"baseline"}
                """.formatted(name);
    }

    private static String updateBody(long revision) {
        return """
                {"expectedRevision":%d,"name":"Governance %d","rules":[
                  {"description":"No findings","kind":"QUALITY_THRESHOLD","severity":"BLOCKER",
                   "qualityMetric":"FINDINGS","comparison":"LTE","threshold":0}
                ],"actor":"alice","reason":"update"}
                """.formatted(revision, revision);
    }

    private static HttpResponse<String> send(String method, URI uri, String body) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri).header("Content-Type", "application/json");
        builder.method(method, body == null
                ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        }
    }

    private static List<String> uuids(String text) {
        Matcher matcher = UUID_V7.matcher(text);
        List<String> values = new ArrayList<>();
        while (matcher.find()) {
            values.add(matcher.group());
        }
        if (values.isEmpty()) {
            throw new AssertionError("UUIDv7 not found in " + text);
        }
        return List.copyOf(values);
    }
}
