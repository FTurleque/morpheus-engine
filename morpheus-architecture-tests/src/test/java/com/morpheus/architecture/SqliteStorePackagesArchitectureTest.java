package com.morpheus.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The SQLite store is two packages: {@code adapter} holds the implementations of the application ports, {@code runtime}
 * holds the connection, scope, lease, schema, transaction and maintenance machinery they run on. The stores call the
 * runtime across the package line, so part of the runtime is {@code public}; these rules say who may use it.
 *
 * <p>Two forms hold the first boundary, because they do not see the same thing
 * ({@code .claude/rules/architecture.md}): the bytecode rule sees a dependency however it is spelled, the text sees a
 * compile-time constant that {@code javac} inlines and leaves no dependency behind.</p>
 *
 * <p>The store's other dependency bounds - no other store, no provider, no transport - are held by
 * {@code AdapterSiblingArchitectureTest}, whose rules select {@code com.morpheus.store..} and its sub-packages.</p>
 */
class SqliteStorePackagesArchitectureTest {

    private static final String STORE = "com.morpheus.store.sqlite";
    private static final String ADAPTER = STORE + ".adapter";
    private static final String RUNTIME = STORE + ".runtime";
    private static final String STORE_MODULE = "morpheus-store-sqlite";

    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.morpheus");

    /**
     * The runtime classes a production class outside the store may depend on, with their nested types. The transports
     * build the stores of each operation through {@code SqliteOperationRuntime} and run the offline server operations
     * through {@code SqliteServerMaintenance}. Whether a transport may hold either, or only the ports they return, is
     * the decision of AUD-ARC-01 (sprint 11), which removes or confirms each entry. The list is closed: nothing is
     * added to it.
     */
    private static final Set<String> RUNTIME_ENTRY_POINTS = Set.of("SqliteOperationRuntime", "SqliteServerMaintenance");

    /** The classes of the adapter package that implement no port: the reader and the writer of the snapshot content store. */
    private static final Set<String> ADAPTER_COLLABORATORS =
            Set.of("SqliteSnapshotBusinessContentReader", "SqliteSnapshotBusinessContentWriter");

    private static final Pattern RUNTIME_NAME =
            Pattern.compile("com\\.morpheus\\.store\\.sqlite\\.runtime\\.([A-Za-z0-9_]+|\\*)");

    @Test
    void onlyTheStoreDependsOnItsRuntimeBesideTheTwoNamedEntryPoints() {
        DescribedPredicate<JavaClass> runtimeBeyondTheEntryPoints = DescribedPredicate.describe(
                "are classes of " + RUNTIME + " other than " + new TreeSet<>(RUNTIME_ENTRY_POINTS),
                target -> isInTheRuntime(target) && !RUNTIME_ENTRY_POINTS.contains(topLevelSimpleName(target)));

        Set<String> entryPointsReached = new TreeSet<>();
        for (JavaClass origin : PRODUCTION) {
            if (origin.getPackageName().equals(STORE) || origin.getPackageName().startsWith(STORE + ".")) {
                continue;
            }
            origin.getDirectDependenciesFromSelf().stream()
                    .map(dependency -> dependency.getTargetClass())
                    .filter(SqliteStorePackagesArchitectureTest::isInTheRuntime)
                    .forEach(target -> entryPointsReached.add(topLevelSimpleName(target)));
        }
        assertEquals(new TreeSet<>(RUNTIME_ENTRY_POINTS), entryPointsReached,
                "the rule must see the transports reach the two entry points it tolerates, or it checks nothing");

        noClasses()
                .that().resideOutsideOfPackage(STORE + "..")
                .should().dependOnClassesThat(runtimeBeyondTheEntryPoints)
                .because("the runtime of the SQLite store is what its adapter runs on; a class outside the store "
                        + "reaches the store through the application ports, and the two named entry points stay "
                        + "until AUD-ARC-01 decides them")
                .check(PRODUCTION);
    }

    @Test
    void noMainSourceOutsideTheStoreNamesARuntimeClassBesideTheTwoNamedEntryPoints() throws IOException {
        Path root = repositoryRoot();
        List<String> offenders = new ArrayList<>();
        Set<String> entryPointsNamed = new TreeSet<>();
        int scanned = 0;
        for (Path sources : mainSourceRootsOutsideTheStore(root)) {
            try (Stream<Path> tree = Files.walk(sources)) {
                for (Path source : tree.filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
                    scanned++;
                    classifyRuntimeNames(root, source, entryPointsNamed, offenders);
                }
            }
        }

        assertTrue(scanned > 100, "too few main sources scanned outside " + STORE_MODULE + ": " + scanned);
        assertEquals(new TreeSet<>(RUNTIME_ENTRY_POINTS), entryPointsNamed,
                "the scan must see the two entry points it tolerates, or the pattern it reads with is vacant");
        assertEquals(List.of(), offenders, "a main source outside " + STORE_MODULE + " names a runtime class of the "
                + "SQLite store: a compile-time constant read this way leaves no dependency for the bytecode rule. "
                + "Reach the store through an application port");
    }

    private static void classifyRuntimeNames(Path root, Path source, Set<String> entryPointsNamed, List<String> offenders)
            throws IOException {
        Matcher named = RUNTIME_NAME.matcher(Files.readString(source));
        while (named.find()) {
            String name = named.group(1);
            if (RUNTIME_ENTRY_POINTS.contains(name)) {
                entryPointsNamed.add(name);
            } else {
                offenders.add(root.relativize(source).toString().replace('\\', '/') + " names " + name);
            }
        }
    }

    @Test
    void everyTopLevelClassOfTheAdapterPackageImplementsAnApplicationPortBesideTheTwoNamedCollaborators() {
        classes()
                .that().resideInAPackage(ADAPTER)
                .and().areTopLevelClasses()
                .and(DescribedPredicate.describe("are not " + new TreeSet<>(ADAPTER_COLLABORATORS),
                        candidate -> !ADAPTER_COLLABORATORS.contains(candidate.getSimpleName())))
                .should().implement(JavaClass.Predicates.resideInAPackage("com.morpheus.application..")
                        .as("an interface of com.morpheus.application.."))
                .because("the adapter package holds the implementations of the application ports; a class that "
                        + "implements none is runtime machinery and belongs to " + RUNTIME)
                .check(PRODUCTION);
    }

    @Test
    void noClassOfTheStoreResidesDirectlyInItsParentPackage() {
        noClasses()
                .that().resideInAPackage(STORE + "..")
                .should().resideInAPackage(STORE)
                .because("every class of the SQLite store is either an adapter or its runtime, and a rule can name "
                        + "only a package")
                .check(PRODUCTION);
    }

    private static boolean isInTheRuntime(JavaClass javaClass) {
        String packageName = javaClass.getPackageName();
        return packageName.equals(RUNTIME) || packageName.startsWith(RUNTIME + ".");
    }

    private static String topLevelSimpleName(JavaClass javaClass) {
        String name = javaClass.getName().substring(javaClass.getPackageName().length() + 1);
        int nested = name.indexOf('$');
        return nested < 0 ? name : name.substring(0, nested);
    }

    private static List<Path> mainSourceRootsOutsideTheStore(Path root) throws IOException {
        try (Stream<Path> modules = Files.list(root)) {
            return modules
                    .filter(Files::isDirectory)
                    .filter(module -> !module.getFileName().toString().equals(STORE_MODULE))
                    .map(module -> module.resolve("src/main/java"))
                    .filter(Files::isDirectory)
                    .sorted()
                    .toList();
        }
    }

    private static Path repositoryRoot() {
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
