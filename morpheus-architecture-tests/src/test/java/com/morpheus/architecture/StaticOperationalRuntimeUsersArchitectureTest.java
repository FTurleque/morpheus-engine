package com.morpheus.architecture;

import com.morpheus.store.sqlite.runtime.SqliteConnectionScope;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The classes that read the process-wide operational runtime are named, and their number only goes down.
 *
 * <p>{@code LocalOperationalRuntime} is one set of counters and one sink for the whole process. A class that reads it
 * directly cannot be observed by a test without every other test of the same JVM writing to the same counters, and
 * cannot be given another destination. The direction is to hand each class its recorder or its metrics by
 * constructor, as most of the classes below already accept, and to keep the static runtime as the default of the
 * process only.</p>
 *
 * <p>This rule holds the direction while that is done. It is textual: it is about which source mentions the name. A
 * class that starts to mention it is refused; a class that stops is removed from the list, which is the only edit the
 * list accepts. No test mentions it at all: a test that asserts on counters builds the metrics it reads.</p>
 *
 * <p>Twenty-five classes on 13/10/2026, twenty after the store path, six on 15/10/2026: the HTTP and MCP trunks, the
 * MINOS and NEXUS integrations and the application services they build now take the recorder of their entry point by
 * constructor. What remains, each for a reason:</p>
 * <ul>
 *   <li>{@code MorpheusMain} and {@code MorpheusCli} are the two entry points: they take the runtime of the process and
 *   hand it on;</li>
 *   <li>{@code OperationalSlf4jServiceProvider} is instantiated by the service loader (ADR-0146): nobody can hand it
 *   anything;</li>
 *   <li>{@code SqliteContentionMetrics} is the default of a connection opened outside a scope, whose opener named no
 *   recorder: a single store a class builds alone, such as the backup of {@code SqliteServerMaintenance};</li>
 *   <li>{@code SqliteDatabaseLease} holds one lease per database for the whole process: its release belongs to no single
 *   opener;</li>
 *   <li>{@code ExternalJarIntegrity} stages a pinned JAR for the peer gateways and for the plugin activator, which runs
 *   in the probe worker's own JVM and in the plugin service, neither of which is handed a runtime; the counter it moves
 *   - a rejected copy it could not delete - is a residue of the process, in that process's temporary directory. Handing
 *   it a recorder waits for the activator to have one.</li>
 * </ul>
 */
class StaticOperationalRuntimeUsersArchitectureTest {
    private static final String RUNTIME = "LocalOperationalRuntime";

    /** Main classes that mention the static runtime, besides the runtime itself. Measured on 15/10/2026. */
    private static final Set<String> MAIN_USERS = Set.of(
            "ExternalJarIntegrity.java",
            "MorpheusCli.java",
            "MorpheusMain.java",
            "OperationalSlf4jServiceProvider.java",
            "SqliteContentionMetrics.java",
            "SqliteDatabaseLease.java");

    /** The classes that take the runtime of the process as a value: the launcher, and the second entry point. */
    private static final Set<String> PROCESS_RUNTIME_TAKERS = Set.of("MorpheusCli.java", "MorpheusMain.java");

    @Test
    void noMainClassStartsToReadTheStaticRuntime() throws IOException {
        Set<String> found = sourcesMentioning("src/main/java");
        assertTrue(found.remove("LocalOperationalRuntime.java"), "the runtime itself was not found: " + found);

        assertEquals(new TreeSet<>(MAIN_USERS), found, "a class that reads " + RUNTIME + " directly is one a test "
                + "cannot observe alone. Take the recorder or the metrics by constructor and let the caller pass the "
                + "process-wide one; a class that no longer mentions it is removed from MAIN_USERS, and none is added");
    }

    @Test
    void noTestReadsTheStaticRuntime() throws IOException {
        assertEquals(Set.of(), sourcesMentioning("src/test/java"), "a test that reads " + RUNTIME
                + " observes what every other test of the JVM wrote. Build an OperationalMetrics or an "
                + "OperationalRuntime of its own and hand it to the code under test");
    }

    @Test
    void onlyAnEntryPointTakesTheRuntimeOfTheProcessAsAValue() throws IOException {
        Set<String> found = sourcesMentioning("src/main/java", RUNTIME + ".process()");

        assertEquals(new TreeSet<>(PROCESS_RUNTIME_TAKERS), found, "the runtime of the process is taken once, by "
                + "the entry point that then hands it on by constructor. A class that takes it elsewhere has a "
                + "runtime nobody gave it, and a test cannot give it another");
    }

    /**
     * The connection scope counted the connections it opened and the schema checks it ran in static fields of the
     * process, and ten test classes on four modules read them as differences: two tests running at once read each
     * other's, which is what made JUnit parallel execution fail on the store module (ADR-0115). They now count on the
     * recorder the opener of the scope names. A test that reads them names that recorder; this holds that none goes
     * back to the process.
     */
    @Test
    void noTestReadsProcessWideCountersOfTheConnectionScope() throws IOException {
        Set<String> found = new TreeSet<>(sourcesMentioning("src/test/java", "SqliteConnectionScope.diagnostics("));
        found.addAll(sourcesMentioning("src/test/java", "SqliteConnectionScope.Diagnostics"));

        assertEquals(Set.of(), found, "a test that reads the counters of the connection scope reads the recorder it "
                + "opened the scope with, never counters of the process");
    }

    /** The scope keeps no state of the process beside the thread-confined scope itself and its constant names. */
    @Test
    void theConnectionScopeKeepsNoCounterOfTheProcess() {
        JavaClasses classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.morpheus.store.sqlite");
        Set<String> processState = new TreeSet<>();
        for (JavaClass javaClass : classes) {
            if (!javaClass.getName().startsWith(SqliteConnectionScope.class.getName())) {
                continue;
            }
            for (JavaField field : javaClass.getFields()) {
                boolean isStatic = field.getModifiers().contains(JavaModifier.STATIC);
                boolean constant = field.getModifiers().contains(JavaModifier.FINAL)
                        && (field.getRawType().isEquivalentTo(String.class)
                        || field.getRawType().isEquivalentTo(ThreadLocal.class)
                        || field.getName().equals("serialVersionUID"));
                if (isStatic && !constant) {
                    processState.add(javaClass.getSimpleName() + "." + field.getName());
                }
            }
        }

        assertTrue(classes.contain(SqliteConnectionScope.class), "the scope class was not imported");
        assertEquals(Set.of(), processState, "a static field of the connection scope is state of the process: a "
                + "counter there is read by every test of the JVM at once. Count on the recorder of the scope");
    }

    private static Set<String> sourcesMentioning(String sourceRoot) throws IOException {
        return sourcesMentioning(sourceRoot, RUNTIME);
    }

    private static Set<String> sourcesMentioning(String sourceRoot, String mention) throws IOException {
        Path root = repositoryRoot();
        Set<String> found = new TreeSet<>();
        try (Stream<Path> modules = Files.list(root)) {
            for (Path module : modules.filter(Files::isDirectory).sorted().toList()) {
                Path sources = module.resolve(sourceRoot);
                if (!module.getFileName().toString().startsWith("morpheus-") || !Files.isDirectory(sources)
                        || module.getFileName().toString().equals("morpheus-architecture-tests")) {
                    continue;
                }
                try (Stream<Path> files = Files.walk(sources)) {
                    for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                        if (Files.readString(file).contains(mention)) {
                            assertTrue(found.add(file.getFileName().toString()),
                                    () -> "two sources share the name " + file.getFileName());
                        }
                    }
                }
            }
        }
        return found;
    }

    private static Path repositoryRoot() throws IOException {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("contracts/public-surfaces.tsv"))
                    && Files.isRegularFile(current.resolve("pom.xml"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IOException("cannot locate MORPHEUS repository root from " + Path.of("").toAbsolutePath());
    }
}
