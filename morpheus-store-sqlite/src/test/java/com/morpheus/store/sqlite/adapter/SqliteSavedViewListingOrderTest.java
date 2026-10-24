package com.morpheus.store.sqlite.adapter;

import com.morpheus.application.query.dsl.ProjectQueryScope;
import com.morpheus.application.query.dsl.QueryDefinition;
import com.morpheus.application.query.dsl.QueryEntityType;
import com.morpheus.application.query.dsl.QueryPage;
import com.morpheus.application.query.saved.SavedViewDefinition;
import com.morpheus.application.query.saved.SavedViewEntry;
import com.morpheus.application.query.saved.SavedViewId;
import com.morpheus.application.query.saved.SavedViewStatus;
import com.morpheus.application.query.saved.SavedViewVersion;
import com.morpheus.domain.project.ProjectSpecificationId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The listing of a scope orders by name without regard to case, then by id: the order the index now delivers. */
class SqliteSavedViewListingOrderTest {
    private static final Instant NOW = Instant.parse("2026-10-17T10:00:00Z");

    @TempDir
    Path tempDir;

    @Test
    void viewsAreListedByNameWithoutRegardToCaseThenById() {
        try (SqliteSavedViewStore store = new SqliteSavedViewStore(tempDir.resolve("saved-views.db"))) {
            ProjectQueryScope scope = new ProjectQueryScope(ProjectSpecificationId.generate());
            QueryDefinition query = QueryDefinition.all(scope, QueryEntityType.CHANGE, QueryPage.first(10));
            SavedViewDefinition beta = create(store, "beta", query);
            SavedViewDefinition upperAlpha = create(store, "Alpha", query);
            SavedViewDefinition lowerAlpha = create(store, "alpha", query);

            List<SavedViewId> listed = store.list(scope).stream()
                    .map(entry -> ((SavedViewEntry.Readable) entry).definition().id())
                    .toList();

            List<SavedViewId> alphas = List.of(upperAlpha.id(), lowerAlpha.id()).stream()
                    .sorted()
                    .toList();
            assertEquals(List.of(alphas.get(0), alphas.get(1), beta.id()), listed);
        }
    }

    private static SavedViewDefinition create(SqliteSavedViewStore store, String name, QueryDefinition query) {
        SavedViewDefinition definition = new SavedViewDefinition(
                SavedViewId.generate(), name, query, 1L, SavedViewStatus.ACTIVE, NOW, NOW);
        store.create(definition, new SavedViewVersion(
                definition.id(), definition.revision(), definition.name(), definition.query(),
                definition.status(), definition.updatedAt()));
        return definition;
    }
}
