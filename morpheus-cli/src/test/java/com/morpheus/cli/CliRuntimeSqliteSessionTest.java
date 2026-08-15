package com.morpheus.cli;

import com.morpheus.store.sqlite.SqliteConnectionScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CliRuntimeSqliteSessionTest {
    @TempDir
    Path temp;

    @Test
    void oneCliInvocationUsesOnePhysicalSqliteConnectionAndLeaksNone() {
        SqliteConnectionScope.Diagnostics before = SqliteConnectionScope.diagnostics();

        try (CliRuntime ignored = new CliRuntime(temp.resolve("cli.db"))) {
            SqliteConnectionScope.Diagnostics during = SqliteConnectionScope.diagnostics();
            assertEquals(1, during.opened() - before.opened());
            assertEquals(1, during.active() - before.active());
        }

        SqliteConnectionScope.Diagnostics after = SqliteConnectionScope.diagnostics();
        assertEquals(1, after.opened() - before.opened());
        assertEquals(1, after.closed() - before.closed());
        assertEquals(before.active(), after.active());
    }
}
