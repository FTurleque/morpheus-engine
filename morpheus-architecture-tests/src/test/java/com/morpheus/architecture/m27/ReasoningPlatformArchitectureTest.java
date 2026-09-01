package com.morpheus.architecture.m27;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReasoningPlatformArchitectureTest {

    @Test
    void applicationReasoningDoesNotDependOnAdaptersConcreteStoresOrMutationPackages() {
        var classes = new ClassFileImporter().importPackages("com.morpheus.application.reasoning");
        noClasses()
                .that().resideInAnyPackage("..application.reasoning..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..cli..", "..mcp..", "..api..",
                        "..store.memory..", "..store.sqlite..",
                        "..provider..", "..application.lifecycle.mutation..")
                .check(classes);
    }

    @Test
    void reasoningCoreContainsNoTransportPersistenceOrProcessExecution() throws IOException {
        Path sourceRoot = repositoryRoot().resolve(
                "morpheus-application/src/main/java/com/morpheus/application/reasoning");
        StringBuilder source = new StringBuilder();
        try (var files = Files.walk(sourceRoot)) {
            files.filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .forEach(path -> append(source, path));
        }
        String lower = source.toString().toLowerCase();
        assertFalse(lower.contains("java.net.http"));
        assertFalse(lower.contains("tools.jackson"));
        assertFalse(lower.contains("java.sql"));
        assertFalse(lower.contains("processbuilder"));
        assertFalse(lower.contains("runtime.getruntime"));
        assertFalse(lower.contains("com.morpheus.application.store"));
        assertFalse(lower.contains("com.morpheus.application.lifecycle.mutation"));
    }

    @Test
    void publicManifestAndOpenApiExposeSameReadOnlyM27IntentFamilies() throws IOException {
        Path root = repositoryRoot();
        String manifest = Files.readString(root.resolve("contracts/public-surfaces.tsv"));
        String openApi = Files.readString(root.resolve("docs/openapi/morpheus-v1-reasoning-m27.yaml"));

        assertTrue(manifest.contains(
                "reasoning.adapters\tREAD\treason adapters\tlist_reasoning_adapters\tGET /api/v1/reasoning/adapters"));
        assertTrue(manifest.contains(
                "reasoning.analyze\tREAD\treason analyze\treason_with_evidence\tPOST /api/v1/reasoning/analyze"));
        assertTrue(openApi.contains("/reasoning/adapters:"));
        assertTrue(openApi.contains("/reasoning/analyze:"));
        assertTrue(openApi.contains("additionalProperties: false"));
        assertTrue(openApi.contains("maxItems: 256"));
        assertTrue(openApi.contains("maximum: 1"));
        assertTrue(openApi.contains("const: false"));
        assertFalse(openApi.toLowerCase().contains("apply mutation"));
        assertFalse(openApi.toLowerCase().contains("sql passthrough"));
    }

    @Test
    void remoteFacadeRegistersReasoningAnalysisAsExplicitReadOnlyPost() throws IOException {
        String source = Files.readString(repositoryRoot().resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusRemoteRoutePolicy.java"));
        assertTrue(source.contains(
                "route(\"reasoning/analyze\", Map.of(POST, MorpheusRemoteRole.READ))"));
        assertTrue(source.contains(
                "route(\"reasoning/adapters\", Map.of(GET, MorpheusRemoteRole.READ))"));
        assertFalse(source.contains("method.equals(\"GET\") || method.equals(\"HEAD\")"),
                "M27 reasoning routes must remain explicit entries in the fail-closed remote registry");
    }

    private static void append(StringBuilder target, Path path) {
        try {
            target.append(Files.readString(path)).append('\n');
        } catch (IOException failure) {
            throw new IllegalStateException(failure);
        }
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
