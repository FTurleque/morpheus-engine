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

class RequirementQueryApiServiceArchitectureTest {

    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.morpheus.api");

    @Test
    void activeRequirementQueriesStayExtractedFromApiFacade() throws IOException {
        String facade = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusApiService"));
        String requirements = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusRequirementQueryApiService"));
        String routes = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusRequirementsHttpRoutes"));

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
        assertTrue(requirements.contains("new RequirementQueryService(runtime.snapshots(), runtime.requirements())"));
        assertTrue(requirements.contains("new RequirementSearchQuery(queryText)"));
        assertTrue(requirements.contains("new TraceRequirementQueryService("));
        assertTrue(requirements.contains("new CompactQueryViewService(runtime.content()).traceRequirement(result)"));
        assertTrue(requirements.contains("currentRequirement(snapshot.id(), requirementId.value())"));

        assertTrue(routes.contains("private final MorpheusRequirementQueryApiService service;"));
        assertTrue(routes.contains("requirementQueryService()"));
        assertTrue(routes.contains("service.requirements(projectId, query.string(\"query\").orElse(\"\"), page)"));
        assertTrue(routes.contains("service.requirement(projectId, segments.get(3))"));
        assertTrue(routes.contains("service.traceRequirement(projectId, segments.get(3), depth)"));
        assertFalse(routes.contains("private final MorpheusApiService service;"));
    }

    /**
     * {@code MorpheusRequirementQueryApiService} serves the requirement queries and depends on none of the application
     * services another API service owns: {@code SpecificationContextQueryService}, {@code ChangeContextQueryService},
     * {@code *QualityReportService}, {@code PublishedSnapshotHistoryService}, {@code LocalSourceInventoryScanner}. A
     * name ending in a banned name is banned with it, as the text ban it replaces matched it.
     *
     * <p>None of these services declares a compile-time constant, so the rule alone covers them. Broken before
     * acceptance, 30/10/2026: a {@code SpecificationContextQueryService} field added to
     * {@code MorpheusRequirementQueryApiService} failed this rule.</p>
     */
    @Test
    void theRequirementQueryServiceDependsOnNoOtherQueryCapability() {
        noClasses()
                .that().haveSimpleName("MorpheusRequirementQueryApiService")
                .should().dependOnClassesThat(simpleNameEndingWith("SpecificationContextQueryService")
                        .or(simpleNameEndingWith("ChangeContextQueryService"))
                        .or(simpleNameEndingWith("QualityReportService"))
                        .or(simpleNameEndingWith("PublishedSnapshotHistoryService"))
                        .or(simpleNameEndingWith("LocalSourceInventoryScanner")))
                .because("each API service serves one capability through its own application services; reaching "
                        + "another capability's service brings a second owner for the same answer")
                .check(PRODUCTION);
    }
}
