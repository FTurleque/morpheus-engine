package com.morpheus.store.sqlite;

import com.morpheus.application.operability.StartupOwnership;
import com.morpheus.application.query.dsl.QueryExecutionService;
import com.morpheus.application.query.export.QueryExportService;
import com.morpheus.application.query.saved.SavedViewService;

import java.nio.file.Path;
import java.util.Objects;

/**
 * The SQLite-backed query runtime, opened for the duration of one operation.
 *
 * <p>The same reasoning as {@link SqlitePolicyRuntime}: the CLI and MCP adapters each kept an identical copy of
 * open-five-stores-under-ownership, wire three services, close. Choosing SQLite implementations is this
 * adapter's business and was the only part repeated.</p>
 */
public final class SqliteQueryRuntime implements AutoCloseable {

    private final SqliteQueryStores stores;
    private final QueryExecutionService queries;
    private final SavedViewService views;
    private final QueryExportService exports;

    private SqliteQueryRuntime(
            SqliteQueryStores stores,
            QueryExecutionService queries,
            SavedViewService views,
            QueryExportService exports) {
        this.stores = stores;
        this.queries = queries;
        this.views = views;
        this.exports = exports;
    }

    /** Opens every store and wires the services, releasing what it opened if any of that fails. */
    public static SqliteQueryRuntime open(Path databasePath) {
        Objects.requireNonNull(databasePath, "databasePath");
        try (StartupOwnership owned = new StartupOwnership()) {
            SqliteQueryStores opened = SqliteQueryStores.open(databasePath, owned);
            QueryExecutionService queries = new QueryExecutionService(
                    opened.snapshots(), opened.requirements(), opened.content(), opened.portfolios());
            SqliteQueryRuntime runtime = new SqliteQueryRuntime(
                    opened,
                    queries,
                    new SavedViewService(opened.saved(), queries),
                    new QueryExportService(queries));
            owned.transferred();
            return runtime;
        }
    }

    public QueryExecutionService queries() {
        return queries;
    }

    public SavedViewService views() {
        return views;
    }

    public QueryExportService exports() {
        return exports;
    }

    @Override
    public void close() {
        stores.close();
    }
}
