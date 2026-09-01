package com.morpheus.api;

import com.morpheus.application.query.PageRequest;
import com.morpheus.domain.project.ProjectSpecificationId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MorpheusHistoryApiServiceContractTest {

    @TempDir
    Path tempDirectory;

    private final ApiTestSupport http = new ApiTestSupport();

    @Test
    void preservesPublishedLineagePaginationComparisonAndProjectIsolation() throws Exception {
        Path database = tempDirectory.resolve("history-service.db");
        Path fixture = http.fixture("openspec-basic");
        MorpheusProjectRegistryApiService registry = new MorpheusProjectRegistryApiService(database, Optional.empty());
        String projectId = ((Map<?, ?>) registry.registerProject(fixture.toString()).project()).get("projectId").toString();

        MorpheusProjectSyncApiService sync = new MorpheusProjectSyncApiService(database, Optional.empty());
        sync.sync(projectId, Optional.of("history-r1"));
        sync.sync(projectId, Optional.of("history-r2"));

        MorpheusHistoryApiService service = new MorpheusHistoryApiService(database);
        Map<?, ?> versions = (Map<?, ?>) service.versions(projectId);
        List<?> versionItems = (List<?>) versions.get("items");
        Map<?, ?> sourceVersion = (Map<?, ?>) versionItems.getFirst();
        Map<?, ?> targetVersion = (Map<?, ?>) versionItems.getLast();
        String sourceSnapshotId = sourceVersion.get("snapshotId").toString();
        String targetSnapshotId = targetVersion.get("snapshotId").toString();
        assertVersionsResponse(projectId, versions, versionItems, sourceVersion, targetVersion);

        Map<?, ?> firstPage = (Map<?, ?>) service.historicalRequirements(
                projectId, sourceSnapshotId, new PageRequest(0, 1));
        assertHistoricalRequirementsFirstPage(firstPage, sourceSnapshotId);

        Map<?, ?> secondPage = (Map<?, ?>) service.historicalRequirements(
                projectId, sourceSnapshotId, new PageRequest(1, 1));
        assertHistoricalRequirementsSecondPage(secondPage);

        Map<?, ?> emptyPage = (Map<?, ?>) service.historicalRequirements(
                projectId, sourceSnapshotId, new PageRequest(99, 1));
        assertHistoricalRequirementsEmptyPage(emptyPage);

        Map<?, ?> comparison = (Map<?, ?>) service.compareVersions(projectId, sourceSnapshotId, targetSnapshotId);
        assertVersionComparison(comparison, projectId, sourceSnapshotId, targetSnapshotId);

        String missingSnapshotId = "00000000-0000-7000-8000-000000000000";
        Path secondWorkspace = Files.createDirectory(tempDirectory.resolve("second-workspace"));
        String secondProjectId = ((Map<?, ?>) registry.registerProject(secondWorkspace.toString()).project())
                .get("projectId").toString();
        assertMissingAndWrongProjectFailures(service, projectId, sourceSnapshotId, missingSnapshotId, secondProjectId);
    }

    private void assertVersionsResponse(
            String projectId,
            Map<?, ?> versions,
            List<?> versionItems,
            Map<?, ?> sourceVersion,
            Map<?, ?> targetVersion) {
        assertEquals(projectId, versions.get("projectId"));
        assertEquals("KEEP_ALL_PUBLISHED", versions.get("retentionPolicy"));
        assertEquals(2, versionItems.size());
        assertEquals("RETIRED", sourceVersion.get("snapshotState"));
        assertEquals("ACTIVE", targetVersion.get("snapshotState"));
    }

    private void assertHistoricalRequirementsFirstPage(Map<?, ?> firstPage, String sourceSnapshotId) {
        assertEquals(sourceSnapshotId, firstPage.get("snapshotId"));
        assertEquals(0, firstPage.get("offset"));
        assertEquals(1, firstPage.get("limit"));
        assertEquals(2, firstPage.get("totalMatches"));
        assertEquals(Boolean.TRUE, firstPage.get("hasMore"));
        assertEquals(1, ((List<?>) firstPage.get("items")).size());
    }

    private void assertHistoricalRequirementsSecondPage(Map<?, ?> secondPage) {
        assertEquals(Boolean.FALSE, secondPage.get("hasMore"));
        assertEquals(1, ((List<?>) secondPage.get("items")).size());
    }

    private void assertHistoricalRequirementsEmptyPage(Map<?, ?> emptyPage) {
        assertEquals(Boolean.FALSE, emptyPage.get("hasMore"));
        assertEquals(List.of(), emptyPage.get("items"));
    }

    private void assertVersionComparison(
            Map<?, ?> comparison, String projectId, String sourceSnapshotId, String targetSnapshotId) {
        assertEquals(projectId, comparison.get("projectId"));
        assertEquals(sourceSnapshotId, comparison.get("sourceSnapshotId"));
        assertEquals(targetSnapshotId, comparison.get("targetSnapshotId"));
        List<?> differences = (List<?>) comparison.get("differences");
        assertFalse(differences.isEmpty());
        assertTrue(differences.stream().map(Map.class::cast).allMatch(item -> "UNCHANGED".equals(item.get("kind"))));
    }

    private void assertMissingAndWrongProjectFailures(
            MorpheusHistoryApiService service,
            String projectId,
            String sourceSnapshotId,
            String missingSnapshotId,
            String secondProjectId) {
        ApiFailure missingProject = assertThrows(
                ApiFailure.class,
                () -> service.versions(ProjectSpecificationId.generate().toString()));
        assertEquals(404, missingProject.status());

        PageRequest firstOfOne = PageRequest.first(1);
        ApiFailure missingSnapshot = assertThrows(
                ApiFailure.class,
                () -> service.historicalRequirements(projectId, missingSnapshotId, firstOfOne));
        assertEquals(404, missingSnapshot.status());
        assertTrue(missingSnapshot.getMessage().startsWith("snapshot not found: "));

        ApiFailure wrongProject = assertThrows(
                ApiFailure.class,
                () -> service.historicalRequirements(secondProjectId, sourceSnapshotId, firstOfOne));
        assertEquals(404, wrongProject.status());
        assertTrue(wrongProject.getMessage().startsWith("snapshot not found in project: "));
    }
}
