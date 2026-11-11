package com.morpheus.architecture;

import com.morpheus.application.sync.LocalSourceInventoryScanner;
import com.morpheus.application.sync.IncrementalSyncService;
import com.morpheus.application.ingestion.ProjectContentIngestion;
import com.morpheus.application.ingestion.ProjectSnapshotImportService;
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

class ProjectSyncApiServiceArchitectureTest {

    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.morpheus.api");

    @Test
    void projectSyncOrchestrationStaysExtractedFromApiFacade() throws IOException {
        String facade = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusApiService"));
        String sync = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusProjectSyncApiService"));
        String routes = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusProjectSyncHttpRoutes"));

        assertTrue(facade.contains("private final MorpheusProjectSyncApiService projectSyncService;"));
        assertTrue(facade.contains("return projectSyncService.sync(projectIdValue, revision);"));
        assertTrue(facade.contains("return projectSyncService.syncStatus(projectIdValue, maxAgeMinutes);"));
        assertFalse(facade.contains("SpecificationContentReader()"));
        assertFalse(facade.contains("private Path projectWorkspace("));
        assertFalse(facade.contains("policy.requireAllowedDirectory(workspace)"));

        assertTrue(sync.contains("final class MorpheusProjectSyncApiService"));
        assertTrue(sync.contains("new LocalSourceInventoryScanner(recorder).scan("));
        assertTrue(sync.contains("new IncrementalSyncService(runtime.syncState(), recorder)"));
        assertTrue(sync.contains("new ProjectContentIngestion(new OpenSpecSpecificationContentReader()).read("));
        assertTrue(sync.contains("OpenSpecSpecificationProvider.SOURCE_DIRECTORIES"));
        assertFalse(sync.contains("ProjectContentReader"),
                "the sync service names no project content reader: reading the sources is the provider's, behind "
                        + "the sync use case");
        assertTrue(sync.contains("new ProjectSnapshotImportService("));
        assertTrue(sync.contains("new SyncFreshnessService(runtime.syncState())"));
        assertTrue(sync.contains("AllowedWorkspaceRoots"));
        assertTrue(sync.contains("policy.requireAllowedDirectory(workspace)"));

        assertTrue(routes.contains("private final MorpheusProjectSyncApiService service;"));
        assertTrue(routes.contains("projectSyncService()"));
        assertTrue(routes.contains("service.sync(projectId"));
        assertTrue(routes.contains("service.syncStatus(projectId"));
    }

    /**
     * {@code MorpheusProjectSyncApiService} serves the project sync and depends on none of the application services
     * another API service owns: {@code *RequirementQueryService}, {@code *QualityReportService}. A name ending in a
     * banned name is banned with it, as the text ban it replaces matched it.
     *
     * <p>None of these services declares a compile-time constant, so the rule alone covers them. Broken before
     * acceptance, 30/10/2026: a {@code RequirementQueryService} field added to {@code MorpheusProjectSyncApiService}
     * failed this rule.</p>
     */
    @Test
    void theSyncServiceDependsOnNoQueryOrQualityCapability() {
        noClasses()
                .that().haveSimpleName("MorpheusProjectSyncApiService")
                .should().dependOnClassesThat(simpleNameEndingWith("RequirementQueryService")
                        .or(simpleNameEndingWith("QualityReportService")))
                .because("each API service serves one capability through its own application services; reaching "
                        + "another capability's service brings a second owner for the same answer")
                .check(PRODUCTION);
    }

    /**
     * The facade holds none of the application services that serve the project sync: {@code
     * LocalSourceInventoryScanner}, {@code IncrementalSyncService}, {@code ProjectContentIngestion}, {@code
     * ProjectSnapshotImportService}. They are {@code MorpheusProjectSyncApiService}'s, and the facade delegates to it.
     *
     * <p>None of these types declares a compile-time constant, so the rule alone covers them, and it sees a dependency
     * of any kind where the text saw a construction only. Broken before acceptance, 30/10/2026: a {@code
     * IncrementalSyncService} field added to {@code MorpheusApiService} failed this rule.</p>
     */
    @Test
    void theFacadeHoldsNoProjectSyncService() {
        noClasses()
                .that().haveSimpleName("MorpheusApiService")
                .should().dependOnClassesThat(belongToAnyOf(
                        LocalSourceInventoryScanner.class,
                        IncrementalSyncService.class,
                        ProjectContentIngestion.class,
                        ProjectSnapshotImportService.class))
                .because("the facade delegates the project sync to MorpheusProjectSyncApiService; "
                        + "a service of its own would answer beside that owner")
                .check(PRODUCTION);
    }
}
