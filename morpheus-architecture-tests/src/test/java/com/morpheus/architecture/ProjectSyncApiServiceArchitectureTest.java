package com.morpheus.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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
        assertFalse(facade.contains("new LocalSourceInventoryScanner("));
        assertFalse(facade.contains("new IncrementalSyncService("));
        assertFalse(facade.contains("new ProjectContentIngestion("));
        assertFalse(facade.contains("SpecificationContentReader()"));
        assertFalse(facade.contains("new ProjectSnapshotImportService("));
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
}
