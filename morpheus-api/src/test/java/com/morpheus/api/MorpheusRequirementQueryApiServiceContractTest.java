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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MorpheusRequirementQueryApiServiceContractTest {

    @TempDir
    Path tempDirectory;

    private final ApiTestSupport http = new ApiTestSupport();

    @Test
    void preservesSearchDetailTraceValidationAndActiveSnapshotContracts() throws Exception {
        Path database = tempDirectory.resolve("requirement-query-service.db");
        Path fixture = http.fixture("openspec-basic");
        MorpheusProjectRegistryApiService registry = new MorpheusProjectRegistryApiService(database, Optional.empty());
        String projectId = ((Map<?, ?>) registry.registerProject(fixture.toString()).project()).get("projectId").toString();
        new MorpheusProjectSyncApiService(database, Optional.empty()).sync(projectId, Optional.of("requirements-r1"));

        MorpheusRequirementQueryApiService service = new MorpheusRequirementQueryApiService(database);
        Map<?, ?> firstPage = (Map<?, ?>) service.requirements(projectId, null, PageRequest.first(1));
        assertEquals("", firstPage.get("query"));
        assertEquals(0, firstPage.get("offset"));
        assertEquals(1, firstPage.get("limit"));
        assertEquals(2, firstPage.get("totalMatches"));
        assertEquals(Boolean.TRUE, firstPage.get("hasMore"));
        List<?> items = (List<?>) firstPage.get("items");
        assertEquals(1, items.size());

        Map<?, ?> firstRecord = (Map<?, ?>) items.getFirst();
        Map<?, ?> firstRequirement = (Map<?, ?>) firstRecord.get("requirement");
        String requirementId = firstRequirement.get("id").toString();
        assertEquals("CURRENT", firstRecord.get("temporalState"));

        Map<?, ?> trimmedSearch = (Map<?, ?>) service.requirements(
                projectId, "  definitely-not-a-requirement  ", PageRequest.first(10));
        assertEquals("definitely-not-a-requirement", trimmedSearch.get("query"));
        assertEquals(0, trimmedSearch.get("totalMatches"));
        assertEquals(Boolean.FALSE, trimmedSearch.get("hasMore"));
        assertEquals(List.of(), trimmedSearch.get("items"));

        Map<?, ?> detail = (Map<?, ?>) service.requirement(projectId, requirementId);
        assertTrue(detail.containsKey("snapshotId"));
        Map<?, ?> detailRecord = (Map<?, ?>) detail.get("requirement");
        assertEquals(requirementId, ((Map<?, ?>) detailRecord.get("requirement")).get("id"));

        assertNotNull(service.traceRequirement(projectId, requirementId, 1));
        assertNotNull(service.traceRequirement(projectId, requirementId, MorpheusApiService.MAX_DEPTH));

        ApiFailure tooShallow = assertThrows(
                ApiFailure.class,
                () -> service.traceRequirement(projectId, requirementId, 0));
        assertEquals(400, tooShallow.status());
        assertTrue(tooShallow.getMessage().contains("depth must be between 1 and 20"));

        ApiFailure tooDeep = assertThrows(
                ApiFailure.class,
                () -> service.traceRequirement(projectId, requirementId, MorpheusApiService.MAX_DEPTH + 1));
        assertEquals(400, tooDeep.status());
        assertTrue(tooDeep.getMessage().contains("depth must be between 1 and 20"));

        String missingRequirementId = "00000000-0000-7000-8000-000000000000";
        ApiFailure missingRequirement = assertThrows(
                ApiFailure.class,
                () -> service.requirement(projectId, missingRequirementId));
        assertEquals(404, missingRequirement.status());

        ApiFailure missingProject = assertThrows(
                ApiFailure.class,
                () -> service.requirements(ProjectSpecificationId.generate().toString(), "", PageRequest.first(1)));
        assertEquals(404, missingProject.status());

        Path unsyncedWorkspace = Files.createDirectory(tempDirectory.resolve("unsynced-workspace"));
        String unsyncedProjectId = ((Map<?, ?>) registry.registerProject(unsyncedWorkspace.toString()).project())
                .get("projectId").toString();
        ApiFailure noActiveSnapshot = assertThrows(
                ApiFailure.class,
                () -> service.requirements(unsyncedProjectId, "", PageRequest.first(1)));
        assertEquals(409, noActiveSnapshot.status());
    }
}
