package com.morpheus.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noMethods;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A SQLite store adapter takes no monitor: it is confined to one thread, so a monitor on it protects nothing.
 *
 * <p>A store owns one {@code java.sql.Connection} and a {@code closed} flag, nothing else that changes. Every store of
 * an operation is built by {@code SqliteOperationRuntime} under a {@code SqliteConnectionScope}, which refuses its
 * connection to any thread but the one that opened it; the two stores built outside a scope live inside one
 * {@code try} block of one thread. A {@code synchronized} method on such an object is never contended: it serialises a
 * thread with itself, costs a reader the search for what it guards, and suggests to whoever parallelises later that the
 * store may be shared, when sharing its connection is exactly what the scope refuses.</p>
 *
 * <p>The monitors the store module keeps guard state that several threads do reach, and they live in the runtime
 * package: the process-wide registry of database leases ({@code SqliteDatabaseLease}) and the server lease, released by
 * whichever thread closes the remote server that holds it.</p>
 *
 * <p>Two forms, because they do not see the same thing ({@code .claude/rules/architecture.md}): the bytecode rule sees
 * the {@code synchronized} modifier of a method however the source is written; a {@code synchronized (lock)} block
 * leaves no modifier, only {@code monitorenter} instructions ArchUnit does not expose, and the text sees it.</p>
 */
class SqliteStoreAdapterHoldsNoMonitorArchitectureTest {

    private static final String ADAPTER = "com.morpheus.store.sqlite.adapter";
    private static final Path ADAPTER_SOURCES =
            Path.of("morpheus-store-sqlite", "src", "main", "java", "com", "morpheus", "store", "sqlite", "adapter");
    private static final Pattern MONITOR = Pattern.compile("\\bsynchronized\\b");

    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ADAPTER);

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
}
