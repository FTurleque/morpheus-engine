package com.morpheus.architecture;

import com.morpheus.application.quality.QualityReportService;
import com.morpheus.application.quality.ChangeCompletenessService;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.belongToAnyOf;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameEndingWith;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiagnosticsApiServiceArchitectureTest {

    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.morpheus.api");

    @Test
    void qualityDiagnosticsStayExtractedFromApiFacade() throws Exception {
        String facade = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusApiService"));
        String diagnostics = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusDiagnosticsApiService"));

        assertTrue(facade.contains("private final MorpheusDiagnosticsApiService diagnosticsService;"));
        assertTrue(facade.contains("return diagnosticsService.changeStatus(projectIdValue, changeIdValue);"));
        assertTrue(facade.contains("return diagnosticsService.blockingConditions(projectIdValue, changeIdValue);"));
        assertTrue(facade.contains("return diagnosticsService.diagnostics(projectIdValue);"));
        assertFalse(facade.contains("private ChangeCompletenessAssessment completeness("));
        assertFalse(facade.contains("private Object lifecycleFacts("));
        assertFalse(facade.contains("private Object finding(QualityFinding"));

        assertTrue(diagnostics.contains("final class MorpheusDiagnosticsApiService"));
        assertTrue(diagnostics.contains("new QualityReportService("));
        assertTrue(diagnostics.contains("new ChangeCompletenessService("));
        assertTrue(diagnostics.contains("private ChangeCompletenessAssessment completeness("));
        assertTrue(diagnostics.contains("private Object lifecycleFacts("));
        assertTrue(diagnostics.contains("private Object finding(QualityFinding"));
    }

    /**
     * {@code MorpheusDiagnosticsApiService} serves the quality diagnostics and depends on none of the application
     * services another API service owns: {@code *RequirementQueryService}, {@code LocalSourceInventoryScanner}. A name
     * ending in a banned name is banned with it, as the text ban it replaces matched it.
     *
     * <p>None of these services declares a compile-time constant, so the rule alone covers them. Broken before
     * acceptance, 30/10/2026: a {@code RequirementQueryService} field added to {@code MorpheusDiagnosticsApiService}
     * failed this rule.</p>
     */
    @Test
    void theDiagnosticsServiceDependsOnNoQueryOrInventoryCapability() {
        noClasses()
                .that().haveSimpleName("MorpheusDiagnosticsApiService")
                .should().dependOnClassesThat(simpleNameEndingWith("RequirementQueryService")
                        .or(simpleNameEndingWith("LocalSourceInventoryScanner")))
                .because("each API service serves one capability through its own application services; reaching "
                        + "another capability's service brings a second owner for the same answer")
                .check(PRODUCTION);
    }

    /**
     * The facade holds none of the application services that serve the quality diagnostics: {@code
     * QualityReportService}, {@code ChangeCompletenessService}. They are {@code MorpheusDiagnosticsApiService}'s, and
     * the facade delegates to it.
     *
     * <p>None of these types declares a compile-time constant, so the rule alone covers them, and it sees a dependency
     * of any kind where the text saw a construction only. Broken before acceptance, 30/10/2026: a {@code
     * QualityReportService} field added to {@code MorpheusApiService} failed this rule.</p>
     */
    @Test
    void theFacadeHoldsNoDiagnosticsService() {
        noClasses()
                .that().haveSimpleName("MorpheusApiService")
                .should().dependOnClassesThat(belongToAnyOf(
                        QualityReportService.class,
                        ChangeCompletenessService.class))
                .because("the facade delegates the quality diagnostics to MorpheusDiagnosticsApiService; "
                        + "a service of its own would answer beside that owner")
                .check(PRODUCTION);
    }
}
