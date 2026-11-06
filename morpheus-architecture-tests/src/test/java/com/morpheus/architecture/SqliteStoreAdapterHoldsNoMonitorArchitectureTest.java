package com.morpheus.architecture;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaFieldAccess;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A SQLite store adapter takes no monitor, and the store runtime takes only the monitors it names, each with its reason.
 *
 * <p>A store owns one {@code java.sql.Connection} and a {@code closed} flag, nothing else that changes. Every store of
 * an operation is built by {@code SqliteOperationRuntime} under a {@code SqliteConnectionScope}, which refuses its
 * connection to any thread but the one that opened it; the two stores built outside a scope live inside one
 * {@code try} block of one thread. A {@code synchronized} method on such an object is never contended: it serialises a
 * thread with itself, costs a reader the search for what it guards, and suggests to whoever parallelises later that the
 * store may be shared, when sharing its connection is exactly what the scope refuses.</p>
 *
 * <p>The monitors the store module keeps guard state that several threads do reach, and they live in the runtime
 * package. They are exactly {@link #RUNTIME_MONITORS}, a table of facts: each entry is a monitor the runtime takes and
 * the reason several threads reach what it guards. A monitor taken anywhere else in the runtime fails, and so does a
 * named site that no longer takes its monitor, so the table never outlives what it states.</p>
 *
 * <p>Two forms, because they do not see the same thing ({@code .claude/rules/architecture.md}): the bytecode rule sees
 * the {@code synchronized} modifier of a method however the source is written; a {@code synchronized (lock)} block
 * leaves no modifier, only {@code monitorenter} instructions ArchUnit does not expose, and the text sees it.</p>
 */
class SqliteStoreAdapterHoldsNoMonitorArchitectureTest {

    private static final String ADAPTER = "com.morpheus.store.sqlite.adapter";
    private static final String RUNTIME = "com.morpheus.store.sqlite.runtime";
    private static final Path ADAPTER_SOURCES =
            Path.of("morpheus-store-sqlite", "src", "main", "java", "com", "morpheus", "store", "sqlite", "adapter");
    private static final Path RUNTIME_SOURCES =
            Path.of("morpheus-store-sqlite", "src", "main", "java", "com", "morpheus", "store", "sqlite", "runtime");
    private static final Pattern MONITOR = Pattern.compile("\\bsynchronized\\b");
    private static final Pattern BLOCK_LOCK = Pattern.compile("\\s*\\(\\s*([^()]*?)\\s*\\)");
    private static final Pattern MODIFIED_METHOD = Pattern.compile("[^;{}=()]*?\\b(\\w+)\\s*\\(");

    /**
     * Every monitor of {@code com.morpheus.store.sqlite.runtime}: {@code Owner#FIELD} for a field locked by
     * {@code synchronized} blocks, {@code Owner#method()} for a {@code synchronized} method; a nested owner is written
     * {@code Outer.Nested}. The value is why several threads reach what the monitor guards.
     */
    private static final Map<String, String> RUNTIME_MONITORS = Map.of(
            "SqliteDatabaseLease#MONITOR", "guards the process-wide registry of shared, exclusive and retained "
                    + "leases, which every thread that opens or closes a physical connection, or runs maintenance, "
                    + "reads and changes",
            "SqliteServerMaintenance.ServerLease#close()", "the lease is held by the remote server, which any "
                    + "thread may close, and two closes must release the lock and the channel once");

    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ADAPTER);

    private static final JavaClasses RUNTIME_PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(RUNTIME);

    @Test
    void noMethodOfTheStoreAdapterIsSynchronized() {
        noMethods()
                .that().areDeclaredInClassesThat().resideInAPackage(ADAPTER + "..")
                .should().haveModifier(JavaModifier.SYNCHRONIZED)
                .because("a SQLite store is confined to the thread of its operation (SqliteConnectionScope refuses "
                        + "its connection to any other), so its monitor is never contended and guards nothing")
                .check(PRODUCTION);
    }

    @Test
    void noSourceOfTheStoreAdapterTakesAMonitor() throws IOException {
        Path root = RepositoryRoot.locate();
        Path sources = root.resolve(ADAPTER_SOURCES);
        List<String> offenders = new ArrayList<>();
        int scanned = 0;
        try (Stream<Path> tree = Files.walk(sources)) {
            for (Path source : tree.filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
                scanned++;
                String code = RefusalPathTextArchitectureTest.withoutComments(Files.readString(source));
                Matcher monitor = MONITOR.matcher(code);
                if (monitor.find()) {
                    offenders.add(root.relativize(source).toString().replace('\\', '/'));
                }
            }
        }

        assertTrue(scanned >= 12, "too few sources scanned under " + ADAPTER_SOURCES + ": " + scanned);
        assertEquals(List.of(), offenders, "a source of " + ADAPTER + " takes a monitor (synchronized method or "
                + "block): a store is confined to the thread of its operation, so the monitor guards nothing. State "
                + "that several threads reach belongs to the runtime package, with the reason on its lock");
    }

    @Test
    void theSynchronizedMethodsOfTheStoreRuntimeAreExactlyTheNamedOnes() {
        Set<String> synchronizedMethods = new TreeSet<>();
        Set<String> finalFieldsRead = new TreeSet<>();
        for (JavaClass type : RUNTIME_PRODUCTION) {
            String owner = ownerName(type);
            for (JavaMethod method : type.getMethods()) {
                if (method.getModifiers().contains(JavaModifier.SYNCHRONIZED)) {
                    synchronizedMethods.add(owner + "#" + method.getName() + "()");
                }
            }
            type.getFieldAccessesFromSelf().stream()
                    .filter(access -> access.getAccessType() == JavaFieldAccess.AccessType.GET)
                    .filter(access -> access.getTargetOwner().equals(type))
                    .map(access -> access.getTarget().resolveMember())
                    .flatMap(Optional::stream)
                    .filter(field -> field.getModifiers().contains(JavaModifier.FINAL))
                    .map(JavaField::getName)
                    .forEach(field -> finalFieldsRead.add(owner + "#" + field));
        }

        Set<String> namedMethods = new TreeSet<>();
        Set<String> namedFields = new TreeSet<>();
        RUNTIME_MONITORS.keySet().forEach(site -> (site.endsWith("()") ? namedMethods : namedFields).add(site));

        assertFalse(RUNTIME_PRODUCTION.isEmpty(), "no class imported from " + RUNTIME);
        assertEquals(namedMethods, synchronizedMethods, "the synchronized methods of " + RUNTIME + " are exactly "
                + "the methods RUNTIME_MONITORS names: a new one is named there with why several threads reach what it "
                + "guards, and a named one that is no longer synchronized leaves the table");
        Set<String> unread = new TreeSet<>(namedFields);
        unread.removeAll(finalFieldsRead);
        assertEquals(Set.of(), unread, "a lock field RUNTIME_MONITORS names is no final field its class reads");
    }

    @Test
    void theStoreRuntimeSourcesTakeExactlyTheNamedMonitors() throws IOException {
        Path root = RepositoryRoot.locate();
        Set<String> taken = new TreeSet<>();
        int scanned = 0;
        try (Stream<Path> tree = Files.walk(root.resolve(RUNTIME_SOURCES))) {
            for (Path source : tree.filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
                scanned++;
                String file = source.getFileName().toString().replace(".java", "");
                taken.addAll(monitorsTakenBy(file, Files.readString(source)));
            }
        }

        Set<String> named = new TreeSet<>();
        RUNTIME_MONITORS.keySet().forEach(site -> named.add(sourceSite(site)));

        assertTrue(scanned > 0, "no source scanned under " + RUNTIME_SOURCES);
        assertEquals(named, taken, "the sources of " + RUNTIME + " take exactly the monitors RUNTIME_MONITORS names "
                + "(File#lock for a synchronized block, File#method() for a synchronized method): a monitor taken "
                + "elsewhere is named there with its reason, and a named site that no longer takes it leaves the table");
    }

    @Test
    void theRuntimeMonitorScanSeesABlockOnThisAndASynchronizedMethod() {
        String source = """
                final class Planted {
                    // synchronized (COMMENTED) is not code
                    private final Object lock = new Object();
                    public synchronized void close() { }
                    static synchronized <T> java.util.List<T> copy(java.util.List<T> values) { return values; }
                    void run() {
                        synchronized (this) { }
                        synchronized ( lock ) { }
                    }
                }
                """;

        assertEquals(Set.of("Planted#close()", "Planted#copy()", "Planted#this", "Planted#lock"),
                monitorsTakenBy("Planted", source));
    }

    private static Set<String> monitorsTakenBy(String file, String source) {
        String code = RefusalPathTextArchitectureTest.withoutComments(source);
        Set<String> taken = new TreeSet<>();
        Matcher monitor = MONITOR.matcher(code);
        while (monitor.find()) {
            Matcher block = BLOCK_LOCK.matcher(code).region(monitor.end(), code.length());
            Matcher method = MODIFIED_METHOD.matcher(code).region(monitor.end(), code.length());
            if (block.lookingAt()) {
                taken.add(file + "#" + block.group(1));
            } else if (method.lookingAt()) {
                taken.add(file + "#" + method.group(1) + "()");
            } else {
                taken.add(file + "#<unrecognised synchronized at offset " + monitor.start() + ">");
            }
        }
        return taken;
    }

    private static String sourceSite(String site) {
        int hash = site.indexOf('#');
        String owner = site.substring(0, hash);
        int dot = owner.indexOf('.');
        return (dot < 0 ? owner : owner.substring(0, dot)) + site.substring(hash);
    }

    private static String ownerName(JavaClass type) {
        return type.getName().substring(type.getPackageName().length() + 1).replace('$', '.');
    }
}
