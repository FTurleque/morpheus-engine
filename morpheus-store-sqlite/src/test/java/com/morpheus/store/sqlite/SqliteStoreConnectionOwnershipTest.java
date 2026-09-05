package com.morpheus.store.sqlite;

import com.morpheus.application.store.KnowledgeStoreException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A store constructor acquires a connection and then does more work that can fail.
 *
 * <p>Every store used to unwind from a {@code catch (SQLException | RuntimeException)}, so an {@link Error}
 * raised while migrating — an {@link ExceptionInInitializerError} from a static initializer, a
 * {@link LinkageError} from a mismatched driver — walked past the cleanup. The connection and the shared
 * database lease behind it stayed held with nobody left to release them: the constructor never returns, so the
 * object that would have closed them was never built.</p>
 *
 * <p>The preparation step runs at exactly the point where that used to happen, which is what makes the failure
 * reproducible without a seam that exists only for tests.</p>
 */
class SqliteStoreConnectionOwnershipTest {

    @TempDir
    Path temp;

    @Test
    void anErrorAfterTheConnectionIsAcquiredStillReleasesItAndItsLease() {
        Path database = temp.resolve("error-during-initialization.db").toAbsolutePath().normalize();
        LinkageError injected = new LinkageError("injected initialization Error");

        LinkageError thrown = assertThrows(LinkageError.class, () -> SqliteStoreConnection.openAndMigrate(
                database,
                "Cannot initialize the test store",
                connection -> {
                    throw injected;
                }));

        assertSame(injected, thrown, "an Error must reach the caller unchanged, not wrapped");
        assertEquals(0, SqliteDatabaseLease.retainedCount(database),
                "the connection closed cleanly, so nothing is left retained");

        // The exclusive lease is refused while any shared reference is outstanding: taking it proves the failed
        // initialization gave its reference back rather than reserving the database for the process.
        SqliteDatabaseLease.acquireExclusive(database).close();
    }

    @Test
    void aRuntimeFailureDuringInitializationIsReportedUnderTheStoreName() {
        Path database = temp.resolve("runtime-during-initialization.db").toAbsolutePath().normalize();
        IllegalStateException injected = new IllegalStateException("injected initialization failure");

        KnowledgeStoreException thrown = assertThrows(
                KnowledgeStoreException.class,
                () -> SqliteStoreConnection.openAndMigrate(
                        database,
                        "Cannot initialize the test store",
                        connection -> {
                            throw injected;
                        }));

        assertEquals("Cannot initialize the test store", thrown.getMessage());
        assertSame(injected, thrown.getCause());
        SqliteDatabaseLease.acquireExclusive(database).close();
    }

    /** A store failure already named for the caller is not renamed on the way out. */
    @Test
    void aKnowledgeStoreFailureIsNotRewrapped() {
        Path database = temp.resolve("already-named.db").toAbsolutePath().normalize();
        KnowledgeStoreException injected = new KnowledgeStoreException("already named for the caller");

        assertSame(injected, assertThrows(
                KnowledgeStoreException.class,
                () -> SqliteStoreConnection.openAndMigrate(
                        database,
                        "Cannot initialize the test store",
                        connection -> {
                            throw injected;
                        })));
        SqliteDatabaseLease.acquireExclusive(database).close();
    }

    @Test
    void anOutOfRangeBusyTimeoutIsRejectedBeforeAnythingIsAcquired() {
        Path database = temp.resolve("bad-timeout.db").toAbsolutePath().normalize();

        assertThrows(IllegalArgumentException.class,
                () -> SqliteStoreConnection.openAndMigrate(database, 0, "Cannot initialize the test store"));
        assertThrows(IllegalArgumentException.class,
                () -> SqliteStoreConnection.openAndMigrate(database, 60_001, "Cannot initialize the test store"));

        assertTrue(java.nio.file.Files.notExists(database), "a rejected timeout must not have opened anything");
        SqliteDatabaseLease.acquireExclusive(database).close();
    }
}
