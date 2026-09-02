package com.morpheus.api;

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

class MorpheusProjectSyncApiServiceContractTest {

    @TempDir
    Path tempDirectory;

    private final ApiTestSupport http = new ApiTestSupport();

    @Test
    void preservesSyncStatusValidationAndWorkspaceConfinement() throws Exception {
        Path database = tempDirectory.resolve("sync-service.db");
        Path fixture = http.fixture("openspec-basic");
        MorpheusProjectRegistryApiService registry = new MorpheusProjectRegistryApiService(database, Optional.empty());
        MorpheusProjectRegistryApiService.RegistrationResult registration = registry.registerProject(fixture.toString());
        String projectId = ((Map<?, ?>) registration.project()).get("projectId").toString();

        MorpheusProjectSyncApiService service = new MorpheusProjectSyncApiService(database, Optional.empty());
        Map<?, ?> sync = (Map<?, ?>) service.sync(projectId, Optional.of("  sync-service-r1  "));
        assertEquals(projectId, sync.get("projectId"));
        assertEquals("FULL_REBUILD", sync.get("mode"));
        assertEquals(Boolean.TRUE, sync.get("published"));
        assertEquals(2, sync.get("requirementCount"));

        Map<?, ?> status = (Map<?, ?>) service.syncStatus(projectId, MorpheusApiService.DEFAULT_MAX_AGE_MINUTES);
        assertEquals(projectId, status.get("projectId"));
        assertEquals("FRESH", status.get("state"));
        assertEquals("sync-service-r1", status.get("sourceRevision"));
        assertEquals("FULL_REBUILD", status.get("lastSuccessfulMode"));

        Map<?, ?> blankRevisionSync = (Map<?, ?>) service.sync(projectId, Optional.of("   "));
        assertEquals(projectId, blankRevisionSync.get("projectId"));
        assertEquals(Boolean.TRUE, blankRevisionSync.get("published"));

        ApiFailure tooYoung = assertThrows(ApiFailure.class, () -> service.syncStatus(projectId, 0));
        assertEquals(400, tooYoung.status());
        assertTrue(tooYoung.getMessage().contains("maxAgeMinutes must be between 1 and"));

        ApiFailure tooOld = assertThrows(
                ApiFailure.class,
                () -> service.syncStatus(projectId, MorpheusApiService.MAX_MAX_AGE_MINUTES + 1));
        assertEquals(400, tooOld.status());
        assertTrue(tooOld.getMessage().contains("maxAgeMinutes must be between 1 and"));

        Path allowed = Files.createDirectory(tempDirectory.resolve("allowed"));
        MorpheusProjectSyncApiService confined = new MorpheusProjectSyncApiService(
                database,
                Optional.of(AllowedWorkspaceRoots.of(List.of(allowed))));
        IllegalArgumentException denied = assertThrows(
                IllegalArgumentException.class,
                () -> confined.sync(projectId, Optional.empty()));
        assertTrue(denied.getMessage().contains("outside the server-configured allowed roots"));
    }

    /**
     * {@code POST /projects/{id}/sync} is remote WRITE, and the workspace it resolves comes from the stored
     * project rather than from the request. A project the operator registered locally would otherwise hand its
     * absolute pathname to a remote caller through this conflict message.
     */
    @Test
    void aVanishedWorkspaceIsReportedByProjectIdRatherThanByPathname() throws Exception {
        Path database = tempDirectory.resolve("vanished.db");
        Path workspace = Files.createDirectory(tempDirectory.resolve("vanishing-workspace"));
        MorpheusProjectRegistryApiService registry = new MorpheusProjectRegistryApiService(database, Optional.empty());
        String projectId = ((Map<?, ?>) registry.registerProject(workspace.toString()).project())
                .get("projectId").toString();
        Files.delete(workspace);

        MorpheusProjectSyncApiService service = new MorpheusProjectSyncApiService(database, Optional.empty());
        ApiFailure conflict = assertThrows(ApiFailure.class, () -> service.sync(projectId, Optional.empty()));

        assertEquals(409, conflict.status());
        assertTrue(conflict.getMessage().contains(projectId),
                () -> "the caller must still learn which project is affected: " + conflict.getMessage());
        assertFalse(conflict.getMessage().contains(workspace.toString()),
                () -> "the conflict leaked the stored workspace pathname: " + conflict.getMessage());
        assertFalse(conflict.getMessage().contains(tempDirectory.toString()),
                () -> "the conflict leaked a server location: " + conflict.getMessage());
    }
}
