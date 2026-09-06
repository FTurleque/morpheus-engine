package com.morpheus.application.query.export;

import com.morpheus.application.query.dsl.PortfolioQueryScope;
import com.morpheus.application.query.dsl.ProjectQueryScope;
import com.morpheus.application.query.dsl.QueryBudgets;
import com.morpheus.application.query.dsl.QueryDefinition;
import com.morpheus.application.query.dsl.QueryExecutionService;
import com.morpheus.application.query.dsl.QueryMaterializationLimitException;
import com.morpheus.application.query.dsl.QueryMaterializedView;
import com.morpheus.application.query.dsl.QueryRow;

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
     * Exports the complete filtered/sorted view from one bounded materialization.
     * The export row/byte budgets are enforced before any partial payload can be returned.
     */
    public QueryExport export(QueryDefinition query, QueryExportFormat format) {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(format, "format");
        return formatter.render(collect(query), format);
    }

    private QueryExportView collect(QueryDefinition query) {
        final QueryMaterializedView materialized;
        try {
            materialized = queries.materializeComplete(query, QueryBudgets.MAX_EXPORT_ROWS);
        } catch (QueryMaterializationLimitException failure) {
            // Keep the public export contract and its QUERY_BUDGET_EXCEEDED mapping while the query engine
            // refuses the over-budget full projection before allocating it.
            budgets.requireRows(failure.actualRows());
            throw failure;
        }
        budgets.requireRows(materialized.totalMatches());

        return new QueryExportView(
                SCHEMA_VERSION,
                scopeKind(query),
                scopeId(query),
                query.entityType().name(),
                materialized.columns(),
                materialized.totalMatches(),
                materialized.items().stream().map(this::rowView).toList());
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
