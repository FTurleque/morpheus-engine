package com.morpheus.architecture;

import com.morpheus.application.lifecycle.mutation.ChangeLifecycleMutationStore;
import com.morpheus.application.store.SyncStateStore;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaCall;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import static com.tngtech.archunit.core.domain.AccessTarget.Predicates.declaredIn;
import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Three tables only grow: the policy audit, the audit of a lifecycle mutation and the archives of a synchronisation
 * (AUD-PRF-04). A journal is read by pages bounded at the store, never whole by a public surface, and the product does
 * not delete from it without a recorded decision.
 *
 * <p>The policy audit has a store method that takes a page and no method that returns the journal. The two others
 * still have a method that returns every row of a change or of a project, and no production class calls either: they
 * load nothing on any public path today. This keeps it so, until the surface that needs one bounds it first.</p>
 *
 * <p>Enforcement mechanism: the first rule is a dependency, so it is a rule on the compiled calls, which a constant
 * cannot inline away; the second names SQL, which lives in strings no bytecode rule reads, so it is a textual
 * assertion.</p>
 */
class AppendOnlyJournalReadsArchitectureTest {
    private static final List<String> JOURNAL_TABLES = List.of(
            "policy_audit", "change_lifecycle_mutation_audit", "sync_source_archives");

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.morpheus");

    @Test
    void noProductionClassReadsAWholeLifecycleAuditOrAProjectsArchives() {
        DescribedPredicate<JavaCall<?>> readsAWholeJournal =
                target(declaredIn(ChangeLifecycleMutationStore.class).and(name("listAudit")))
                        .or(target(declaredIn(SyncStateStore.class).and(name("listArchives"))));

        noClasses()
                .should().callMethodWhere(readsAWholeJournal)
                .because("a journal only grows: ChangeLifecycleMutationStore.listAudit and SyncStateStore.listArchives "
                        + "return every row and have no caller; the surface that needs one bounds it first")
                .check(classes);
    }

    @Test
    void theRuleSeesTheCallsItIsMeantToRefuse() {
        long callers = classes.stream()
                .flatMap(type -> type.getMethodCallsFromSelf().stream())
                .filter(call -> call.getTarget().getOwner().isAssignableTo(ChangeLifecycleMutationStore.class)
                        || call.getTarget().getOwner().isAssignableTo(SyncStateStore.class))
                .count();

        assertTrue(callers > 0, "no call to either store was imported: the rule above would pass on nothing");
    }

    @Test
    void noStatementOfTheProductDeletesFromAJournal() throws IOException {
        Path sources = repositoryRoot().resolve("morpheus-store-sqlite/src/main");
        List<String> offenders = new ArrayList<>();
        try (Stream<Path> files = Files.walk(sources)) {
            for (Path file : files.filter(Files::isRegularFile).sorted().toList()) {
                String name = file.getFileName().toString();
                if (!name.endsWith(".java") && !name.endsWith(".sql")) {
                    continue;
                }
                String text = Files.readString(file).toLowerCase(Locale.ROOT);
                for (String table : JOURNAL_TABLES) {
                    if (text.matches("(?s).*delete\\s+from\\s+" + table + "\\b.*")
                            || text.matches("(?s).*drop\\s+table\\s+(if\\s+exists\\s+)?" + table + "\\b.*")) {
                        offenders.add(sources.relativize(file) + " deletes from " + table);
                    }
                }
            }
        }

        assertEquals(List.of(), offenders,
                "the journals are kept for the life of the database; deleting from one is a decision to record first");
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
