package com.morpheus.store.sqlite;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.morpheus.application.query.dsl.ProjectQueryScope;
import com.morpheus.application.query.dsl.QueryDefinition;
import com.morpheus.application.query.dsl.QueryEntityType;
import com.morpheus.application.query.dsl.QueryPage;
import com.morpheus.application.query.saved.SavedViewConflictException;
import com.morpheus.application.query.saved.SavedViewDefinition;
import com.morpheus.application.query.saved.SavedViewEntry;
import com.morpheus.application.query.saved.SavedViewId;
import com.morpheus.application.query.saved.SavedViewStatus;
import com.morpheus.application.query.saved.SavedViewVersion;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.domain.project.ProjectSpecificationId;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * One row whose stored definition cannot be decoded must not make its whole scope unusable, and must not vanish
 * from it either. Installations that already hold such a row (a view written past the old decode bound) cannot
 * be repaired by a bound alone: the row has to be listed by name and retirable without being read.
 */
class SqliteSavedViewUnreadableRowTest {
    private static final Instant NOW = Instant.parse("2026-09-25T10:00:00Z");

    @TempDir
    Path tempDir;

    @Test
    void aListNamesTheUnreadableRowAndStillReturnsTheReadableOnes() throws SQLException {
        Path database = tempDir.resolve("views.db");
        ProjectQueryScope scope = new ProjectQueryScope(ProjectSpecificationId.generate());
        SavedViewDefinition good;
        SavedViewDefinition poisoned;
        try (SqliteSavedViewStore store = new SqliteSavedViewStore(database)) {
            good = create(store, scope, "a-good");
            poisoned = create(store, scope, "b-poisoned");
        }
        poison(database, poisoned.id());
        try (SqliteSavedViewStore store = new SqliteSavedViewStore(database)) {
            List<SavedViewEntry> entries = store.list(scope);

            assertEquals(2, entries.size());
            assertEquals(good, assertInstanceOf(SavedViewEntry.Readable.class, entries.get(0)).definition());
            SavedViewEntry.Unreadable unreadable = assertInstanceOf(SavedViewEntry.Unreadable.class, entries.get(1));
            assertEquals(poisoned.id(), unreadable.id());
            assertEquals(1L, unreadable.revision());
            assertEquals(SavedViewStatus.ACTIVE, unreadable.status());
            assertFalse(unreadable.reason().isBlank());
        }
    }

    @Test
    void readingTheUnreadableViewByIdSaysToArchiveIt() throws SQLException {
        Path database = tempDir.resolve("find.db");
        ProjectQueryScope scope = new ProjectQueryScope(ProjectSpecificationId.generate());
        SavedViewDefinition poisoned = seedPoisoned(database, scope);
        try (SqliteSavedViewStore store = new SqliteSavedViewStore(database)) {
            KnowledgeStoreException failure =
                    assertThrows(KnowledgeStoreException.class, () -> store.find(poisoned.id()));

            assertTrue(failure.getMessage().contains(poisoned.id().toString()), failure.getMessage());
            assertTrue(failure.getMessage().contains("archive it"), failure.getMessage());
        }
    }

    @Test
    void anUnreadableViewCanBeArchivedWithoutDecodingItsDefinitionAndKeepsItsHistory() throws SQLException {
        Path database = tempDir.resolve("archive.db");
        ProjectQueryScope scope = new ProjectQueryScope(ProjectSpecificationId.generate());
        SavedViewDefinition poisoned = seedPoisoned(database, scope);
        try (SqliteSavedViewStore store = new SqliteSavedViewStore(database)) {
            SavedViewEntry archived = store.archive(poisoned.id(), 1L, NOW.plusSeconds(5));

            SavedViewEntry.Unreadable entry = assertInstanceOf(SavedViewEntry.Unreadable.class, archived);
            assertEquals(SavedViewStatus.ARCHIVED, entry.status());
            assertEquals(2L, entry.revision());
            assertEquals(SavedViewStatus.ARCHIVED, store.list(scope).getFirst().status());
        }
        assertEquals(2, versionRows(database, poisoned.id()), "the retired revision is recorded, not overwritten");
    }

    @Test
    void archivingRefusesAnUnknownAnArchivedAndAStaleViewInThatOrder() throws SQLException {
        Path database = tempDir.resolve("refusals.db");
        ProjectQueryScope scope = new ProjectQueryScope(ProjectSpecificationId.generate());
        try (SqliteSavedViewStore store = new SqliteSavedViewStore(database)) {
            SavedViewDefinition view = create(store, scope, "readable");

            assertThrows(IllegalArgumentException.class,
                    () -> store.archive(SavedViewId.generate(), 1L, NOW));
            SavedViewConflictException stale = assertThrows(SavedViewConflictException.class,
                    () -> store.archive(view.id(), 7L, NOW));
            assertTrue(stale.getMessage().contains("expected 7 but current is 1"), stale.getMessage());

            SavedViewEntry.Readable archived =
                    assertInstanceOf(SavedViewEntry.Readable.class, store.archive(view.id(), 1L, NOW));
            assertEquals(SavedViewStatus.ARCHIVED, archived.definition().status());
            assertThrows(IllegalStateException.class, () -> store.archive(view.id(), 2L, NOW));
        }
    }

    private SavedViewDefinition seedPoisoned(Path database, ProjectQueryScope scope) throws SQLException {
        SavedViewDefinition poisoned;
        try (SqliteSavedViewStore store = new SqliteSavedViewStore(database)) {
            poisoned = create(store, scope, "poisoned");
        }
        poison(database, poisoned.id());
        return poisoned;
    }

    private SavedViewDefinition create(SqliteSavedViewStore store, ProjectQueryScope scope, String name) {
        QueryDefinition query = QueryDefinition.all(scope, QueryEntityType.CHANGE, QueryPage.first(10));
        SavedViewDefinition definition =
                new SavedViewDefinition(SavedViewId.generate(), name, query, 1L, SavedViewStatus.ACTIVE, NOW, NOW);
        store.create(definition, new SavedViewVersion(
                definition.id(), 1L, name, query, SavedViewStatus.ACTIVE, NOW));
        return definition;
    }

    private void poison(Path database, SavedViewId id) throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("UPDATE saved_views SET query_definition = 'not-a-definition' WHERE id = '"
                    + id + "'");
        }
    }

    private int versionRows(Path database, SavedViewId id) throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(
                     "SELECT COUNT(*) FROM saved_view_versions WHERE saved_view_id = '" + id + "'")) {
            result.next();
            return result.getInt(1);
        }
    }
}
