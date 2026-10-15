package com.morpheus.store.sqlite;

import com.morpheus.application.composition.CompositionStateStore;
import com.morpheus.application.identity.EntityIdentityStore;
import com.morpheus.application.lifecycle.mutation.ChangeLifecycleMutationStore;
import com.morpheus.application.operability.ExhaustiveShutdown;
import com.morpheus.application.policy.PolicyEvaluationService;
import com.morpheus.application.policy.PolicyPackService;
import com.morpheus.application.policy.PolicyRuntimeServices;
import com.morpheus.application.query.dsl.QueryExecutionService;
import com.morpheus.application.query.export.QueryExportService;
import com.morpheus.application.query.saved.SavedViewService;
import com.morpheus.application.store.ExternalReferenceStore;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.PolicyPackStore;
import com.morpheus.application.store.PortfolioStore;
import com.morpheus.application.store.SnapshotBusinessContentStore;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.application.store.SyncStateStore;
import com.morpheus.application.store.TraceabilityStore;
import com.morpheus.application.store.VersionedRequirementStore;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * The SQLite stores of one operation, on one connection: the only class that builds several of them.
 *
 * <p>Every transport used to assemble its own sets. HTTP opened nine stores under a scope for most routes, then the
 * five of a query set and the eight of a policy set under scopes of their own, and a lone store, with no scope at all,
 * for portfolios, policy management, readiness and start-up recovery; the CLI and the MCP server did the same with
 * seven to nine. A route chose its connection policy by choosing a set. Now the scope is opened here, always, before
 * any store exists, and a store is built the first time an operation asks for it: an operation pays for the stores it
 * reads and for one physical connection and one schema check, whichever transport carries it.</p>
 *
 * <p>Accessors answer the application ports, never a SQLite type: a transport that reads through this runtime depends
 * on {@code store-sqlite} only where it opens it.</p>
 *
 * <p>The runtime is confined to the thread that opened it, as its scope is: it is built, used and closed by the thread
 * serving the operation, which is why the lazy accessors need no synchronisation. A store is registered for release
 * the moment it exists, so a store that fails to open leaves only the ones before it, and closing the runtime releases
 * those, then the connection.</p>
 */
public final class SqliteOperationRuntime implements AutoCloseable {

    private final Path databasePath;
    private final SqliteConnectionScope scope;
    private final List<AutoCloseable> opened = new ArrayList<>();
    private boolean closed;

    private SqliteSpecificationKnowledgeStore snapshots;
    private SqliteVersionedRequirementStore requirements;
    private SqliteSnapshotBusinessContentStore content;
    private SqliteTraceabilityStore traceability;
    private SqliteExternalReferenceStore externalReferences;
    private SqliteEntityIdentityStore identities;
    private SqliteSyncStateStore syncState;
    private SqliteChangeLifecycleMutationStore lifecycleMutations;
    private SqliteCompositionStateStore compositions;
    private SqlitePortfolioStore portfolios;
    private SqlitePolicyPackStore policyPacks;
    private SqliteSavedViewStore savedViewDefinitions;

    private QueryExecutionService queries;
    private SavedViewService views;
    private QueryExportService exports;
    private PolicyRuntimeServices policy;

    private SqliteOperationRuntime(Path databasePath, SqliteConnectionScope scope) {
        this.databasePath = databasePath;
        this.scope = scope;
    }

    /**
     * Opens the connection of one operation. A database that cannot be opened is reported as the failure every
     * adapter answers, the one its first store used to raise; a scope already open on the thread is a defect of the
     * caller, never a state of the database, and is left as it is.
     */
    public static SqliteOperationRuntime open(Path databasePath) {
        Objects.requireNonNull(databasePath, "databasePath");
        try {
            return new SqliteOperationRuntime(databasePath, SqliteConnectionScope.open(databasePath));
        } catch (SqliteConnectionScope.NestedScopeException nested) {
            throw nested;
        } catch (IllegalStateException unavailable) {
            throw new KnowledgeStoreException(
                    SqliteSpecificationKnowledgeStore.INITIALIZATION_FAILURE, unavailable.getCause());
        } catch (RuntimeException unsafe) {
            throw new KnowledgeStoreException(SqliteSpecificationKnowledgeStore.INITIALIZATION_FAILURE, unsafe);
        }
    }

    public SpecificationKnowledgeStore snapshots() {
        if (snapshots == null) snapshots = registered(() -> new SqliteSpecificationKnowledgeStore(databasePath));
        return snapshots;
    }

    public VersionedRequirementStore requirements() {
        if (requirements == null) requirements = registered(() -> new SqliteVersionedRequirementStore(databasePath));
        return requirements;
    }

    public SnapshotBusinessContentStore content() {
        if (content == null) content = registered(() -> new SqliteSnapshotBusinessContentStore(databasePath));
        return content;
    }

    public TraceabilityStore traceability() {
        if (traceability == null) traceability = registered(() -> new SqliteTraceabilityStore(databasePath));
        return traceability;
    }

    public ExternalReferenceStore externalReferences() {
        if (externalReferences == null) {
            externalReferences = registered(() -> new SqliteExternalReferenceStore(databasePath));
        }
        return externalReferences;
    }

    public EntityIdentityStore identities() {
        if (identities == null) identities = registered(() -> new SqliteEntityIdentityStore(databasePath));
        return identities;
    }

    public SyncStateStore syncState() {
        if (syncState == null) syncState = registered(() -> new SqliteSyncStateStore(databasePath));
        return syncState;
    }

    public ChangeLifecycleMutationStore lifecycleMutations() {
        if (lifecycleMutations == null) {
            lifecycleMutations = registered(() -> new SqliteChangeLifecycleMutationStore(databasePath));
        }
        return lifecycleMutations;
    }

    public CompositionStateStore compositions() {
        if (compositions == null) compositions = registered(() -> new SqliteCompositionStateStore(databasePath));
        return compositions;
    }

    public PortfolioStore portfolios() {
        if (portfolios == null) portfolios = registered(() -> new SqlitePortfolioStore(databasePath));
        return portfolios;
    }

    public PolicyPackStore policyPacks() {
        if (policyPacks == null) policyPacks = registered(() -> new SqlitePolicyPackStore(databasePath));
        return policyPacks;
    }

    private SqliteSavedViewStore savedViewDefinitions() {
        if (savedViewDefinitions == null) {
            savedViewDefinitions = registered(() -> new SqliteSavedViewStore(databasePath));
        }
        return savedViewDefinitions;
    }

    public QueryExecutionService queries() {
        if (queries == null) queries = new QueryExecutionService(snapshots(), requirements(), content(), portfolios());
        return queries;
    }

    public SavedViewService views() {
        if (views == null) views = new SavedViewService(savedViewDefinitions(), queries());
        return views;
    }

    public QueryExportService exports() {
        if (exports == null) exports = new QueryExportService(queries());
        return exports;
    }

    public PolicyPackService registry() {
        return policy().registry();
    }

    public PolicyEvaluationService evaluation() {
        return policy().evaluation();
    }

    private PolicyRuntimeServices policy() {
        if (policy == null) {
            policy = PolicyRuntimeServices.from(snapshots(), requirements(), content(), traceability(),
                    externalReferences(), portfolios(), policyPacks(), compositions());
        }
        return policy;
    }

    public int logicalConnectionsBorrowed() {
        return scope.logicalConnectionsBorrowed();
    }

    public int schemaInitializations() {
        return scope.schemaInitializations();
    }

    /**
     * Builds a store on the connection of the scope and registers it for release. Refused once the runtime is closed:
     * with its scope gone, the store would open a physical connection of its own that nothing releases.
     */
    private <T extends AutoCloseable> T registered(Supplier<T> opening) {
        if (closed) {
            throw new IllegalStateException("the SQLite operation runtime is closed");
        }
        T store = opening.get();
        opened.add(store);
        return store;
    }

    /**
     * Releases every store it opened, last opened first, then the connection they shared, even when one fails. Each of
     * them releases once, so a second call retries only what the first could not release: the connection of a scope
     * whose close failed.
     */
    @Override
    public void close() {
        closed = true;
        AutoCloseable[] resources = new AutoCloseable[opened.size() + 1];
        for (int index = 0; index < opened.size(); index++) {
            resources[index] = opened.get(opened.size() - 1 - index);
        }
        resources[opened.size()] = scope;
        ExhaustiveShutdown.releaseAll("cannot close the SQLite operation runtime", resources);
    }
}
