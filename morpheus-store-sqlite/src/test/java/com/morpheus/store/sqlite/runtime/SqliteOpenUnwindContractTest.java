package com.morpheus.store.sqlite.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Opening a physical connection acquires the database lease before it can fail, and it can fail after the
 * connection exists: the driver opens lazily, so the first statement is what rejects a file that is not a
 * database. Unwinding must close that connection and, only then, give the lease back.
 *
 * <p>Releasing the lease while the close is unresolved is what would let offline maintenance start over a
 * handle that may still be alive, which is the same rule the guarded connection follows.</p>
 */
class SqliteOpenUnwindContractTest {

    @TempDir
    Path temp;

    @Test
    void anOpenThatFailsAfterConnectingReleasesBothTheConnectionAndTheLease() throws Exception {
        Path database = temp.resolve("not-a-database.db").toAbsolutePath().normalize();
        Files.writeString(database, "this is definitely not a sqlite database", StandardCharsets.UTF_8);

        SQLException firstFailure = assertThrows(SQLException.class, () -> SqliteDatabaseSecurity.openPhysical(
                database, SqliteDatabaseSecurity.DEFAULT_BUSY_TIMEOUT_MILLIS));
        assertTrue(firstFailure.getMessage().contains("not a database"), firstFailure.getMessage());

        // The exclusive lease is refused while any shared reference is outstanding, so taking it is what proves
        // the failed open gave its reference back rather than leaving the database reserved for the process.
        SqliteDatabaseLease.acquireExclusive(database).close();

        // And the unwind is repeatable: a second failed open must not double-release or leave a reference behind.
        SQLException secondFailure = assertThrows(SQLException.class, () -> SqliteDatabaseSecurity.openPhysical(
                database, SqliteDatabaseSecurity.DEFAULT_BUSY_TIMEOUT_MILLIS));
        assertTrue(secondFailure.getMessage().contains("not a database"), secondFailure.getMessage());
        SqliteDatabaseLease.acquireExclusive(database).close();
    }
}
