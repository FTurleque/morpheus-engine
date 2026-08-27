package com.morpheus.application.query.export;

import com.morpheus.application.query.compact.CanonicalJsonSerializer;
import com.morpheus.application.query.compact.Utf8BoundedTextBuilder;
import com.morpheus.application.query.dsl.QueryBudgets;

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
        try {
            String content = switch (format) {
                case JSON -> json.toJson(view, QueryBudgets.MAX_EXPORT_BYTES);
                case CSV -> csv(view);
                case MARKDOWN -> markdown(view);
            };
            return new QueryExport(format, mediaType(format), content);
        } catch (Utf8BoundedTextBuilder.LimitExceededException failure) {
            throw new QueryExportBudgetException(
                    "export bytes exceed " + QueryBudgets.MAX_EXPORT_BYTES + " while rendering " + format.name());
        }
    }

    private String mediaType(QueryExportFormat format) {
        return switch (format) {
            case JSON -> "application/json; charset=utf-8";
            case CSV -> "text/csv; charset=utf-8";
            case MARKDOWN -> "text/markdown; charset=utf-8";
        };
    }

    private String csv(QueryExportView view) {
        Utf8BoundedTextBuilder out = new Utf8BoundedTextBuilder(QueryBudgets.MAX_EXPORT_BYTES);
        appendCsvHeader(out, view.columns());
        for (QueryExportView.RowView row : view.rows()) {
            appendCsvRow(out, view.columns(), row);
        }
        return out.toString();
    }

    private void appendCsvHeader(Utf8BoundedTextBuilder out, List<String> columns) {
        for (int index = 0; index < columns.size(); index++) {
            if (index > 0) {
                out.append(',');
            }
            out.append('"');
            appendCsvText(out, columns.get(index));
            out.append('"');
        }
        out.append('\n');
    }

    private void appendCsvRow(
            Utf8BoundedTextBuilder out,
            List<String> columns,
            QueryExportView.RowView row) {
        for (int index = 0; index < columns.size(); index++) {
            if (index > 0) {
                out.append(',');
            }
            out.append('"');
            appendCsvValues(out, cellValues(row, columns.get(index)));
            out.append('"');
        }
        out.append('\n');
    }

    private void appendCsvValues(Utf8BoundedTextBuilder out, List<String> values) {
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                out.append(", ");
            }
            appendCsvText(out, values.get(index));
        }
    }

    private void appendCsvText(Utf8BoundedTextBuilder out, String value) {
        int start = 0;
        for (int index = 0; index < value.length(); index++) {
            if (value.charAt(index) != '"') {
                continue;
            }
            out.append(value, start, index);
            out.append("\"\"");
            start = index + 1;
        }
        out.append(value, start, value.length());
    }

    private String markdown(QueryExportView view) {
        Utf8BoundedTextBuilder out = new Utf8BoundedTextBuilder(QueryBudgets.MAX_EXPORT_BYTES);
        appendMarkdownHeader(out, view.columns());
        appendMarkdownSeparator(out, view.columns().size());
        for (QueryExportView.RowView row : view.rows()) {
            appendMarkdownRow(out, view.columns(), row);
        }
        return out.toString();
    }

    private void appendMarkdownHeader(Utf8BoundedTextBuilder out, List<String> columns) {
        out.append('|');
        for (String column : columns) {
            out.append(' ');
            appendMarkdownText(out, column);
            out.append(" |");
        }
        out.append('\n');
    }

    private void appendMarkdownSeparator(Utf8BoundedTextBuilder out, int columnCount) {
        out.append('|');
        for (int index = 0; index < columnCount; index++) {
            out.append(" --- |");
        }
        out.append('\n');
    }

    private void appendMarkdownRow(
            Utf8BoundedTextBuilder out,
            List<String> columns,
            QueryExportView.RowView row) {
        out.append('|');
        for (String column : columns) {
            out.append(' ');
            appendMarkdownValues(out, cellValues(row, column));
            out.append(" |");
        }
        out.append('\n');
    }

    private void appendMarkdownValues(Utf8BoundedTextBuilder out, List<String> values) {
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                out.append(", ");
            }
            appendMarkdownText(out, values.get(index));
        }
    }

    private void appendMarkdownText(Utf8BoundedTextBuilder out, String value) {
        int start = 0;
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (character != '\\' && character != '|' && character != '\r' && character != '\n') {
                continue;
            }
            out.append(value, start, index);
            switch (character) {
                case '\\' -> out.append("\\\\");
                case '|' -> out.append("\\|");
                case '\r' -> {
                    if (index + 1 < value.length() && value.charAt(index + 1) == '\n') {
                        index++;
                    }
                    out.append("<br>");
                }
                case '\n' -> out.append("<br>");
                default -> throw new IllegalStateException("unexpected markdown escape character");
            }
            start = index + 1;
        }
        out.append(value, start, value.length());
    }

    private List<String> cellValues(QueryExportView.RowView row, String column) {
        return row.cells().stream()
                .filter(cell -> cell.field().equals(column))
                .findFirst()
                .map(QueryExportView.CellView::values)
                .orElseGet(List::of);
    }
}
