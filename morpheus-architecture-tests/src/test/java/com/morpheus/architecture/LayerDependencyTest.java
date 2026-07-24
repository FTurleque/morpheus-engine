package com.morpheus.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class LayerDependencyTest {

    private final JavaClasses classes = new ClassFileImporter().importPackages("com.morpheus");

    @Test
    void domainMustNotDependOnAdapters() {
        noClasses()
                .that().resideInAPackage("com.morpheus.domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.morpheus.provider..",
                        "com.morpheus.store..",
                        "com.morpheus.cli..",
                        "com.morpheus.mcp..",
                        "com.morpheus.api..",
                        "com.morpheus.integration..")
                .because("the MORPHEUS domain owns its model and adapters depend inward")
                .check(classes);
    }

    @Test
    void applicationMustNotDependOnAdapters() {
        noClasses()
                .that().resideInAPackage("com.morpheus.application..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.morpheus.provider..",
                        "com.morpheus.store..",
                        "com.morpheus.cli..",
                        "com.morpheus.mcp..",
                        "com.morpheus.api..",
                        "com.morpheus.integration..")
                .because("application services define use cases and ports without depending on adapters")
                .check(classes);
    }

    @Test
    void httpApiMustRemainSiblingOfCliAndMcp() {
        noClasses()
                .that().resideInAPackage("com.morpheus.api..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.morpheus.cli..",
                        "com.morpheus.mcp..",
                        "com.morpheus.integration..")
                .because("the M11/M12 HTTP adapter must reuse application contracts rather than other adapters")
                .check(classes);
    }

    @Test
    void minosIntegrationMustNotDependOnOuterMorpheusAdapters() {
        noClasses()
                .that().resideInAPackage("com.morpheus.integration.minos..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.morpheus.cli..",
                        "com.morpheus.mcp..",
                        "com.morpheus.api..",
                        "com.morpheus.store..")
                .because("MINOS integration implements application ports and is composed only from the launcher")
                .check(classes);
    }
}
