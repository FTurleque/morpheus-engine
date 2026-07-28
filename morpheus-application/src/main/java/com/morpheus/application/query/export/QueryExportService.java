package com.morpheus.application.query.export;

import com.morpheus.application.query.compact.CanonicalJsonSerializer;
import com.morpheus.application.query.dsl.PortfolioQueryScope;
import com.morpheus.application.query.dsl.ProjectQueryScope;
import com.morpheus.application.query.dsl.QueryBudgets;
import com.morpheus.application.query.dsl.QueryDefinition;
import com.morpheus.application.query.dsl.QueryExecutionService;
import com.morpheus.application.query.dsl.QueryPage;
import com.morpheus.application.query.dsl.QueryResult;
import com.morpheus.application.query.dsl.QueryRow;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Read-only deterministic JSON/CSV/Markdown reporting over complete bounded query views. */
public final class QueryExportService {
    private static final int SCHEMA_VERSION = 1;

    private final QueryExecutionService queries;
    private final CanonicalJsonSerializer json = new CanonicalJsonSerializer();

    public QueryExportService(QueryExecutionService queries) {
        this.queries = Objects.requireNonNull(queries, "queries");
    }

    /**
     * Exports the complete filtered/sorted view, intentionally independent from interactive pagination.
     * The export row/byte budgets are enforced before any partial payload can be returned.
     */
    public QueryExport export(QueryDefinition query, QueryExportFormat format) {
        Objects.requireNonNull(query, "query");
        Objects.requireNonNull(format, "format");
        QueryExportView view = collect(query);
        String content = switch (format) {
            case JSON -> json.toJson(view);
            case CSV -> csv(view);
            case MARKDOWN -> markdown(view);
        };
        int bytes = content.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > QueryBudgets.MAX_EXPORT_BYTES) {
            throw new QueryExportBudgetException(
                    "export bytes exceed " + QueryBudgets.MAX_EXPORT_BYTES + ": " + bytes);
        }
        String mediaType = switch (format) {
            case JSON -> "application/json; charset=utf-8";
            case CSV -> "text/csv; charset=utf-8";
            case MARKDOWN -> "text/markdown; charset=utf-8";
        };
        return new QueryExport(format, mediaType, content);
    }

    private QueryExportView collect(QueryDefinition query) {
        QueryDefinition firstQuery = withPage(query, 0, QueryBudgets.MAX_PAGE_SIZE);
        QueryResult first = queries.execute(firstQuery);
        if (first.totalMatches() > QueryBudgets.MAX_EXPORT_ROWS) {
            throw new QueryExportBudgetException(
                    "export rows exceed " + QueryBudgets.MAX_EXPORT_ROWS + ": " + first.totalMatches());
        }

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

    private String csv(QueryExportView view) {
        StringBuilder out = new StringBuilder();
        appendCsvRow(out, view.columns());
        for (QueryExportView.RowView row : view.rows()) {
            appendCsvRow(out, view.columns().stream()
                    .map(column -> row.cells().stream()
                            .filter(cell -> cell.field().equals(column))
                            .findFirst()
                            .map(cell -> String.join(", ", cell.values()))
                            .orElse(""))
                    .toList());
        }
        return out.toString();
    }

    private void appendCsvRow(StringBuilder out, List<String> values) {
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                out.append(',');
            }
            out.append('"').append(values.get(index).replace("\"", "\"\"")).append('"');
        }
        out.append('\n');
    }

    private String markdown(QueryExportView view) {
        StringBuilder out = new StringBuilder();
        appendMarkdownRow(out, view.columns());
        appendMarkdownRow(out, view.columns().stream().map(ignored -> "---").toList());
        for (QueryExportView.RowView row : view.rows()) {
            appendMarkdownRow(out, view.columns().stream()
                    .map(column -> row.cells().stream()
                            .filter(cell -> cell.field().equals(column))
                            .findFirst()
                            .map(cell -> String.join(", ", cell.values()))
                            .orElse(""))
                    .toList());
        }
        return out.toString();
    }

    private void appendMarkdownRow(StringBuilder out, List<String> values) {
        out.append('|');
        for (String value : values) {
            out.append(' ').append(markdownCell(value)).append(" |");
        }
        out.append('\n');
    }

    private String markdownCell(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("|", "\\|")
                .replace("\r\n", "<br>")
                .replace("\n", "<br>")
                .replace("\r", "<br>");
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
