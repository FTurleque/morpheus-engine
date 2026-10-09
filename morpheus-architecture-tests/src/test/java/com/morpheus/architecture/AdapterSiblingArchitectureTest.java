package com.morpheus.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Dependency rules whose decision is written in an ADR or in the project rules, with the provider SDK, the
 * providers, the stores, the MINOS and NEXUS integrations and the MCP adapter as subjects; elsewhere they appear only
 * as forbidden targets. Today the POMs already prevent each of these edges, so these rules refuse a future dependency
 * added to a POM by name, instead of accepting it because the compiler allows it.
 *
 * <p>Only production classes are imported: {@code PublicSurfaceManifestCoversEveryServedToolTest} lives in the
 * production package {@code com.morpheus.mcp} and depends on this module, which is not an adapter dependency.</p>
 *
 * <p>The SDK lives in {@code com.morpheus.sdk.provider}, not under {@code com.morpheus.provider}: a pattern spelled
 * {@code ..provider.sdk..} matches nothing. {@link #everyPackageTheseRulesNameResolvesToImportedClasses()} refuses
 * that kind of vacant pattern for every package these rules name.</p>
 */
class AdapterSiblingArchitectureTest {

    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.morpheus");

    private static final String DOMAIN = "com.morpheus.domain..";
    private static final String APPLICATION = "com.morpheus.application..";
    private static final String SDK = "com.morpheus.sdk..";
    private static final String PROVIDERS = "com.morpheus.provider..";
    private static final String OPENSPEC = "com.morpheus.provider.openspec..";
    private static final String MARKDOWN = "com.morpheus.provider.markdown..";
    private static final String SYNTHETIC = "com.morpheus.provider.synthetic..";
    private static final String STORES = "com.morpheus.store..";
    private static final String INTEGRATIONS = "com.morpheus.integration..";
    private static final String MINOS = "com.morpheus.integration.minos..";
    private static final String NEXUS = "com.morpheus.integration.nexus..";
    private static final String MCP = "com.morpheus.mcp..";
    private static final String API = "com.morpheus.api..";
    private static final String CLI = "com.morpheus.cli..";

    private static final List<String> NAMED = List.of(
            DOMAIN, APPLICATION, SDK, PROVIDERS, OPENSPEC, MARKDOWN, SYNTHETIC, STORES, INTEGRATIONS, MINOS, NEXUS,
            MCP, API, CLI);

    @Test
    void domainAndApplicationDoNotDependOnTheProviderSdk() {
        noClasses()
                .that().resideInAnyPackage(DOMAIN, APPLICATION)
                .should().dependOnClassesThat().resideInAnyPackage(SDK)
                .because("ADR-0090: domain and application never depend on the provider SDK")
                .check(PRODUCTION);
    }

    @Test
    void builtInProvidersDoNotDependOnTheProviderSdk() {
        noClasses()
                .that().resideInAnyPackage(OPENSPEC, MARKDOWN, SYNTHETIC)
                .should().dependOnClassesThat().resideInAnyPackage(SDK)
                .because("ADR-0028: the built-in providers do not depend on the SDK")
                .check(PRODUCTION);
    }

    @Test
    void providersDoNotDependOnStoresTransportsOrIntegrations() {
        noClasses()
                .that().resideInAPackage(PROVIDERS)
                .should().dependOnClassesThat().resideInAnyPackage(STORES, INTEGRATIONS, MCP, API, CLI)
                .because("adapters are siblings and depend inward only")
                .check(PRODUCTION);
    }

    @Test
    void providersDoNotDependOnEachOther() {
        slices().matching("com.morpheus.provider.(*)..")
                .should().notDependOnEachOther()
                .because("adapters are siblings: a provider never reads through another provider")
                .check(PRODUCTION);
    }

    @Test
    void storesDoNotDependOnProvidersTransportsOrIntegrations() {
        noClasses()
                .that().resideInAPackage(STORES)
                .should().dependOnClassesThat().resideInAnyPackage(PROVIDERS, SDK, INTEGRATIONS, MCP, API, CLI)
                .because("adapters are siblings and depend inward only")
                .check(PRODUCTION);
    }

    @Test
    void storesDoNotDependOnEachOther() {
        slices().matching("com.morpheus.store.(*)..")
                .should().notDependOnEachOther()
                .because("adapters are siblings: the memory and SQLite stores implement the same ports independently")
                .check(PRODUCTION);
    }

    @Test
    void minosIntegrationDoesNotDependOnNexusProvidersOrStores() {
        noClasses()
                .that().resideInAPackage(MINOS)
                .should().dependOnClassesThat().resideInAnyPackage(NEXUS, PROVIDERS, SDK, STORES)
                .because("adapters are siblings and depend inward only")
                .check(PRODUCTION);
    }

    @Test
    void nexusIntegrationDoesNotDependOnMinosProvidersOrStores() {
        noClasses()
                .that().resideInAPackage(NEXUS)
                .should().dependOnClassesThat().resideInAnyPackage(MINOS, PROVIDERS, SDK, STORES)
                .because("adapters are siblings and depend inward only")
                .check(PRODUCTION);
    }

    @Test
    void mcpAdapterDoesNotDependOnTheHttpAdapterOrTheCli() {
        noClasses()
                .that().resideInAPackage(MCP)
                .should().dependOnClassesThat().resideInAnyPackage(API, CLI)
                .because("ADR-0107: morpheus-mcp and morpheus-api are sibling adapters, neither calls the other")
                .check(PRODUCTION);
    }

    @Test
    void everyPackageTheseRulesNameResolvesToImportedClasses() {
        Set<String> imported = ImportedClasspathCompletenessTest.importedPackages();
        Set<String> vacant = new TreeSet<>();
        for (String pattern : NAMED) {
            String prefix = pattern.substring(0, pattern.length() - 2);
            boolean resolved = imported.stream()
                    .anyMatch(name -> name.equals(prefix) || name.startsWith(prefix + "."));
            if (!resolved) {
                vacant.add(pattern);
            }
        }
        assertEquals(Set.of(), vacant, "a rule naming a package that matches no imported class forbids nothing");
    }
}
