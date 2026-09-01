package com.morpheus.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequirementQueryApiServiceArchitectureTest {

    @Test
    void activeRequirementQueriesStayExtractedFromApiFacade() throws IOException {
        Path root = repositoryRoot();
        String facade = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusApiService.java"));
        String requirements = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusRequirementQueryApiService.java"));
        String routes = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusRequirementsHttpRoutes.java"));

        assertTrue(facade.contains("private final MorpheusRequirementQueryApiService requirementQueryService;"));
        assertTrue(facade.contains("return requirementQueryService.requirements(projectIdValue, query, pageRequest);"));
        assertTrue(facade.contains("return requirementQueryService.requirement(projectIdValue, requirementIdValue);"));
        assertTrue(facade.contains("return requirementQueryService.traceRequirement(projectIdValue, requirementIdValue, depth);"));
        assertFalse(facade.contains("new RequirementQueryService("));
        assertFalse(facade.contains("new RequirementSearchQuery("));
        assertFalse(facade.contains("new TraceRequirementQueryService("));
        assertFalse(facade.contains("currentRequirement(snapshot.id(), requirementId.value())"));
        assertFalse(facade.contains("private Object requirementRecord(RequirementVersionRecord record)"));

        assertTrue(requirements.contains("final class MorpheusRequirementQueryApiService"));
        assertTrue(requirements.contains("new RequirementQueryService(runtime.snapshots, runtime.requirements)"));
        assertTrue(requirements.contains("new RequirementSearchQuery(queryText)"));
        assertTrue(requirements.contains("new TraceRequirementQueryService("));
        assertTrue(requirements.contains("new CompactQueryViewService(runtime.content).traceRequirement(result)"));
        assertTrue(requirements.contains("currentRequirement(snapshot.id(), requirementId.value())"));
        assertFalse(requirements.contains("SpecificationContextQueryService"));
        assertFalse(requirements.contains("ChangeContextQueryService"));
        assertFalse(requirements.contains("QualityReportService"));
        assertFalse(requirements.contains("PublishedSnapshotHistoryService"));
        assertFalse(requirements.contains("LocalSourceInventoryScanner"));
        assertFalse(requirements.contains("MorpheusHttpResponseWriter"));
        assertFalse(requirements.contains("MorpheusRemote"));

        assertTrue(routes.contains("private final MorpheusRequirementQueryApiService service;"));
        assertTrue(routes.contains("requirementQueryService()"));
        assertTrue(routes.contains("service.requirements(projectId, query.string(\"query\").orElse(\"\"), page)"));
        assertTrue(routes.contains("service.requirement(projectId, segments.get(3))"));
        assertTrue(routes.contains("service.traceRequirement(projectId, segments.get(3), depth)"));
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
