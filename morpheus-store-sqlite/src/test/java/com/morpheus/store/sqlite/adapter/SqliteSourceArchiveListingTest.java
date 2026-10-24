package com.morpheus.store.sqlite.adapter;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The archive listing asks the database for no order: the instant is stored as text that does not sort by time, so
 * the store orders after reading. An {@code ORDER BY} put back would be paid for and then replaced.
 */
class SqliteSourceArchiveListingTest {

    @Test
    void theArchiveListingStatementCarriesNoOrderBy() {
        assertFalse(SqliteSyncStateStore.LIST_ARCHIVES_SQL.toUpperCase().contains("ORDER BY"),
                "SqliteSyncStateStore.LIST_ARCHIVES_SQL must not order: " + SqliteSyncStateStore.LIST_ARCHIVES_SQL);
    }

    @Test
    void theReasonIsWrittenAboveTheStatement() throws IOException {
        String source = Files.readString(Path.of("src/main/java/com/morpheus/store/sqlite/adapter/SqliteSyncStateStore.java"));
        int statement = source.indexOf("static final String LIST_ARCHIVES_SQL");
        assertTrue(statement > 0, "LIST_ARCHIVES_SQL must be declared in SqliteSyncStateStore");

        String above = source.substring(Math.max(0, statement - 400), statement);

        assertTrue(above.contains("does not sort by time"),
                "the comment above LIST_ARCHIVES_SQL must say why the statement has no ORDER BY");
    }

    @Test
    void otherStatementsOfTheClassMayStillOrder() {
        assertTrue(SqliteSyncStateStore.READ_INVENTORY_ENTRIES_SQL.contains("ORDER BY source_path"));
    }
}
