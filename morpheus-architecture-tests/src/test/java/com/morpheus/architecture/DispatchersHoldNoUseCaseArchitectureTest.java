package com.morpheus.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A class that dispatches a CLI command or registers an MCP tool depends on no use case of the application, and on no
 * concrete store or provider (ADR-0149).
 *
 * <p>The dispatch classes are the classes of {@code com.morpheus.cli} itself, the launcher and the dispatcher, and of
 * {@code com.morpheus.mcp.server}, the server. A command or a tool is served by a class of the adapter's capability
 * package. A dispatch class may name of {@code com.morpheus.application..}, {@code com.morpheus.provider..} and
 * {@code com.morpheus.store..} only the types of its adapter's list: {@link #CLI_ALLOWANCE}, the operational runtime
 * the launcher hands on, the product version and the bound the help text prints; {@link #MCP_ALLOWANCE}, the recorder,
 * the product version and the integration types the server passes to the capability classes. A command or a tool
 * written in a dispatch class has a use case to call, and fails here naming the class and the type.</p>
 *
 * <p>The rule is a dependency, so it is read on the compiled classes; the source of each dispatch class is read too,
 * for every type of those packages it names by its qualified name, because a compile-time constant
 * ({@code QueryBudgets.MAX_EXPORT_ROWS} in the help text) is inlined by {@code javac} and leaves no dependency behind;
 * an import on demand of those packages is refused, since it would let a constant in under its simple name. Every name
 * of each list must still be used by a dispatch class of its adapter, so no list grows wider than what dispatching
 * does.</p>
 *
 * <p>{@link #MORPHEUS_CLI_DEBT} is a debt counter: {@code MorpheusCli} still serves {@code projects}, {@code sync} and
 * {@code analyze-change}, the commands {@code ingest-a-project-through-the-provider-port} rewires, and may use the
 * types they use and no other. It only shrinks.</p>
 */
class DispatchersHoldNoUseCaseArchitectureTest {

    private static final String CLI_DISPATCH = "com.morpheus.cli";
    private static final String MCP_DISPATCH = "com.morpheus.mcp.server";
    private static final String MORPHEUS_CLI = "com.morpheus.cli.MorpheusCli";
    private static final List<String> GUARDED = List.of(
            "com.morpheus.application", "com.morpheus.provider", "com.morpheus.store");
    private static final String APPLICATION = "com.morpheus.application";

    /** The types the launcher and the dispatcher of the CLI may name: what dispatching needs. */
    static final Set<String> CLI_ALLOWANCE = Set.of(
            APPLICATION + ".operability.LocalOperationalRuntime",
            APPLICATION + ".operability.OperationalLogFileException",
            APPLICATION + ".operability.OperationalLogMode",
            APPLICATION + ".operability.OperationalRecorder",
            APPLICATION + ".operability.OperationalRuntime",
            APPLICATION + ".product.ProductMetadata",
            APPLICATION + ".query.dsl.QueryBudgets");

    /** The types the MCP server may name: what registering the tools needs. */
    static final Set<String> MCP_ALLOWANCE = Set.of(
            APPLICATION + ".context.DisabledTechnicalContextProvider",
            APPLICATION + ".context.TechnicalContextProvider",
            APPLICATION + ".lifecycle.mutation.ChangeWriteCapabilityObservation",
            APPLICATION + ".lifecycle.mutation.ChangeWriteCapabilityResolver",
            APPLICATION + ".operability.OperationalRecorder",
            APPLICATION + ".product.ProductMetadata",
            APPLICATION + ".reference.ExternalReferenceResolverRegistry");

    /** The types {@code MorpheusCli} uses for the three commands it still serves. */
    static final Set<String> MORPHEUS_CLI_DEBT = Set.of(
            APPLICATION + ".analysis.ChangeAnalysisResult",
            APPLICATION + ".analysis.ChangeAnalysisService",
            APPLICATION + ".analysis.ChangeAnalysisSummary",
            APPLICATION + ".analysis.ChangeAnalysisWarning",
            APPLICATION + ".analysis.ChangeAnalysisWarningCode",
            APPLICATION + ".analysis.ProposedChangeSet",
            APPLICATION + ".analysis.compact.CompactChangeAnalysisViewService",
            APPLICATION + ".identity.PersistentEntityIdentityResolver",
            APPLICATION + ".ingestion.BoundedDiagnostics",
            APPLICATION + ".ingestion.ObservedProjectSnapshotPublisher",
            APPLICATION + ".ingestion.ProjectSnapshotImportResult",
            APPLICATION + ".ingestion.ProjectSnapshotImportService",
            APPLICATION + ".store.ProjectStoreEntry",
            APPLICATION + ".store.SpecificationKnowledgeStore",
            APPLICATION + ".sync.IncrementalSyncService",
            APPLICATION + ".sync.LocalSourceInventoryScanner",
            APPLICATION + ".sync.SourceInventory",
            APPLICATION + ".sync.SourceInventoryScanResult",
            APPLICATION + ".sync.SyncPlan",
            "com.morpheus.provider.openspec.OpenSpecProjectContentReader");

    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(CLI_DISPATCH, MCP_DISPATCH, "com.morpheus.application", "com.morpheus.provider",
                    "com.morpheus.store");

    private static final Pattern GUARDED_TYPE_NAME =
            Pattern.compile("\\bcom\\.morpheus\\.(?:application|provider|store)(?:\\.[a-z][a-z0-9]*)+\\.[A-Z]\\w*");
    private static final Pattern GUARDED_IMPORT_ON_DEMAND =
            Pattern.compile("(?m)^import\\s+(?:static\\s+)?com\\.morpheus\\.(?:application|provider|store)[\\w.]*\\.\\*\\s*;");
    private static final Pattern COMMENT = Pattern.compile("/\\*.*?\\*/|//[^\\n]*", Pattern.DOTALL);

    @Test
    void aDispatchClassDependsOnNoGuardedTypeBesideItsAllowance() {
        noClasses()
                .that(classesOf(CLI_DISPATCH).and(DescribedPredicate.not(morpheusCli())))
                .should().dependOnClassesThat(guardedTypesOutsideOf(CLI_ALLOWANCE))
                .because("a command is served by a capability class; the launcher has no use case to call")
                .check(PRODUCTION);
        noClasses()
                .that(morpheusCli())
                .should().dependOnClassesThat(guardedTypesOutsideOf(union(CLI_ALLOWANCE, MORPHEUS_CLI_DEBT)))
                .because("MorpheusCli serves projects, sync and analyze-change until their ingestion is rewired, with "
                        + "the types they use today and no other")
                .check(PRODUCTION);
        noClasses()
                .that(classesOf(MCP_DISPATCH))
                .should().dependOnClassesThat(guardedTypesOutsideOf(MCP_ALLOWANCE))
                .because("a tool is served by a capability class; the server registers tools and serves none")
                .check(PRODUCTION);
    }

    @Test
    void aDispatchSourceNamesNoGuardedTypeBesideItsAllowance() {
        Map<String, List<String>> offenders = new TreeMap<>();
        for (String dispatchClass : dispatchClassNames()) {
            Set<String> permitted = permitted(dispatchClass);
            String source = codeOf(dispatchClass);
            List<String> refused = new ArrayList<>();
            for (String named : guardedTypesNamed(source)) {
                if (!permitted.contains(named)) {
                    refused.add(named);
                }
            }
            Matcher onDemand = GUARDED_IMPORT_ON_DEMAND.matcher(source);
            while (onDemand.find()) {
                refused.add(onDemand.group().trim());
            }
            if (!refused.isEmpty()) {
                offenders.put(dispatchClass, refused);
            }
        }
        assertEquals(Map.of(), offenders, "a dispatch class names a type outside its allowance, or imports one of the "
                + "guarded packages on demand: a constant read this way leaves no dependency for the bytecode rule");
    }

    @Test
    void everyNameOfEachListIsStillUsed() {
        Set<String> usedByTheCli = new TreeSet<>();
        Set<String> usedByMorpheusCli = new TreeSet<>();
        Set<String> usedByTheServer = new TreeSet<>();
        for (JavaClass origin : PRODUCTION) {
            if (!isDispatchClass(origin)) {
                continue;
            }
            usedBy(topLevelName(origin), usedByTheCli, usedByMorpheusCli, usedByTheServer)
                    .addAll(guardedDependencies(origin));
        }
        for (String dispatchClass : dispatchClassNames()) {
            usedBy(dispatchClass, usedByTheCli, usedByMorpheusCli, usedByTheServer)
                    .addAll(guardedTypesNamed(codeOf(dispatchClass)));
        }
        assertEquals(Set.of(), unused(CLI_ALLOWANCE, union(usedByTheCli, usedByMorpheusCli)),
                "names of the CLI allowance no dispatch class of the CLI uses: remove them");
        assertEquals(Set.of(), unused(MCP_ALLOWANCE, usedByTheServer),
                "names of the MCP allowance the server does not use: remove them");
        assertEquals(Set.of(), unused(MORPHEUS_CLI_DEBT, usedByMorpheusCli),
                "names of the MorpheusCli debt it no longer uses: remove them");
    }

    private static Set<String> usedBy(String dispatchClass, Set<String> cli, Set<String> morpheusCli, Set<String> server) {
        if (dispatchClass.equals(MORPHEUS_CLI)) {
            return morpheusCli;
        }
        return dispatchClass.startsWith(MCP_DISPATCH + ".") ? server : cli;
    }

    private static Set<String> permitted(String dispatchClass) {
        if (dispatchClass.equals(MORPHEUS_CLI)) {
            return union(CLI_ALLOWANCE, MORPHEUS_CLI_DEBT);
        }
        return dispatchClass.startsWith(MCP_DISPATCH + ".") ? MCP_ALLOWANCE : CLI_ALLOWANCE;
    }

    private static Set<String> guardedDependencies(JavaClass origin) {
        Set<String> names = new TreeSet<>();
        origin.getDirectDependenciesFromSelf().stream()
                .map(dependency -> topLevelName(dependency.getTargetClass().getBaseComponentType()))
                .filter(DispatchersHoldNoUseCaseArchitectureTest::isGuarded)
                .forEach(names::add);
        return names;
    }

    private static Set<String> unused(Set<String> list, Set<String> used) {
        Set<String> unused = new TreeSet<>(list);
        unused.removeAll(used);
        return unused;
    }

    private static boolean isDispatchClass(JavaClass javaClass) {
        return javaClass.getPackageName().equals(CLI_DISPATCH) || javaClass.getPackageName().equals(MCP_DISPATCH);
    }

    private static boolean isGuarded(String name) {
        return GUARDED.stream().anyMatch(prefix -> name.startsWith(prefix + "."));
    }

    private static DescribedPredicate<JavaClass> classesOf(String packageName) {
        return DescribedPredicate.describe("reside in " + packageName,
                javaClass -> javaClass.getPackageName().equals(packageName));
    }

    private static DescribedPredicate<JavaClass> morpheusCli() {
        return DescribedPredicate.describe("are " + MORPHEUS_CLI, javaClass -> topLevelName(javaClass).equals(MORPHEUS_CLI));
    }

    private static DescribedPredicate<JavaClass> guardedTypesOutsideOf(Set<String> permitted) {
        return DescribedPredicate.describe("are types of " + GUARDED + " outside " + new TreeSet<>(permitted),
                target -> isGuarded(topLevelName(target)) && !permitted.contains(topLevelName(target)));
    }

    /** The top-level dispatch classes, by fully qualified name. */
    private static Set<String> dispatchClassNames() {
        Set<String> names = new TreeSet<>();
        for (JavaClass javaClass : PRODUCTION) {
            if (isDispatchClass(javaClass)) {
                names.add(topLevelName(javaClass));
            }
        }
        assertTrue(names.size() >= 4, "too few dispatch classes were imported to hold the rule: " + names);
        return names;
    }

    /** The source of {@code dispatchClass} without its comments. */
    private static String codeOf(String dispatchClass) {
        String module = dispatchClass.startsWith(CLI_DISPATCH + ".") ? "morpheus-cli" : "morpheus-mcp";
        String source = ProductionSources.readMain(module, dispatchClass.substring(dispatchClass.lastIndexOf('.') + 1));
        return COMMENT.matcher(source).replaceAll("");
    }

    /** The guarded types a source names by their qualified name. */
    private static Set<String> guardedTypesNamed(String code) {
        Set<String> named = new TreeSet<>();
        Matcher matcher = GUARDED_TYPE_NAME.matcher(code);
        while (matcher.find()) {
            named.add(matcher.group());
        }
        return named;
    }

    private static Set<String> union(Set<String> first, Set<String> second) {
        Set<String> union = new TreeSet<>(first);
        union.addAll(second);
        return union;
    }

    private static String topLevelName(JavaClass javaClass) {
        String name = javaClass.getName();
        int nested = name.indexOf('$');
        return nested < 0 ? name : name.substring(0, nested);
    }
}
