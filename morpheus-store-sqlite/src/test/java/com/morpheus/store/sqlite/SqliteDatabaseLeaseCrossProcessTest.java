package com.morpheus.store.sqlite;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The lease is an operating-system lock, so it holds across processes, not only across threads of one JVM: another
 * MORPHEUS process sharing the database does not stop this one from reading it, and keeps offline maintenance out
 * (STO-AUD-5).
 */
class SqliteDatabaseLeaseCrossProcessTest {
    @TempDir
    Path temp;

    @Test
    void aDatabaseSharedByAnotherProcessCanBeSharedButNotTakenExclusively() throws Exception {
        Path database = temp.resolve("shared.db").toAbsolutePath().normalize();
        try (var created = SqliteDatabaseSecurity.open(database)) {
            assertFalse(created.isClosed());
        }

        Process holder = startHolder("shared", database);
        try {
            try (var reader = SqliteDatabaseSecurity.open(database)) {
                assertFalse(reader.isClosed());
            }
            IllegalStateException refused = assertThrows(IllegalStateException.class,
                    () -> SqliteDatabaseLease.acquireExclusive(database));
            assertEquals("SQLite database is still open in another MORPHEUS process", refused.getMessage());
        } finally {
            release(holder);
        }

        try (SqliteDatabaseLease.Lease exclusive = SqliteDatabaseLease.acquireExclusive(database)) {
            assertNotNull(exclusive);
        }
    }

    @Test
    void aDatabaseReservedByAnotherProcessCannotBeOpened() throws Exception {
        Path database = temp.resolve("reserved.db").toAbsolutePath().normalize();
        try (var created = SqliteDatabaseSecurity.open(database)) {
            assertFalse(created.isClosed());
        }

        Process holder = startHolder("exclusive", database);
        try {
            IllegalStateException refused = assertThrows(IllegalStateException.class,
                    () -> SqliteDatabaseSecurity.open(database));
            assertEquals("SQLite database is reserved for exclusive maintenance", refused.getMessage());
        } finally {
            release(holder);
        }

        try (var reopened = SqliteDatabaseSecurity.open(database)) {
            assertFalse(reopened.isClosed());
        }
    }

    private static Process startHolder(String mode, Path database) throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win") ? "java.exe" : "java")
                .toString();
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        Process process = new ProcessBuilder(List.of(java, "-cp", classpath,
                SqliteLeaseHoldingProcess.class.getName(), mode, database.toString()))
                .redirectError(ProcessBuilder.Redirect.INHERIT)
                .start();
        BufferedReader output = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        String line = output.readLine();
        if (!"HELD".equals(line)) {
            process.destroyForcibly();
            throw new AssertionError("the second process did not take the lease: " + line);
        }
        return process;
    }

    private static void release(Process holder) throws Exception {
        holder.getOutputStream().close();
        if (!holder.waitFor(30, TimeUnit.SECONDS)) {
            holder.destroyForcibly();
            throw new AssertionError("the second process did not release the lease");
        }
        assertEquals(0, holder.exitValue());
    }
}
