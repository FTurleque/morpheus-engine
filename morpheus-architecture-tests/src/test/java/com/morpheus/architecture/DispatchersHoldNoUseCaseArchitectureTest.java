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
 * A class that dispatches a CLI command or registers an MCP tool depends on no use case of the application.
 *
 * <p>The dispatch classes are the classes of {@code com.morpheus.cli} itself, the launcher and the dispatcher, and of
 * {@code com.morpheus.mcp.server}, the server. A command or a tool is served by a class of the adapter's capability
 * package; a dispatch class may name of {@code com.morpheus.application..} only the types of {@link #ALLOWANCE}, which
 * dispatching itself needs: the operational runtime it hands on, the product version, the bound the help text prints,
 * and the integration types the server passes to the capability classes. A command or a tool written in a dispatch
 * class has a use case to call, and fails here naming the class and the type.</p>
 *
 * <p>The rule is a dependency, so it is read on the compiled classes; the source of each dispatch class is read too,
 * for every application type it names, because a compile-time constant of the application
 * ({@code QueryBudgets.MAX_EXPORT_ROWS} in the help text) is inlined by {@code javac} and leaves no dependency behind.
 * Every name of the allowance and of the debt must still be used by a dispatch class, so neither list grows wider than
 * what dispatching does.</p>
 *
 * <p>{@link #MORPHEUS_CLI_DEBT} is a debt counter: {@code MorpheusCli} still serves {@code projects}, {@code sync} and
 * {@code analyze-change}, the commands {@code ingest-a-project-through-the-provider-port} rewires, and may use the
 * application types they use and no other. It only shrinks.</p>
 */
class DispatchersHoldNoUseCaseArchitectureTest {

    private static final String CLI_DISPATCH = "com.morpheus.cli";
    private static final String MCP_DISPATCH = "com.morpheus.mcp.server";
    private static final String APPLICATION = "com.morpheus.application";
    private static final String MORPHEUS_CLI = "com.morpheus.cli.MorpheusCli";

    /** The application types a dispatch class may use: what dispatching needs, and nothing that serves a command. */
    static final Set<String> ALLOWANCE = Set.of(
            APPLICATION + ".context.DisabledTechnicalContextProvider",
            APPLICATION + ".context.TechnicalContextProvider",
            APPLICATION + ".lifecycle.mutation.ChangeWriteCapabilityObservation",
            APPLICATION + ".lifecycle.mutation.ChangeWriteCapabilityResolver",
            APPLICATION + ".operability.LocalOperationalRuntime",
            APPLICATION + ".operability.OperationalLogFileException",
            APPLICATION + ".operability.OperationalLogMode",
            APPLICATION + ".operability.OperationalRecorder",
            APPLICATION + ".operability.OperationalRuntime",
            APPLICATION + ".product.ProductMetadata",
            APPLICATION + ".query.dsl.QueryBudgets",
            APPLICATION + ".reference.ExternalReferenceResolverRegistry");

    /** The application types {@code MorpheusCli} uses for the three commands it still serves. */
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
            APPLICATION + ".sync.SyncPlan");

    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(CLI_DISPATCH, MCP_DISPATCH, APPLICATION);

    private static final Pattern APPLICATION_TYPE_NAME =
            Pattern.compile("\\bcom\\.morpheus\\.application(?:\\.[a-z][a-z0-9]*)+\\.[A-Z]\\w*");
    private static final Pattern COMMENT = Pattern.compile("/\\*.*?\\*/|//[^\\n]*", Pattern.DOTALL);

    @Test
    void aDispatchClassDependsOnNoApplicationTypeBesideTheAllowance() {
        noClasses()
                .that(dispatchClasses().and(DescribedPredicate.describe("are not " + MORPHEUS_CLI,
                        javaClass -> !topLevelName(javaClass).equals(MORPHEUS_CLI))))
                .should().dependOnClassesThat(applicationTypesOutsideOf(ALLOWANCE))
                .because("a command or a tool is served by a capability class; a class that dispatches has no use "
                        + "case to call")
                .check(PRODUCTION);
        noClasses()
                .that(DescribedPredicate.describe("are " + MORPHEUS_CLI,
                        javaClass -> topLevelName(javaClass).equals(MORPHEUS_CLI)))
                .should().dependOnClassesThat(applicationTypesOutsideOf(union(ALLOWANCE, MORPHEUS_CLI_DEBT)))
                .because("MorpheusCli serves projects, sync and analyze-change until their ingestion is rewired, with "
                        + "the application types they use today and no other")
                .check(PRODUCTION);
    }

    @Test
    void aDispatchSourceNamesNoApplicationTypeBesideTheAllowance() {
        Map<String, List<String>> offenders = new TreeMap<>();
        for (String dispatchClass : dispatchClassNames()) {
            Set<String> permitted = dispatchClass.equals(MORPHEUS_CLI) ? union(ALLOWANCE, MORPHEUS_CLI_DEBT) : ALLOWANCE;
            List<String> refused = new ArrayList<>();
            for (String named : applicationTypesNamed(dispatchClass)) {
                if (!permitted.contains(named)) {
                    refused.add(named);
                }
            }
            if (!refused.isEmpty()) {
                offenders.put(dispatchClass, refused);
            }
        }
        assertEquals(Map.of(), offenders, "a dispatch class names an application type outside the allowance: a "
                + "constant read this way leaves no dependency for the bytecode rule");
    }

    @Test
    void everyNameOfTheAllowanceAndOfTheDebtIsStillUsed() {
        Set<String> usedByDispatchers = new TreeSet<>();
        Set<String> usedByMorpheusCli = new TreeSet<>();
        for (JavaClass origin : PRODUCTION) {
            if (!dispatchClasses().test(origin)) {
                continue;
            }
            Set<String> used = topLevelName(origin).equals(MORPHEUS_CLI) ? usedByMorpheusCli : usedByDispatchers;
            origin.getDirectDependenciesFromSelf().stream()
                    .map(dependency -> topLevelName(dependency.getTargetClass()))
                    .filter(name -> name.startsWith(APPLICATION + "."))
                    .forEach(used::add);
        }
        for (String dispatchClass : dispatchClassNames()) {
            (dispatchClass.equals(MORPHEUS_CLI) ? usedByMorpheusCli : usedByDispatchers)
                    .addAll(applicationTypesNamed(dispatchClass));
        }
        Set<String> unusedAllowance = new TreeSet<>(ALLOWANCE);
        unusedAllowance.removeAll(usedByDispatchers);
        unusedAllowance.removeAll(usedByMorpheusCli);
        Set<String> unusedDebt = new TreeSet<>(MORPHEUS_CLI_DEBT);
        unusedDebt.removeAll(usedByMorpheusCli);
        assertEquals(Set.of(), unusedAllowance, "names of the allowance no dispatch class uses: remove them");
        assertEquals(Set.of(), unusedDebt, "names of the MorpheusCli debt it no longer uses: remove them");
        Set<String> beyondTheAllowance = new TreeSet<>(usedByMorpheusCli);
        beyondTheAllowance.removeAll(ALLOWANCE);
        assertTrue(!MORPHEUS_CLI_DEBT.isEmpty() || beyondTheAllowance.isEmpty(),
                "MorpheusCli serves no command any more: the debt and its exception go");
    }

    private static DescribedPredicate<JavaClass> dispatchClasses() {
        return DescribedPredicate.describe("reside in " + CLI_DISPATCH + " or in " + MCP_DISPATCH,
                javaClass -> javaClass.getPackageName().equals(CLI_DISPATCH) || javaClass.getPackageName().equals(MCP_DISPATCH));
    }

    private static DescribedPredicate<JavaClass> applicationTypesOutsideOf(Set<String> permitted) {
        return DescribedPredicate.describe("are types of " + APPLICATION + ".. outside " + new TreeSet<>(permitted),
                target -> topLevelName(target).startsWith(APPLICATION + ".") && !permitted.contains(topLevelName(target)));
    }

    /** The top-level dispatch classes, by fully qualified name. */
    private static Set<String> dispatchClassNames() {
        Set<String> names = new TreeSet<>();
        for (JavaClass javaClass : PRODUCTION) {
            if (dispatchClasses().test(javaClass)) {
                names.add(topLevelName(javaClass));
            }
        }
        assertTrue(names.size() >= 4, "too few dispatch classes were imported to hold the rule: " + names);
        return names;
    }

    /** The application types the source of {@code dispatchClass} names by their qualified name, outside comments. */
    private static Set<String> applicationTypesNamed(String dispatchClass) {
        String module = dispatchClass.startsWith(CLI_DISPATCH + ".") ? "morpheus-cli" : "morpheus-mcp";
        String source = ProductionSources.readMain(module, dispatchClass.substring(dispatchClass.lastIndexOf('.') + 1));
        Set<String> named = new TreeSet<>();
        Matcher matcher = APPLICATION_TYPE_NAME.matcher(COMMENT.matcher(source).replaceAll(""));
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
