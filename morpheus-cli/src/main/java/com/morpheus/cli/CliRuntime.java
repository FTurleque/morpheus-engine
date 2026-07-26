package com.morpheus.cli;

import com.morpheus.application.snapshot.RuntimeSnapshotRecovery;
import com.morpheus.store.sqlite.SqliteChangeLifecycleMutationStore;
import com.morpheus.store.sqlite.SqliteCompositionStateStore;
import com.morpheus.store.sqlite.SqliteEntityIdentityStore;
import com.morpheus.store.sqlite.SqliteExternalReferenceStore;
import com.morpheus.store.sqlite.SqliteSnapshotBusinessContentStore;
import com.morpheus.store.sqlite.SqliteSpecificationKnowledgeStore;
import com.morpheus.store.sqlite.SqliteSyncStateStore;
import com.morpheus.store.sqlite.SqliteTraceabilityStore;
import com.morpheus.store.sqlite.SqliteVersionedRequirementStore;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

/** Owns the SQLite adapter set used by one CLI invocation. */
final class CliRuntime implements AutoCloseable {
    final SqliteSpecificationKnowledgeStore snapshots;
    final SqliteVersionedRequirementStore requirements;
    final SqliteSnapshotBusinessContentStore content;
    final SqliteTraceabilityStore traceability;
    final SqliteExternalReferenceStore externalReferences;
    final SqliteEntityIdentityStore identities;
    final SqliteSyncStateStore syncState;
    final SqliteChangeLifecycleMutationStore lifecycleMutations;
    final SqliteCompositionStateStore compositions;

    CliRuntime(Path databasePath) {
        Objects.requireNonNull(databasePath, "databasePath");
        snapshots = new SqliteSpecificationKnowledgeStore(databasePath);
        new RuntimeSnapshotRecovery(snapshots).recoverAll(Instant.now());
        requirements = new SqliteVersionedRequirementStore(databasePath);
        content = new SqliteSnapshotBusinessContentStore(databasePath);
        traceability = new SqliteTraceabilityStore(databasePath);
        externalReferences = new SqliteExternalReferenceStore(databasePath);
        identities = new SqliteEntityIdentityStore(databasePath);
        syncState = new SqliteSyncStateStore(databasePath);
        lifecycleMutations = new SqliteChangeLifecycleMutationStore(databasePath);
        compositions = new SqliteCompositionStateStore(databasePath);
    }

    @Override
    public void close() {
        RuntimeException failure = null;
        failure = close(compositions, failure);
        failure = close(lifecycleMutations, failure);
        failure = close(syncState, failure);
        failure = close(identities, failure);
        failure = close(externalReferences, failure);
        failure = close(traceability, failure);
        failure = close(content, failure);
        failure = close(requirements, failure);
        failure = close(snapshots, failure);
        if (failure != null) {
            throw failure;
        }
    }

    private RuntimeException close(AutoCloseable closeable, RuntimeException previous) {
        try {
            closeable.close();
            return previous;
        } catch (RuntimeException exception) {
            if (previous != null) {
                previous.addSuppressed(exception);
                return previous;
            }
            return exception;
        } catch (Exception exception) {
            RuntimeException wrapped = new IllegalStateException("cannot close CLI runtime resource", exception);
            if (previous != null) {
                previous.addSuppressed(wrapped);
                return previous;
            }
            return wrapped;
        }
    }
}
