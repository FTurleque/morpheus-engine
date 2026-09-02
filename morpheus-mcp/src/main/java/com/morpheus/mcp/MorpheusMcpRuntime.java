package com.morpheus.mcp;

import com.morpheus.application.operability.StartupOwnership;
import com.morpheus.store.sqlite.SqliteChangeLifecycleMutationStore;
import com.morpheus.store.sqlite.SqliteExternalReferenceStore;
import com.morpheus.store.sqlite.SqliteSnapshotBusinessContentStore;
import com.morpheus.store.sqlite.SqliteSpecificationKnowledgeStore;
import com.morpheus.store.sqlite.SqliteSyncStateStore;
import com.morpheus.store.sqlite.SqliteTraceabilityStore;
import com.morpheus.store.sqlite.SqliteVersionedRequirementStore;

import java.nio.file.Path;
import java.util.Objects;

/** Owns the SQLite adapters used by one MCP tool invocation. */
final class MorpheusMcpRuntime implements AutoCloseable {
    final SqliteSpecificationKnowledgeStore snapshots;
    final SqliteVersionedRequirementStore requirements;
    final SqliteSnapshotBusinessContentStore content;
    final SqliteTraceabilityStore traceability;
    final SqliteExternalReferenceStore externalReferences;
    final SqliteSyncStateStore syncState;
    final SqliteChangeLifecycleMutationStore lifecycleMutations;

    /**
     * Each store opens its own SQLite connection, and the constructor opens seven of them. Assigning straight to
     * the fields meant a failure on the fourth left the first three open with no way to reach them: the
     * constructor never returns, so nothing can call {@link #close()} on a half-built runtime. The stores stay
     * owned here until all seven exist.
     */
    MorpheusMcpRuntime(Path databasePath) {
        Objects.requireNonNull(databasePath, "databasePath");
        try (StartupOwnership owned = new StartupOwnership()) {
            SqliteSpecificationKnowledgeStore openedSnapshots = owned.keep(
                    new SqliteSpecificationKnowledgeStore(databasePath),
                    SqliteSpecificationKnowledgeStore::close);
            SqliteVersionedRequirementStore openedRequirements = owned.keep(
                    new SqliteVersionedRequirementStore(databasePath),
                    SqliteVersionedRequirementStore::close);
            SqliteSnapshotBusinessContentStore openedContent = owned.keep(
                    new SqliteSnapshotBusinessContentStore(databasePath),
                    SqliteSnapshotBusinessContentStore::close);
            SqliteTraceabilityStore openedTraceability = owned.keep(
                    new SqliteTraceabilityStore(databasePath),
                    SqliteTraceabilityStore::close);
            SqliteExternalReferenceStore openedExternalReferences = owned.keep(
                    new SqliteExternalReferenceStore(databasePath),
                    SqliteExternalReferenceStore::close);
            SqliteSyncStateStore openedSyncState = owned.keep(
                    new SqliteSyncStateStore(databasePath),
                    SqliteSyncStateStore::close);
            SqliteChangeLifecycleMutationStore openedLifecycleMutations = owned.keep(
                    new SqliteChangeLifecycleMutationStore(databasePath),
                    SqliteChangeLifecycleMutationStore::close);

            owned.transferred();
            snapshots = openedSnapshots;
            requirements = openedRequirements;
            content = openedContent;
            traceability = openedTraceability;
            externalReferences = openedExternalReferences;
            syncState = openedSyncState;
            lifecycleMutations = openedLifecycleMutations;
        }
    }

    @Override
    public void close() {
        RuntimeException failure = null;
        failure = close(lifecycleMutations, failure);
        failure = close(syncState, failure);
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
            RuntimeException wrapped = new IllegalStateException("cannot close MCP runtime resource", exception);
            if (previous != null) {
                previous.addSuppressed(wrapped);
                return previous;
            }
            return wrapped;
        }
    }
}
