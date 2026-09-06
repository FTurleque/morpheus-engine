package com.morpheus.store.memory;

import com.morpheus.application.query.dsl.ProjectQueryScope;
import com.morpheus.application.query.dsl.QueryBudgets;
import com.morpheus.application.query.dsl.QueryDefinition;
import com.morpheus.application.query.dsl.QueryEntityType;
import com.morpheus.application.query.dsl.QueryPage;
import com.morpheus.application.query.saved.SavedViewDefinition;
import com.morpheus.application.query.saved.SavedViewId;
import com.morpheus.application.query.saved.SavedViewStatus;
import com.morpheus.application.query.saved.SavedViewVersion;
import com.morpheus.domain.project.ProjectSpecificationId;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MemorySavedViewStoreBudgetTest {
    private static final Instant NOW = Instant.parse("2026-09-06T14:20:00Z");

    @Test
    void createEnforcesScopeBudgetInsideTheStore() {
        MemorySavedViewStore store = new MemorySavedViewStore();
        ProjectQueryScope scope = new ProjectQueryScope(ProjectSpecificationId.generate());
        QueryDefinition query = QueryDefinition.all(scope, QueryEntityType.CHANGE, QueryPage.first(10));

        for (int index = 0; index < QueryBudgets.MAX_SAVED_VIEWS_PER_SCOPE; index++) {
            SavedViewDefinition definition = definition("view-" + index, query);
            store.create(definition, version(definition));
        }

        SavedViewDefinition overflow = definition("overflow", query);
        assertThrows(IllegalStateException.class, () -> store.create(overflow, version(overflow)));
        assertEquals(QueryBudgets.MAX_SAVED_VIEWS_PER_SCOPE, store.count(scope));
    }

    private SavedViewDefinition definition(String name, QueryDefinition query) {
        return new SavedViewDefinition(
                SavedViewId.generate(), name, query, 1L, SavedViewStatus.ACTIVE, NOW, NOW);
    }

    private SavedViewVersion version(SavedViewDefinition definition) {
        return new SavedViewVersion(
                definition.id(), definition.revision(), definition.name(), definition.query(),
                definition.status(), definition.updatedAt());
    }
}
