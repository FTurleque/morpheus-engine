package com.morpheus.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangeQueryApiServiceArchitectureTest {

    @Test
    void activeChangeQueriesStayExtractedFromApiFacade() throws IOException {
        Path root = repositoryRoot();
        String facade = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusApiService.java"));
        String changes = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusChangeQueryApiService.java"));
        String routes = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusChangesHttpRoutes.java"));

        assertTrue(facade.contains("private final MorpheusChangeQueryApiService changeQueryService;"));
        assertTrue(facade.contains("return changeQueryService.listChanges(projectIdValue, pageRequest);"));
        assertTrue(facade.contains("return changeQueryService.change(projectIdValue, changeIdValue);"));
        assertTrue(facade.contains("return changeQueryService.constraints(projectIdValue, changeIdValue, pageRequest);"));
        assertTrue(facade.contains("return changeQueryService.acceptanceCriteria(projectIdValue, changeIdValue);"));
        assertTrue(facade.contains("return changeQueryService.acceptanceCriteria(projectIdValue, changeIdValue, pageRequest);"));
        assertTrue(facade.contains("return changeQueryService.designDecisions(projectIdValue, changeIdValue, pageRequest);"));
        assertTrue(facade.contains("return changeQueryService.implementationTasks(projectIdValue, changeIdValue, pageRequest);"));
        assertTrue(facade.contains("return changeQueryService.changeContext(projectIdValue, changeIdValue, depth);"));
        assertFalse(facade.contains("new BusinessContentQueryService("));
        assertFalse(facade.contains("new ChangeContextQueryService("));
        assertFalse(facade.contains("private com.morpheus.application.query.SnapshotItemResult<ChangeProposal> requireChange("));
        assertFalse(facade.contains("private Object change(ChangeProposal item)"));
        assertFalse(facade.contains("private Object constraint(Constraint item)"));
        assertFalse(facade.contains("private Object acceptanceCriterion(AcceptanceCriterion item)"));
        assertFalse(facade.contains("private Object decision(DesignDecision item)"));
        assertFalse(facade.contains("private Object task(ImplementationTask item)"));

        assertTrue(changes.contains("final class MorpheusChangeQueryApiService"));
        assertTrue(changes.contains("new BusinessContentQueryService(runtime.snapshots, runtime.content)"));
        assertTrue(changes.contains("new ChangeContextQueryService("));
        assertTrue(changes.contains("activeAcceptanceCriteriaForChange(projectId, changeId, pageRequest)"));
        assertTrue(changes.contains("activeDesignDecisions(projectId, changeId, pageRequest)"));
        assertTrue(changes.contains("activeImplementationTasks(projectId, changeId, pageRequest)"));
        assertFalse(changes.contains("SpecificationContextQueryService"));
        assertFalse(changes.contains("RequirementQueryService"));
        assertFalse(changes.contains("TraceRequirementQueryService"));
        assertFalse(changes.contains("QualityReportService"));
        assertFalse(changes.contains("PublishedSnapshotHistoryService"));
        assertFalse(changes.contains("LocalSourceInventoryScanner"));
        assertFalse(changes.contains("MorpheusHttpResponseWriter"));
        assertFalse(changes.contains("MorpheusRemote"));

        assertTrue(routes.contains("private final MorpheusChangeQueryApiService service;"));
        assertTrue(routes.contains("private final MorpheusDiagnosticsApiService diagnosticsService;"));
        assertTrue(routes.contains("changeQueryService()"));
        assertTrue(routes.contains("diagnosticsService()"));
        assertTrue(routes.contains("service.listChanges(projectId, page(query))"));
        assertTrue(routes.contains("service.change(projectId, segments.get(3))"));
        assertTrue(routes.contains("service.changeContext(projectId, changeId, depth)"));
        assertTrue(routes.contains("diagnosticsService.changeStatus(projectId, changeId)"));
        assertTrue(routes.contains("diagnosticsService.blockingConditions(projectId, changeId)"));
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
