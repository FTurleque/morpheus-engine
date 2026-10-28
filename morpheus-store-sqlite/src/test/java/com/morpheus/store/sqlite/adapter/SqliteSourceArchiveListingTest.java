package com.morpheus.store.sqlite.adapter;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The archive listing asks the database for one page in the order of the archive record: the instant is stored as text
 * that does not sort by time, so the statement orders by the fixed-width key of that instant, then by the columns that
 * break its ties, and bounds the page in the query.
 */
class SqliteSourceArchiveListingTest {

    @Test
    void theArchiveListingStatementOrdersByTheKeyAndBoundsThePage() {
        String sql = SqliteSyncStateStore.LIST_ARCHIVES_SQL;

        assertTrue(sql.contains("ORDER BY archived_key, source_path, reason, fingerprint_sha256"),
                "SqliteSyncStateStore.LIST_ARCHIVES_SQL must order as SourceArchiveRecord compares: " + sql);
        assertTrue(sql.contains("LIMIT ? OFFSET ?"), "SqliteSyncStateStore.LIST_ARCHIVES_SQL must bound the page: " + sql);
    }

    @Test
    void theReasonIsWrittenAboveTheStatement() throws IOException {
        String source = Files.readString(Path.of("src/main/java/com/morpheus/store/sqlite/adapter/SqliteSyncStateStore.java"));
        int statement = source.indexOf("static final String LIST_ARCHIVES_SQL");
        assertTrue(statement > 0, "LIST_ARCHIVES_SQL must be declared in SqliteSyncStateStore");

        String above = source.substring(Math.max(0, statement - 500), statement);

        assertTrue(above.contains("does not sort by time"),
                "the comment above LIST_ARCHIVES_SQL must say why the statement orders by archived_key");
    }

    @Test
    void otherStatementsOfTheClassMayStillOrder() {
        assertTrue(SqliteSyncStateStore.READ_INVENTORY_ENTRIES_SQL.contains("ORDER BY source_path"));
    }
}
