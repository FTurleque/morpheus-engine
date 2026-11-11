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

class ProjectRegistryApiServiceArchitectureTest {

    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.morpheus.api");

    @Test
    void projectRegistryOrchestrationStaysExtractedFromApiFacade() throws IOException {
        String facade = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusApiService"));
        String registry = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusProjectRegistryApiService"));
        String routes = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusProjectRootHttpRoutes"));

        assertTrue(facade.contains("private final MorpheusProjectRegistryApiService projectRegistryService;"));
        assertTrue(facade.contains("return projectRegistryService.listProjects();"));
        assertTrue(facade.contains("projectRegistryService.registerProject(workspace)"));
        assertTrue(facade.contains("return projectRegistryService.project(projectIdValue);"));
        assertFalse(facade.contains("runtime.snapshots().listProjects()"));
        assertFalse(facade.contains("findProjectByRoot(root)"));
        assertFalse(facade.contains("new ProjectStoreEntry(ProjectSpecificationId.generate(), root)"));
        assertFalse(facade.contains("private Path existingDirectory(String workspace)"));
        assertFalse(facade.contains("private Object project(ApiRuntime runtime, ProjectStoreEntry entry)"));

        assertTrue(registry.contains("final class MorpheusProjectRegistryApiService"));
        assertTrue(registry.contains("runtime.snapshots().listProjects()"));
        assertTrue(registry.contains("findProjectByRoot(root)"));
        assertTrue(registry.contains("new ProjectStoreEntry(ProjectSpecificationId.generate(), root)"));
        assertTrue(registry.contains("AllowedWorkspaceRoots"));
        assertTrue(registry.contains("policy.requireAllowedDirectory(workspace)"));
        assertTrue(registry.contains("workspace is not a directory: "));
        assertTrue(registry.contains("project not found: "));
        assertFalse(registry.contains("MorpheusHttp"),
                "the registry service names no MorpheusHttp* type: it serves the application, and every transport"
                        + " type of the adapter, local server included, carries that prefix");

        assertTrue(routes.contains("private final MorpheusProjectRegistryApiService service;"));
        assertTrue(routes.contains("service.project(projectId)"));
        assertFalse(routes.contains("private final MorpheusApiService service;"));
    }

    /**
     * {@code MorpheusProjectRegistryApiService} serves the project registry and depends on none of the application
     * services another API service owns: {@code IncrementalSyncService}, {@code LocalSourceInventoryScanner},
     * {@code *RequirementQueryService}, {@code *QualityReportService}. A name ending in a banned name is banned with
     * it, as the text ban it replaces matched it.
     *
     * <p>None of these services declares a compile-time constant, so the rule alone covers them. Broken before
     * acceptance, 30/10/2026: a {@code IncrementalSyncService} field added to {@code MorpheusProjectRegistryApiService}
     * failed this rule.</p>
     */
    @Test
    void theRegistryServiceDependsOnNoSyncQueryOrInventoryCapability() {
        noClasses()
                .that().haveSimpleName("MorpheusProjectRegistryApiService")
                .should().dependOnClassesThat(simpleNameEndingWith("IncrementalSyncService")
                        .or(simpleNameEndingWith("LocalSourceInventoryScanner"))
                        .or(simpleNameEndingWith("RequirementQueryService"))
                        .or(simpleNameEndingWith("QualityReportService")))
                .because("each API service serves one capability through its own application services; reaching "
                        + "another capability's service brings a second owner for the same answer")
                .check(PRODUCTION);
    }
}
