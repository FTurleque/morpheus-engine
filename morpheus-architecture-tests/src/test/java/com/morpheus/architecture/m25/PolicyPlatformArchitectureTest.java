package com.morpheus.architecture.m25;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PolicyPlatformArchitectureTest {

    @Test
    void applicationPolicyPlatformDoesNotDependOnAdaptersProvidersOrConcreteStores() {
        var classes = new ClassFileImporter().importPackages("com.morpheus.application.policy");
        noClasses()
                .that().resideInAnyPackage("..application.policy..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..cli..", "..mcp..", "..api..",
                        "..store.memory..", "..store.sqlite..",
                        "..provider.openspec..", "..provider.markdown..", "..provider.sdk..")
                .check(classes);
    }

    @Test
    void policyModelContainsNoScriptSqlOrReflectionExecutionContract() throws IOException {
        Path root = repositoryRoot();
        Path policyRoot = root.resolve("morpheus-application/src/main/java/com/morpheus/application/policy");
        StringBuilder source = new StringBuilder();
        try (var files = Files.walk(policyRoot)) {
            files.filter(path -> path.toString().endsWith(".java"))
                    .sorted()
                    .forEach(path -> {
                        try {
                            source.append(Files.readString(path)).append('\n');
                        } catch (IOException exception) {
                            throw new IllegalStateException(exception);
                        }
                    });
        }
        String lower = source.toString().toLowerCase();
        assertFalse(lower.contains("scriptengine"));
        assertFalse(lower.contains("class.forname"));
        assertFalse(lower.contains("runtime.getruntime"));
        assertFalse(lower.contains("processbuilder"));
        assertFalse(lower.contains("select * from"));
        assertFalse(lower.contains("executequery("));
    }

    @Test
    void publicManifestAndOpenApiExposeSameM25IntentFamilies() throws IOException {
        Path root = repositoryRoot();
        String manifest = Files.readString(root.resolve("contracts/public-surfaces.tsv"));
        String openApi = Files.readString(root.resolve("docs/openapi/morpheus-v1-policy-m25.yaml"));

        assertTrue(manifest.contains("policy.pack.create\tWRITE\tpolicy pack create\tcreate_policy_pack\tPOST /api/v1/policy-packs"));
        assertTrue(manifest.contains("policy.pack.activate\tWRITE\tpolicy activate\tactivate_policy_pack\tPOST /api/v1/policy-packs/{id}/activate"));
        assertTrue(manifest.contains("policy.override.put\tWRITE\tpolicy override put\tput_policy_override\tPUT /api/v1/policy-packs/{id}/overrides/{ruleId}"));
        assertTrue(manifest.contains("policy.evaluate\tREAD\tpolicy evaluate\tevaluate_policies\tPOST /api/v1/policies/evaluate"));
        assertTrue(manifest.contains("policy.dry-run\tREAD\tpolicy dry-run\tdry_run_policy_pack\tPOST /api/v1/policies/dry-run"));
        assertTrue(manifest.contains("policy.audit\tREAD\tpolicy audit\tget_policy_audit\tGET /api/v1/policy-packs/{id}/audit"));

        assertTrue(openApi.contains("/policy-packs:"));
        assertTrue(openApi.contains("/policy-packs/{policyPackId}/versions:"));
        assertTrue(openApi.contains("/policy-packs/{policyPackId}/activate:"));
        assertTrue(openApi.contains("/policy-packs/{policyPackId}/overrides/{ruleId}:"));
        assertTrue(openApi.contains("/policies/evaluate:"));
        assertTrue(openApi.contains("/policies/dry-run:"));
        assertTrue(openApi.contains("additionalProperties: false"));
        assertTrue(openApi.contains("maximum: 128"));
        assertFalse(openApi.toLowerCase().contains("sql passthrough"));
        assertFalse(openApi.toLowerCase().contains("script source"));
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