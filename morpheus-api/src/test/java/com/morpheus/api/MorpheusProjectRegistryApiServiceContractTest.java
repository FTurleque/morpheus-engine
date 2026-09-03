package com.morpheus.api;

import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.source.SourceLocator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
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
        // The rejection echoes what the caller sent, not what the server resolved it to; a relative request would
        // otherwise report the server's working directory back.
        assertEquals(
                "workspace is not a directory: " + missingDirectory,
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

    /**
     * The HTTP surface names a workspace; it never locates one.
     *
     * <p>A workspace registered at a filesystem root has no last segment, and the projection used to fall back
     * to the locator itself for exactly those cases -- handing a remote READ caller the absolute pathname the
     * whole projection exists to withhold. The fallback names the shape instead.</p>
     */
    @Test
    void aWorkspaceAtAFilesystemRootIsNamedWithoutBeingLocated() throws Exception {
        Path database = tempDirectory.resolve("roots.db");
        MorpheusProjectRegistryApiService service = new MorpheusProjectRegistryApiService(database, Optional.empty());

        Map<String, String> expected = new LinkedHashMap<>();
        expected.put("/", MorpheusProjectRegistryApiService.FILESYSTEM_ROOT_WORKSPACE_NAME);
        expected.put("C:\\", MorpheusProjectRegistryApiService.FILESYSTEM_ROOT_WORKSPACE_NAME);
        expected.put("C:/", MorpheusProjectRegistryApiService.FILESYSTEM_ROOT_WORKSPACE_NAME);
        expected.put("//", MorpheusProjectRegistryApiService.FILESYSTEM_ROOT_WORKSPACE_NAME);
        expected.put("/srv/morpheus/private/classified/", "classified");
        expected.put("/srv/morpheus/private/classified", "classified");
        expected.put("C:\\secret\\workspace\\classified", "classified");

        for (Map.Entry<String, String> entry : expected.entrySet()) {
            String projected = service.workspaceName(new SourceLocator("file", entry.getKey()));
            assertEquals(entry.getValue(), projected,
                    () -> "unexpected workspace name for locator " + entry.getKey());
            assertFalse(projected.contains("/") || projected.contains("\\"),
                    () -> "the workspace name still locates the workspace: " + projected);
        }

        // A scheme that locates nothing is still relayed as it is; one that locates something never is.
        assertEquals("openspec", service.workspaceName(new SourceLocator("synthetic", "openspec")));
        assertEquals("synthetic",
                service.workspaceName(new SourceLocator("synthetic", "/srv/morpheus/private/spec")));
        assertTrue(Files.notExists(database), "naming a workspace must not open the store");
    }

    /**
     * A relative request must not be answered with the resolved absolute path.
     *
     * <p>The server resolves a relative workspace against its own working directory. Reporting the result back
     * would tell a caller where the server runs, which is something it did not supply and cannot reach.</p>
     */
    @Test
    void aRelativeWorkspaceRejectionDoesNotReportTheServersWorkingDirectory() {
        MorpheusProjectRegistryApiService service = new MorpheusProjectRegistryApiService(
                tempDirectory.resolve("relative.db"), Optional.empty());

        ApiFailure rejection = assertThrows(
                ApiFailure.class,
                () -> service.registerProject("no-such-relative-workspace"));

        assertEquals(400, rejection.status());
        assertEquals("workspace is not a directory: no-such-relative-workspace", rejection.getMessage());
        assertFalse(rejection.getMessage().contains(Path.of("").toAbsolutePath().toString()),
                () -> "the rejection leaked the server working directory: " + rejection.getMessage());
    }
}
