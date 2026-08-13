package com.morpheus.cli;

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
        } catch (RuntimeException failure) {
            RuntimeException cleanup = null;
            cleanup = close(openedCompositions, cleanup);
            cleanup = close(openedLifecycleMutations, cleanup);
            cleanup = close(openedSyncState, cleanup);
            cleanup = close(openedIdentities, cleanup);
            cleanup = close(openedExternalReferences, cleanup);
            cleanup = close(openedTraceability, cleanup);
            cleanup = close(openedContent, cleanup);
            cleanup = close(openedRequirements, cleanup);
            cleanup = close(openedSnapshots, cleanup);
            cleanup = close(sqliteScope, cleanup);
            if (cleanup != null) failure.addSuppressed(cleanup);
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
        if (failure != null) {
            throw failure;
        }
    }

    private static RuntimeException close(AutoCloseable closeable, RuntimeException previous) {
        if (closeable == null) return previous;
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
