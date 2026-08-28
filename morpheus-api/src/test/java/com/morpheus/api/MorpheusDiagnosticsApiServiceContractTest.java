package com.morpheus.api;

import com.morpheus.application.query.PageRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MorpheusDiagnosticsApiServiceContractTest {

    @TempDir
    Path tempDirectory;

    private final ApiTestSupport http = new ApiTestSupport();

    @Test
    void preservesChangeCompletenessAndAggregateDiagnosticsContracts() {
        Path database = tempDirectory.resolve("diagnostics-service.db");
        Path fixture = http.fixture("openspec-basic");
        MorpheusProjectRegistryApiService registry = new MorpheusProjectRegistryApiService(database, Optional.empty());
        String projectId = ((Map<?, ?>) registry.registerProject(fixture.toString()).project()).get("projectId").toString();
        new MorpheusProjectSyncApiService(database, Optional.empty()).sync(projectId, Optional.of("diagnostics-r1"));

        Map<?, ?> changes = (Map<?, ?>) new MorpheusApiService(database)
                .listChanges(projectId, PageRequest.first(1));
        List<?> items = (List<?>) changes.get("items");
        String changeId = ((Map<?, ?>) items.getFirst()).get("id").toString();

        MorpheusDiagnosticsApiService service = new MorpheusDiagnosticsApiService(database);
        Map<?, ?> status = (Map<?, ?>) service.changeStatus(projectId, changeId);
        assertEquals(changeId, status.get("changeId"));
        assertEquals("UNAVAILABLE_REQUIRES_EXPLICIT_LIFECYCLE_INPUT", status.get("status"));
        assertEquals("UNAVAILABLE", status.get("lifecycleState"));
        assertTrue(status.containsKey("observableFacts"));

        Map<?, ?> blocking = (Map<?, ?>) service.blockingConditions(projectId, changeId);
        assertEquals(changeId, blocking.get("changeId"));
        assertTrue(blocking.containsKey("findings"));
        assertTrue(blocking.containsKey("unavailableFacts"));

        assertNotNull(service.diagnostics(projectId));

        ApiFailure missingChange = assertThrows(
                ApiFailure.class,
                () -> service.changeStatus(projectId, "00000000-0000-7000-8000-000000000000"));
        assertEquals(404, missingChange.status());

        ApiFailure missingProject = assertThrows(
                ApiFailure.class,
                () -> service.diagnostics("00000000-0000-7000-8000-000000000000"));
        assertEquals(404, missingProject.status());
    }
}
