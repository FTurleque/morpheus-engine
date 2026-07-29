package com.morpheus.api;

import com.morpheus.domain.project.ProjectSpecificationId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MorpheusPolicyManagementApiContractTest {
    private static final Pattern UUID_V7 = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");

    @TempDir
    Path temporaryDirectory;

    @Test
    void activationRevisionIsDiscoverableAndOverrideCanBeRemovedWithAudit() throws Exception {
        try (MorpheusHttpServer server = MorpheusHttpServer.start(
                temporaryDirectory.resolve("management.db"), "127.0.0.1", 0)) {
            URI packs = URI.create(server.baseUri() + "/policy-packs");
            HttpResponse<String> created = postJson(packs, """
                    {"name":"Governance","rules":[
                      {"description":"No findings","kind":"QUALITY_THRESHOLD","severity":"BLOCKER",
                       "qualityMetric":"FINDINGS","comparison":"LTE","threshold":0}
                    ],"actor":"alice","reason":"baseline"}
                    """);
            String packId = uuids(created.body()).getFirst();
            HttpResponse<String> versions = get(URI.create(packs + "/" + packId + "/versions"));
            List<String> ids = uuids(versions.body());
            String versionId = ids.get(1);
            String ruleId = ids.get(2);
            String projectId = ProjectSpecificationId.generate().toString();

            HttpResponse<String> activated = postJson(URI.create(packs + "/" + packId + "/activate"), """
                    {"versionId":"%s","scopeKind":"PROJECT","scopeId":"%s","expectedRevision":0,
                     "actor":"alice","reason":"enable"}
                    """.formatted(versionId, projectId));
            assertEquals(200, activated.statusCode(), activated.body());

            URI activationUri = URI.create(server.baseUri() + "/policy-activations?scopeKind=PROJECT&scopeId="
                    + URLEncoder.encode(projectId, StandardCharsets.UTF_8));
            HttpResponse<String> activations = get(activationUri);
            assertEquals(200, activations.statusCode(), activations.body());
            assertTrue(activations.body().contains("\"revision\":1"), activations.body());
            assertTrue(activations.body().contains(versionId), activations.body());

            HttpResponse<String> override = putJson(URI.create(packs + "/" + packId + "/overrides/" + ruleId), """
                    {"scopeKind":"PROJECT","scopeId":"%s","mode":"FORCE_BLOCK","expectedRevision":0,
                     "actor":"security","reason":"temporary"}
                    """.formatted(projectId));
            assertEquals(200, override.statusCode(), override.body());

            HttpResponse<String> removed = postJson(URI.create(server.baseUri() + "/policy-overrides/remove"), """
                    {"id":"%s","ruleId":"%s","scopeKind":"PROJECT","scopeId":"%s","expectedRevision":1,
                     "actor":"security","reason":"waiver expired"}
                    """.formatted(packId, ruleId, projectId));
            assertEquals(200, removed.statusCode(), removed.body());
            assertTrue(removed.body().contains("\"removed\":true"), removed.body());

            HttpResponse<String> evaluated = postJson(URI.create(server.baseUri() + "/policies/evaluate"), """
                    {"scopeKind":"PROJECT","scopeId":"%s","id":"%s"}
                    """.formatted(projectId, packId));
            assertEquals(200, evaluated.statusCode(), evaluated.body());
            assertTrue(evaluated.body().contains("\"originalDecision\":\"UNKNOWN\""), evaluated.body());
            assertTrue(evaluated.body().contains("\"effectiveDecision\":\"UNKNOWN\""), evaluated.body());

            HttpResponse<String> audit = get(URI.create(packs + "/" + packId + "/audit"));
            assertTrue(audit.body().contains("REMOVE_OVERRIDE"), audit.body());
            assertTrue(audit.body().contains("waiver expired"), audit.body());
        }
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