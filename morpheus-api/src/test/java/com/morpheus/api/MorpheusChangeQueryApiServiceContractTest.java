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

class MorpheusChangeQueryApiServiceContractTest {

    @TempDir
    Path tempDirectory;

    private final ApiTestSupport http = new ApiTestSupport();

    @Test
    void preservesChangeReadsSubresourcesContextValidationAndCompatibilityPagination() throws Exception {
        Path database = tempDirectory.resolve("change-query-service.db");
        Path fixture = http.fixture("openspec-basic");
        MorpheusProjectRegistryApiService registry = new MorpheusProjectRegistryApiService(database, Optional.empty());
        String projectId = ((Map<?, ?>) registry.registerProject(fixture.toString()).project()).get("projectId").toString();
        MorpheusChangeQueryApiService service = new MorpheusChangeQueryApiService(database);

        ApiFailure noActiveSnapshot = assertThrows(
                ApiFailure.class,
                () -> service.listChanges(projectId, PageRequest.first(1)));
        assertEquals(409, noActiveSnapshot.status());

        String missingProjectId = ProjectSpecificationId.generate().toString();
        ApiFailure missingProject = assertThrows(
                ApiFailure.class,
                () -> service.listChanges(missingProjectId, PageRequest.first(1)));
        assertEquals(404, missingProject.status());

        new MorpheusProjectSyncApiService(database, Optional.empty()).sync(projectId, Optional.of("changes-r1"));

        Map<?, ?> changes = (Map<?, ?>) service.listChanges(projectId, PageRequest.first(1));
        assertEquals(1, changes.get("totalMatches"));
        assertEquals(1, ((List<?>) changes.get("items")).size());
        Map<?, ?> listedChange = (Map<?, ?>) ((List<?>) changes.get("items")).getFirst();
        String changeId = listedChange.get("id").toString();

        Map<?, ?> detail = (Map<?, ?>) service.change(projectId, changeId);
        assertEquals(changeId, ((Map<?, ?>) detail.get("change")).get("id"));

        Map<?, ?> constraints = (Map<?, ?>) service.constraints(projectId, changeId, PageRequest.first(1));
        assertEquals(2, constraints.get("totalMatches"));
        assertEquals(Boolean.TRUE, constraints.get("hasMore"));
        assertEquals(1, ((List<?>) constraints.get("items")).size());

        Map<?, ?> compatibilityAcceptance = (Map<?, ?>) service.acceptanceCriteria(projectId, changeId);
        assertEquals(MorpheusApiService.MAX_LIMIT, compatibilityAcceptance.get("limit"));
        Map<?, ?> pagedAcceptance = (Map<?, ?>) service.acceptanceCriteria(projectId, changeId, new PageRequest(1, 1));
        assertEquals(1, pagedAcceptance.get("offset"));
        assertEquals(1, pagedAcceptance.get("limit"));

        Map<?, ?> decisions = (Map<?, ?>) service.designDecisions(projectId, changeId, PageRequest.first(1));
        assertEquals(2, decisions.get("totalMatches"));
        assertEquals(Boolean.TRUE, decisions.get("hasMore"));
        assertEquals(1, ((List<?>) decisions.get("items")).size());

        Map<?, ?> tasks = (Map<?, ?>) service.implementationTasks(projectId, changeId, PageRequest.first(1));
        assertEquals(8, tasks.get("totalMatches"));
        assertEquals(Boolean.TRUE, tasks.get("hasMore"));
        assertEquals(1, ((List<?>) tasks.get("items")).size());

        assertNotNull(service.changeContext(projectId, changeId, 1));
        assertNotNull(service.changeContext(projectId, changeId, MorpheusApiService.MAX_DEPTH));

        ApiFailure tooShallow = assertThrows(
                ApiFailure.class,
                () -> service.changeContext(projectId, changeId, 0));
        assertEquals(400, tooShallow.status());
        assertTrue(tooShallow.getMessage().contains("depth must be between 1 and 20"));

        ApiFailure tooDeep = assertThrows(
                ApiFailure.class,
                () -> service.changeContext(projectId, changeId, MorpheusApiService.MAX_DEPTH + 1));
        assertEquals(400, tooDeep.status());
        assertTrue(tooDeep.getMessage().contains("depth must be between 1 and 20"));

        String missingChangeId = "00000000-0000-7000-8000-000000000000";
        ApiFailure missingChange = assertThrows(
                ApiFailure.class,
                () -> service.change(projectId, missingChangeId));
        assertEquals(404, missingChange.status());

        ApiFailure missingContext = assertThrows(
                ApiFailure.class,
                () -> service.changeContext(projectId, missingChangeId, 1));
        assertEquals(404, missingContext.status());

        Path unsyncedWorkspace = Files.createDirectory(tempDirectory.resolve("unsynced-workspace"));
        String unsyncedProjectId = ((Map<?, ?>) registry.registerProject(unsyncedWorkspace.toString()).project())
                .get("projectId").toString();
        ApiFailure unsyncedDetail = assertThrows(
                ApiFailure.class,
                () -> service.change(unsyncedProjectId, changeId));
        assertEquals(409, unsyncedDetail.status());
    }
}
