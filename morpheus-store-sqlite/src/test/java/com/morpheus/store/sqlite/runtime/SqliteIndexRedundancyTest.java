package com.morpheus.store.sqlite.runtime;

import com.morpheus.store.sqlite.adapter.SqliteSpecificationKnowledgeStore;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The migrated schema holds no index that repeats the leading columns of another (sqlite-index-coverage). */
class SqliteIndexRedundancyTest {

    @TempDir
    Path tempDir;

    @Test
    void aMigratedDatabaseHoldsNoRepeatedIndex() throws Exception {
        Path database = tempDir.resolve("migrated.db");
        try (var ignored = new SqliteSpecificationKnowledgeStore(database)) {
            // The constructor applies every migration.
        }

        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath())) {
            assertEquals(List.of(), SqliteIndexRepetitions.find(connection));
        }
    }

    @Test
    void anIndexThatRepeatsThePrimaryKeyIsNamedWithItsTableAndTheIndexItRepeats() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE child (parent TEXT NOT NULL, ordinal INTEGER NOT NULL, "
                    + "PRIMARY KEY (parent, ordinal))");
            statement.execute("CREATE INDEX idx_child_parent ON child(parent)");

            List<String> repetitions = SqliteIndexRepetitions.find(connection);

            assertEquals(1, repetitions.size(), repetitions.toString());
            assertTrue(repetitions.getFirst().startsWith("index idx_child_parent of table child repeats the leading part of "),
                    repetitions.getFirst());
        }
    }

    @Test
    void anIndexThatRepeatsAUniqueConstraintExactlyIsNamed() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE versions (pack TEXT NOT NULL, number INTEGER NOT NULL, UNIQUE (pack, number))");
            statement.execute("CREATE INDEX idx_versions_pack ON versions(pack, number)");

            List<String> repetitions = SqliteIndexRepetitions.find(connection);

            assertEquals(1, repetitions.size(), repetitions.toString());
            assertTrue(repetitions.getFirst().startsWith("index idx_versions_pack of table versions repeats exactly "),
                    repetitions.getFirst());
        }
    }

    @Test
    void anIndexThatSharesItsLeadingColumnWithAPartialIndexOnlyIsNotReported() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE versions (snapshot TEXT NOT NULL, identity TEXT NOT NULL, state TEXT NOT NULL)");
            statement.execute("CREATE UNIQUE INDEX uq_current ON versions(snapshot, identity) WHERE state = 'CURRENT'");
            statement.execute("CREATE INDEX idx_snapshot ON versions(snapshot)");

            assertEquals(List.of(), SqliteIndexRepetitions.find(connection));
        }
    }

    @Test
    void indexesWithAnotherColumnOrderOrAnotherCollationAreNotReported() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE items (scope TEXT NOT NULL, name TEXT NOT NULL, id TEXT NOT NULL, "
                    + "PRIMARY KEY (scope, id))");
            statement.execute("CREATE INDEX idx_reverse ON items(id, scope)");
            statement.execute("CREATE INDEX idx_nocase ON items(scope COLLATE NOCASE)");

            assertEquals(List.of(), SqliteIndexRepetitions.find(connection));
        }
    }
}
