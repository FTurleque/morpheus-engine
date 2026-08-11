package com.morpheus.store.sqlite;

import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteSqlStatementsTest {

    @Test
    void preservesSemicolonsInsideStringLiterals() {
        List<String> statements = SqliteSqlStatements.split("""
                CREATE TABLE sample(value TEXT NOT NULL);
                INSERT INTO sample(value) VALUES ('alpha;beta');
                """);

        assertEquals(2, statements.size());
        assertEquals("CREATE TABLE sample(value TEXT NOT NULL);", statements.get(0));
        assertEquals("INSERT INTO sample(value) VALUES ('alpha;beta');", statements.get(1));
    }

    @Test
    void preservesTriggerBodyIncludingCaseExpressions() {
        List<String> statements = SqliteSqlStatements.split("""
                CREATE TABLE source(id INTEGER PRIMARY KEY, value INTEGER);
                CREATE TABLE audit(value TEXT);
                CREATE TRIGGER source_audit AFTER UPDATE ON source
                BEGIN
                    INSERT INTO audit(value) VALUES ('before;after');
                    INSERT INTO audit(value) VALUES (
                        CASE WHEN NEW.value > 0 THEN 'positive;value' ELSE 'other' END
                    );
                END;
                INSERT INTO source(id, value) VALUES (1, 1);
                """);

        assertEquals(4, statements.size());
        assertTrue(statements.get(2).startsWith("CREATE TRIGGER source_audit"));
        assertTrue(statements.get(2).endsWith("END;"));
    }

    @Test
    void executesTriggerWithLiteralSemicolonsAgainstSQLite() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            new SqliteSchemaManager().executeScript(connection, """
                    CREATE TABLE source(id INTEGER PRIMARY KEY, value INTEGER);
                    CREATE TABLE audit(value TEXT NOT NULL);
                    CREATE TRIGGER source_audit AFTER UPDATE ON source
                    BEGIN
                        INSERT INTO audit(value) VALUES (
                            CASE WHEN NEW.value > 0 THEN 'positive;value' ELSE 'other' END
                        );
                    END;
                    INSERT INTO source(id, value) VALUES (1, 0);
                    UPDATE source SET value = 1 WHERE id = 1;
                    """);

            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("SELECT value FROM audit")) {
                assertTrue(result.next());
                assertEquals("positive;value", result.getString(1));
                assertFalse(result.next());
            }
        }
    }

    @Test
    void invalidScriptRollsBackWithoutAdvancingMigrationLedger() throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TABLE schema_migrations (
                            version INTEGER PRIMARY KEY,
                            name TEXT NOT NULL
                        )
                        """);
            }

            assertThrows(
                    RuntimeException.class,
                    () -> SqliteTransactionRunner.runVoid(connection, "synthetic migration failed", current -> {
                        new SqliteSchemaManager().executeScript(current, """
                                CREATE TABLE must_be_rolled_back(value TEXT);
                                INSERT INTO must_be_rolled_back(value) VALUES ('before;failure');
                                THIS IS NOT VALID SQLITE;
                                """);
                        try (Statement statement = current.createStatement()) {
                            statement.executeUpdate(
                                    "INSERT INTO schema_migrations(version, name) VALUES (99, 'invalid')");
                        }
                    }));

            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery(
                         "SELECT COUNT(*) FROM sqlite_master WHERE type = 'table' AND name = 'must_be_rolled_back'")) {
                assertTrue(result.next());
                assertEquals(0, result.getInt(1));
            }
            try (Statement statement = connection.createStatement();
                 ResultSet result = statement.executeQuery("SELECT COUNT(*) FROM schema_migrations")) {
                assertTrue(result.next());
                assertEquals(0, result.getInt(1));
            }
        }
    }

    @Test
    void ignoresSemicolonsInCommentsAndQuotedIdentifiers() {
        List<String> statements = SqliteSqlStatements.split("""
                -- comment ; must not split
                CREATE TABLE "semi;colon"(id INTEGER); /* block ; comment */
                INSERT INTO "semi;colon"(id) VALUES (1);
                """);

        assertEquals(2, statements.size());
    }

    @Test
    void rejectsUnterminatedQuotedContent() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> SqliteSqlStatements.split("INSERT INTO sample(value) VALUES ('unterminated);"));
        assertTrue(error.getMessage().contains("line 1, column"));
    }

    @Test
    void rejectsUnterminatedTriggerBody() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> SqliteSqlStatements.split("""
                        CREATE TRIGGER broken AFTER INSERT ON sample
                        BEGIN
                            INSERT INTO sample(value) VALUES ('x');
                        """));
        assertTrue(error.getMessage().contains("line 4, column"));
    }
}
