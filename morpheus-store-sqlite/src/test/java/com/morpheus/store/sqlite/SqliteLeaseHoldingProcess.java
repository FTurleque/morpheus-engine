package com.morpheus.store.sqlite;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * A second JVM holding the database lease: {@code shared} opens a connection, {@code exclusive} takes the
 * maintenance lease. It prints {@code HELD} once the lease is held and releases it when its standard input closes.
 */
public final class SqliteLeaseHoldingProcess {
    private SqliteLeaseHoldingProcess() {
    }

    public static void main(String[] args) throws Exception {
        Path database = Path.of(args[1]);
        AutoCloseable held = args[0].equals("exclusive")
                ? SqliteDatabaseLease.acquireExclusive(database)
                : SqliteDatabaseSecurity.open(database);
        try (held; BufferedReader input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8))) {
            System.out.println("HELD");
            System.out.flush();
            while (input.readLine() != null) {
                // Held until the parent closes our standard input.
            }
        }
    }
}
