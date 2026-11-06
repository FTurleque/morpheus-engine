package com.morpheus.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.CompositeArchRule;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import static com.tngtech.archunit.lang.conditions.ArchConditions.never;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Which technology {@code com.morpheus.cli} may reach, and through which classes.
 *
 * <p>The CLI wires the process and carries the transport ADR-0150 moved out of the application. Each family of the
 * shared vocabulary ({@link TechnologyFamily}) has a regime here: refused in every class of the CLI, confined to the
 * classes named for it with their reason, free, or held by another gate that is named. A confined list is exactly the
 * set of classes that reach the family, measured on the bytecode, so a new dependency on the network, on threads or on
 * the files of the process is written here, with its reason, before it builds. The lists are tables of facts: the
 * CLI is a transport module, whose role is to carry these technologies, and no decision record expects them to
 * shrink.</p>
 */
class CliTechnologyBoundaryArchitectureTest {
    private static final String CLI = "com.morpheus.cli";

    private enum Regime { REFUSED, CONFINED, FREE, HELD_BY_ANOTHER_GATE }

    private record Decision(Regime regime, String reason) {
    }

    private static final Map<TechnologyFamily, Decision> REGIMES = Map.of(
            TechnologyFamily.SQL, new Decision(Regime.REFUSED,
                    "persistence goes through the SQLite operation runtime of the store, ADR-0156"),
            TechnologyFamily.JSON_LIBRARY, new Decision(Regime.REFUSED,
                    "the JSON a command prints is written by the application"),
            TechnologyFamily.PROCESS_EXECUTION, new Decision(Regime.REFUSED,
                    "the CLI starts no process; the MCP peers are launched by the integrations"),
            TechnologyFamily.OUTBOUND_NETWORK, new Decision(Regime.CONFINED,
                    "the transport of update discovery, behind the port of application.product"),
            TechnologyFamily.THREADS, new Decision(Regime.CONFINED,
                    "the bound on the read of the update manifest"),
            TechnologyFamily.FILE_ACCESS, new Decision(Regime.CONFINED,
                    "the files the process opens itself: its standard streams, a local manifest, a workspace root"),
            TechnologyFamily.PROCESS_ENVIRONMENT, new Decision(Regime.CONFINED,
                    "the wiring of the process reads its environment once and hands values down"),
            TechnologyFamily.STANDARD_STREAMS, new Decision(Regime.CONFINED,
                    "the wiring of the process owns its streams; a command writes to the stream it is given"),
            TechnologyFamily.WALL_CLOCK, new Decision(Regime.FREE,
                    "the CLI is the composition root that gives a command its time; the application receiving a "
                            + "java.time.Clock is held by the application gate"),
            TechnologyFamily.REFLECTION_AND_CLASS_LOADING, new Decision(Regime.HELD_BY_ANOTHER_GATE,
                    "ReflectionAndClassLoadingBoundaryArchitectureTest confines it over the whole product"));

    /** Each confined family, with each class of the CLI that reaches it and why. */
    private static final Map<TechnologyFamily, Map<String, String>> CONFINED_FAMILIES = Map.of(
            TechnologyFamily.OUTBOUND_NETWORK, Map.of(
                    CLI + ".command.UpdateManifestFetcher", "reads an https: update manifest with java.net.http"),
            TechnologyFamily.THREADS, Map.of(
                    CLI + ".command.UpdateManifestFetcher", "bounds the read of a local manifest by a deadline"),
            TechnologyFamily.FILE_ACCESS, Map.of(
                    CLI + ".command.UpdateManifestFetcher", "reads a file: update manifest",
                    CLI + ".command.ProjectWorkspace", "resolves the workspace root of a project command",
                    CLI + ".MorpheusCli", "opens the standard output by its file descriptor",
                    CLI + ".MorpheusMain", "opens the standard output by its file descriptor",
                    CLI + ".launch.RemoteApiLaunchOptions", "splits a list of paths on File.pathSeparator"),
            TechnologyFamily.PROCESS_ENVIRONMENT, Map.of(
                    CLI + ".MorpheusCli", "reads the launch environment of the process",
                    CLI + ".MorpheusMain", "reads the launch environment of the process"),
            TechnologyFamily.STANDARD_STREAMS, Map.of(
                    CLI + ".MorpheusMain", "writes a launch failure to the standard error of the process"));

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(CLI);

    @Test
    void everyFamilyOfTheVocabularyHasARegimeInTheCli() {
        Set<String> vocabulary = new TreeSet<>();
        TechnologyFamily.VOCABULARY.forEach(family -> vocabulary.add(family.name()));
        Set<String> withRegime = new TreeSet<>();
        REGIMES.keySet().forEach(family -> withRegime.add(family.name()));
        assertEquals(vocabulary, withRegime, "every family of the shared vocabulary has a regime in the CLI: "
                + "refused, confined, free or held by another gate, with its reason");

        Set<String> confined = new TreeSet<>();
        REGIMES.forEach((family, decision) -> {
            if (decision.regime() == Regime.CONFINED) confined.add(family.name());
        });
        Set<String> listed = new TreeSet<>();
        CONFINED_FAMILIES.keySet().forEach(family -> listed.add(family.name()));
        assertEquals(confined, listed, "a family is confined in the CLI exactly when the gate names its classes");
    }

    @Test
    void aFamilyTheCliRefusesIsReachedByNoClassOfTheCli() {
        ArchRule rule = CompositeArchRule.of(REGIMES.entrySet().stream()
                .filter(entry -> entry.getValue().regime() == Regime.REFUSED)
                .sorted(Map.Entry.comparingByKey((left, right) -> left.name().compareTo(right.name())))
                .map(entry -> (ArchRule) classes().that().resideInAPackage(CLI + "..")
                        .should(never(entry.getKey().reach()))
                        .because("the CLI uses no " + entry.getKey().name() + ": " + entry.getValue().reason()))
                .toList());
        rule.check(classes);
    }

    @Test
    void aConfinedFamilyIsReachedOnlyByTheClassesOfTheCliNamedForIt() {
        ArchRule rule = CompositeArchRule.of(CONFINED_FAMILIES.entrySet().stream()
                .sorted(Map.Entry.comparingByKey((left, right) -> left.name().compareTo(right.name())))
                .map(entry -> (ArchRule) classes().that().resideInAPackage(CLI + "..")
                        .and(DescribedPredicate.not(namedFor(entry.getValue().keySet(), entry.getKey())))
                        .should(never(entry.getKey().reach()))
                        .because(entry.getKey().name() + " is reached in the CLI only by the classes this gate "
                                + "names for it, each with its reason"))
                .toList());
        rule.check(classes);
    }

    @Test
    void everyClassNamedForAConfinedFamilyStillReachesIt() {
        Map<String, Set<String>> named = new TreeMap<>();
        Map<String, Set<String>> reached = new TreeMap<>();
        CONFINED_FAMILIES.forEach((family, reasons) -> {
            named.put(family.name(), new TreeSet<>(reasons.keySet()));
            reached.put(family.name(), classesReaching(family));
        });
        assertEquals(named, reached, "each list of the CLI gate names exactly the classes of the CLI that reach its "
                + "family: name a class that reaches it with its reason, remove one that no longer does");
    }

    private Set<String> classesReaching(TechnologyFamily family) {
        Set<String> reaching = new TreeSet<>();
        for (JavaClass origin : classes) {
            if (family.reachedBy(origin)) reaching.add(TechnologyFamily.topLevelName(origin));
        }
        return reaching;
    }

    private static DescribedPredicate<JavaClass> namedFor(Set<String> names, TechnologyFamily family) {
        return DescribedPredicate.describe("named by the CLI gate for " + family.name(),
                javaClass -> names.contains(TechnologyFamily.topLevelName(javaClass)));
    }
}
