package com.morpheus.application.query.export;

import com.morpheus.application.query.dsl.PortfolioQueryScope;
import com.morpheus.application.query.dsl.ProjectQueryScope;
import com.morpheus.application.query.dsl.QueryBudgets;
import com.morpheus.application.query.dsl.QueryDefinition;
import com.morpheus.application.query.dsl.QueryExecutionService;
import com.morpheus.application.query.dsl.QueryPage;
import com.morpheus.application.query.dsl.QueryResult;
import com.morpheus.application.query.dsl.QueryRow;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Read-only deterministic JSON/CSV/Markdown reporting over complete bounded query views. */
public final class QueryExportService {
    private static final int SCHEMA_VERSION = 1;

    private final QueryExecutionService queries;
    private final QueryExportBudgetPolicy budgets;
    private final QueryReportFormatter formatter;

    public QueryExportService(QueryExecutionService queries) {
        this(queries, new QueryExportBudgetPolicy(), new QueryReportFormatter());
    }

    QueryExportService(
            QueryExecutionService queries,
            QueryExportBudgetPolicy budgets,
            QueryReportFormatter formatter) {
        this.queries = Objects.requireNonNull(queries, "queries");
        this.budgets = Objects.requireNonNull(budgets, "budgets");
        this.formatter = Objects.requireNonNull(formatter, "formatter");
    }

    /**
     * Exports the complete filtered/sorted view, intentionally independent from interactive pagination.
     * The export row/byte budgets are enforced before any partial payload can be returned.
     */
    public QueryExport export(QueryDefinition query, QueryExportFormat format) {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(format, "format");
        return formatter.render(collect(query), format);
    }

    private QueryExportView collect(QueryDefinition query) {
        QueryDefinition firstQuery = withPage(query, 0, QueryBudgets.MAX_PAGE_SIZE);
        QueryResult first = queries.execute(firstQuery);
        budgets.requireRows(first.totalMatches());

        List<QueryRow> rows = new ArrayList<>(first.items());
        int offset = first.items().size();
        while (offset < first.totalMatches()) {
            QueryResult page = queries.execute(withPage(query, offset, QueryBudgets.MAX_PAGE_SIZE));
            if (!page.columns().equals(first.columns()) || page.totalMatches() != first.totalMatches()) {
                throw new IllegalStateException("query changed while building deterministic export");
            }
            if (page.items().isEmpty()) {
                throw new IllegalStateException("query export pagination made no progress");
            }
            rows.addAll(page.items());
            offset += page.items().size();
        }

        return new QueryExportView(
                SCHEMA_VERSION,
                scopeKind(query),
                scopeId(query),
                query.entityType().name(),
                first.columns(),
                first.totalMatches(),
                rows.stream().map(this::rowView).toList());
    }

    private QueryDefinition withPage(QueryDefinition query, int offset, int limit) {
        return new QueryDefinition(
                query.scope(), query.entityType(), query.filter(), query.sort(), query.projection(), new QueryPage(offset, limit));
    }

    private QueryExportView.RowView rowView(QueryRow row) {
        return new QueryExportView.RowView(
                row.projectId(),
                row.entityId(),
                row.cells().stream()
                        .map(cell -> new QueryExportView.CellView(cell.field(), cell.values()))
                        .toList());
    }

    private String scopeKind(QueryDefinition query) {
        return query.scope() instanceof ProjectQueryScope ? "PROJECT" : "PORTFOLIO";
    }

    private String scopeId(QueryDefinition query) {
        if (query.scope() instanceof ProjectQueryScope project) {
            return project.projectId().toString();
        }
        return ((PortfolioQueryScope) query.scope()).portfolioId().toString();
    }
}
