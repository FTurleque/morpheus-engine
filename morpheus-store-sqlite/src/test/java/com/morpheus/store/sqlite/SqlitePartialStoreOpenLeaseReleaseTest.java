package com.morpheus.store.sqlite;

import com.morpheus.application.operability.StartupOwnership;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins that opening a sequence of stores is all-or-nothing with respect to database leases.
 *
 * <p>Runtimes that assemble several stores at once -- the MCP runtime opens seven -- used to assign each one
 * straight to a field, so a failure partway left the earlier stores open with no way to reach them: the
 * constructor never returns, so nothing can close a half-built runtime. Every open store holds a shared lease,
 * and the exclusive lease refuses to be taken while one exists, which makes it a real leak detector rather than
 * a stand-in for one.</p>
 */
class SqlitePartialStoreOpenLeaseReleaseTest {
    @TempDir
    Path tempDir;

    /** Establishes that the detector detects; without this the leak assertion below could pass vacuously. */
    @Test
    void anOpenStoreDoesBlockTheExclusiveLease() {
        Path database = tempDir.resolve("detector.db");

        try (SqliteSpecificationKnowledgeStore ignored = new SqliteSpecificationKnowledgeStore(database)) {
            IllegalStateException refused = assertThrows(
                    IllegalStateException.class,
                    () -> SqliteDatabaseLease.acquireExclusive(database));
            assertTrue(
                    refused.getMessage().contains("still open"),
                    () -> "expected the exclusive lease to refuse: " + refused.getMessage());
        }

        assertDoesNotThrow(() -> takeAndReleaseExclusiveLease(database));
    }

    @Test
    void aFailureWhileOpeningStoresReleasesTheOnesAlreadyOpen() {
        Path database = tempDir.resolve("partial-open.db");

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> openThreeStoresThenFail(database));

        assertTrue(failure.getMessage().contains("cannot open the fourth store"));
        assertDoesNotThrow(
                () -> takeAndReleaseExclusiveLease(database),
                "the stores opened before the failure must have been closed");
    }

    /** Reproduces the opening sequence of a multi-store runtime and fails partway through it. */
    private static void openThreeStoresThenFail(Path database) {
        try (StartupOwnership owned = new StartupOwnership()) {
            owned.keep(
                    new SqliteSpecificationKnowledgeStore(database),
                    SqliteSpecificationKnowledgeStore::close);
            owned.keep(
                    new SqliteVersionedRequirementStore(database),
                    SqliteVersionedRequirementStore::close);
            owned.keep(
                    new SqliteSnapshotBusinessContentStore(database),
                    SqliteSnapshotBusinessContentStore::close);
            throw new IllegalStateException("cannot open the fourth store");
        }
    }

    private static void takeAndReleaseExclusiveLease(Path database) {
        SqliteDatabaseLease.acquireExclusive(database).close();
    }
}
