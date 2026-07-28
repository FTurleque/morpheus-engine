package com.morpheus.application.query.export;

import com.morpheus.application.query.compact.CanonicalJsonSerializer;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/** Pure deterministic renderer for canonical JSON, CSV and Markdown M24 report views. */
public final class QueryReportFormatter {
    private final CanonicalJsonSerializer json = new CanonicalJsonSerializer();
    private final QueryExportBudgetPolicy budgets = new QueryExportBudgetPolicy();

    public QueryExport render(QueryExportView view, QueryExportFormat format) {
        Objects.requireNonNull(view, "view");
        Objects.requireNonNull(format, "format");
        budgets.requireRows(view.totalMatches());
        String content = switch (format) {
            case JSON -> json.toJson(view);
            case CSV -> csv(view);
            case MARKDOWN -> markdown(view);
        };
        budgets.requireBytes(content.getBytes(StandardCharsets.UTF_8).length);
        return new QueryExport(format, mediaType(format), content);
    }

    private String mediaType(QueryExportFormat format) {
        return switch (format) {
            case JSON -> "application/json; charset=utf-8";
            case CSV -> "text/csv; charset=utf-8";
            case MARKDOWN -> "text/markdown; charset=utf-8";
        };
    }

    private String csv(QueryExportView view) {
        StringBuilder out = new StringBuilder();
        appendCsvRow(out, view.columns());
        for (QueryExportView.RowView row : view.rows()) {
            appendCsvRow(out, values(view.columns(), row));
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
            appendMarkdownRow(out, values(view.columns(), row));
        }
        return out.toString();
    }

    private List<String> values(List<String> columns, QueryExportView.RowView row) {
        return columns.stream()
                .map(column -> row.cells().stream()
                        .filter(cell -> cell.field().equals(column))
                        .findFirst()
                        .map(cell -> String.join(", ", cell.values()))
                        .orElse(""))
                .toList();
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
}
