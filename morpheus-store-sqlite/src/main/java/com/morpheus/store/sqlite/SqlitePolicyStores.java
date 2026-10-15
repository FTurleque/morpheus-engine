package com.morpheus.store.sqlite;

import com.morpheus.application.operability.ExhaustiveShutdown;
import com.morpheus.application.operability.StartupOwnership;

import java.nio.file.Path;
import java.util.Objects;

/**
 * The SQLite stores a policy runtime reads from, opened together on one connection and released together.
 *
 * <p>The set opens a {@link SqliteConnectionScope} before its first store, so the eight stores share one physical
 * connection and one schema check. It did not: a policy operation opened eight connections on every transport.</p>
 *
 * <p>Three adapters expose policy, and each opened the same eight stores in the same order. Opening several
 * stores is where a partial assembly leaks, so the set is opened in one place that gets that right once: each
 * store is registered with the caller's {@link StartupOwnership} as it opens, and the caller transfers only
 * when the whole runtime it is building is complete.</p>
 */
public record SqlitePolicyStores(
        SqliteConnectionScope scope,
        SqliteSpecificationKnowledgeStore snapshots,
        SqliteVersionedRequirementStore requirements,
        SqliteSnapshotBusinessContentStore content,
        SqliteTraceabilityStore traceability,
        SqliteExternalReferenceStore externalReferences,
        SqlitePortfolioStore portfolios,
        SqlitePolicyPackStore policies,
        SqliteCompositionStateStore compositions) implements AutoCloseable {

    /**
     * Opens the set, registering each store with the ownership the caller is still assembling under.
     *
     * <p>Ownership is not transferred here: the caller is not finished, and a store opened for a runtime that
     * never finishes being built has to be released with the rest of it.</p>
     */
    public static SqlitePolicyStores open(Path databasePath, StartupOwnership owned) {
        Objects.requireNonNull(databasePath, "databasePath");
        Objects.requireNonNull(owned, "owned");
        return new SqlitePolicyStores(
                owned.keep(SqliteStoreConnection.openScope(databasePath), SqliteConnectionScope::close),
                owned.keep(new SqliteSpecificationKnowledgeStore(databasePath),
                        SqliteSpecificationKnowledgeStore::close),
                owned.keep(new SqliteVersionedRequirementStore(databasePath),
                        SqliteVersionedRequirementStore::close),
                owned.keep(new SqliteSnapshotBusinessContentStore(databasePath),
                        SqliteSnapshotBusinessContentStore::close),
                owned.keep(new SqliteTraceabilityStore(databasePath), SqliteTraceabilityStore::close),
                owned.keep(new SqliteExternalReferenceStore(databasePath), SqliteExternalReferenceStore::close),
                owned.keep(new SqlitePortfolioStore(databasePath), SqlitePortfolioStore::close),
                owned.keep(new SqlitePolicyPackStore(databasePath), SqlitePolicyPackStore::close),
                owned.keep(new SqliteCompositionStateStore(databasePath), SqliteCompositionStateStore::close));
    }

    /** Releases every store, then the connection they shared, even when one release fails. */
    @Override
    public void close() {
        ExhaustiveShutdown.releaseAll(
                "cannot close the SQLite policy store set",
                compositions,
                policies,
                portfolios,
                externalReferences,
                traceability,
                content,
                requirements,
                snapshots,
                scope);
    }
}
