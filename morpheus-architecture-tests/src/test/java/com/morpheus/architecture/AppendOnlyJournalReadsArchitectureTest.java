package com.morpheus.architecture;

import com.morpheus.application.lifecycle.mutation.ChangeLifecycleMutationAuditPage;
import com.morpheus.application.lifecycle.mutation.ChangeLifecycleMutationAuditRecord;
import com.morpheus.application.lifecycle.mutation.ChangeLifecycleMutationStore;
import com.morpheus.application.policy.PolicyAuditPage;
import com.morpheus.application.policy.PolicyConfiguration;
import com.morpheus.application.query.PageRequest;
import com.morpheus.application.store.PolicyPackStore;
import com.morpheus.application.store.SyncStateStore;
import com.morpheus.application.sync.SourceArchivePage;
import com.morpheus.application.sync.SourceArchiveRecord;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.methods;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Three tables only grow: the policy audit, the audit of a lifecycle mutation and the archives of a synchronisation
 * (AUD-PRF-04). Each is read from its store one page at a time, and the product does not delete from any of them without
 * a recorded decision.
 *
 * <p>A port method that returns a whole journal is the short signature the next surface picks, so the bound is a
 * property of the ports: no public method of a journal store port, or of a class implementing one, returns a
 * collection of journal records, and a method that returns a page of them takes a {@link PageRequest}. The guard names
 * every method the rule judges, so a rename or a move that hides one from the rule fails here instead of passing
 * empty, and a fixture port breaks the rule on every run.</p>
 *
 * <p>Enforcement mechanism: the first rule reads compiled signatures, which source formatting cannot hide; the second
 * names SQL, which lives in strings no bytecode rule reads, so it is a textual assertion.</p>
 */
class AppendOnlyJournalReadsArchitectureTest {
    private static final List<String> JOURNAL_TABLES = List.of(
            "policy_audit", "change_lifecycle_mutation_audit", "sync_source_archives");
    private static final List<Class<?>> JOURNAL_PORTS = List.of(
            PolicyPackStore.class, ChangeLifecycleMutationStore.class, SyncStateStore.class);
    private static final List<Class<?>> JOURNAL_RECORDS = List.of(
            PolicyConfiguration.AuditRecord.class, ChangeLifecycleMutationAuditRecord.class, SourceArchiveRecord.class);
    private static final List<Class<?>> JOURNAL_PAGES = List.of(
            PolicyAuditPage.class, ChangeLifecycleMutationAuditPage.class, SourceArchivePage.class);
    private static final List<Class<?>> MANY_VALUES = List.of(Iterable.class, Stream.class, Map.class);

    private static final DescribedPredicate<JavaMethod> PUBLIC_METHOD_OF_A_JOURNAL_STORE =
            DescribedPredicate.describe("public methods of a journal store port or of a class implementing one",
                    method -> method.getModifiers().contains(JavaModifier.PUBLIC)
                            && !method.getModifiers().contains(JavaModifier.SYNTHETIC)
                            && !method.getModifiers().contains(JavaModifier.BRIDGE)
                            && JOURNAL_PORTS.stream().anyMatch(port -> method.getOwner().isAssignableTo(port)));

    private static final DescribedPredicate<JavaMethod> RETURNING_JOURNAL_RECORDS =
            DescribedPredicate.describe("that return journal records",
                    AppendOnlyJournalReadsArchitectureTest::returnsJournalRecords);

    private static final ArchCondition<JavaMethod> TAKE_A_PAGE_REQUEST_AND_RETURN_A_PAGE =
            new ArchCondition<>("take a PageRequest and return a page, never a collection of journal records") {
                @Override
                public void check(JavaMethod method, ConditionEvents events) {
                    if (!isAPage(method.getRawReturnType())) {
                        events.add(SimpleConditionEvent.violated(method, method.getFullName()
                                + " returns a whole journal (" + method.getReturnType().getName() + ")"));
                    } else if (method.getRawParameterTypes().stream()
                            .noneMatch(type -> type.isEquivalentTo(PageRequest.class))) {
                        events.add(SimpleConditionEvent.violated(method, method.getFullName()
                                + " returns a page of a journal without taking a PageRequest"));
                    }
                }
            };

    private static final ArchRule NO_PORT_OFFERS_A_WHOLE_JOURNAL = methods()
            .that(PUBLIC_METHOD_OF_A_JOURNAL_STORE.and(RETURNING_JOURNAL_RECORDS))
            .should(TAKE_A_PAGE_REQUEST_AND_RETURN_A_PAGE)
            .because("a journal only grows: a store reads it one page at a time, bounded in its query, and a port "
                    + "that offers the whole journal waits for its next caller");

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.morpheus");

    @Test
    void noJournalStoreOffersAWholeJournal() {
        NO_PORT_OFFERS_A_WHOLE_JOURNAL.check(classes);
    }

    @Test
    void theRuleJudgesThePagedReadOfEachJournalOnItsPortAndOnBothStores() {
        List<String> judged = classes.stream()
                .flatMap(type -> type.getMethods().stream())
                .filter(method -> PUBLIC_METHOD_OF_A_JOURNAL_STORE.test(method) && RETURNING_JOURNAL_RECORDS.test(method))
                .map(method -> method.getOwner().getSimpleName() + "." + method.getName())
                .sorted()
                .toList();

        assertEquals(List.of(
                        "ChangeLifecycleMutationStore.listAudit",
                        "MemoryChangeLifecycleMutationStore.listAudit",
                        "MemoryPolicyPackStore.listAudit",
                        "MemorySyncStateStore.listArchives",
                        "PolicyPackStore.listAudit",
                        "SqliteChangeLifecycleMutationStore.listAudit",
                        "SqlitePolicyPackStore.listAudit",
                        "SqliteSyncStateStore.listArchives",
                        "SyncStateStore.listArchives"),
                judged,
                "the rule must judge exactly the journal reads of the three ports and of their memory and SQLite stores");
    }

    @Test
    void theRuleRefusesAPortThatOffersAWholeJournalOrAPageWithoutABound() {
        JavaClasses fixture = new ClassFileImporter().importClasses(WholeArchiveJournal.class);

        AssertionError refusal = assertThrows(AssertionError.class, () -> NO_PORT_OFFERS_A_WHOLE_JOURNAL.check(fixture));

        String message = refusal.getMessage();
        assertTrue(message.contains("listEveryArchive") && message.contains("returns a whole journal"), message);
        assertTrue(message.contains("listFirstAudits") && message.contains("without taking a PageRequest"), message);
    }

    @Test
    void noStatementOfTheProductDeletesFromAJournal() throws IOException {
        Path sources = RepositoryRoot.locate().resolve("morpheus-store-sqlite/src/main");
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

    private static boolean returnsJournalRecords(JavaMethod method) {
        JavaClass returned = method.getRawReturnType();
        if (isAPage(returned)) {
            return true;
        }
        boolean manyValues = returned.isArray() || MANY_VALUES.stream().anyMatch(returned::isAssignableTo);
        return manyValues && method.getReturnType().getAllInvolvedRawTypes().stream()
                .anyMatch(type -> JOURNAL_RECORDS.stream().anyMatch(type::isEquivalentTo));
    }

    private static boolean isAPage(JavaClass type) {
        return JOURNAL_PAGES.stream().anyMatch(type::isEquivalentTo);
    }

    /** A port that breaks the rule both ways: a whole journal, and a page that takes no bound. */
    interface WholeArchiveJournal extends SyncStateStore {
        List<SourceArchiveRecord> listEveryArchive(ProjectSpecificationId projectId);

        PolicyAuditPage listFirstAudits(ProjectSpecificationId projectId);
    }
}
