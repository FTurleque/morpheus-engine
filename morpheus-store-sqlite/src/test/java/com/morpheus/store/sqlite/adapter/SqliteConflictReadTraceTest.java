package com.morpheus.store.sqlite.adapter;

import com.morpheus.application.lifecycle.mutation.ChangeLifecycleMutationAttempt;
import com.morpheus.application.lifecycle.mutation.ChangeLifecycleMutationPersistenceResult;
import com.morpheus.application.lifecycle.mutation.ChangeLifecycleMutationPersistenceState;
import com.morpheus.application.operability.OperationalEventSink;
import com.morpheus.application.operability.OperationalMetrics;
import com.morpheus.application.operability.OperationalRecorder;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleIdempotencyKey;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleMutationId;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleRevision;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleState;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.source.SourceLocator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A lifecycle conflict answered without one of the reads that would have qualified it says so.
 *
 * <p>When a mutation collides, the store reads the audit record - to tell a mutation that was already applied from a
 * real collision - and the current state, to hand it back with the conflict. Either read can fail on a database that
 * breaks between two statements, and the conflict is then answered without it. The caller cannot tell; the counter
 * can. The database is a real one, broken the way an operator could break it: a table moved away under an open
 * store.</p>
 */
class SqliteConflictReadTraceTest {
    private static final String COUNTER = "failure.suppressed.sqlite-conflict-read";
    private static final ProviderId PROVIDER = new ProviderId("conflict-read-fixture");

    @TempDir
    Path temporaryDirectory;

    private final OperationalMetrics metrics = new OperationalMetrics();
    private final OperationalRecorder recorder = new OperationalRecorder(OperationalEventSink.noop(), metrics);
    private final ProjectSpecificationId projectId = ProjectSpecificationId.generate();
    private final ChangeId changeId = ChangeId.generate();

    @Test
    void aConflictWhoseCurrentStateCannotBeReadIsAnsweredWithoutItAndCounted() throws SQLException {
        Path database = databaseWithProject("current-state.db");
        try (SqliteChangeLifecycleMutationStore store = new SqliteChangeLifecycleMutationStore(database, recorder)) {
            assertEquals(ChangeLifecycleMutationPersistenceState.APPLIED,
                    store.apply(attempt("key", "fingerprint-a")).state());

            ChangeLifecycleMutationPersistenceResult readable = store.apply(attempt("key", "fingerprint-b"));
            assertEquals(ChangeLifecycleMutationPersistenceState.CONFLICT, readable.state());
            assertTrue(readable.lifecycleState().isPresent(), "a conflict on a healthy database carries its state");
            assertEquals(Map.of(), metrics.snapshot().counters(), "a conflict that reads everything loses nothing");

            moveAway(database, "change_lifecycle_state");

            ChangeLifecycleMutationPersistenceResult unreadable = store.apply(attempt("key", "fingerprint-b"));

            assertEquals(ChangeLifecycleMutationPersistenceState.CONFLICT, unreadable.state());
            assertEquals("Idempotency key already belongs to a different command", unreadable.reason());
            assertEquals(Optional.empty(), unreadable.lifecycleState());
            assertEquals(Map.of(COUNTER, 1L), metrics.snapshot().counters());
        }
    }

    @Test
    void aFailedMutationWhoseAuditCannotBeReadKeepsItsFailureAndIsCounted() throws SQLException {
        Path database = databaseWithProject("audit.db");
        try (SqliteChangeLifecycleMutationStore store = new SqliteChangeLifecycleMutationStore(database, recorder)) {
            moveAway(database, "change_lifecycle_mutation_audit");

            KnowledgeStoreException failure = assertThrows(KnowledgeStoreException.class,
                    () -> store.apply(attempt("key", "fingerprint-a")));

            assertEquals("Cannot apply lifecycle mutation for " + changeId, failure.getMessage());
            assertInstanceOf(SQLException.class, failure.getCause());
            assertEquals(Map.of(COUNTER, 1L), metrics.snapshot().counters(),
                    "the read that would have said whether the mutation was already applied failed too");
        }
    }

    private Path databaseWithProject(String name) {
        Path database = temporaryDirectory.resolve(name);
        try (SqliteSpecificationKnowledgeStore setup = new SqliteSpecificationKnowledgeStore(database)) {
            setup.putProject(new ProjectStoreEntry(projectId, SourceLocator.file("fixtures/conflict-read")));
        }
        return database;
    }

    private static void moveAway(Path database, String table) throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
             Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode = MEMORY");
            statement.execute("ALTER TABLE " + table + " RENAME TO " + table + "_moved_away");
        }
    }

    private ChangeLifecycleMutationAttempt attempt(String key, String fingerprint) {
        return new ChangeLifecycleMutationAttempt(
                ChangeLifecycleMutationId.generate(),
                new ChangeLifecycleIdempotencyKey(key),
                fingerprint,
                projectId,
                changeId,
                ChangeLifecycleState.DRAFT,
                ChangeLifecycleState.PROPOSED,
                Optional.empty(),
                ChangeLifecycleRevision.initial(),
                "conflict-read-test",
                PROVIDER,
                "a conflict read that fails is counted",
                Instant.parse("2026-10-13T08:00:00Z"));
    }
}
