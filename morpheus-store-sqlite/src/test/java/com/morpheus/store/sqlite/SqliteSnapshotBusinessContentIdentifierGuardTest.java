package com.morpheus.store.sqlite;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Pins the SQL-identifier guard on the two helpers that interpolate a name into their statement.
 *
 * <p>A table or column name cannot be a bound parameter, so {@code insertOrderedValues} and
 * {@code readOrderedValues} build theirs into the SQL text. Every call site passes a literal today; the guard is
 * what keeps that true, so that reusing either helper with a value derived from input fails closed instead of
 * composing SQL. These are the shapes an attacker-supplied identifier would take.</p>
 */
class SqliteSnapshotBusinessContentIdentifierGuardTest {
    @Test
    void identifiersThatCouldCloseOrExtendTheStatementAreRefused() {
        List<String> hostile = List.of(
                "snapshot_change_scope; DROP TABLE snapshot_business_content--",
                "snapshot_change_scope WHERE 1=1",
                "\"snapshot_change_scope\"",
                "snapshot_change_scope)",
                "snapshot change scope",
                "snapshot_change_scope--",
                "'x'",
                "",
                "1table",
                "Snapshot_Change_Scope");

        for (String identifier : hostile) {
            IllegalArgumentException refusal = assertThrows(
                    IllegalArgumentException.class,
                    () -> SqliteSnapshotBusinessContentStore.requireSqlIdentifier(identifier, "table"),
                    () -> "identifier must be refused: " + identifier);
            assertEquals(
                    "SQLite table identifier must match [a-z][a-z0-9_]{0,63}",
                    refusal.getMessage());
        }
    }

    @Test
    void theIdentifiersTheStoreActuallyUsesAreAccepted() {
        List<String> accepted = List.of(
                "snapshot_change_scope",
                "snapshot_change_out_of_scope",
                "snapshot_change_risks",
                "snapshot_scenario_preconditions",
                "snapshot_constraint_blocking_targets",
                "snapshot_constraint_supporting_evidence",
                "change_id",
                "scenario_id",
                "constraint_id");

        for (String identifier : accepted) {
            assertEquals(
                    identifier,
                    SqliteSnapshotBusinessContentStore.requireSqlIdentifier(identifier, "table"),
                    () -> "must accept: " + identifier);
        }
    }

    @Test
    void aNullIdentifierIsRefused() {
        assertThrows(
                NullPointerException.class,
                () -> SqliteSnapshotBusinessContentStore.requireSqlIdentifier(null, "table"));
    }
}
