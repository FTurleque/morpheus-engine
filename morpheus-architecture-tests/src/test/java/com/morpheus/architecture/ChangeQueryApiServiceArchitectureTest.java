package com.morpheus.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameEndingWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangeQueryApiServiceArchitectureTest {

    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.morpheus.api");

    @Test
    void activeChangeQueriesStayExtractedFromApiFacade() throws IOException {
        String facade = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusApiService"));
        String changes = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusChangeQueryApiService"));
        String routes = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusChangesHttpRoutes"));

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
        assertTrue(changes.contains("new BusinessContentQueryService(runtime.snapshots(), runtime.content())"));
        assertTrue(changes.contains("new ChangeContextQueryService("));
        assertTrue(changes.contains("activeAcceptanceCriteriaForChange(projectId, changeId, pageRequest)"));
        assertTrue(changes.contains("activeDesignDecisions(projectId, changeId, pageRequest)"));
        assertTrue(changes.contains("activeImplementationTasks(projectId, changeId, pageRequest)"));

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

    /**
     * {@code MorpheusChangeQueryApiService} serves the change queries and depends on none of the application services
     * another API service owns: {@code SpecificationContextQueryService}, {@code *RequirementQueryService},
     * {@code *QualityReportService}, {@code PublishedSnapshotHistoryService}, {@code LocalSourceInventoryScanner}. A
     * name ending in a banned name is banned with it, as the text ban it replaces matched it.
     *
     * <p>None of these services declares a compile-time constant, so the rule alone covers them. Broken before
     * acceptance, 30/10/2026: a {@code SpecificationContextQueryService} field added to
     * {@code MorpheusChangeQueryApiService} failed this rule.</p>
     */
    @Test
    void theChangeQueryServiceDependsOnNoOtherQueryCapability() {
        noClasses()
                .that().haveSimpleName("MorpheusChangeQueryApiService")
                .should().dependOnClassesThat(simpleNameEndingWith("SpecificationContextQueryService")
                        .or(simpleNameEndingWith("RequirementQueryService"))
                        .or(simpleNameEndingWith("QualityReportService"))
                        .or(simpleNameEndingWith("PublishedSnapshotHistoryService"))
                        .or(simpleNameEndingWith("LocalSourceInventoryScanner")))
                .because("each API service serves one capability through its own application services; reaching "
                        + "another capability's service brings a second owner for the same answer")
                .check(PRODUCTION);
    }
}
