package com.morpheus.api;

import com.morpheus.application.operability.ExhaustiveShutdown;
import com.morpheus.application.operability.StartupOwnership;
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
        // The stores all borrow the scope's single physical connection, so releasing the scope releases every
        // one of them. What the previous catch missed was the kind of failure: an Error raised while a store
        // class initializes skipped it entirely and left the scope, and its connection, open.
        try (StartupOwnership owned = new StartupOwnership()) {
            SqliteConnectionScope scope = owned.keep(
                    SqliteConnectionScope.open(databasePath), SqliteConnectionScope::close);
            snapshots = new SqliteSpecificationKnowledgeStore(databasePath);
            requirements = new SqliteVersionedRequirementStore(databasePath);
            content = new SqliteSnapshotBusinessContentStore(databasePath);
            traceability = new SqliteTraceabilityStore(databasePath);
            externalReferences = new SqliteExternalReferenceStore(databasePath);
            identities = new SqliteEntityIdentityStore(databasePath);
            syncState = new SqliteSyncStateStore(databasePath);
            lifecycleMutations = new SqliteChangeLifecycleMutationStore(databasePath);
            compositions = new SqliteCompositionStateStore(databasePath);
            owned.transferred();
            this.sqliteScope = scope;
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
        ExhaustiveShutdown.releaseAll(
                "cannot close API runtime resource",
                compositions,
                lifecycleMutations,
                syncState,
                identities,
                externalReferences,
                traceability,
                content,
                requirements,
                snapshots,
                sqliteScope);
    }

}
