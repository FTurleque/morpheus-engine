package com.morpheus.architecture.m24;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryPlatformArchitectureTest {

    @Test
    void applicationQueryPlatformDoesNotDependOnTransportOrConcreteStores() {
        var classes = new ClassFileImporter().importPackages("com.morpheus.application.query");

        noClasses()
                .that().resideInAnyPackage(
                        "..application.query.dsl..",
                        "..application.query.saved..",
                        "..application.query.export..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..cli..",
                        "..mcp..",
                        "..api..",
                        "..store.memory..",
                        "..store.sqlite..",
                        "..provider.openspec..",
                        "..provider.markdown..")
                .check(classes);
    }

    @Test
    void publicManifestAndOpenApiExposeTheSameM24IntentFamilies() throws IOException {
        Path root = repositoryRoot();
        String manifest = Files.readString(root.resolve("contracts/public-surfaces.tsv"));
        String openApi = Files.readString(root.resolve("docs/openapi/morpheus-v1-query-m24.yaml"));

        assertTrue(manifest.contains("query.execute\tREAD\tquery execute\texecute_query\tPOST /api/v1/queries/execute"));
        assertTrue(manifest.contains("saved-view.create\tWRITE\tviews create\tcreate_saved_view\tPOST /api/v1/saved-views"));
        assertTrue(manifest.contains("saved-view.update\tWRITE\tviews update\tupdate_saved_view\tPUT /api/v1/saved-views/{id}"));
        assertTrue(manifest.contains("query.export\tREAD\texport query\texport_query\tPOST /api/v1/exports"));
        assertTrue(manifest.contains("saved-view.export\tREAD\texport view\texport_saved_view\tPOST /api/v1/saved-views/{id}/export"));

        assertTrue(openApi.contains("/queries/execute:"));
        assertTrue(openApi.contains("/saved-views:"));
        assertTrue(openApi.contains("/saved-views/{savedViewId}/versions:"));
        assertTrue(openApi.contains("/saved-views/{savedViewId}/export:"));
        assertTrue(openApi.contains("/exports:"));
        assertTrue(openApi.contains("additionalProperties: false"));
        assertTrue(openApi.contains("maximum: 500"));
        assertTrue(openApi.contains("maxLength: 16384"));
        assertFalse(openApi.toLowerCase().contains("sql query"));
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
        throw new IllegalStateException("cannot locate MORPHEUS repository root from " + Path.of("").toAbsolutePath());
    }
}
