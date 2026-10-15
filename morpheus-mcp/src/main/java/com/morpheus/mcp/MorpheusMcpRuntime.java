package com.morpheus.mcp;

import com.morpheus.application.composition.CompositionStateStore;
import com.morpheus.application.lifecycle.mutation.ChangeLifecycleMutationStore;
import com.morpheus.application.policy.PolicyEvaluationService;
import com.morpheus.application.policy.PolicyPackService;
import com.morpheus.application.query.dsl.QueryExecutionService;
import com.morpheus.application.query.export.QueryExportService;
import com.morpheus.application.query.saved.SavedViewService;
import com.morpheus.application.store.ExternalReferenceStore;
import com.morpheus.application.store.PolicyPackStore;
import com.morpheus.application.store.PortfolioStore;
import com.morpheus.application.store.SnapshotBusinessContentStore;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.application.store.SyncStateStore;
import com.morpheus.application.store.TraceabilityStore;
import com.morpheus.application.store.VersionedRequirementStore;
import com.morpheus.store.sqlite.SqliteOperationRuntime;

import java.nio.file.Path;
import java.util.Objects;

/**
 * The runtime of one MCP tool call, and the only type through which a tool reaches the database.
 *
 * <p>Every store a tool asks for shares one physical SQLite connection and one schema check, and is built the first
 * time it is asked for. The connection is thread-confined, so a runtime is built, used and closed by the thread that
 * handles the tool call. A database that cannot be opened is the store failure every tool handler answers.</p>
 */
final class MorpheusMcpRuntime implements AutoCloseable {
    private final SqliteOperationRuntime operation;

    MorpheusMcpRuntime(Path databasePath) {
        this.operation = SqliteOperationRuntime.open(Objects.requireNonNull(databasePath, "databasePath"));
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

    PolicyPackStore policyPacks() {
        return operation.policyPacks();
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
