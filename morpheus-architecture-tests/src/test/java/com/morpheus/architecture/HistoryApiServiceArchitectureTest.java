package com.morpheus.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistoryApiServiceArchitectureTest {

    @Test
    void publishedHistoryQueriesStayExtractedFromApiFacade() throws IOException {
        Path root = repositoryRoot();
        String facade = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusApiService.java"));
        String history = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusHistoryApiService.java"));
        String routes = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusVersionsHttpRoutes.java"));

        assertTrue(facade.contains("private final MorpheusHistoryApiService historyService;"));
        assertTrue(facade.contains("return historyService.versions(projectIdValue);"));
        assertTrue(facade.contains("return historyService.historicalRequirements(projectIdValue, snapshotIdValue, pageRequest);"));
        assertTrue(facade.contains("return historyService.compareVersions(projectIdValue, sourceIdValue, targetIdValue);"));
        assertFalse(facade.contains("PublishedSnapshotHistoryService"));
        assertFalse(facade.contains("HistoricalRequirementQueryService"));
        assertFalse(facade.contains("RequirementSnapshotComparisonService"));
        assertFalse(facade.contains("private void requireSnapshotProject("));
        assertFalse(facade.contains("private Object version(ApiRuntime runtime, KnowledgeSnapshotMetadata snapshot)"));

        assertTrue(history.contains("final class MorpheusHistoryApiService"));
        assertTrue(history.contains("new PublishedSnapshotHistoryService(runtime.snapshots)"));
        assertTrue(history.contains("new HistoricalRequirementQueryService(runtime.snapshots, runtime.requirements)"));
        assertTrue(history.contains("new RequirementSnapshotComparisonService(runtime.snapshots, runtime.requirements)"));
        assertTrue(history.contains("private void requireSnapshotProject("));
        assertTrue(history.contains("private Object version(ApiRuntime runtime, KnowledgeSnapshotMetadata snapshot)"));
        assertFalse(history.contains("RequirementQueryService"));
        assertFalse(history.contains("QualityReportService"));
        assertFalse(history.contains("LocalSourceInventoryScanner"));
        assertFalse(history.contains("MorpheusHttpResponseWriter"));
        assertFalse(history.contains("MorpheusRemote"));

        assertTrue(routes.contains("private final MorpheusHistoryApiService service;"));
        assertTrue(routes.contains("historyService()"));
        assertTrue(routes.contains("service.versions(projectId)"));
        assertTrue(routes.contains("service.compareVersions("));
        assertTrue(routes.contains("service.historicalRequirements(projectId, segments.get(3), page(query))"));
        assertFalse(routes.contains("private final MorpheusApiService service;"));
    }

    private Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("contracts/public-surfaces.tsv"))
                    && Files.isRegularFile(current.resolve("pom.xml"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("cannot locate MORPHEUS repository root");
    }
}
