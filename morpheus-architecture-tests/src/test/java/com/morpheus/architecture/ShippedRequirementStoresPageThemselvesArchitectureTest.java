package com.morpheus.architecture;

import com.morpheus.application.store.VersionedRequirementStore;
import com.morpheus.store.memory.MemorySpecificationKnowledgeStore;
import com.morpheus.store.sqlite.adapter.SqliteVersionedRequirementStore;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The page and the count of CURRENT requirements have a default on {@link VersionedRequirementStore} that answers
 * correctly from the whole population, so a third-party store keeps compiling. A store the product ships must not
 * inherit that answer: each one declares both operations itself, so the predicate, the order, the page and the count
 * run in its backend. A new production implementation of the port is held by the same rule.
 */
class ShippedRequirementStoresPageThemselvesArchitectureTest {

    private static final List<String> OPERATIONS_A_SHIPPED_STORE_DECLARES = List.of(
            "pageCurrentRequirementVersions",
            "countCurrentRequirementVersions");

    private static final List<Class<?>> SHIPPED_STORES = List.of(
            MemorySpecificationKnowledgeStore.class,
            SqliteVersionedRequirementStore.class);

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.morpheus");

    @Test
    void everyShippedRequirementStoreDeclaresThePageAndTheCountItself() {
        List<String> missing = new ArrayList<>();
        for (Class<?> store : SHIPPED_STORES) {
            JavaClass imported = classes.get(store);
            for (String operation : OPERATIONS_A_SHIPPED_STORE_DECLARES) {
                boolean declared = imported.getMethods().stream()
                        .map(JavaMethod::getName)
                        .anyMatch(operation::equals);
                if (!declared) {
                    missing.add(store.getName() + " inherits " + operation + " from the port's default");
                }
            }
        }

        assertEquals(List.of(), missing,
                "a store the product ships evaluates the page and the count of CURRENT requirements in its backend");
    }

    @Test
    void theListOfShippedStoresIsEveryProductionImplementationOfThePort() {
        Set<String> implementations = new TreeSet<>();
        for (JavaClass type : classes) {
            if (!type.isInterface() && type.isAssignableTo(VersionedRequirementStore.class)) {
                implementations.add(type.getName());
            }
        }
        Set<String> listed = new TreeSet<>();
        SHIPPED_STORES.forEach(store -> listed.add(store.getName()));

        assertEquals(listed, implementations,
                "every production implementation of VersionedRequirementStore is a shipped store and is listed");
    }
}
