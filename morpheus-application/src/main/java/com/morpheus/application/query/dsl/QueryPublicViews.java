package com.morpheus.application.query.dsl;

import com.morpheus.application.query.saved.SavedViewDefinition;
import com.morpheus.application.query.saved.SavedViewVersion;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Transport-safe M24 projections shared by CLI, MCP and HTTP. */
public final class QueryPublicViews {
    private QueryPublicViews() {
    }

    public static QueryResultView result(QueryResult result) {
        Objects.requireNonNull(result, "result");
        return new QueryResultView(
                query(result.query()),
                result.columns(),
                result.items().stream().map(QueryPublicViews::row).toList(),
                result.totalMatches(),
                result.hasMore());
    }

    public static SavedViewView savedView(SavedViewDefinition view) {
        Objects.requireNonNull(view, "view");
        return new SavedViewView(
                view.id().toString(), view.name(), query(view.query()), view.revision(),
                view.status().name(), view.createdAt().toString(), view.updatedAt().toString());
    }

    public static SavedViewVersionView savedVersion(SavedViewVersion version) {
        Objects.requireNonNull(version, "version");
        return new SavedViewVersionView(
                version.id().toString(), version.revision(), version.name(), query(version.query()),
                version.status().name(), version.recordedAt().toString());
    }

    public static List<SavedViewView> savedViews(List<SavedViewDefinition> views) {
        return List.copyOf(views).stream().map(QueryPublicViews::savedView).toList();
    }

    public static List<SavedViewVersionView> savedVersions(List<SavedViewVersion> versions) {
        return List.copyOf(versions).stream().map(QueryPublicViews::savedVersion).toList();
    }

    public static QueryDefinitionView query(QueryDefinition query) {
        return new QueryDefinitionView(
                scope(query.scope()),
                query.entityType().name(),
                query.filter().map(QueryPublicViews::filter),
                query.sort().stream().map(item -> new SortView(item.field(), item.direction().name())).toList(),
                query.projection().fields(),
                new PageView(query.page().offset(), query.page().limit()));
    }

    private static ScopeView scope(QueryScope scope) {
        if (scope instanceof ProjectQueryScope project) {
            return new ScopeView("PROJECT", project.projectId().toString());
        }
        return new ScopeView("PORTFOLIO", ((PortfolioQueryScope) scope).portfolioId().toString());
    }

    private static FilterView filter(QueryFilter filter) {
        if (filter instanceof QueryPredicate predicate) {
            return new FilterView(
                    "PREDICATE", Optional.of(predicate.field()), Optional.of(predicate.operator().name()),
                    predicate.values(), List.of());
        }
        if (filter instanceof QueryAnd and) {
            return new FilterView(
                    "AND", Optional.empty(), Optional.empty(), List.of(),
                    and.children().stream().map(QueryPublicViews::filter).toList());
        }
        if (filter instanceof QueryOr or) {
            return new FilterView(
                    "OR", Optional.empty(), Optional.empty(), List.of(),
                    or.children().stream().map(QueryPublicViews::filter).toList());
        }
        QueryNot not = (QueryNot) filter;
        return new FilterView(
                "NOT", Optional.empty(), Optional.empty(), List.of(), List.of(filter(not.child())));
    }

    private static RowView row(QueryRow row) {
        return new RowView(
                row.entityType().name(), row.projectId(), row.entityId(),
                row.cells().stream().map(cell -> new CellView(cell.field(), cell.values())).toList());
    }

    public record ScopeView(String kind, String id) {
    }

    public record FilterView(
            String kind,
            Optional<String> field,
            Optional<String> operator,
            List<String> values,
            List<FilterView> children) {
    }

    public record SortView(String field, String direction) {
    }

    public record PageView(int offset, int limit) {
    }

    public record QueryDefinitionView(
            ScopeView scope,
            String entityType,
            Optional<FilterView> filter,
            List<SortView> sort,
            List<String> projection,
            PageView page) {
    }

    public record CellView(String field, List<String> values) {
    }

    public record RowView(String entityType, String projectId, String entityId, List<CellView> cells) {
    }

    public record QueryResultView(
            QueryDefinitionView query,
            List<String> columns,
            List<RowView> items,
            int totalMatches,
            boolean hasMore) {
    }

    public record SavedViewView(
            String id,
            String name,
            QueryDefinitionView query,
            long revision,
            String status,
            String createdAt,
            String updatedAt) {
    }

    public record SavedViewVersionView(
            String id,
            long revision,
            String name,
            QueryDefinitionView query,
            String status,
            String recordedAt) {
    }
}
