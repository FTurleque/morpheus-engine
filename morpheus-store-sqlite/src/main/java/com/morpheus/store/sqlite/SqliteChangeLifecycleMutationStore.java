package com.morpheus.store.sqlite;

import com.morpheus.application.lifecycle.mutation.ChangeLifecycleMutationAttempt;
import com.morpheus.application.lifecycle.mutation.ChangeLifecycleMutationAuditRecord;
import com.morpheus.application.lifecycle.mutation.ChangeLifecycleMutationPersistenceResult;
import com.morpheus.application.lifecycle.mutation.ChangeLifecycleMutationPersistenceState;
import com.morpheus.application.lifecycle.mutation.ChangeLifecycleMutationStore;
import com.morpheus.application.lifecycle.mutation.ChangeLifecycleOperationalState;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.lifecycle.ChangeAbandonmentReason;
import com.morpheus.domain.change.lifecycle.ChangeLifecycle;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleIdempotencyKey;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleMutationId;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleRevision;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleState;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provider.ProviderId;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** SQLite implementation of M17 operational lifecycle CAS, idempotency and append-only audit. */
public final class SqliteChangeLifecycleMutationStore implements ChangeLifecycleMutationStore, AutoCloseable {
    private final Connection connection;
    private boolean closed;

    public SqliteChangeLifecycleMutationStore(Path databasePath) {
        Objects.requireNonNull(databasePath, "databasePath");
        Connection opened = null;
        try {
            opened = SqliteDatabaseSecurity.open(databasePath);
            configure(opened);
            new SqliteSchemaManager().migrate(opened);
            connection = opened;
        } catch (SQLException | RuntimeException exception) {
            closeQuietly(opened);
            if (exception instanceof KnowledgeStoreException knowledgeStoreException) {
                throw knowledgeStoreException;
            }
            throw new KnowledgeStoreException("Cannot initialize SQLite lifecycle mutation store", exception);
        }
    }

    @Override
    public synchronized Optional<ChangeLifecycleOperationalState> findState(
            ProjectSpecificationId projectId,
            ChangeId changeId) {
        ensureOpen();
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(changeId, "changeId");
        try {
            return findStateInternal(projectId, changeId);
        } catch (SQLException exception) {
            throw new KnowledgeStoreException("Cannot read lifecycle state for " + changeId, exception);
        }
    }

    @Override
    public synchronized Optional<ChangeLifecycleMutationAuditRecord> findByIdempotencyKey(
            ProjectSpecificationId projectId,
            ChangeLifecycleIdempotencyKey idempotencyKey) {
        ensureOpen();
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        try {
            return findAuditByIdempotencyInternal(projectId, idempotencyKey);
        } catch (SQLException exception) {
            throw new KnowledgeStoreException("Cannot read lifecycle mutation idempotency record", exception);
        }
    }

    @Override
    public synchronized List<ChangeLifecycleMutationAuditRecord> listAudit(
            ProjectSpecificationId projectId,
            ChangeId changeId) {
        ensureOpen();
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(changeId, "changeId");
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT *
                FROM change_lifecycle_mutation_audit
                WHERE project_id = ? AND change_id = ?
                ORDER BY to_revision, mutation_id
                """)) {
            statement.setString(1, projectId.toString());
            statement.setString(2, changeId.toString());
            List<ChangeLifecycleMutationAuditRecord> records = new ArrayList<>();
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    records.add(readAudit(result));
                }
            }
            return List.copyOf(records);
        } catch (SQLException exception) {
            throw new KnowledgeStoreException("Cannot list lifecycle mutation audit for " + changeId, exception);
        }
    }

    @Override
    public synchronized ChangeLifecycleMutationPersistenceResult apply(ChangeLifecycleMutationAttempt attempt) {
        ensureOpen();
        Objects.requireNonNull(attempt, "attempt");
        requireProject(attempt.projectId());

        try {
            boolean previousAutoCommit = connection.getAutoCommit();
            try {
                connection.setAutoCommit(false);

                Optional<ChangeLifecycleMutationAuditRecord> existing =
                        findAuditByIdempotencyInternal(attempt.projectId(), attempt.idempotencyKey());
                if (existing.isPresent()) {
                    connection.rollback();
                    return existingResult(attempt, existing.orElseThrow());
                }
                if (findAuditByMutationIdInternal(attempt.mutationId()).isPresent()) {
                    connection.rollback();
                    return conflict(Optional.of(currentState(attempt)), "Mutation id already exists");
                }

                ChangeLifecycleOperationalState current = currentState(attempt);
                if (!current.revision().equals(attempt.expectedRevision())) {
                    connection.rollback();
                    return conflict(Optional.of(current),
                            "Expected revision " + attempt.expectedRevision() + " does not match " + current.revision());
                }
                if (current.lifecycle().state() != attempt.fromState()) {
                    connection.rollback();
                    return conflict(Optional.of(current),
                            "Expected lifecycle state " + attempt.fromState() + " does not match " + current.lifecycle().state());
                }

                ChangeLifecycleOperationalState next = nextState(attempt, current);
                int changed = current.revision().value() == 0
                        ? insertInitialState(attempt, next)
                        : updateExistingState(attempt, next);
                if (changed != 1) {
                    connection.rollback();
                    return collisionResult(attempt, "Lifecycle CAS lost to another writer");
                }

                ChangeLifecycleMutationAuditRecord audit = audit(attempt, current, next);
                insertAudit(audit);
                connection.commit();
                return new ChangeLifecycleMutationPersistenceResult(
                        ChangeLifecycleMutationPersistenceState.APPLIED,
                        Optional.of(next),
                        Optional.of(audit),
                        "Lifecycle mutation applied");
            } catch (SQLException exception) {
                rollbackQuietly();
                ChangeLifecycleMutationPersistenceResult collision = collisionResult(attempt, exception.getMessage());
                if (collision.state() == ChangeLifecycleMutationPersistenceState.ALREADY_APPLIED) {
                    return collision;
                }
                throw exception;
            } catch (RuntimeException exception) {
                rollbackQuietly();
                throw exception;
            } finally {
                connection.setAutoCommit(previousAutoCommit);
            }
        } catch (SQLException exception) {
            throw new KnowledgeStoreException("Cannot apply lifecycle mutation for " + attempt.changeId(), exception);
        }
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        try {
            connection.close();
            closed = true;
        } catch (SQLException exception) {
            throw new KnowledgeStoreException("Cannot close SQLite lifecycle mutation store", exception);
        }
    }

    private Optional<ChangeLifecycleOperationalState> findStateInternal(
            ProjectSpecificationId projectId,
            ChangeId changeId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT state, abandonment_reason, revision, updated_at, last_mutation_id
                FROM change_lifecycle_state
                WHERE project_id = ? AND change_id = ?
                """)) {
            statement.setString(1, projectId.toString());
            statement.setString(2, changeId.toString());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                ChangeLifecycleState state = ChangeLifecycleState.valueOf(result.getString("state"));
                Optional<ChangeAbandonmentReason> abandonmentReason = optional(result.getString("abandonment_reason"))
                        .map(ChangeAbandonmentReason::valueOf);
                ChangeLifecycle lifecycle = state == ChangeLifecycleState.ABANDONED
                        ? ChangeLifecycle.abandoned(changeId, abandonmentReason.orElseThrow())
                        : ChangeLifecycle.of(changeId, state);
                return Optional.of(new ChangeLifecycleOperationalState(
                        projectId,
                        lifecycle,
                        new ChangeLifecycleRevision(result.getLong("revision")),
                        Optional.of(Instant.parse(result.getString("updated_at"))),
                        Optional.of(ChangeLifecycleMutationId.parse(result.getString("last_mutation_id")))));
            }
        }
    }

    private Optional<ChangeLifecycleMutationAuditRecord> findAuditByIdempotencyInternal(
            ProjectSpecificationId projectId,
            ChangeLifecycleIdempotencyKey key) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT * FROM change_lifecycle_mutation_audit
                WHERE project_id = ? AND idempotency_key = ?
                """)) {
            statement.setString(1, projectId.toString());
            statement.setString(2, key.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(readAudit(result)) : Optional.empty();
            }
        }
    }

    private Optional<ChangeLifecycleMutationAuditRecord> findAuditByMutationIdInternal(
            ChangeLifecycleMutationId mutationId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT * FROM change_lifecycle_mutation_audit WHERE mutation_id = ?")) {
            statement.setString(1, mutationId.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(readAudit(result)) : Optional.empty();
            }
        }
    }

    private ChangeLifecycleMutationAuditRecord readAudit(ResultSet result) throws SQLException {
        return new ChangeLifecycleMutationAuditRecord(
                ChangeLifecycleMutationId.parse(result.getString("mutation_id")),
                new ChangeLifecycleIdempotencyKey(result.getString("idempotency_key")),
                result.getString("command_fingerprint"),
                ProjectSpecificationId.parse(result.getString("project_id")),
                ChangeId.parse(result.getString("change_id")),
                ChangeLifecycleState.valueOf(result.getString("from_state")),
                ChangeLifecycleState.valueOf(result.getString("target_state")),
                optional(result.getString("target_abandonment_reason")).map(ChangeAbandonmentReason::valueOf),
                new ChangeLifecycleRevision(result.getLong("from_revision")),
                new ChangeLifecycleRevision(result.getLong("to_revision")),
                result.getString("actor"),
                new ProviderId(result.getString("provider_id")),
                result.getString("reason"),
                Instant.parse(result.getString("applied_at")));
    }

    private ChangeLifecycleOperationalState currentState(ChangeLifecycleMutationAttempt attempt) throws SQLException {
        return findStateInternal(attempt.projectId(), attempt.changeId())
                .orElseGet(() -> ChangeLifecycleOperationalState.initial(attempt.projectId(), attempt.changeId()));
    }

    private ChangeLifecycleOperationalState nextState(
            ChangeLifecycleMutationAttempt attempt,
            ChangeLifecycleOperationalState current) {
        ChangeLifecycle lifecycle = attempt.targetState() == ChangeLifecycleState.ABANDONED
                ? ChangeLifecycle.abandoned(attempt.changeId(), attempt.targetAbandonmentReason().orElseThrow())
                : ChangeLifecycle.of(attempt.changeId(), attempt.targetState());
        return new ChangeLifecycleOperationalState(
                attempt.projectId(),
                lifecycle,
                current.revision().next(),
                Optional.of(attempt.appliedAt()),
                Optional.of(attempt.mutationId()));
    }

    private int insertInitialState(
            ChangeLifecycleMutationAttempt attempt,
            ChangeLifecycleOperationalState next) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT OR IGNORE INTO change_lifecycle_state(
                    project_id, change_id, state, abandonment_reason, revision, updated_at, last_mutation_id)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """)) {
            bindState(statement, attempt, next, false);
            return statement.executeUpdate();
        }
    }

    private int updateExistingState(
            ChangeLifecycleMutationAttempt attempt,
            ChangeLifecycleOperationalState next) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                UPDATE change_lifecycle_state
                SET state = ?, abandonment_reason = ?, revision = ?, updated_at = ?, last_mutation_id = ?
                WHERE project_id = ? AND change_id = ? AND revision = ? AND state = ?
                """)) {
            statement.setString(1, next.lifecycle().state().name());
            nullable(statement, 2, next.lifecycle().abandonmentReason().map(Enum::name));
            statement.setLong(3, next.revision().value());
            statement.setString(4, next.updatedAt().orElseThrow().toString());
            statement.setString(5, next.lastMutationId().orElseThrow().toString());
            statement.setString(6, attempt.projectId().toString());
            statement.setString(7, attempt.changeId().toString());
            statement.setLong(8, attempt.expectedRevision().value());
            statement.setString(9, attempt.fromState().name());
            return statement.executeUpdate();
        }
    }

    private void bindState(
            PreparedStatement statement,
            ChangeLifecycleMutationAttempt attempt,
            ChangeLifecycleOperationalState next,
            boolean unused) throws SQLException {
        statement.setString(1, attempt.projectId().toString());
        statement.setString(2, attempt.changeId().toString());
        statement.setString(3, next.lifecycle().state().name());
        nullable(statement, 4, next.lifecycle().abandonmentReason().map(Enum::name));
        statement.setLong(5, next.revision().value());
        statement.setString(6, next.updatedAt().orElseThrow().toString());
        statement.setString(7, next.lastMutationId().orElseThrow().toString());
    }

    private ChangeLifecycleMutationAuditRecord audit(
            ChangeLifecycleMutationAttempt attempt,
            ChangeLifecycleOperationalState current,
            ChangeLifecycleOperationalState next) {
        return new ChangeLifecycleMutationAuditRecord(
                attempt.mutationId(),
                attempt.idempotencyKey(),
                attempt.commandFingerprint(),
                attempt.projectId(),
                attempt.changeId(),
                attempt.fromState(),
                attempt.targetState(),
                attempt.targetAbandonmentReason(),
                current.revision(),
                next.revision(),
                attempt.actor(),
                attempt.providerId(),
                attempt.reason(),
                attempt.appliedAt());
    }

    private void insertAudit(ChangeLifecycleMutationAuditRecord audit) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO change_lifecycle_mutation_audit(
                    mutation_id, project_id, change_id, idempotency_key, command_fingerprint,
                    from_state, target_state, target_abandonment_reason,
                    from_revision, to_revision, actor, provider_id, reason, applied_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """)) {
            statement.setString(1, audit.mutationId().toString());
            statement.setString(2, audit.projectId().toString());
            statement.setString(3, audit.changeId().toString());
            statement.setString(4, audit.idempotencyKey().toString());
            statement.setString(5, audit.commandFingerprint());
            statement.setString(6, audit.fromState().name());
            statement.setString(7, audit.targetState().name());
            nullable(statement, 8, audit.targetAbandonmentReason().map(Enum::name));
            statement.setLong(9, audit.fromRevision().value());
            statement.setLong(10, audit.toRevision().value());
            statement.setString(11, audit.actor());
            statement.setString(12, audit.providerId().toString());
            statement.setString(13, audit.reason());
            statement.setString(14, audit.appliedAt().toString());
            statement.executeUpdate();
        }
    }

    private ChangeLifecycleMutationPersistenceResult existingResult(
            ChangeLifecycleMutationAttempt attempt,
            ChangeLifecycleMutationAuditRecord existing) {
        if (!existing.commandFingerprint().equals(attempt.commandFingerprint())) {
            return conflict(readCurrentQuietly(attempt), "Idempotency key already belongs to a different command");
        }
        return new ChangeLifecycleMutationPersistenceResult(
                ChangeLifecycleMutationPersistenceState.ALREADY_APPLIED,
                Optional.of(stateFrom(existing)),
                Optional.of(existing),
                "Idempotent lifecycle mutation was already applied");
    }

    private ChangeLifecycleMutationPersistenceResult collisionResult(
            ChangeLifecycleMutationAttempt attempt,
            String reason) {
        try {
            Optional<ChangeLifecycleMutationAuditRecord> existing =
                    findAuditByIdempotencyInternal(attempt.projectId(), attempt.idempotencyKey());
            if (existing.isPresent()) {
                return existingResult(attempt, existing.orElseThrow());
            }
        } catch (SQLException ignored) {
            // Preserve the original collision/concurrency signal.
        }
        return conflict(readCurrentQuietly(attempt), reason == null || reason.isBlank()
                ? "Lifecycle mutation conflict"
                : reason);
    }

    private Optional<ChangeLifecycleOperationalState> readCurrentQuietly(ChangeLifecycleMutationAttempt attempt) {
        try {
            return Optional.of(currentState(attempt));
        } catch (SQLException ignored) {
            return Optional.empty();
        }
    }

    private ChangeLifecycleOperationalState stateFrom(ChangeLifecycleMutationAuditRecord audit) {
        ChangeLifecycle lifecycle = audit.targetState() == ChangeLifecycleState.ABANDONED
                ? ChangeLifecycle.abandoned(audit.changeId(), audit.targetAbandonmentReason().orElseThrow())
                : ChangeLifecycle.of(audit.changeId(), audit.targetState());
        return new ChangeLifecycleOperationalState(
                audit.projectId(), lifecycle, audit.toRevision(), Optional.of(audit.appliedAt()), Optional.of(audit.mutationId()));
    }

    private ChangeLifecycleMutationPersistenceResult conflict(
            Optional<ChangeLifecycleOperationalState> state,
            String reason) {
        return new ChangeLifecycleMutationPersistenceResult(
                ChangeLifecycleMutationPersistenceState.CONFLICT,
                state,
                Optional.empty(),
                reason == null || reason.isBlank() ? "Lifecycle mutation conflict" : reason);
    }

    private void requireProject(ProjectSpecificationId projectId) {
        try (PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM projects WHERE id = ?")) {
            statement.setString(1, projectId.toString());
            try (ResultSet result = statement.executeQuery()) {
                if (!result.next()) {
                    throw new KnowledgeStoreException("project not found for lifecycle mutation: " + projectId);
                }
            }
        } catch (SQLException exception) {
            throw new KnowledgeStoreException("Cannot validate project for lifecycle mutation: " + projectId, exception);
        }
    }

    private void configure(Connection connection) throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = 5000");
        }
    }

    private void ensureOpen() {
        if (closed) {
            throw new KnowledgeStoreException("SQLite lifecycle mutation store is closed");
        }
    }

    private void rollbackQuietly() {
        try {
            connection.rollback();
        } catch (SQLException ignored) {
            // Preserve original error.
        }
    }

    private static Optional<String> optional(String value) {
        return Optional.ofNullable(value);
    }

    private static void nullable(PreparedStatement statement, int index, Optional<String> value) throws SQLException {
        if (value.isPresent()) {
            statement.setString(index, value.orElseThrow());
        } else {
            statement.setNull(index, java.sql.Types.VARCHAR);
        }
    }

    private static void closeQuietly(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.close();
        } catch (SQLException ignored) {
            // Best effort during construction failure.
        }
    }
}
