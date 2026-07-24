package com.morpheus.mcp;

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

    MorpheusMcpRuntime(Path databasePath) {
        Objects.requireNonNull(databasePath, "databasePath");
        snapshots = new SqliteSpecificationKnowledgeStore(databasePath);
        requirements = new SqliteVersionedRequirementStore(databasePath);
        content = new SqliteSnapshotBusinessContentStore(databasePath);
        traceability = new SqliteTraceabilityStore(databasePath);
        externalReferences = new SqliteExternalReferenceStore(databasePath);
        syncState = new SqliteSyncStateStore(databasePath);
    }

    @Override
    public void close() {
        RuntimeException failure = null;
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
