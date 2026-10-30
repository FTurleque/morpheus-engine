package com.morpheus.architecture;

import com.morpheus.application.query.SpecificationContextQueryService;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.belongToAnyOf;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameEndingWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SpecificationQueryApiServiceArchitectureTest {

    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.morpheus.api");

    @Test
    void activeSpecificationQueriesStayExtractedFromApiFacade() throws IOException {
        String facade = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusApiService"));
        String specifications = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusSpecificationQueryApiService"));
        String routes = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusSpecificationsHttpRoutes"));

        assertFacadeDelegatesToSpecificationQueryService(facade);
        assertSpecificationQueryServiceImplementsQueries(specifications);
        assertSpecificationsHttpRoutesUseQueryService(routes);
    }

    private void assertFacadeDelegatesToSpecificationQueryService(String facade) {
        assertTrue(facade.contains("private final MorpheusSpecificationQueryApiService specificationQueryService;"));
        assertTrue(facade.contains("return specificationQueryService.listSpecifications(projectIdValue, pageRequest);"));
        assertTrue(facade.contains("return specificationQueryService.specification(projectIdValue, specificationIdValue);"));
        assertTrue(facade.contains("return specificationQueryService.specificationContext(projectIdValue, specificationIdValue, pageRequest);"));
        assertFalse(facade.contains("content.specifications().stream()"));
        assertFalse(facade.contains("activeSpecification(projectId, specificationId)"));
        assertFalse(facade.contains("private Object specification(Specification item)"));
        assertFalse(facade.contains("private Object scenario(Scenario item)"));
    }

    private void assertSpecificationQueryServiceImplementsQueries(String specifications) {
        assertTrue(specifications.contains("final class MorpheusSpecificationQueryApiService"));
        assertTrue(specifications.contains("content.specifications().stream()"));
        assertTrue(specifications.contains("new SpecificationContextQueryService("));
        assertTrue(specifications.contains("activeSpecification(projectId, specificationId)"));
        assertTrue(specifications.contains("PublicPayloads::specification"));
        assertTrue(specifications.contains("PublicPayloads::scenario"));
        assertFalse(specifications.contains("private Object specification(Specification item)"));
        assertFalse(specifications.contains("private Object scenario(Scenario item)"));
    }

    private void assertSpecificationsHttpRoutesUseQueryService(String routes) {
        assertTrue(routes.contains("private final MorpheusSpecificationQueryApiService service;"));
        assertTrue(routes.contains("specificationQueryService()"));
        assertTrue(routes.contains("service.listSpecifications(projectId, page(query))"));
        assertTrue(routes.contains("service.specification(projectId, segments.get(3))"));
        assertTrue(routes.contains("service.specificationContext(projectId, segments.get(3), page(query))"));
        assertFalse(routes.contains("private final MorpheusApiService service;"));
    }

    /**
     * {@code MorpheusSpecificationQueryApiService} serves the specification queries and depends on none of the
     * application services another API service owns: {@code *RequirementQueryService},
     * {@code ChangeContextQueryService}, {@code *QualityReportService}, {@code PublishedSnapshotHistoryService},
     * {@code LocalSourceInventoryScanner}. A name ending in a banned name is banned with it, as the text ban it
     * replaces matched it.
     *
     * <p>None of these services declares a compile-time constant, so the rule alone covers them. Broken before
     * acceptance, 30/10/2026: a {@code RequirementQueryService} field added to
     * {@code MorpheusSpecificationQueryApiService} failed this rule.</p>
     */
    @Test
    void theSpecificationQueryServiceDependsOnNoOtherQueryCapability() {
        noClasses()
                .that().haveSimpleName("MorpheusSpecificationQueryApiService")
                .should().dependOnClassesThat(simpleNameEndingWith("RequirementQueryService")
                        .or(simpleNameEndingWith("ChangeContextQueryService"))
                        .or(simpleNameEndingWith("QualityReportService"))
                        .or(simpleNameEndingWith("PublishedSnapshotHistoryService"))
                        .or(simpleNameEndingWith("LocalSourceInventoryScanner")))
                .because("each API service serves one capability through its own application services; reaching "
                        + "another capability's service brings a second owner for the same answer")
                .check(PRODUCTION);
    }

    /**
     * The facade holds none of the application services that serve the specification queries: {@code
     * SpecificationContextQueryService}. They are {@code MorpheusSpecificationQueryApiService}'s, and the facade
     * delegates to it.
     *
     * <p>None of these types declares a compile-time constant, so the rule alone covers them, and it sees a dependency
     * of any kind where the text saw a construction only. Broken before acceptance, 30/10/2026: a {@code
     * SpecificationContextQueryService} field added to {@code MorpheusApiService} failed this rule.</p>
     */
    @Test
    void theFacadeHoldsNoSpecificationQueryService() {
        noClasses()
                .that().haveSimpleName("MorpheusApiService")
                .should().dependOnClassesThat(belongToAnyOf(
                        SpecificationContextQueryService.class))
                .because("the facade delegates the specification queries to MorpheusSpecificationQueryApiService; "
                        + "a service of its own would answer beside that owner")
                .check(PRODUCTION);
    }
}
