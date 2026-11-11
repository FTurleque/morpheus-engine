package com.morpheus.architecture;

import com.morpheus.application.query.RequirementQueryService;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameEndingWith;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.type;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistoryApiServiceArchitectureTest {

    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.morpheus.api");

    @Test
    void publishedHistoryQueriesStayExtractedFromApiFacade() throws IOException {
        String facade = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusApiService"));
        String history = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusHistoryApiService"));
        String routes = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusVersionsHttpRoutes"));

        assertTrue(facade.contains("private final MorpheusHistoryApiService historyService;"));
        assertTrue(facade.contains("return historyService.versions(projectIdValue, pageRequest);"));
        assertTrue(facade.contains("return historyService.historicalRequirements(projectIdValue, snapshotIdValue, pageRequest);"));
        assertTrue(facade.contains("return historyService.compareVersions(projectIdValue, sourceIdValue, targetIdValue);"));
        assertFalse(facade.contains("private void requireSnapshotProject("));
        assertFalse(facade.contains("private Object version(ApiRuntime runtime, KnowledgeSnapshotMetadata snapshot)"));

        assertTrue(history.contains("final class MorpheusHistoryApiService"));
        assertTrue(history.contains("new PublishedSnapshotHistoryService(runtime.snapshots())"));
        assertTrue(history.contains("new HistoricalRequirementQueryService(runtime.snapshots(), runtime.requirements())"));
        assertTrue(history.contains("new RequirementSnapshotComparisonService(runtime.snapshots(), runtime.requirements())"));
        assertTrue(history.contains("private void requireSnapshotProject("));
        assertTrue(history.contains("private Object version(ApiRuntime runtime, KnowledgeSnapshotMetadata snapshot)"));

        assertTrue(routes.contains("private final MorpheusHistoryApiService service;"));
        assertTrue(routes.contains("historyService()"));
        assertTrue(routes.contains("service.versions(projectId, page(query))"));
        assertTrue(routes.contains("service.compareVersions("));
        assertTrue(routes.contains("service.historicalRequirements(projectId, segments.get(3), page(query))"));
        assertFalse(routes.contains("private final MorpheusApiService service;"));
    }

    /**
     * {@code MorpheusApiService} delegates the history and depends on none of the application services another API
     * service owns: {@code PublishedSnapshotHistoryService}, {@code HistoricalRequirementQueryService},
     * {@code RequirementSnapshotComparisonService}. A name ending in a banned name is banned with it, as the text ban
     * it replaces matched it.
     *
     * <p>None of these services declares a compile-time constant, so the rule alone covers them. Broken before
     * acceptance, 30/10/2026: a {@code PublishedSnapshotHistoryService} field added to {@code MorpheusApiService}
     * failed this rule.</p>
     */
    @Test
    void theFacadeDependsOnNoHistoryCapability() {
        noClasses()
                .that().haveSimpleName("MorpheusApiService")
                .should().dependOnClassesThat(simpleNameEndingWith("PublishedSnapshotHistoryService")
                        .or(simpleNameEndingWith("HistoricalRequirementQueryService"))
                        .or(simpleNameEndingWith("RequirementSnapshotComparisonService")))
                .because("the facade delegates each capability to the API service that owns it; an application "
                        + "service of its own would answer beside that owner")
                .check(PRODUCTION);
    }

    /**
     * {@code MorpheusHistoryApiService} serves the published history and depends on none of the application services
     * another API service owns: {@code *QualityReportService}, {@code LocalSourceInventoryScanner}. A name ending in a
     * banned name is banned with it, as the text ban it replaces matched it.
     *
     * <p>None of these services declares a compile-time constant, so the rule alone covers them. Broken before
     * acceptance, 30/10/2026: a {@code QualityReportService} field added to {@code MorpheusHistoryApiService} failed
     * this rule.</p>
     */
    @Test
    void theHistoryServiceDependsOnNoQualityOrInventoryCapability() {
        noClasses()
                .that().haveSimpleName("MorpheusHistoryApiService")
                .should().dependOnClassesThat(simpleNameEndingWith("QualityReportService")
                        .or(simpleNameEndingWith("LocalSourceInventoryScanner")))
                .because("each API service serves one capability through its own application services; reaching "
                        + "another capability's service brings a second owner for the same answer")
                .check(PRODUCTION);
    }

    /**
     * {@code MorpheusHistoryApiService} answers from the published history, never from the current requirements:
     * it holds no {@code RequirementQueryService}, which {@code MorpheusRequirementQueryApiService} owns. The exact
     * type, not a name suffix: {@code HistoricalRequirementQueryService} is the history service's own.
     *
     * <p>{@code RequirementQueryService} declares no compile-time constant, so the rule alone covers it, and it sees
     * a dependency of any kind where the text saw a construction only. Broken before acceptance, 30/10/2026: a
     * {@code RequirementQueryService} field added to {@code MorpheusHistoryApiService} failed this rule.</p>
     */
    @Test
    void theHistoryServiceHoldsNoCurrentRequirementQuery() {
        noClasses()
                .that().haveSimpleName("MorpheusHistoryApiService")
                .should().dependOnClassesThat(type(RequirementQueryService.class))
                .because("the history answers from the published snapshots; the current requirements are "
                        + "MorpheusRequirementQueryApiService's")
                .check(PRODUCTION);
    }
}
