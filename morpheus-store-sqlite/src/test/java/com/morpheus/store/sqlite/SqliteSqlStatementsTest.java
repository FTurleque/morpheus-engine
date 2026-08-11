package com.morpheus.store.sqlite;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
        assertEquals(true, statements.get(2).startsWith("CREATE TRIGGER source_audit"));
        assertEquals(true, statements.get(2).endsWith("END;"));
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
        assertThrows(
                IllegalArgumentException.class,
                () -> SqliteSqlStatements.split("INSERT INTO sample(value) VALUES ('unterminated);"));
    }

    @Test
    void rejectsUnterminatedTriggerBody() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SqliteSqlStatements.split("""
                        CREATE TRIGGER broken AFTER INSERT ON sample
                        BEGIN
                            INSERT INTO sample(value) VALUES ('x');
                        """));
    }
}
