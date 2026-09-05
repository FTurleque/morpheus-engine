package com.morpheus.store.sqlite;

import com.morpheus.application.operability.ExhaustiveShutdown;
import com.morpheus.application.operability.StartupOwnership;

import java.nio.file.Path;
import java.util.Objects;

/**
 * The SQLite stores a query runtime reads from, opened together and released together.
 *
 * <p>The same reasoning as {@link SqlitePolicyStores}: three adapters expose query, each opened the same five
 * stores in the same order, and opening several stores is where a partial assembly leaks. Each store is
 * registered with the caller's {@link StartupOwnership} as it opens, and the caller transfers only when the
 * runtime it is building is complete.</p>
 */
public record SqliteQueryStores(
        SqliteSpecificationKnowledgeStore snapshots,
        SqliteVersionedRequirementStore requirements,
        SqliteSnapshotBusinessContentStore content,
        SqlitePortfolioStore portfolios,
        SqliteSavedViewStore saved) implements AutoCloseable {

    /** Opens the set, registering each store with the ownership the caller is still assembling under. */
    public static SqliteQueryStores open(Path databasePath, StartupOwnership owned) {
        Objects.requireNonNull(databasePath, "databasePath");
        Objects.requireNonNull(owned, "owned");
        return new SqliteQueryStores(
                owned.keep(new SqliteSpecificationKnowledgeStore(databasePath),
                        SqliteSpecificationKnowledgeStore::close),
                owned.keep(new SqliteVersionedRequirementStore(databasePath),
                        SqliteVersionedRequirementStore::close),
                owned.keep(new SqliteSnapshotBusinessContentStore(databasePath),
                        SqliteSnapshotBusinessContentStore::close),
                owned.keep(new SqlitePortfolioStore(databasePath), SqlitePortfolioStore::close),
                owned.keep(new SqliteSavedViewStore(databasePath), SqliteSavedViewStore::close));
    }

    /** Releases every store, in the reverse of the order they were opened, even when one release fails. */
    @Override
    public void close() {
        ExhaustiveShutdown.releaseAll(
                "cannot close the SQLite query store set",
                saved,
                portfolios,
                content,
                requirements,
                snapshots);
    }
}
