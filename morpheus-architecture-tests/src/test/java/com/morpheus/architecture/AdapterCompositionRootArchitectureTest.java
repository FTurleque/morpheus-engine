package com.morpheus.architecture;

import com.morpheus.domain.provider.ProviderProbeResult;
import com.morpheus.domain.requirement.RequirementDelta;
import com.morpheus.domain.scenario.Scenario;
import com.morpheus.domain.source.SourceLocator;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.TreeSet;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAnyPackage;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideOutsideOfPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The HTTP and MCP adapters are composition roots of their own runtime, through a named set of classes that may only
 * shrink (ADR-0109).
 *
 * <p>Measured with ArchUnit on 8 October 2026: 32 classes of {@code com.morpheus.api} and {@code com.morpheus.mcp}
 * depend directly on {@code com.morpheus.store..} or {@code com.morpheus.provider..}. They are the named roots below.
 * A new class reaching a concrete store or provider is refused until somebody names it, and a named class that no
 * longer reaches one is refused until somebody removes it, so the list can shrink and cannot silently grow.</p>
 */
class AdapterCompositionRootArchitectureTest {
    private static final String[] TRANSPORT_ADAPTERS = {"com.morpheus.api..", "com.morpheus.mcp.."};
    private static final String[] CONCRETE_ADAPTERS = {"com.morpheus.store..", "com.morpheus.provider.."};

    /** The classes allowed to build a runtime from concrete stores and providers (ADR-0109, decision 1). */
    static final Set<String> COMPOSITION_ROOTS = Set.of(
            "com.morpheus.api.ApiRuntime",
            "com.morpheus.api.MorpheusAugmentedContextApiService",
            "com.morpheus.api.MorpheusChangeQueryApiService",
            "com.morpheus.api.MorpheusControlledLifecycleApiService",
            "com.morpheus.api.MorpheusDiagnosticsApiService",
            "com.morpheus.api.MorpheusExternalReferenceApiService",
            "com.morpheus.api.MorpheusHistoryApiService",
            "com.morpheus.api.MorpheusJarvisOrchestrationApiService",
            "com.morpheus.api.MorpheusLocalHttpServerBootstrap",
            "com.morpheus.api.MorpheusOperabilityApiService",
            "com.morpheus.api.MorpheusPolicyApiService",
            "com.morpheus.api.MorpheusPolicyManagementHttpRoutes",
            "com.morpheus.api.MorpheusPortfolioApiService",
            "com.morpheus.api.MorpheusProjectRegistryApiService",
            "com.morpheus.api.MorpheusProjectSyncApiService",
            "com.morpheus.api.MorpheusQueryApiService",
            "com.morpheus.api.MorpheusRemoteHttpServer",
            "com.morpheus.api.MorpheusRemoteHttpServerBootstrap",
            "com.morpheus.api.MorpheusRequirementQueryApiService",
            "com.morpheus.api.MorpheusSpecificationQueryApiService",
            "com.morpheus.mcp.MorpheusAugmentedContextMcpTools",
            "com.morpheus.mcp.MorpheusCompositionMcpTools",
            "com.morpheus.mcp.MorpheusControlledLifecycleMcpTools",
            "com.morpheus.mcp.MorpheusExternalReferenceMcpTools",
            "com.morpheus.mcp.MorpheusJarvisOrchestrationMcpTools",
            "com.morpheus.mcp.MorpheusMcpRuntime",
            "com.morpheus.mcp.MorpheusMcpServer",
            "com.morpheus.mcp.MorpheusMcpToolService",
            "com.morpheus.mcp.MorpheusPolicyMcpManagementTools",
            "com.morpheus.mcp.MorpheusPolicyMcpTools",
            "com.morpheus.mcp.MorpheusPortfolioMcpTools",
            "com.morpheus.mcp.MorpheusQueryMcpTools");

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.morpheus");

    @Test
    void onlyTheNamedCompositionRootsReachAConcreteStoreOrProvider() {
        noClasses()
                .that().resideInAnyPackage(TRANSPORT_ADAPTERS)
                .and(DescribedPredicate.not(namedCompositionRoot()))
                .should().dependOnClassesThat().resideInAnyPackage(CONCRETE_ADAPTERS)
                .because("only the composition roots named by ADR-0109 may build a runtime from a concrete store or "
                        + "provider; every other transport class depends on application ports")
                .check(classes);
    }

    @Test
    void everyNamedCompositionRootStillReachesAConcreteStoreOrProvider() {
        assertEquals(new TreeSet<>(COMPOSITION_ROOTS), rootsReachingConcreteAdapters(),
                "the named composition roots must be exactly the transport classes that reach a concrete store or "
                        + "provider: remove a root that no longer does, so the list only shrinks");
    }

    @Test
    void theMcpTransportDependsOnNoOtherMorpheusPackage() {
        noClasses()
                .that().resideInAPackage("com.morpheus.integration.mcp..")
                .should().dependOnClassesThat(resideInAPackage("com.morpheus..")
                        .and(resideOutsideOfPackage("com.morpheus.integration.mcp..")))
                .because("the bounded STDIO transport is shared by the MCP server and the MINOS and NEXUS clients and "
                        + "knows no MORPHEUS type (ADR-0109, decision 2)")
                .check(classes);
    }

    /**
     * The two cycles measured on 8 October 2026 are named by the exact pair of types that closes each, so any other
     * dependency between the same packages, or any new cycle, still fails. ArchUnit reported them one at a time: the
     * second appeared only once the first was ignored.
     */
    @Test
    void domainPackagesAreFreeOfCyclesBesideTheTwoNamedOnes() {
        slices().matching("com.morpheus.domain.(*)..")
                .should().beFreeOfCycles()
                // A provider probe result carries the locator of the source it probed.
                .ignoreDependency(
                        JavaClass.Predicates.type(ProviderProbeResult.class),
                        JavaClass.Predicates.type(SourceLocator.class))
                // A requirement delta carries its scenarios, and a scenario names its requirement.
                .ignoreDependency(
                        JavaClass.Predicates.type(RequirementDelta.class),
                        JavaClass.Predicates.type(Scenario.class))
                .because("domain packages form no cycle beside the two named by ADR-0109, decision 3")
                .check(classes);
    }

    private Set<String> rootsReachingConcreteAdapters() {
        Set<String> roots = new TreeSet<>();
        for (JavaClass origin : classes) {
            if (!resideInAnyPackage(TRANSPORT_ADAPTERS).test(origin)) continue;
            boolean reachesConcrete = origin.getDirectDependenciesFromSelf().stream()
                    .anyMatch(dependency -> resideInAnyPackage(CONCRETE_ADAPTERS).test(dependency.getTargetClass()));
            if (reachesConcrete) roots.add(topLevelName(origin));
        }
        return roots;
    }

    private static DescribedPredicate<JavaClass> namedCompositionRoot() {
        return DescribedPredicate.describe("a composition root named by ADR-0109",
                javaClass -> COMPOSITION_ROOTS.contains(topLevelName(javaClass)));
    }

    /** A nested or anonymous class belongs to the root that declares it. */
    private static String topLevelName(JavaClass javaClass) {
        String name = javaClass.getName();
        int nested = name.indexOf('$');
        return nested < 0 ? name : name.substring(0, nested);
    }
}
