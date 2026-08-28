package com.morpheus.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectRegistryApiServiceArchitectureTest {

    @Test
    void projectRegistryOrchestrationStaysExtractedFromApiFacade() throws IOException {
        Path root = repositoryRoot();
        String facade = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusApiService.java"));
        String registry = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusProjectRegistryApiService.java"));
        String routes = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusProjectRootHttpRoutes.java"));

        assertTrue(facade.contains("private final MorpheusProjectRegistryApiService projectRegistryService;"));
        assertTrue(facade.contains("return projectRegistryService.listProjects();"));
        assertTrue(facade.contains("projectRegistryService.registerProject(workspace)"));
        assertTrue(facade.contains("return projectRegistryService.project(projectIdValue);"));
        assertFalse(facade.contains("runtime.snapshots.listProjects()"));
        assertFalse(facade.contains("findProjectByRoot(root)"));
        assertFalse(facade.contains("new ProjectStoreEntry(ProjectSpecificationId.generate(), root)"));
        assertFalse(facade.contains("private Path existingDirectory(String workspace)"));
        assertFalse(facade.contains("private Object project(ApiRuntime runtime, ProjectStoreEntry entry)"));

        assertTrue(registry.contains("final class MorpheusProjectRegistryApiService"));
        assertTrue(registry.contains("runtime.snapshots.listProjects()"));
        assertTrue(registry.contains("findProjectByRoot(root)"));
        assertTrue(registry.contains("new ProjectStoreEntry(ProjectSpecificationId.generate(), root)"));
        assertTrue(registry.contains("AllowedWorkspaceRoots"));
        assertTrue(registry.contains("policy.requireAllowedDirectory(workspace)"));
        assertTrue(registry.contains("workspace is not a directory: "));
        assertTrue(registry.contains("project not found: "));
        assertFalse(registry.contains("IncrementalSyncService"));
        assertFalse(registry.contains("LocalSourceInventoryScanner"));
        assertFalse(registry.contains("MorpheusHttp"));
        assertFalse(registry.contains("MorpheusRemote"));
        assertFalse(registry.contains("RequirementQueryService"));
        assertFalse(registry.contains("QualityReportService"));

        assertTrue(routes.contains("private final MorpheusProjectRegistryApiService service;"));
        assertTrue(routes.contains("service.project(projectId)"));
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
