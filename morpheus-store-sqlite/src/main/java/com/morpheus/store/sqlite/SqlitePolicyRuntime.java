package com.morpheus.store.sqlite;

import com.morpheus.application.operability.StartupOwnership;
import com.morpheus.application.policy.PolicyEvaluationService;
import com.morpheus.application.policy.PolicyPackService;
import com.morpheus.application.policy.PolicyRuntimeServices;

import java.nio.file.Path;
import java.util.Objects;

/**
 * The SQLite-backed policy runtime, opened for the duration of one operation.
 *
 * <p>Three adapters expose policy — HTTP, CLI and MCP — and each kept its own copy of this: open seven stores
 * under ownership, hand them to the application's policy factory, expose the two services, close the stores.
 * The copies were identical, and a wiring decision kept in three places drifts in the one nobody reads.</p>
 *
 * <p>Choosing SQLite implementations is this adapter's business, and it is the only part that was repeated:
 * {@link PolicyRuntimeServices} already decides how the services are wired together, inward in the application
 * layer, and is called from here rather than reimplemented.</p>
 */
public final class SqlitePolicyRuntime implements AutoCloseable {

    private final SqlitePolicyStores stores;
    private final PolicyRuntimeServices services;

    private SqlitePolicyRuntime(SqlitePolicyStores stores, PolicyRuntimeServices services) {
        this.stores = stores;
        this.services = services;
    }

    /** Opens every store and wires the services, releasing what it opened if any of that fails. */
    public static SqlitePolicyRuntime open(Path databasePath) {
        Objects.requireNonNull(databasePath, "databasePath");
        try (StartupOwnership owned = new StartupOwnership()) {
            SqlitePolicyStores opened = SqlitePolicyStores.open(databasePath, owned);
            SqlitePolicyRuntime runtime = new SqlitePolicyRuntime(
                    opened,
                    PolicyRuntimeServices.from(
                            opened.snapshots(),
                            opened.requirements(),
                            opened.content(),
                            opened.traceability(),
                            opened.externalReferences(),
                            opened.portfolios(),
                            opened.policies()));
            owned.transferred();
            return runtime;
        }
    }

    public PolicyPackService registry() {
        return services.registry();
    }

    public PolicyEvaluationService evaluation() {
        return services.evaluation();
    }

    @Override
    public void close() {
        stores.close();
    }
}
