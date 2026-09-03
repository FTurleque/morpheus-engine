package com.morpheus.cli;

import com.morpheus.application.operability.ExhaustiveShutdown;
import com.morpheus.application.operability.StartupOwnership;
import com.morpheus.application.snapshot.RuntimeSnapshotRecovery;
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
    private final SqliteConnectionScope sqliteScope;

    CliRuntime(Path databasePath) {
        Objects.requireNonNull(databasePath, "databasePath");
        try (StartupOwnership owned = new StartupOwnership()) {
            sqliteScope = owned.keep(
                    SqliteConnectionScope.open(databasePath), SqliteConnectionScope::close);
            snapshots = owned.keep(
                    new SqliteSpecificationKnowledgeStore(databasePath), SqliteSpecificationKnowledgeStore::close);
            new RuntimeSnapshotRecovery(snapshots).recoverAll(Instant.now());
            requirements = owned.keep(
                    new SqliteVersionedRequirementStore(databasePath), SqliteVersionedRequirementStore::close);
            content = owned.keep(
                    new SqliteSnapshotBusinessContentStore(databasePath), SqliteSnapshotBusinessContentStore::close);
            traceability = owned.keep(
                    new SqliteTraceabilityStore(databasePath), SqliteTraceabilityStore::close);
            externalReferences = owned.keep(
                    new SqliteExternalReferenceStore(databasePath), SqliteExternalReferenceStore::close);
            identities = owned.keep(
                    new SqliteEntityIdentityStore(databasePath), SqliteEntityIdentityStore::close);
            syncState = owned.keep(
                    new SqliteSyncStateStore(databasePath), SqliteSyncStateStore::close);
            lifecycleMutations = owned.keep(
                    new SqliteChangeLifecycleMutationStore(databasePath), SqliteChangeLifecycleMutationStore::close);
            compositions = owned.keep(
                    new SqliteCompositionStateStore(databasePath), SqliteCompositionStateStore::close);

            owned.transferred();
        }
    }

    @Override
    public void close() {
        ExhaustiveShutdown.releaseAll(
                "cannot close CLI runtime resource",
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
