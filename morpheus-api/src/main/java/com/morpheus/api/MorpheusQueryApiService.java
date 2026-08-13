package com.morpheus.api;

import com.morpheus.application.query.dsl.PortfolioQueryScope;
import com.morpheus.application.query.dsl.ProjectQueryScope;
import com.morpheus.application.query.dsl.QueryBudgets;
import com.morpheus.application.query.dsl.QueryDefinition;
import com.morpheus.application.query.dsl.QueryDslParser;
import com.morpheus.application.query.dsl.QueryExecutionService;
import com.morpheus.application.query.dsl.QueryPublicViews;
import com.morpheus.application.query.dsl.QueryScope;
import com.morpheus.application.query.export.QueryExport;
import com.morpheus.application.query.export.QueryExportFormat;
import com.morpheus.application.query.export.QueryExportService;
import com.morpheus.application.query.saved.SavedViewId;
import com.morpheus.application.query.saved.SavedViewService;
import com.morpheus.application.query.saved.SavedViewStatus;
import com.morpheus.domain.portfolio.PortfolioId;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.store.sqlite.SqliteConnectionScope;
import com.morpheus.store.sqlite.SqlitePortfolioStore;
import com.morpheus.store.sqlite.SqliteSavedViewStore;
import com.morpheus.store.sqlite.SqliteSnapshotBusinessContentStore;
import com.morpheus.store.sqlite.SqliteSpecificationKnowledgeStore;
import com.morpheus.store.sqlite.SqliteVersionedRequirementStore;

import java.nio.file.Path;
import java.util.Objects;

/** HTTP-facing M24 adapter; parsing and persistence wiring only, never query business semantics. */
public final class MorpheusQueryApiService {
    public static final int DEFAULT_LIMIT = 100;

    private final Path databasePath;
    private final QueryDslParser parser = new QueryDslParser();

    public MorpheusQueryApiService(Path databasePath) {
        this.databasePath = Objects.requireNonNull(databasePath, "databasePath").toAbsolutePath().normalize();
    }

    public Object executeProject(String projectId, QueryRequest request) {
        return execute(new ProjectQueryScope(ProjectSpecificationId.parse(projectId)), request);
    }

    public Object executePortfolio(String portfolioId, QueryRequest request) {
        return execute(new PortfolioQueryScope(PortfolioId.parse(portfolioId)), request);
    }

    public Object execute(ScopedQueryRequest request) {
        Objects.requireNonNull(request, "request");
        return execute(scope(request.scopeKind(), request.scopeId()), request.query());
    }

    public Object createSavedView(CreateSavedViewRequest request) {
        Objects.requireNonNull(request, "request");
        try (Runtime runtime = runtime()) {
            QueryDefinition definition = query(scope(request.scopeKind(), request.scopeId()), request.query());
            return QueryPublicViews.savedView(runtime.views.create(request.name(), definition));
        }
    }

    public Object listSavedViews(String scopeKind, String scopeId) {
        try (Runtime runtime = runtime()) {
            return QueryPublicViews.savedViews(runtime.views.list(scope(scopeKind, scopeId)));
        }
    }

    public Object getSavedView(String id) {
        try (Runtime runtime = runtime()) {
            return QueryPublicViews.savedView(runtime.views.get(SavedViewId.parse(id)));
        }
    }

    public Object savedViewVersions(String id) {
        try (Runtime runtime = runtime()) {
            return QueryPublicViews.savedVersions(runtime.views.versions(SavedViewId.parse(id)));
        }
    }

    public Object updateSavedView(String id, UpdateSavedViewRequest request) {
        Objects.requireNonNull(request, "request");
        try (Runtime runtime = runtime()) {
            SavedViewId savedViewId = SavedViewId.parse(id);
            var current = runtime.views.get(savedViewId);
            QueryDefinition definition = query(current.query().scope(), request.query());
            return QueryPublicViews.savedView(runtime.views.update(
                    savedViewId,
                    requirePositive(request.expectedRevision(), "expectedRevision"),
                    request.name(),
                    definition));
        }
    }

    public Object archiveSavedView(String id, RevisionRequest request) {
        Objects.requireNonNull(request, "request");
        try (Runtime runtime = runtime()) {
            return QueryPublicViews.savedView(runtime.views.archive(
                    SavedViewId.parse(id), requirePositive(request.expectedRevision(), "expectedRevision")));
        }
    }

    public Object executeSavedView(String id) {
        try (Runtime runtime = runtime()) {
            return QueryPublicViews.result(runtime.views.execute(SavedViewId.parse(id)));
        }
    }

    public QueryExport export(ExportRequest request) {
        Objects.requireNonNull(request, "request");
        try (Runtime runtime = runtime()) {
            QueryDefinition definition = query(scope(request.scopeKind(), request.scopeId()), request.query());
            return runtime.exports.export(definition, format(request.format()));
        }
    }

    public QueryExport exportSavedView(String id, ExportSavedViewRequest request) {
        Objects.requireNonNull(request, "request");
        try (Runtime runtime = runtime()) {
            var view = runtime.views.get(SavedViewId.parse(id));
            if (view.status() != SavedViewStatus.ACTIVE) {
                throw new IllegalStateException("saved view is archived: " + view.id());
            }
            return runtime.exports.export(view.query(), format(request.format()));
        }
    }

    private Object execute(QueryScope scope, QueryRequest request) {
        Objects.requireNonNull(request, "request");
        try (Runtime runtime = runtime()) {
            return QueryPublicViews.result(runtime.queries.execute(query(scope, request)));
        }
    }

    private QueryDefinition query(QueryScope scope, QueryRequest request) {
        return parser.parse(
                scope,
                requireText(request.entity(), "entity"),
                request.filter(),
                request.sort(),
                request.fields(),
                request.offset() == null ? 0 : request.offset(),
                request.limit() == null ? DEFAULT_LIMIT : request.limit());
    }

    private QueryScope scope(String kind, String id) {
        String normalized = requireText(kind, "scopeKind").toUpperCase();
        String scopeId = requireText(id, "scopeId");
        return switch (normalized) {
            case "PROJECT" -> new ProjectQueryScope(ProjectSpecificationId.parse(scopeId));
            case "PORTFOLIO" -> new PortfolioQueryScope(PortfolioId.parse(scopeId));
            default -> throw new IllegalArgumentException("scopeKind must be PROJECT or PORTFOLIO");
        };
    }

    private QueryExportFormat format(String raw) {
        try {
            return QueryExportFormat.valueOf(requireText(raw, "format").toUpperCase());
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("format must be JSON, CSV or MARKDOWN");
        }
    }

    private Runtime runtime() {
        return new Runtime(databasePath);
    }

    private static long requirePositive(Long value, String name) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(name + " must be a positive integer");
        }
        return value;
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    public record QueryRequest(
            String entity,
            String filter,
            String sort,
            String fields,
            Integer offset,
            Integer limit) {
        public QueryRequest {
            if (limit != null && (limit <= 0 || limit > QueryBudgets.MAX_PAGE_SIZE)) {
                throw new IllegalArgumentException("limit must be between 1 and " + QueryBudgets.MAX_PAGE_SIZE);
            }
            if (offset != null && offset < 0) {
                throw new IllegalArgumentException("offset must be non-negative");
            }
        }
    }

    public record ScopedQueryRequest(String scopeKind, String scopeId, QueryRequest query) {
        public ScopedQueryRequest {
            Objects.requireNonNull(query, "query");
        }
    }

    public record CreateSavedViewRequest(String name, String scopeKind, String scopeId, QueryRequest query) {
        public CreateSavedViewRequest {
            if (name == null || name.isBlank() || name.trim().length() > QueryBudgets.MAX_SAVED_VIEW_NAME) {
                throw new IllegalArgumentException(
                        "name must contain 1 to " + QueryBudgets.MAX_SAVED_VIEW_NAME + " characters");
            }
            name = name.trim();
            Objects.requireNonNull(query, "query");
        }
    }

    public record UpdateSavedViewRequest(Long expectedRevision, String name, QueryRequest query) {
        public UpdateSavedViewRequest {
            if (name == null || name.isBlank() || name.trim().length() > QueryBudgets.MAX_SAVED_VIEW_NAME) {
                throw new IllegalArgumentException(
                        "name must contain 1 to " + QueryBudgets.MAX_SAVED_VIEW_NAME + " characters");
            }
            name = name.trim();
            Objects.requireNonNull(query, "query");
        }
    }

    public record RevisionRequest(Long expectedRevision) {
    }

    public record ExportRequest(String scopeKind, String scopeId, String format, QueryRequest query) {
        public ExportRequest {
            Objects.requireNonNull(query, "query");
        }
    }

    public record ExportSavedViewRequest(String format) {
    }

    private static final class Runtime implements AutoCloseable {
        private final SqliteConnectionScope sqliteScope;
        private final SqliteSpecificationKnowledgeStore snapshots;
        private final SqliteVersionedRequirementStore requirements;
        private final SqliteSnapshotBusinessContentStore content;
        private final SqlitePortfolioStore portfolios;
        private final SqliteSavedViewStore saved;
        private final QueryExecutionService queries;
        private final SavedViewService views;
        private final QueryExportService exports;

        private Runtime(Path databasePath) {
            sqliteScope = SqliteConnectionScope.open(databasePath);
            SqliteSpecificationKnowledgeStore openedSnapshots = null;
            SqliteVersionedRequirementStore openedRequirements = null;
            SqliteSnapshotBusinessContentStore openedContent = null;
            SqlitePortfolioStore openedPortfolios = null;
            SqliteSavedViewStore openedSaved = null;
            try {
                openedSnapshots = new SqliteSpecificationKnowledgeStore(databasePath);
                openedRequirements = new SqliteVersionedRequirementStore(databasePath);
                openedContent = new SqliteSnapshotBusinessContentStore(databasePath);
                openedPortfolios = new SqlitePortfolioStore(databasePath);
                openedSaved = new SqliteSavedViewStore(databasePath);
            } catch (RuntimeException failure) {
                RuntimeException cleanup = null;
                cleanup = closeResource(openedSaved, cleanup);
                cleanup = closeResource(openedPortfolios, cleanup);
                cleanup = closeResource(openedContent, cleanup);
                cleanup = closeResource(openedRequirements, cleanup);
                cleanup = closeResource(openedSnapshots, cleanup);
                cleanup = closeResource(sqliteScope, cleanup);
                if (cleanup != null) failure.addSuppressed(cleanup);
                throw failure;
            }
            snapshots = openedSnapshots;
            requirements = openedRequirements;
            content = openedContent;
            portfolios = openedPortfolios;
            saved = openedSaved;
            queries = new QueryExecutionService(snapshots, requirements, content, portfolios);
            views = new SavedViewService(saved, queries);
            exports = new QueryExportService(queries);
        }

        @Override
        public void close() {
            RuntimeException failure = null;
            failure = closeResource(saved, failure);
            failure = closeResource(portfolios, failure);
            failure = closeResource(content, failure);
            failure = closeResource(requirements, failure);
            failure = closeResource(snapshots, failure);
            failure = closeResource(sqliteScope, failure);
            if (failure != null) throw failure;
        }

        private static RuntimeException closeResource(AutoCloseable closeable, RuntimeException previous) {
            if (closeable == null) return previous;
            try {
                closeable.close();
                return previous;
            } catch (RuntimeException failure) {
                if (previous != null) {
                    previous.addSuppressed(failure);
                    return previous;
                }
                return failure;
            } catch (Exception failure) {
                RuntimeException wrapped = new IllegalStateException("cannot close query runtime resource", failure);
                if (previous != null) {
                    previous.addSuppressed(wrapped);
                    return previous;
                }
                return wrapped;
            }
        }
    }
}
