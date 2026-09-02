package com.morpheus.api;

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

class MorpheusProjectRegistryApiServiceContractTest {

    @TempDir
    Path tempDirectory;

    @Test
    void preservesRegistryViewsIdempotenceFailuresAndWorkspaceConfinement() throws Exception {
        Path database = tempDirectory.resolve("registry.db");
        Path workspace = Files.createDirectory(tempDirectory.resolve("workspace"));
        MorpheusProjectRegistryApiService service = new MorpheusProjectRegistryApiService(database, Optional.empty());

        assertEquals(List.of(), service.listProjects());

        MorpheusProjectRegistryApiService.RegistrationResult created = service.registerProject(workspace.toString());
        assertTrue(created.created());
        Map<?, ?> createdProject = (Map<?, ?>) created.project();
        // GET /projects lists every registered project, including ones the operator registered locally, so the
        // HTTP view names the workspace instead of locating it. projectId remains the addressable identity.
        assertEquals("workspace", createdProject.get("workspaceName"));
        assertFalse(createdProject.containsKey("workspace"),
                "the HTTP project view must not carry the absolute workspace pathname");
        assertFalse(createdProject.toString().contains(tempDirectory.toString()),
                () -> "the HTTP project view leaked a server location: " + createdProject);
        assertEquals("none", createdProject.get("activeSnapshotId"));

        MorpheusProjectRegistryApiService.RegistrationResult existing = service.registerProject(workspace.toString());
        assertFalse(existing.created());
        assertEquals(created.project(), existing.project());
        assertEquals(List.of(created.project()), service.listProjects());
        assertEquals(created.project(), service.project(createdProject.get("projectId").toString()));

        ApiFailure missing = assertThrows(
                ApiFailure.class,
                () -> service.project(ProjectSpecificationId.generate().toString()));
        assertEquals(404, missing.status());
        assertTrue(missing.getMessage().startsWith("project not found: "));

        ApiFailure blank = assertThrows(ApiFailure.class, () -> service.registerProject("  "));
        assertEquals(400, blank.status());
        assertEquals("workspace is required", blank.getMessage());

        ApiFailure nullWorkspace = assertThrows(ApiFailure.class, () -> service.registerProject(null));
        assertEquals(400, nullWorkspace.status());
        assertEquals("workspace is required", nullWorkspace.getMessage());

        Path missingDirectory = tempDirectory.resolve("missing-directory");
        ApiFailure notDirectory = assertThrows(
                ApiFailure.class,
                () -> service.registerProject(missingDirectory.toString()));
        assertEquals(400, notDirectory.status());
        assertEquals(
                "workspace is not a directory: " + missingDirectory.toAbsolutePath().normalize(),
                notDirectory.getMessage());

        Path allowed = Files.createDirectory(tempDirectory.resolve("allowed"));
        Path outside = Files.createDirectory(tempDirectory.resolve("outside"));
        MorpheusProjectRegistryApiService confined = new MorpheusProjectRegistryApiService(
                tempDirectory.resolve("confined.db"), Optional.of(AllowedWorkspaceRoots.of(List.of(allowed))));
        IllegalArgumentException denied = assertThrows(
                IllegalArgumentException.class,
                () -> confined.registerProject(outside.toString()));
        assertTrue(denied.getMessage().contains("outside the server-configured allowed roots"));
    }
}
