package com.morpheus.api;

import com.morpheus.application.operability.OperationalRecorder;
import com.morpheus.application.query.dsl.PortfolioQueryScope;
import com.morpheus.application.query.dsl.ProjectQueryScope;
import com.morpheus.application.query.dsl.QueryBudgets;
import com.morpheus.application.query.dsl.QueryDefinition;
import com.morpheus.application.query.dsl.QueryDslParser;
import com.morpheus.application.query.dsl.QueryPublicViews;
import com.morpheus.application.query.dsl.QueryScope;
import com.morpheus.application.query.export.QueryExport;
import com.morpheus.application.query.export.QueryExportFormat;
import com.morpheus.application.query.saved.SavedViewId;
import com.morpheus.application.query.saved.SavedViewStatus;
import com.morpheus.domain.portfolio.PortfolioId;
import com.morpheus.domain.project.ProjectSpecificationId;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;

/** HTTP-facing M24 adapter; parsing and persistence wiring only, never query business semantics. */
public final class MorpheusQueryApiService {
    public static final int DEFAULT_LIMIT = 100;

    private final Path databasePath;
    private final OperationalRecorder recorder;
    private final QueryDslParser parser = new QueryDslParser();

    public MorpheusQueryApiService(Path databasePath, OperationalRecorder recorder) {
        this.databasePath = Objects.requireNonNull(databasePath, "databasePath").toAbsolutePath().normalize();
        this.recorder = Objects.requireNonNull(recorder, "recorder");
    }

    public Object executeProject(String projectId, QueryRequest request) {
        return execute(new ProjectQueryScope(ProjectSpecificationId.parse(projectId)), request);
    }

    public Object execute(ScopedQueryRequest request) {
        Objects.requireNonNull(request, "request");
        return execute(scope(request.scopeKind(), request.scopeId()), request.query());
    }

    public Object createSavedView(CreateSavedViewRequest request) {
        Objects.requireNonNull(request, "request");
        try (ApiRuntime runtime = runtime()) {
            QueryDefinition definition = query(scope(request.scopeKind(), request.scopeId()), request.query());
            return QueryPublicViews.savedView(runtime.views().create(request.name(), definition));
        }
    }

    public Object listSavedViews(String scopeKind, String scopeId) {
        try (ApiRuntime runtime = runtime()) {
            return QueryPublicViews.savedViews(runtime.views().list(scope(scopeKind, scopeId)));
        }
    }

    public Object getSavedView(String id) {
        try (ApiRuntime runtime = runtime()) {
            return QueryPublicViews.savedView(runtime.views().get(SavedViewId.parse(id)));
        }
    }

    public Object savedViewVersions(String id) {
        try (ApiRuntime runtime = runtime()) {
            return QueryPublicViews.savedVersions(runtime.views().versions(SavedViewId.parse(id)));
        }
    }

    public Object updateSavedView(String id, UpdateSavedViewRequest request) {
        Objects.requireNonNull(request, "request");
        try (ApiRuntime runtime = runtime()) {
            SavedViewId savedViewId = SavedViewId.parse(id);
            var current = runtime.views().get(savedViewId);
            QueryDefinition definition = query(current.query().scope(), request.query());
            return QueryPublicViews.savedView(runtime.views().update(
                    savedViewId,
                    requirePositive(request.expectedRevision(), "expectedRevision"),
                    request.name(),
                    definition));
        }
    }

    public Object archiveSavedView(String id, RevisionRequest request) {
        Objects.requireNonNull(request, "request");
        try (ApiRuntime runtime = runtime()) {
            return QueryPublicViews.savedView(runtime.views().archive(
                    SavedViewId.parse(id), requirePositive(request.expectedRevision(), "expectedRevision")));
        }
    }

    public Object executeSavedView(String id) {
        try (ApiRuntime runtime = runtime()) {
            return QueryPublicViews.result(runtime.views().execute(SavedViewId.parse(id)));
        }
    }

    public QueryExport export(ExportRequest request) {
        Objects.requireNonNull(request, "request");
        try (ApiRuntime runtime = runtime()) {
            ExportQueryRequest selection = request.query();
            QueryDefinition definition = query(
                    scope(request.scopeKind(), request.scopeId()),
                    new QueryRequest(selection.entity(), selection.filter(), selection.sort(), selection.fields(), null, null));
            return runtime.exports().export(definition, format(request.format()));
        }
    }

    public QueryExport exportSavedView(String id, ExportSavedViewRequest request) {
        Objects.requireNonNull(request, "request");
        try (ApiRuntime runtime = runtime()) {
            var view = runtime.views().get(SavedViewId.parse(id));
            if (view.status() != SavedViewStatus.ACTIVE) {
                throw new IllegalStateException("saved view is archived: " + view.id());
            }
            return runtime.exports().export(view.query(), format(request.format()));
        }
    }

    private Object execute(QueryScope scope, QueryRequest request) {
        Objects.requireNonNull(request, "request");
        try (ApiRuntime runtime = runtime()) {
            return QueryPublicViews.result(runtime.queries().execute(query(scope, request)));
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
        String normalized = requireText(kind, "scopeKind").toUpperCase(Locale.ROOT);
        String scopeId = requireText(id, "scopeId");
        return switch (normalized) {
            case "PROJECT" -> new ProjectQueryScope(ProjectSpecificationId.parse(scopeId));
            case "PORTFOLIO" -> new PortfolioQueryScope(PortfolioId.parse(scopeId));
            default -> throw new IllegalArgumentException("scopeKind must be PROJECT or PORTFOLIO");
        };
    }

    private QueryExportFormat format(String raw) {
        try {
            return QueryExportFormat.valueOf(requireText(raw, "format").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("format must be JSON, CSV or MARKDOWN");
        }
    }

    private ApiRuntime runtime() {
        return new ApiRuntime(databasePath, recorder);
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

    /**
     * What an export selects: a {@link QueryRequest} without the page. An export is always complete, so a
     * {@code limit} or an {@code offset} has nothing to apply to; the strict decoder refuses them as unknown
     * properties instead of accepting and ignoring them.
     */
    public record ExportQueryRequest(String entity, String filter, String sort, String fields) {
    }

    public record ExportRequest(String scopeKind, String scopeId, String format, ExportQueryRequest query) {
        public ExportRequest {
            Objects.requireNonNull(query, "query");
        }
    }

    public record ExportSavedViewRequest(String format) {
    }
}
