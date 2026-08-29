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

class MorpheusSpecificationQueryApiServiceContractTest {

    @TempDir
    Path tempDirectory;

    private final ApiTestSupport http = new ApiTestSupport();

    @Test
    void preservesListDetailContextPaginationAndActiveSnapshotContracts() throws Exception {
        Path database = tempDirectory.resolve("specification-query-service.db");
        Path fixture = http.fixture("openspec-basic");
        MorpheusProjectRegistryApiService registry = new MorpheusProjectRegistryApiService(database, Optional.empty());
        String projectId = ((Map<?, ?>) registry.registerProject(fixture.toString()).project()).get("projectId").toString();
        MorpheusSpecificationQueryApiService service = new MorpheusSpecificationQueryApiService(database);

        ApiFailure noActiveSnapshot = assertThrows(
                ApiFailure.class,
                () -> service.listSpecifications(projectId, PageRequest.first(1)));
        assertEquals(409, noActiveSnapshot.status());

        ApiFailure missingProject = assertThrows(
                ApiFailure.class,
                () -> service.listSpecifications(ProjectSpecificationId.generate().toString(), PageRequest.first(1)));
        assertEquals(404, missingProject.status());

        new MorpheusProjectSyncApiService(database, Optional.empty()).sync(projectId, Optional.of("specifications-r1"));

        Map<?, ?> firstPage = (Map<?, ?>) service.listSpecifications(projectId, PageRequest.first(1));
        assertEquals(0, firstPage.get("offset"));
        assertEquals(1, firstPage.get("limit"));
        assertEquals(1, firstPage.get("totalMatches"));
        assertEquals(Boolean.FALSE, firstPage.get("hasMore"));
        List<?> specifications = (List<?>) firstPage.get("items");
        assertEquals(1, specifications.size());
        Map<?, ?> listedSpecification = (Map<?, ?>) specifications.getFirst();
        String specificationId = listedSpecification.get("id").toString();
        assertEquals(projectId, listedSpecification.get("projectId"));

        Map<?, ?> emptyTail = (Map<?, ?>) service.listSpecifications(projectId, new PageRequest(1, 1));
        assertEquals(1, emptyTail.get("offset"));
        assertEquals(List.of(), emptyTail.get("items"));
        assertEquals(Boolean.FALSE, emptyTail.get("hasMore"));

        Map<?, ?> detail = (Map<?, ?>) service.specification(projectId, specificationId);
        assertTrue(detail.containsKey("snapshotId"));
        Map<?, ?> detailedSpecification = (Map<?, ?>) detail.get("specification");
        assertEquals(specificationId, detailedSpecification.get("id"));
        assertEquals(listedSpecification.get("key"), detailedSpecification.get("key"));

        Map<?, ?> context = (Map<?, ?>) service.specificationContext(projectId, specificationId, PageRequest.first(1));
        assertEquals(detail.get("snapshotId"), context.get("snapshotId"));
        assertEquals(specificationId, ((Map<?, ?>) context.get("specification")).get("id"));
        Map<?, ?> requirements = (Map<?, ?>) context.get("requirements");
        assertEquals(2, requirements.get("totalMatches"));
        assertEquals(1, ((List<?>) requirements.get("items")).size());
        assertEquals(Boolean.TRUE, requirements.get("hasMore"));
        assertFalse(((List<?>) context.get("scenarios")).isEmpty());
        assertFalse(((List<?>) context.get("changes")).isEmpty());

        String missingSpecificationId = "00000000-0000-7000-8000-000000000000";
        ApiFailure missingDetail = assertThrows(
                ApiFailure.class,
                () -> service.specification(projectId, missingSpecificationId));
        assertEquals(404, missingDetail.status());

        ApiFailure missingContext = assertThrows(
                ApiFailure.class,
                () -> service.specificationContext(projectId, missingSpecificationId, PageRequest.first(1)));
        assertEquals(404, missingContext.status());

        Path unsyncedWorkspace = Files.createDirectory(tempDirectory.resolve("unsynced-workspace"));
        String unsyncedProjectId = ((Map<?, ?>) registry.registerProject(unsyncedWorkspace.toString()).project())
                .get("projectId").toString();
        ApiFailure unsyncedDetail = assertThrows(
                ApiFailure.class,
                () -> service.specification(unsyncedProjectId, specificationId));
        assertEquals(409, unsyncedDetail.status());
    }
}
