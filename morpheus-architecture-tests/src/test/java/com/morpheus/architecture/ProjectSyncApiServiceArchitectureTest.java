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
        String facade = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusApiService"));
        String sync = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusProjectSyncApiService"));
        String routes = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusProjectSyncHttpRoutes"));

        assertTrue(facade.contains("private final MorpheusProjectSyncApiService projectSyncService;"));
        assertTrue(facade.contains("return projectSyncService.sync(projectIdValue, revision);"));
        assertTrue(facade.contains("return projectSyncService.syncStatus(projectIdValue, maxAgeMinutes);"));
        assertFalse(facade.contains("new LocalSourceInventoryScanner("));
        assertFalse(facade.contains("new IncrementalSyncService("));
        assertFalse(facade.contains("new ProjectContentIngestion("));
        assertFalse(facade.contains("SpecificationContentReader()"));
        assertFalse(facade.contains("new ProjectSnapshotImportService("));
        assertFalse(facade.contains("private Path projectWorkspace("));
        assertFalse(facade.contains("policy.requireAllowedDirectory(workspace)"));

        assertTrue(sync.contains("final class MorpheusProjectSyncApiService"));
        assertTrue(sync.contains("new LocalSourceInventoryScanner(recorder).scan("));
        assertTrue(sync.contains("new IncrementalSyncService(runtime.syncState(), recorder)"));
        assertTrue(sync.contains("new ProjectContentIngestion(new OpenSpecSpecificationContentReader()).read("));
        assertTrue(sync.contains("OpenSpecSpecificationProvider.SOURCE_DIRECTORIES"));
        assertFalse(sync.contains("ProjectContentReader"));
        assertTrue(sync.contains("new ProjectSnapshotImportService("));
        assertTrue(sync.contains("new SyncFreshnessService(runtime.syncState())"));
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
}
