package com.morpheus.mcp;

import com.morpheus.application.operability.ExhaustiveShutdown;
import com.morpheus.application.operability.StartupOwnership;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.store.sqlite.SqliteChangeLifecycleMutationStore;
import com.morpheus.store.sqlite.SqliteConnectionScope;
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
    private final SqliteConnectionScope sqliteScope;

    /**
     * The seven stores borrow the one physical connection of a scope opened first: one connection and one schema
     * check for a tool call. The scope is thread-confined, so a runtime is built, used and closed by the thread that
     * handles the tool call.
     *
     * <p>The scope and the stores stay owned here until all of them exist: a constructor that fails never returns, so
     * nothing could call {@link #close()} on a half-built runtime.</p>
     */
    MorpheusMcpRuntime(Path databasePath) {
        Objects.requireNonNull(databasePath, "databasePath");
        try (StartupOwnership owned = new StartupOwnership()) {
            SqliteConnectionScope scope = owned.keep(openScope(databasePath), SqliteConnectionScope::close);
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
            sqliteScope = scope;
        }
    }

    /**
     * Opens the scope, and reports a database that cannot be opened as the store failure every tool handler answers.
     * A second scope on the same thread is a defect of the caller and is left as it is.
     */
    static SqliteConnectionScope openScope(Path databasePath) {
        try {
            return SqliteConnectionScope.open(databasePath);
        } catch (SqliteConnectionScope.NestedScopeException nested) {
            throw nested;
        } catch (IllegalStateException unavailable) {
            throw new KnowledgeStoreException("Cannot initialize SQLite connection of the tool call", unavailable);
        }
    }

    @Override
    public void close() {
        ExhaustiveShutdown.releaseAll(
                "cannot close MCP runtime resource",
                lifecycleMutations,
                syncState,
                externalReferences,
                traceability,
                content,
                requirements,
                snapshots,
                sqliteScope);
    }

}
