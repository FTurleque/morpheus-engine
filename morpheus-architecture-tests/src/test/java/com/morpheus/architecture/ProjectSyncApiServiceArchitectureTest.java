package com.morpheus.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectSyncApiServiceArchitectureTest {

    @Test
    void projectSyncOrchestrationStaysExtractedFromApiFacade() throws IOException {
        Path root = repositoryRoot();
        String facade = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusApiService.java"));
        String sync = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusProjectSyncApiService.java"));
        String routes = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusProjectSyncHttpRoutes.java"));

        assertTrue(facade.contains("private final MorpheusProjectSyncApiService projectSyncService;"));
        assertTrue(facade.contains("return projectSyncService.sync(projectIdValue, revision);"));
        assertTrue(facade.contains("return projectSyncService.syncStatus(projectIdValue, maxAgeMinutes);"));
        assertFalse(facade.contains("new LocalSourceInventoryScanner()"));
        assertFalse(facade.contains("new IncrementalSyncService("));
        assertFalse(facade.contains("new OpenSpecProjectContentReader()"));
        assertFalse(facade.contains("new ProjectSnapshotImportService("));
        assertFalse(facade.contains("private Path projectWorkspace("));
        assertFalse(facade.contains("policy.requireAllowedDirectory(workspace)"));

        assertTrue(sync.contains("final class MorpheusProjectSyncApiService"));
        assertTrue(sync.contains("new LocalSourceInventoryScanner().scan("));
        assertTrue(sync.contains("new IncrementalSyncService(runtime.syncState)"));
        assertTrue(sync.contains("new OpenSpecProjectContentReader().read("));
        assertTrue(sync.contains("new ProjectSnapshotImportService("));
        assertTrue(sync.contains("new SyncFreshnessService(runtime.syncState)"));
        assertTrue(sync.contains("AllowedWorkspaceRoots"));
        assertTrue(sync.contains("policy.requireAllowedDirectory(workspace)"));
        assertFalse(sync.contains("RequirementQueryService"));
        assertFalse(sync.contains("QualityReportService"));
        assertFalse(sync.contains("MorpheusHttpResponseWriter"));
        assertFalse(sync.contains("MorpheusRemote"));

        assertTrue(routes.contains("private final MorpheusProjectSyncApiService service;"));
        assertTrue(routes.contains("projectSyncService()"));
        assertTrue(routes.contains("service.sync(projectId"));
        assertTrue(routes.contains("service.syncStatus(projectId"));
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
