package com.morpheus.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MorpheusApiHistoryContractTest {

    private static final Pattern SNAPSHOT_ID = Pattern.compile("\\\"snapshotId\\\":\\\"([0-9a-f-]{36})\\\"");

    @TempDir
    Path tempDirectory;

    private final ApiTestSupport http = new ApiTestSupport();

    @Test
    void exposesOnlyPublishedLineageHistoricalRequirementsAndDeterministicComparison() {
        Path database = tempDirectory.resolve("morpheus.db");
        Path fixture = http.fixture("openspec-basic");

        try (MorpheusHttpServer server = MorpheusHttpServer.start(database, "127.0.0.1", 0)) {
            String registration = "{\"workspace\":" + http.jsonString(fixture.toString()) + "}";
            ApiTestSupport.Response created = http.postJson(server, "/projects", registration);
            assertEquals(201, created.status(), created.body());
            String projectId = http.field(created.body(), "projectId");

            ApiTestSupport.Response firstSync = http.postJson(
                    server, "/projects/" + projectId + "/sync", "{\"revision\":\"r1\"}");
            assertEquals(200, firstSync.status(), firstSync.body());

            ApiTestSupport.Response secondSync = http.postJson(
                    server, "/projects/" + projectId + "/sync", "{\"revision\":\"r2\"}");
            assertEquals(200, secondSync.status(), secondSync.body());

            ApiTestSupport.Response versions = http.get(server, "/projects/" + projectId + "/versions");
            assertEquals(200, versions.status(), versions.body());
            assertTrue(versions.body().contains("KEEP_ALL_PUBLISHED"), versions.body());
            assertTrue(versions.body().contains("RETIRED"), versions.body());
            assertTrue(versions.body().contains("ACTIVE"), versions.body());

            List<String> snapshotIds = snapshotIds(versions.body());
            assertEquals(2, snapshotIds.size(), versions.body());

            ApiTestSupport.Response historical = http.get(
                    server, "/projects/" + projectId + "/versions/" + snapshotIds.getFirst() + "/requirements");
            assertEquals(200, historical.status(), historical.body());
            assertTrue(historical.body().contains("\"totalMatches\":2"), historical.body());
            assertTrue(historical.body().contains("\"temporalState\":\"CURRENT\""), historical.body());

            ApiTestSupport.Response comparison = http.get(
                    server,
                    "/projects/" + projectId + "/versions/compare?fromSnapshotId=" + snapshotIds.getFirst()
                            + "&toSnapshotId=" + snapshotIds.getLast());
            assertEquals(200, comparison.status(), comparison.body());
            assertTrue(comparison.body().contains("\"kind\":\"UNCHANGED\""), comparison.body());
            assertTrue(comparison.body().contains(snapshotIds.getFirst()), comparison.body());
            assertTrue(comparison.body().contains(snapshotIds.getLast()), comparison.body());
        }
    }

    private List<String> snapshotIds(String body) {
        Matcher matcher = SNAPSHOT_ID.matcher(body);
        List<String> ids = new ArrayList<>();
        while (matcher.find()) {
            String id = matcher.group(1);
            if (!ids.contains(id)) {
                ids.add(id);
            }
        }
        return ids;
    }
}
