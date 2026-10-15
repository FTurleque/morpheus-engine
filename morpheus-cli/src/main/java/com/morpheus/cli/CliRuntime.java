package com.morpheus.cli;

import com.morpheus.application.composition.CompositionStateStore;
import com.morpheus.application.identity.EntityIdentityStore;
import com.morpheus.application.lifecycle.mutation.ChangeLifecycleMutationStore;
import com.morpheus.application.operability.StartupOwnership;
import com.morpheus.application.policy.PolicyEvaluationService;
import com.morpheus.application.policy.PolicyPackService;
import com.morpheus.application.query.dsl.QueryExecutionService;
import com.morpheus.application.query.export.QueryExportService;
import com.morpheus.application.query.saved.SavedViewService;
import com.morpheus.application.snapshot.RuntimeSnapshotRecovery;
import com.morpheus.application.store.ExternalReferenceStore;
import com.morpheus.application.store.PortfolioStore;
import com.morpheus.application.store.SnapshotBusinessContentStore;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.application.store.SyncStateStore;
import com.morpheus.application.store.TraceabilityStore;
import com.morpheus.application.store.VersionedRequirementStore;
import com.morpheus.store.sqlite.SqliteOperationRuntime;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Objects;

/**
 * The runtime of one CLI command, and the only type through which a command reaches the database.
 *
 * <p>Every store a command asks for shares one physical SQLite connection and one schema check, and is built the first
 * time it is asked for. The accessors answer application ports.</p>
 */
final class CliRuntime implements AutoCloseable {
    private final SqliteOperationRuntime operation;

    private CliRuntime(SqliteOperationRuntime operation) {
        this.operation = operation;
    }

    /** The runtime of a command that reads or writes through the stores, with nothing else to do first. */
    static CliRuntime open(Path databasePath) {
        return new CliRuntime(SqliteOperationRuntime.open(Objects.requireNonNull(databasePath, "databasePath")));
    }

    /**
     * The runtime of a command that works on published snapshots: a publication a previous process left interrupted is
     * recovered first, as the HTTP and MCP servers do once when they start. A recovery that fails releases the
     * connection before the failure propagates.
     */
    static CliRuntime recovering(Path databasePath) {
        try (StartupOwnership owned = new StartupOwnership()) {
            CliRuntime runtime = owned.keep(open(databasePath), CliRuntime::close);
            new RuntimeSnapshotRecovery(runtime.snapshots()).recoverAll(Instant.now());
            owned.transferred();
            return runtime;
        }
    }

    SpecificationKnowledgeStore snapshots() {
        return operation.snapshots();
    }

    VersionedRequirementStore requirements() {
        return operation.requirements();
    }

    SnapshotBusinessContentStore content() {
        return operation.content();
    }

    TraceabilityStore traceability() {
        return operation.traceability();
    }

    ExternalReferenceStore externalReferences() {
        return operation.externalReferences();
    }

    EntityIdentityStore identities() {
        return operation.identities();
    }

    SyncStateStore syncState() {
        return operation.syncState();
    }

    ChangeLifecycleMutationStore lifecycleMutations() {
        return operation.lifecycleMutations();
    }

    CompositionStateStore compositions() {
        return operation.compositions();
    }

    PortfolioStore portfolios() {
        return operation.portfolios();
    }

    QueryExecutionService queries() {
        return operation.queries();
    }

    SavedViewService views() {
        return operation.views();
    }

    QueryExportService exports() {
        return operation.exports();
    }

    PolicyPackService registry() {
        return operation.registry();
    }

    PolicyEvaluationService evaluation() {
        return operation.evaluation();
    }

    @Override
    public void close() {
        operation.close();
    }
}
