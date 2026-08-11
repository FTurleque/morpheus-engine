package com.morpheus.api;

import com.morpheus.store.sqlite.SqliteChangeLifecycleMutationStore;
import com.morpheus.store.sqlite.SqliteCompositionStateStore;
import com.morpheus.store.sqlite.SqliteConnectionScope;
import com.morpheus.store.sqlite.SqliteEntityIdentityStore;
import com.morpheus.store.sqlite.SqliteExternalReferenceStore;
import com.morpheus.store.sqlite.SqliteSnapshotBusinessContentStore;
import com.morpheus.store.sqlite.SqliteSpecificationKnowledgeStore;
import com.morpheus.store.sqlite.SqliteSyncStateStore;
import com.morpheus.store.sqlite.SqliteTraceabilityStore;
import com.morpheus.store.sqlite.SqliteVersionedRequirementStore;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Owns the SQLite adapters used by one HTTP API operation.
 * Nine logical stores share exactly one physical, thread-confined SQLite connection and one schema check.
 */
final class ApiRuntime implements AutoCloseable {
    final SqliteSpecificationKnowledgeStore snapshots;
    final SqliteVersionedRequirementStore requirements;
    final SqliteSnapshotBusinessContentStore content;
    final SqliteTraceabilityStore traceability;
    final SqliteExternalReferenceStore externalReferences;
    final SqliteEntityIdentityStore identities;
    final SqliteSyncStateStore syncState;
    final SqliteChangeLifecycleMutationStore lifecycleMutations;
    final SqliteCompositionStateStore compositions;
    private final SqliteConnectionScope sqliteScope;

    ApiRuntime(Path databasePath) {
        Objects.requireNonNull(databasePath, "databasePath");
        this.sqliteScope = SqliteConnectionScope.open(databasePath);
        try {
            snapshots = new SqliteSpecificationKnowledgeStore(databasePath);
            requirements = new SqliteVersionedRequirementStore(databasePath);
            content = new SqliteSnapshotBusinessContentStore(databasePath);
            traceability = new SqliteTraceabilityStore(databasePath);
            externalReferences = new SqliteExternalReferenceStore(databasePath);
            identities = new SqliteEntityIdentityStore(databasePath);
            syncState = new SqliteSyncStateStore(databasePath);
            lifecycleMutations = new SqliteChangeLifecycleMutationStore(databasePath);
            compositions = new SqliteCompositionStateStore(databasePath);
        } catch (RuntimeException failure) {
            try {
                sqliteScope.close();
            } catch (RuntimeException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw failure;
        }
    }

    int logicalSqliteConnectionsBorrowed() {
        return sqliteScope.logicalConnectionsBorrowed();
    }

    int sqliteSchemaInitializations() {
        return sqliteScope.schemaInitializations();
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
        failure = close(sqliteScope, failure);
        if (failure != null) throw failure;
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
            RuntimeException wrapped = new IllegalStateException("cannot close API runtime resource", exception);
            if (previous != null) {
                previous.addSuppressed(wrapped);
                return previous;
            }
            return wrapped;
        }
    }
}
