package com.morpheus.architecture;

import com.morpheus.application.delta.RequirementDeltaApplicationService;
import com.morpheus.application.delta.RequirementDeltaPromotionService;
import com.morpheus.application.store.VersionedRequirementStore;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static com.tngtech.archunit.core.domain.JavaCall.Predicates.target;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@code listRequirementVersions} loads every temporal state of a snapshot; a reader that only keeps CURRENT rows
 * must ask the store for CURRENT rows, so the temporal predicate runs in the persistence backend.
 *
 * <p>The classes still allowed to load the whole history are listed by name, each for a reason that needs a
 * non-CURRENT row: the delta application refuses a reused {@code EntityVersionId} of any state and a candidate
 * holding an occurrence outside its plan, and the promotion refuses a candidate holding a non-CURRENT occurrence.
 * A new caller is refused until someone classifies it, and an allowlisted class that stops calling the method is
 * refused too, so the list cannot outlive its reasons.</p>
 */
class CurrentRequirementReaderArchitectureTest {

    private static final String FULL_HISTORY_METHOD = "listRequirementVersions";

    private static final List<Class<?>> FULL_HISTORY_READERS = List.of(
            RequirementDeltaApplicationService.class,
            RequirementDeltaPromotionService.class);

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.morpheus");

    @Test
    void everyCurrentOnlyReaderPushesTheTemporalPredicateDown() {
        noClasses()
                .that().doNotBelongToAnyOf(FULL_HISTORY_READERS.toArray(Class<?>[]::new))
                .and().areNotAssignableTo(VersionedRequirementStore.class)
                .should().callMethodWhere(target(name(FULL_HISTORY_METHOD)))
                .because("a reader that keeps only CURRENT requirement versions must call "
                        + "listCurrentRequirementVersions so the temporal predicate is applied by the store; "
                        + "a reader that needs the whole history is added to FULL_HISTORY_READERS with its reason")
                .check(classes);
    }

    @Test
    void everyFullHistoryReaderStillReadsTheFullHistory() {
        Set<String> expected = new TreeSet<>();
        FULL_HISTORY_READERS.forEach(type -> expected.add(type.getName()));

        Set<String> actual = new TreeSet<>();
        for (Class<?> type : FULL_HISTORY_READERS) {
            JavaClass imported = classes.get(type);
            boolean calls = imported.getMethodCallsFromSelf().stream()
                    .anyMatch(call -> call.getTarget().getName().equals(FULL_HISTORY_METHOD));
            if (calls) {
                actual.add(type.getName());
            }
        }

        assertEquals(expected, actual,
                "a class listed in FULL_HISTORY_READERS no longer loads the full history; remove it from the list");
    }
}
