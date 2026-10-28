package com.morpheus.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Dependency rules whose decision is written in an ADR or in the project rules, for the package families of
 * {@link #SUBJECTS}; elsewhere these families appear only as forbidden targets. Today the POMs already prevent each of
 * these edges, so these rules refuse a future dependency added to a POM by name, instead of accepting it because the
 * compiler allows it. {@link #everyDeclaredSubjectIsTheSubjectOfARuleAndNoOther()} compares {@link #SUBJECTS} to the
 * subjects of {@link #RULES}: the list cannot announce a family that no rule judges.
 *
 * <p>Only production classes are imported: {@code PublicSurfaceManifestCoversEveryServedToolTest} lives in the
 * production package {@code com.morpheus.mcp} and depends on this module, which is not an adapter dependency. The rule
 * on the provider SDK therefore judges {@code morpheus-provider-sdk} only: {@code morpheus-provider-testkit} shares its
 * package but is not on the classpath of this module, and its boundary is its POM.</p>
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
    private static final String TRANSPORT = "com.morpheus.transport..";
    private static final String MCP = "com.morpheus.mcp..";
    private static final String API = "com.morpheus.api..";
    private static final String CLI = "com.morpheus.cli..";

    private static final List<String> NAMED = List.of(
            DOMAIN, APPLICATION, SDK, PROVIDERS, OPENSPEC, MARKDOWN, SYNTHETIC, STORES, INTEGRATIONS, MINOS, NEXUS,
            TRANSPORT, MCP, API, CLI);

    private static final List<String> SUBJECTS = List.of(
            DOMAIN, APPLICATION, SDK, PROVIDERS, OPENSPEC, MARKDOWN, SYNTHETIC, STORES, MINOS, NEXUS, MCP);

    private static final String SDK_SOURCES = "morpheus-provider-sdk/src/main/java";

    private static final SiblingRule CORE_IGNORES_THE_SDK = inward(List.of(DOMAIN, APPLICATION), List.of(SDK),
            "ADR-0129: domain and application never depend on the provider SDK");

    private static final SiblingRule BUILT_IN_PROVIDERS_IGNORE_THE_SDK = inward(List.of(OPENSPEC, MARKDOWN, SYNTHETIC),
            List.of(SDK), "ADR-0028: the built-in providers do not depend on the SDK");

    private static final SiblingRule SDK_DEPENDS_ON_NO_ADAPTER = inward(List.of(SDK),
            List.of(STORES, PROVIDERS, INTEGRATIONS, TRANSPORT, MCP, API, CLI),
            "ADR-0129: the provider SDK is compiled against by plugin authors and depends inward only, on the "
                    + "provider-neutral contracts of MORPHEUS; an adapter it reached would be published with it");

    private static final SiblingRule PROVIDERS_DEPEND_INWARD = inward(List.of(PROVIDERS),
            List.of(STORES, INTEGRATIONS, TRANSPORT, MCP, API, CLI), "adapters are siblings and depend inward only");

    private static final SiblingRule PROVIDERS_ARE_SIBLINGS = siblings(PROVIDERS,
            "adapters are siblings: a provider never reads through another provider");

    private static final SiblingRule STORES_DEPEND_INWARD = inward(List.of(STORES),
            List.of(PROVIDERS, SDK, INTEGRATIONS, TRANSPORT, MCP, API, CLI),
            "adapters are siblings and depend inward only");

    private static final SiblingRule STORES_ARE_SIBLINGS = siblings(STORES,
            "adapters are siblings: the memory and SQLite stores implement the same ports independently");

    private static final SiblingRule MINOS_DEPENDS_INWARD = inward(List.of(MINOS),
            List.of(NEXUS, PROVIDERS, SDK, STORES), "adapters are siblings and depend inward only");

    private static final SiblingRule NEXUS_DEPENDS_INWARD = inward(List.of(NEXUS),
            List.of(MINOS, PROVIDERS, SDK, STORES), "adapters are siblings and depend inward only");

    private static final SiblingRule MCP_IGNORES_HTTP_AND_CLI = inward(List.of(MCP), List.of(API, CLI),
            "ADR-0151: morpheus-mcp and morpheus-api are sibling adapters, neither calls the other");

    private static final List<SiblingRule> RULES = List.of(
            CORE_IGNORES_THE_SDK, BUILT_IN_PROVIDERS_IGNORE_THE_SDK, SDK_DEPENDS_ON_NO_ADAPTER,
            PROVIDERS_DEPEND_INWARD, PROVIDERS_ARE_SIBLINGS, STORES_DEPEND_INWARD, STORES_ARE_SIBLINGS,
            MINOS_DEPENDS_INWARD, NEXUS_DEPENDS_INWARD, MCP_IGNORES_HTTP_AND_CLI);

    @Test
    void domainAndApplicationDoNotDependOnTheProviderSdk() {
        CORE_IGNORES_THE_SDK.rule().check(PRODUCTION);
    }

    @Test
    void builtInProvidersDoNotDependOnTheProviderSdk() {
        BUILT_IN_PROVIDERS_IGNORE_THE_SDK.rule().check(PRODUCTION);
    }

    @Test
    void theProviderSdkDependsOnNoAdapter() {
        SDK_DEPENDS_ON_NO_ADAPTER.rule().check(PRODUCTION);
    }

    @Test
    void noProviderSdkSourceNamesAnAdapterPackage() throws IOException {
        Path root = RepositoryRoot.locate();
        List<String> forbidden = SDK_DEPENDS_ON_NO_ADAPTER.forbidden().stream()
                .map(pattern -> pattern.substring(0, pattern.length() - 1))
                .toList();
        List<String> offenders = new ArrayList<>();
        int scanned = 0;
        try (Stream<Path> tree = Files.walk(root.resolve(SDK_SOURCES))) {
            for (Path source : tree.filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
                scanned++;
                String text = Files.readString(source);
                for (String prefix : forbidden) {
                    if (text.contains(prefix)) {
                        offenders.add(root.relativize(source).toString().replace('\\', '/') + " names " + prefix);
                    }
                }
            }
        }

        assertTrue(scanned > 0, "no source scanned under " + SDK_SOURCES);
        assertEquals(List.of(), offenders, "the provider SDK names an adapter package: a compile-time constant of an "
                + "adapter read this way leaves no dependency for theProviderSdkDependsOnNoAdapter");
    }

    @Test
    void providersDoNotDependOnStoresTransportsOrIntegrations() {
        PROVIDERS_DEPEND_INWARD.rule().check(PRODUCTION);
    }

    @Test
    void providersDoNotDependOnEachOther() {
        PROVIDERS_ARE_SIBLINGS.rule().check(PRODUCTION);
    }

    @Test
    void storesDoNotDependOnProvidersTransportsOrIntegrations() {
        STORES_DEPEND_INWARD.rule().check(PRODUCTION);
    }

    @Test
    void storesDoNotDependOnEachOther() {
        STORES_ARE_SIBLINGS.rule().check(PRODUCTION);
    }

    @Test
    void minosIntegrationDoesNotDependOnNexusProvidersOrStores() {
        MINOS_DEPENDS_INWARD.rule().check(PRODUCTION);
    }

    @Test
    void nexusIntegrationDoesNotDependOnMinosProvidersOrStores() {
        NEXUS_DEPENDS_INWARD.rule().check(PRODUCTION);
    }

    @Test
    void mcpAdapterDoesNotDependOnTheHttpAdapterOrTheCli() {
        MCP_IGNORES_HTTP_AND_CLI.rule().check(PRODUCTION);
    }

    @Test
    void everyDeclaredSubjectIsTheSubjectOfARuleAndNoOther() {
        Set<String> judged = new TreeSet<>();
        RULES.forEach(rule -> judged.addAll(rule.subjects()));
        Set<String> unjudged = new TreeSet<>(SUBJECTS);
        unjudged.removeAll(judged);
        Set<String> undeclared = new TreeSet<>(judged);
        undeclared.removeAll(SUBJECTS);

        assertEquals(Set.of(), unjudged, "a family declared in SUBJECTS is the subject of no rule of RULES");
        assertEquals(Set.of(), undeclared, "a rule of RULES judges a family that SUBJECTS does not declare");
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

    private static SiblingRule inward(List<String> subjects, List<String> forbidden, String because) {
        ArchRule rule = noClasses()
                .that().resideInAnyPackage(subjects.toArray(String[]::new))
                .should().dependOnClassesThat().resideInAnyPackage(forbidden.toArray(String[]::new))
                .because(because);
        return new SiblingRule(subjects, forbidden, rule);
    }

    private static SiblingRule siblings(String family, String because) {
        String parent = family.substring(0, family.length() - 2);
        ArchRule rule = slices().matching(parent + ".(*)..")
                .should().notDependOnEachOther()
                .because(because);
        return new SiblingRule(List.of(family), List.of(family), rule);
    }

    private record SiblingRule(List<String> subjects, List<String> forbidden, ArchRule rule) {
    }
}
