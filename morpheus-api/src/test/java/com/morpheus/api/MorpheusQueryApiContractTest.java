package com.morpheus.api;

import com.morpheus.domain.project.ProjectSpecificationId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MorpheusQueryApiContractTest {
    private static final Pattern UUID_V7 = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");

    @TempDir
    Path temporaryDirectory;

    @Test
    void executesProviderNeutralQueryAndRejectsUnknownRequestProperties() throws Exception {
        try (MorpheusHttpServer server = MorpheusHttpServer.start(
                temporaryDirectory.resolve("morpheus.db"), "127.0.0.1", 0)) {
            ProjectSpecificationId project = ProjectSpecificationId.generate();
            URI endpoint = URI.create(server.baseUri() + "/queries/execute");

            HttpResponse<String> ok = postJson(endpoint, """
                    {
                      "scopeKind":"PROJECT",
                      "scopeId":"%s",
                      "query":{
                        "entity":"change",
                        "filter":"title contains security",
                        "sort":"title:asc",
                        "fields":"id,title",
                        "limit":25
                      }
                    }
                    """.formatted(project));
            HttpResponse<String> invalid = postJson(endpoint, """
                    {
                      "scopeKind":"PROJECT",
                      "scopeId":"%s",
                      "query":{"entity":"change"},
                      "sql":"select * from snapshot_changes"
                    }
                    """.formatted(project));

            assertEquals(200, ok.statusCode(), ok.body());
            assertTrue(ok.body().contains("\"entityType\":\"CHANGE\""), ok.body());
            assertTrue(ok.body().contains("\"totalMatches\":0"), ok.body());
            assertEquals(400, invalid.statusCode(), invalid.body());
            assertTrue(invalid.body().contains("BAD_REQUEST"), invalid.body());
        }
    }

    @Test
    void savedViewUpdateUsesCasAndVersionsRemainReadable() throws Exception {
        try (MorpheusHttpServer server = MorpheusHttpServer.start(
                temporaryDirectory.resolve("saved.db"), "127.0.0.1", 0)) {
            ProjectSpecificationId project = ProjectSpecificationId.generate();
            URI root = URI.create(server.baseUri() + "/saved-views");
            HttpResponse<String> created = postJson(root, """
                    {
                      "name":"Security",
                      "scopeKind":"PROJECT",
                      "scopeId":"%s",
                      "query":{"entity":"change","filter":"title starts-with sec"}
                    }
                    """.formatted(project));
            String id = firstUuid(created.body());

            HttpResponse<String> updated = putJson(URI.create(root + "/" + id), """
                    {
                      "expectedRevision":1,
                      "name":"Security current",
                      "query":{"entity":"change","filter":"title ends-with ity"}
                    }
                    """);
            HttpResponse<String> stale = putJson(URI.create(root + "/" + id), """
                    {
                      "expectedRevision":1,
                      "name":"Stale",
                      "query":{"entity":"change"}
                    }
                    """);
            HttpResponse<String> versions = get(URI.create(root + "/" + id + "/versions"));

            assertEquals(201, created.statusCode(), created.body());
            assertEquals(200, updated.statusCode(), updated.body());
            assertTrue(updated.body().contains("\"revision\":2"), updated.body());
            assertEquals(409, stale.statusCode(), stale.body());
            assertTrue(stale.body().contains("REVISION_CONFLICT"), stale.body());
            assertEquals(200, versions.statusCode(), versions.body());
            assertTrue(versions.body().contains("\"revision\":1"), versions.body());
            assertTrue(versions.body().contains("\"revision\":2"), versions.body());
        }
    }

    @Test
    void csvExportUsesRawMediaTypeAndDoesNotMutateSavedView() throws Exception {
        try (MorpheusHttpServer server = MorpheusHttpServer.start(
                temporaryDirectory.resolve("export.db"), "127.0.0.1", 0)) {
            ProjectSpecificationId project = ProjectSpecificationId.generate();
            URI root = URI.create(server.baseUri() + "/saved-views");
            HttpResponse<String> created = postJson(root, """
                    {
                      "name":"Report",
                      "scopeKind":"PROJECT",
                      "scopeId":"%s",
                      "query":{"entity":"change","fields":"id,title"}
                    }
                    """.formatted(project));
            String id = firstUuid(created.body());

            HttpResponse<String> exported = postJson(
                    URI.create(root + "/" + id + "/export"), "{\"format\":\"CSV\"}");
            HttpResponse<String> after = get(URI.create(root + "/" + id));

            assertEquals(200, exported.statusCode(), exported.body());
            assertTrue(exported.headers().firstValue("Content-Type").orElse("").startsWith("text/csv"));
            assertEquals("\"id\",\"projectId\",\"title\"\n", exported.body());
            assertEquals(200, after.statusCode(), after.body());
            assertTrue(after.body().contains("\"revision\":1"), after.body());
        }
    }

    private HttpResponse<String> postJson(URI uri, String body) throws Exception {
        return send(HttpRequest.newBuilder(uri)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build());
    }

    private HttpResponse<String> putJson(URI uri, String body) throws Exception {
        return send(HttpRequest.newBuilder(uri)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(body))
                .build());
    }

    private HttpResponse<String> get(URI uri) throws Exception {
        return send(HttpRequest.newBuilder(uri).GET().build());
    }

    private HttpResponse<String> send(HttpRequest request) throws Exception {
        try (HttpClient client = HttpClient.newHttpClient()) {
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        }
    }

    private String firstUuid(String text) {
        var matcher = UUID_V7.matcher(text);
        if (!matcher.find()) {
            throw new AssertionError("UUIDv7 not found in " + text);
        }
        return matcher.group();
    }
}
