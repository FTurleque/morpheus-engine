package com.morpheus.cli;

import com.morpheus.application.operability.ExhaustiveShutdown;
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
        sqliteScope = SqliteConnectionScope.open(databasePath);
        SqliteSpecificationKnowledgeStore openedSnapshots = null;
        SqliteVersionedRequirementStore openedRequirements = null;
        SqliteSnapshotBusinessContentStore openedContent = null;
        SqliteTraceabilityStore openedTraceability = null;
        SqliteExternalReferenceStore openedExternalReferences = null;
        SqliteEntityIdentityStore openedIdentities = null;
        SqliteSyncStateStore openedSyncState = null;
        SqliteChangeLifecycleMutationStore openedLifecycleMutations = null;
        SqliteCompositionStateStore openedCompositions = null;
        try {
            openedSnapshots = new SqliteSpecificationKnowledgeStore(databasePath);
            new RuntimeSnapshotRecovery(openedSnapshots).recoverAll(Instant.now());
            openedRequirements = new SqliteVersionedRequirementStore(databasePath);
            openedContent = new SqliteSnapshotBusinessContentStore(databasePath);
            openedTraceability = new SqliteTraceabilityStore(databasePath);
            openedExternalReferences = new SqliteExternalReferenceStore(databasePath);
            openedIdentities = new SqliteEntityIdentityStore(databasePath);
            openedSyncState = new SqliteSyncStateStore(databasePath);
            openedLifecycleMutations = new SqliteChangeLifecycleMutationStore(databasePath);
            openedCompositions = new SqliteCompositionStateStore(databasePath);
        } catch (RuntimeException | Error failure) {
            // An Error raised while a store class initializes used to skip this rollback entirely, leaving every
            // store opened before it -- and the scope's connection -- behind.
            try {
                ExhaustiveShutdown.releaseAll(
                        "cannot close CLI runtime resource",
                        openedCompositions,
                        openedLifecycleMutations,
                        openedSyncState,
                        openedIdentities,
                        openedExternalReferences,
                        openedTraceability,
                        openedContent,
                        openedRequirements,
                        openedSnapshots,
                        sqliteScope);
            } catch (RuntimeException | Error cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
        snapshots = openedSnapshots;
        requirements = openedRequirements;
        content = openedContent;
        traceability = openedTraceability;
        externalReferences = openedExternalReferences;
        identities = openedIdentities;
        syncState = openedSyncState;
        lifecycleMutations = openedLifecycleMutations;
        compositions = openedCompositions;
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
