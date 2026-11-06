package com.morpheus.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.CompositeArchRule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
<<<<<<< HEAD
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
=======
>>>>>>> s15b/arc18
import java.util.stream.Stream;

import static com.tngtech.archunit.lang.conditions.ArchConditions.never;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which technology the application layer may reach, and through which classes (ADR-0150).
 *
 * <p>A family the application does not use is refused in every package. Outbound network and threads are among them:
 * the transport of update discovery is an adapter of {@code morpheus-cli} behind a port of {@code application.product}.
 * A family it uses is reachable only from the classes ADR-0150 names for it, and each list is exactly the set of
 * classes that reach the family, so a list can shrink and cannot grow in silence. The families are the shared
 * vocabulary of {@link TechnologyFamily}, each of which has a regime here. The textual bans of
 * {@code application.reasoning} and {@code application.policy} are separate and stricter: they also refuse a mention.</p>
 */
class ApplicationTechnologyBoundaryArchitectureTest {
    private static final String APPLICATION = "com.morpheus.application";
    private static final String DECISION_RECORD = "ARCH-0150";
    private static final Pattern QUALIFIED_SPAN = Pattern.compile("`((?:[a-z][a-z0-9]*\\.)+[A-Z][A-Za-z0-9]*)`");

    private static final String HARDENER = APPLICATION + ".files.LocalWritePermissionHardener";
    private static final String ROTATING_LOG = APPLICATION + ".operability.RotatingOperationalLogFile";
    private static final String PEER_SETTINGS = APPLICATION + ".security.PeerLaunchSettingsResolver";
    private static final Set<String> REFLECTION_AND_LOADING = Set.of(
            APPLICATION + ".query.compact.CanonicalJsonSerializer",
            APPLICATION + ".reasoning.ReasoningAdapterRegistry");

    private static final List<TechnologyFamily> UNUSED_FAMILIES = List.of(TechnologyFamily.SQL,
            TechnologyFamily.JSON_LIBRARY, TechnologyFamily.PROCESS_EXECUTION, TechnologyFamily.OUTBOUND_NETWORK,
            TechnologyFamily.THREADS);

    private static final Map<TechnologyFamily, Set<String>> CONFINED_FAMILIES = Map.of(
            TechnologyFamily.FILE_ACCESS,
                    Set.of(HARDENER, ROTATING_LOG,
                            APPLICATION + ".files.SafeWorkspaceFileResolver",
                            APPLICATION + ".sync.LocalSourceInventoryScanner",
                            APPLICATION + ".sync.ScanWorkspaceBoundary",
                            APPLICATION + ".sync.SourceFingerprint",
                            APPLICATION + ".security.ExternalJarIntegrity",
                            PEER_SETTINGS,
                            APPLICATION + ".sync.LocalSourceWatcher",
                            APPLICATION + ".discovery.ProjectDiscoveryService",
                            APPLICATION + ".discovery.WorkspaceRootResolver"),
            TechnologyFamily.PROCESS_ENVIRONMENT,
                    Set.of(HARDENER, PEER_SETTINGS,
                            APPLICATION + ".operability.LocalOperationalRuntime",
                            APPLICATION + ".operability.SensitiveValueRedactor",
                            APPLICATION + ".product.ProductMetadata"),
            TechnologyFamily.STANDARD_STREAMS,
                    Set.of(ROTATING_LOG, APPLICATION + ".operability.OperationalRuntime"),
            TechnologyFamily.WALL_CLOCK,
                    Set.of(APPLICATION + ".operability.LocalOperabilityService",
                            APPLICATION + ".operability.OperationalRecorder",
                            APPLICATION + ".lifecycle.mutation.ControlledChangeLifecycleMutationService",
                            APPLICATION + ".policy.PolicyPackService",
                            APPLICATION + ".portfolio.PortfolioRegistryService",
                            APPLICATION + ".query.saved.SavedViewService"),
            TechnologyFamily.REFLECTION_AND_CLASS_LOADING,
                    REFLECTION_AND_LOADING);

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(APPLICATION);

    @Test
    void everyFamilyOfTheVocabularyHasARegimeInTheApplication() {
        Set<String> regimes = new TreeSet<>();
        UNUSED_FAMILIES.forEach(family -> regimes.add(family.name()));
        CONFINED_FAMILIES.keySet().forEach(family -> regimes.add(family.name()));
        Set<String> vocabulary = new TreeSet<>();
        TechnologyFamily.VOCABULARY.forEach(family -> vocabulary.add(family.name()));
        assertEquals(vocabulary, regimes, "ADR-0150: every family of the shared vocabulary is either unused or "
                + "confined in the application");
    }

    @Test
    void aFamilyTheApplicationDoesNotUseIsRefusedInEveryPackage() {
        ArchRule rule = CompositeArchRule.of(UNUSED_FAMILIES.stream()
                .map(family -> (ArchRule) classes().that().resideInAPackage(APPLICATION + "..")
                        .should(never(family.reach()))
                        .because("ADR-0150, decision 1: the application uses no " + family.name()))
                .toList());
        rule.check(classes);
    }

    @Test
    void aConfinedFamilyIsReachedOnlyByTheClassesNamedForIt() {
        ArchRule rule = CompositeArchRule.of(CONFINED_FAMILIES.entrySet().stream()
                .sorted(Map.Entry.comparingByKey((left, right) -> left.name().compareTo(right.name())))
                .map(entry -> (ArchRule) classes().that().resideInAPackage(APPLICATION + "..")
                        .and(DescribedPredicate.not(namedFor(entry.getValue(), entry.getKey())))
                        .should(never(entry.getKey().reach()))
                        .because("ADR-0150, decision 2: " + entry.getKey().name() + " is reached only by the "
                                + "classes it names; adding one takes a consolidated ADR that replaces it"))
                .toList());
        rule.check(classes);
    }

    @Test
    void everyNamedClassStillReachesItsFamily() {
        Map<String, Set<String>> named = new TreeMap<>();
        Map<String, Set<String>> reached = new TreeMap<>();
        CONFINED_FAMILIES.forEach((family, names) -> {
            named.put(family.name(), new TreeSet<>(names));
            reached.put(family.name(), classesReaching(family));
        });
        assertEquals(named, reached, "ADR-0150 names exactly the classes that reach each family: remove a class "
                + "that no longer reaches it, so each list only shrinks");
    }

    /**
     * The bytecode rule does not see a type that only names a lambda: {@code (InvocationHandler) (proxy, m, a) -> null}
     * leaves it in the {@code invokedynamic} descriptor, not in a dependency. The text of the sources does.
     */
    @Test
    void noApplicationSourceNamesReflectionOrLoadingUnlessItsClassIsNamed() throws IOException {
        Path main = RepositoryRoot.locate().resolve("morpheus-application/src/main/java");
        Set<String> unnamed = new TreeSet<>();
        int scanned = 0;
        try (Stream<Path> tree = Files.walk(main)) {
            for (Path source : tree.filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
                scanned++;
                String code = RefusalPathTextArchitectureTest.withoutComments(Files.readString(source));
                String relative = main.relativize(source).toString().replace('\\', '/');
                String className = relative.substring(0, relative.length() - ".java".length()).replace('/', '.');
                boolean names = ReflectionAndClassLoadingBoundaryArchitectureTest.REFLECTION_TEXT.matcher(code).find()
                        || ReflectionAndClassLoadingBoundaryArchitectureTest.LOADING_TEXT.matcher(code).find();
                if (names && !REFLECTION_AND_LOADING.contains(className)) unnamed.add(className);
            }
        }
        assertTrue(scanned > 100, "the scan must read the application sources, read " + scanned);
        assertEquals(Set.of(), unnamed, "ADR-0150, decision 2: a source that names reflection or class loading "
                + "belongs to a class named for that family");
    }

    /**
     * A class ADR-0150 names by a name qualified from a package of the application, in any section, exists at that name:
     * the enumeration compared by {@code ProductionClassListsArchitectureTest} does not see a name written elsewhere.
     */
    @Test
    void everyClassTheDecisionRecordNamesByAQualifiedNameExistsAtThatName() throws IOException {
        Path root = RepositoryRoot.locate();
        List<Path> record;
        try (Stream<Path> records = Files.list(root.resolve("docs/architecture/adr"))) {
            record = records.filter(path -> path.getFileName().toString().startsWith(DECISION_RECORD + "-")).toList();
        }
        assertEquals(1, record.size(), () -> DECISION_RECORD + " is one file of the registry: " + record);

        List<String> refusals = unresolvedQualifiedNames(Files.readString(record.get(0)),
                root.resolve("morpheus-application/src/main/java/com/morpheus/application"));

        assertEquals(List.of(), refusals, DECISION_RECORD + " names each class of the application at the package where "
                + "it lives");
    }

    @Test
    void aQualifiedNameThatResolvesToNoSourceIsRefusedAndASilentRecordToo(@TempDir Path application)
            throws IOException {
        Files.createDirectories(application.resolve("files"));
        Files.createDirectories(application.resolve("security"));
        Files.writeString(application.resolve("files/Hardener.java"), "class Hardener {}");

        List<String> moved = unresolvedQualifiedNames("`security.Hardener` and `files.Hardener`, `java.time.Clock`",
                application);
        List<String> removed = unresolvedQualifiedNames("`files.Hardener`, `files.Gone`", application);
        List<String> silent = unresolvedQualifiedNames("`java.time.Clock` and `Hardener` only", application);

        assertEquals(List.of("security.Hardener has no source under " + application), moved);
        assertEquals(List.of("files.Gone has no source under " + application), removed);
        assertTrue(silent.size() == 1 && silent.get(0).contains("no class of the application"), silent::toString);
    }

    /**
     * The names of {@code text} written in a code span as {@code package(.package)*.Class}, whose first package is a
     * package of the application, that have no source file at that name; a text that names none is refused too, so a
     * scan that read nothing never passes.
     */
    private static List<String> unresolvedQualifiedNames(String text, Path applicationPackage) throws IOException {
        Set<String> packages;
        try (Stream<Path> children = Files.list(applicationPackage)) {
            packages = children.filter(Files::isDirectory).map(path -> path.getFileName().toString())
                    .collect(Collectors.toCollection(TreeSet::new));
        }
        Set<String> read = new TreeSet<>();
        List<String> refusals = new ArrayList<>();
        Matcher span = QUALIFIED_SPAN.matcher(text);
        while (span.find()) {
            String name = span.group(1);
            if (!packages.contains(name.substring(0, name.indexOf('.'))) || !read.add(name)) {
                continue;
            }
            if (!Files.isRegularFile(applicationPackage.resolve(name.replace('.', '/') + ".java"))) {
                refusals.add(name + " has no source under " + applicationPackage);
            }
        }
        if (read.isEmpty()) {
            refusals.add("the text names no class of the application by a qualified name: the scan read nothing");
        }
        return refusals;
    }

    @Test
    void aPathUsedAsAValueIsNotAnAccess() {
        Set<String> pathOnly = new TreeSet<>();
        for (JavaClass origin : classes) {
            boolean usesPath = origin.getDirectDependenciesFromSelf().stream()
                    .anyMatch(dependency -> dependency.getTargetClass().getName().equals("java.nio.file.Path"));
            boolean reachesAFamily = Stream.concat(UNUSED_FAMILIES.stream(), CONFINED_FAMILIES.keySet().stream())
                    .anyMatch(family -> family.reachedBy(origin));
            if (usesPath && !reachesAFamily) pathOnly.add(TechnologyFamily.topLevelName(origin));
        }
        assertTrue(pathOnly.contains(APPLICATION + ".read.ProviderReadRequest")
                        && pathOnly.contains(APPLICATION + ".provider.SpecificationProvider"),
                "a port that carries a Path and opens nothing must not count as file access: " + pathOnly);
    }

    private Set<String> classesReaching(TechnologyFamily family) {
        Set<String> reaching = new TreeSet<>();
        for (JavaClass origin : classes) {
            if (family.reachedBy(origin)) reaching.add(TechnologyFamily.topLevelName(origin));
        }
        return reaching;
    }

    private static DescribedPredicate<JavaClass> namedFor(Set<String> names, TechnologyFamily family) {
        return DescribedPredicate.describe("named by ADR-0150 for " + family.name(),
                javaClass -> names.contains(TechnologyFamily.topLevelName(javaClass)));
    }
}
