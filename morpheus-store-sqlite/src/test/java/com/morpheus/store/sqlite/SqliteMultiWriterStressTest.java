package com.morpheus.store.sqlite;

import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.domain.project.ProjectSpecificationId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sustained multi-writer pressure on one SQLite database, from independent stores.
 *
 * <p>RT-01 is about a remote profile with several writers at once, and the property that matters there is not
 * throughput: it is that contention costs latency and explicit failures, never a lost update, a duplicated
 * identity or a lease that nobody gives back. Writers start together on a barrier so the transactions genuinely
 * overlap rather than queueing behind each other by accident of scheduling.</p>
 */
class SqliteMultiWriterStressTest {
    private static final int WRITERS = 8;
    private static final int WRITES_PER_WRITER = 12;

    @TempDir
    Path tempDir;

    /**
     * Every committed write survives, and no two writers agree on a row.
     *
     * <p>The rows are read back over a fresh connection, so the assertion sees the database rather than any
     * writer's view of it. A writer whose transaction lost to the busy timeout fails explicitly; what must never
     * happen is a write that reported success and is not there.</p>
     */
    @Test
    void concurrentWritersNeverLoseACommittedUpdateOrDuplicateAnIdentity() throws Exception {
        Path database = bootstrappedDatabase();
        CountDownLatch ready = new CountDownLatch(WRITERS);
        CountDownLatch start = new CountDownLatch(1);
        Set<String> committed = ConcurrentHashMap.newKeySet();
        AtomicInteger refused = new AtomicInteger();

        try (ExecutorService writers = Executors.newFixedThreadPool(WRITERS)) {
            List<Future<?>> running = new ArrayList<>();
            for (int writer = 0; writer < WRITERS; writer++) {
                running.add(writers.submit(() -> {
                    ready.countDown();
                    start.await();
                    // A physical connection per writer is exactly the multi-writer shape RT-01 describes.
                    try (Connection connection = SqliteDatabaseSecurity.openPhysical(database, 5_000)) {
                        for (int write = 0; write < WRITES_PER_WRITER; write++) {
                            String identity = ProjectSpecificationId.generate().toString();
                            try {
                                insertProject(connection, identity);
                                committed.add(identity);
                            } catch (KnowledgeStoreException refusal) {
                                refused.incrementAndGet();
                            }
                        }
                    }
                    return null;
                }));
            }
            assertTrue(ready.await(30, TimeUnit.SECONDS), "every writer must reach the barrier");
            start.countDown();
            for (Future<?> writer : running) {
                writer.get(120, TimeUnit.SECONDS);
            }
        }

        List<String> persisted = readAllProjectIds(database);
        assertEquals(committed.size(), persisted.size(),
                () -> "a write that reported success must be durable; refused=" + refused.get());
        assertEquals(committed, Set.copyOf(persisted), "no writer may observe another writer's row as its own");
        assertEquals(persisted.size(), Set.copyOf(persisted).size(), "an identity must never be stored twice");
    }

    /**
     * Repeated contention must not accumulate: every lease is given back, so an exclusive acquisition still
     * succeeds once the writers are gone. A leaked lease would make offline maintenance permanently impossible.
     */
    @Test
    void repeatedContentionLeavesNoLeaseBehind() throws Exception {
        Path database = bootstrappedDatabase();
        CountDownLatch start = new CountDownLatch(1);

        try (ExecutorService writers = Executors.newFixedThreadPool(WRITERS)) {
            List<Future<?>> running = new ArrayList<>();
            for (int writer = 0; writer < WRITERS; writer++) {
                running.add(writers.submit(() -> {
                    start.await();
                    for (int round = 0; round < WRITES_PER_WRITER; round++) {
                        // Open and close a scoped connection every round: the lease is taken and released each
                        // time, which is where a release that only ran on the success path would show up.
                        try (Connection connection = SqliteDatabaseSecurity.openPhysical(database, 5_000)) {
                            insertProject(connection, ProjectSpecificationId.generate().toString());
                        } catch (KnowledgeStoreException ignored) {
                            // A refusal under contention is an accepted outcome; the lease still has to come back.
                        }
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> writer : running) {
                writer.get(120, TimeUnit.SECONDS);
            }
        }

        SqliteServerMaintenance.ServerLease lease = new SqliteServerMaintenance().acquireServerLease(database);
        assertFalse(readAllProjectIds(database).isEmpty(), "the stress run must have committed something");
        lease.close();
    }

    private Path bootstrappedDatabase() {
        Path database = tempDir.resolve("multi-writer.db");
        try (SqliteSpecificationKnowledgeStore bootstrap = new SqliteSpecificationKnowledgeStore(database)) {
            // Migrates the schema once, so the writers contend on writes rather than on migration.
        }
        return database;
    }

    private void insertProject(Connection connection, String identity) {
        SqliteTransactionRunner.runVoid(connection, "Cannot store project " + identity, current -> {
            try (PreparedStatement statement = current.prepareStatement(
                    "INSERT INTO projects(id, root_scheme, root_value) VALUES (?, 'file', ?)")) {
                statement.setString(1, identity);
                statement.setString(2, "stress/" + identity);
                statement.executeUpdate();
            }
        });
    }

    private List<String> readAllProjectIds(Path database) throws SQLException {
        List<String> identities = new ArrayList<>();
        try (Connection connection = SqliteDatabaseSecurity.openPhysical(database, 5_000);
             Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT id FROM projects ORDER BY id")) {
            while (rows.next()) {
                identities.add(rows.getString(1));
            }
        }
        return identities;
    }
}
