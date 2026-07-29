package com.morpheus.api;

import com.morpheus.domain.project.ProjectSpecificationId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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

class MorpheusPolicyApiContractTest {
    private static final Pattern UUID_V7 = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");

    @TempDir
    Path temporaryDirectory;

    @Test
    void createsPolicyPackAndRejectsUnknownProperties() throws Exception {
        try (MorpheusHttpServer server = MorpheusHttpServer.start(
                temporaryDirectory.resolve("policy.db"), "127.0.0.1", 0)) {
            URI root = URI.create(server.baseUri() + "/policy-packs");
            String rule = """
                    {"description":"No findings","kind":"QUALITY_THRESHOLD","severity":"BLOCKER",
                     "qualityMetric":"FINDINGS","comparison":"LTE","threshold":0}
                    """;
            HttpResponse<String> created = postJson(root, """
                    {"name":"Governance","rules":[%s],"actor":"alice","reason":"baseline"}
                    """.formatted(rule));
            HttpResponse<String> invalid = postJson(root, """
                    {"name":"Bad","rules":[%s],"actor":"alice","reason":"baseline","script":"return true"}
                    """.formatted(rule));

            assertEquals(201, created.statusCode(), created.body());
            assertTrue(created.body().contains("\"latestVersionNumber\":1"), created.body());
            assertEquals(400, invalid.statusCode(), invalid.body());
            assertTrue(invalid.body().contains("BAD_REQUEST"), invalid.body());
        }
    }

    @Test
    void updateUsesCasAndImmutableVersionsRemainReadable() throws Exception {
        try (MorpheusHttpServer server = MorpheusHttpServer.start(
                temporaryDirectory.resolve("cas.db"), "127.0.0.1", 0)) {
            URI root = URI.create(server.baseUri() + "/policy-packs");
            HttpResponse<String> created = postJson(root, createBody("Governance"));
            String id = uuids(created.body()).getFirst();
            URI item = URI.create(root + "/" + id);

            HttpResponse<String> updated = putJson(item, updateBody(1, "Governance v2"));
            HttpResponse<String> stale = putJson(item, updateBody(1, "stale"));
            HttpResponse<String> versions = get(URI.create(item + "/versions"));

            assertEquals(200, updated.statusCode(), updated.body());
            assertTrue(updated.body().contains("\"revision\":2"), updated.body());
            assertEquals(409, stale.statusCode(), stale.body());
            assertTrue(stale.body().contains("REVISION_CONFLICT"), stale.body());
            assertEquals(200, versions.statusCode(), versions.body());
            assertTrue(versions.body().contains("\"versionNumber\":1"), versions.body());
            assertTrue(versions.body().contains("\"versionNumber\":2"), versions.body());
        }
    }

    @Test
    void dryRunDoesNotActivateAndOverridePreservesOriginalDecision() throws Exception {
        try (MorpheusHttpServer server = MorpheusHttpServer.start(
                temporaryDirectory.resolve("dry-run.db"), "127.0.0.1", 0)) {
            URI root = URI.create(server.baseUri() + "/policy-packs");
            HttpResponse<String> created = postJson(root, createBody("Governance"));
            String packId = uuids(created.body()).getFirst();
            HttpResponse<String> versions = get(URI.create(root + "/" + packId + "/versions"));
            List<String> ids = uuids(versions.body());
            String versionId = ids.get(1);
            String ruleId = ids.get(2);
            String projectId = ProjectSpecificationId.generate().toString();

            HttpResponse<String> dryRun = postJson(URI.create(server.baseUri() + "/policies/dry-run"), """
                    {"scopeKind":"PROJECT","scopeId":"%s","id":"%s","versionId":"%s"}
                    """.formatted(projectId, packId, versionId));
            HttpResponse<String> auditBefore = get(URI.create(root + "/" + packId + "/audit"));
            HttpResponse<String> activated = postJson(URI.create(root + "/" + packId + "/activate"), """
                    {"versionId":"%s","scopeKind":"PROJECT","scopeId":"%s","expectedRevision":0,
                     "actor":"alice","reason":"enable"}
                    """.formatted(versionId, projectId));
            HttpResponse<String> override = putJson(URI.create(root + "/" + packId + "/overrides/" + ruleId), """
                    {"scopeKind":"PROJECT","scopeId":"%s","mode":"FORCE_BLOCK","expectedRevision":0,
                     "actor":"security","reason":"explicit exception"}
                    """.formatted(projectId));
            HttpResponse<String> evaluated = postJson(URI.create(server.baseUri() + "/policies/evaluate"), """
                    {"scopeKind":"PROJECT","scopeId":"%s","id":"%s"}
                    """.formatted(projectId, packId));

            assertEquals(200, dryRun.statusCode(), dryRun.body());
            assertTrue(dryRun.body().contains("\"dryRun\":true"), dryRun.body());
            assertTrue(auditBefore.body().contains("\"action\":\"CREATE\""), auditBefore.body());
            assertFalse(auditBefore.body().contains("ACTIVATE"), auditBefore.body());
            assertEquals(200, activated.statusCode(), activated.body());
            assertEquals(200, override.statusCode(), override.body());
            assertTrue(evaluated.body().contains("\"originalDecision\":\"UNKNOWN\""), evaluated.body());
            assertTrue(evaluated.body().contains("\"effectiveDecision\":\"BLOCK\""), evaluated.body());
            assertTrue(evaluated.body().contains("explicit exception"), evaluated.body());
        }
    }

    private String createBody(String name) {
        return """
                {"name":"%s","rules":[
                  {"description":"No findings","kind":"QUALITY_THRESHOLD","severity":"BLOCKER",
                   "qualityMetric":"FINDINGS","comparison":"LTE","threshold":0}
                ],"actor":"alice","reason":"baseline"}
                """.formatted(name);
    }

    private String updateBody(long revision, String name) {
        return """
                {"expectedRevision":%d,"name":"%s","rules":[
                  {"description":"No findings","kind":"QUALITY_THRESHOLD","severity":"BLOCKER",
                   "qualityMetric":"FINDINGS","comparison":"LTE","threshold":0}
                ],"actor":"alice","reason":"update"}
                """.formatted(revision, name);
    }

    private HttpResponse<String> postJson(URI uri, String body) throws Exception {
        return send(HttpRequest.newBuilder(uri).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build());
    }

    private HttpResponse<String> putJson(URI uri, String body) throws Exception {
        return send(HttpRequest.newBuilder(uri).header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body)).build());
    }

    private HttpResponse<String> get(URI uri) throws Exception {
        return send(HttpRequest.newBuilder(uri).GET().build());
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }

    private List<String> uuids(String text) {
        Matcher matcher = UUID_V7.matcher(text);
        List<String> values = new ArrayList<>();
        while (matcher.find()) values.add(matcher.group());
        if (values.isEmpty()) throw new AssertionError("UUIDv7 not found in " + text);
        return List.copyOf(values);
    }
}