package com.morpheus.architecture.m24;

import com.morpheus.application.query.dsl.QueryBudgets;
import com.morpheus.application.query.export.QueryExportBudgetException;
import com.morpheus.application.query.export.QueryExportBudgetPolicy;
import com.morpheus.application.query.export.QueryExportFormat;
import com.morpheus.application.query.export.QueryExportView;
import com.morpheus.application.query.export.QueryReportFormatter;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryExportContractTest {
    private final QueryReportFormatter formatter = new QueryReportFormatter();

    @Test
    void canonicalJsonIsStableAndPreservesPortfolioProjectIdentity() {
        QueryExportView view = view();

        String first = formatter.render(view, QueryExportFormat.JSON).content();
        String second = formatter.render(view, QueryExportFormat.JSON).content();

        assertEquals(first, second);
        assertTrue(first.contains("\"scopeKind\":\"PORTFOLIO\""));
        assertTrue(first.contains("\"projectId\":\"project-a\""));
        assertTrue(first.contains("\"projectId\":\"project-b\""));
        assertEquals("application/json; charset=utf-8", formatter.render(view, QueryExportFormat.JSON).mediaType());
    }

    @Test
    void csvHasDeterministicHeadersRowsQuotesAndLfNewlines() {
        String csv = formatter.render(view(), QueryExportFormat.CSV).content();

        assertEquals(
                "\"id\",\"projectId\",\"title\"\n"
                        + "\"entity-a\",\"project-a\",\"A \"\"quoted\"\" title\"\n"
                        + "\"entity-b\",\"project-b\",\"line 1\nline 2\"\n",
                csv);
        assertTrue(!csv.contains("\r"));
    }

    @Test
    void markdownEscapesPipesBackslashesAndNewlinesDeterministically() {
        QueryExportView view = new QueryExportView(
                1,
                "PROJECT",
                "project-a",
                "CHANGE",
                List.of("id", "projectId", "title"),
                1,
                List.of(new QueryExportView.RowView(
                        "project-a",
                        "entity-a",
                        List.of(
                                cell("id", "entity-a"),
                                cell("projectId", "project-a"),
                                cell("title", "path\\value | next\nline")))));

        String markdown = formatter.render(view, QueryExportFormat.MARKDOWN).content();

        assertEquals(
                "| id | projectId | title |\n"
                        + "| --- | --- | --- |\n"
                        + "| entity-a | project-a | path\\\\value \\| next<br>line |\n",
                markdown);
    }

    @Test
    void emptyResultStillEmitsStableHeaders() {
        QueryExportView empty = new QueryExportView(
                1, "PROJECT", "project-a", "CHANGE", List.of("id", "projectId", "title"), 0, List.of());

        assertEquals(
                "\"id\",\"projectId\",\"title\"\n",
                formatter.render(empty, QueryExportFormat.CSV).content());
        assertEquals(
                "| id | projectId | title |\n| --- | --- | --- |\n",
                formatter.render(empty, QueryExportFormat.MARKDOWN).content());
    }

    @Test
    void exportBudgetsFailExplicitlyInsteadOfTruncating() {
        QueryExportBudgetPolicy policy = new QueryExportBudgetPolicy();

        policy.requireRows(QueryBudgets.MAX_EXPORT_ROWS);
        policy.requireBytes(QueryBudgets.MAX_EXPORT_BYTES);
        assertThrows(QueryExportBudgetException.class,
                () -> policy.requireRows(QueryBudgets.MAX_EXPORT_ROWS + 1));
        assertThrows(QueryExportBudgetException.class,
                () -> policy.requireBytes(QueryBudgets.MAX_EXPORT_BYTES + 1));
    }

    private QueryExportView view() {
        return new QueryExportView(
                1,
                "PORTFOLIO",
                "portfolio-1",
                "CHANGE",
                List.of("id", "projectId", "title"),
                2,
                List.of(
                        new QueryExportView.RowView(
                                "project-a",
                                "entity-a",
                                List.of(
                                        cell("id", "entity-a"),
                                        cell("projectId", "project-a"),
                                        cell("title", "A \"quoted\" title"))),
                        new QueryExportView.RowView(
                                "project-b",
                                "entity-b",
                                List.of(
                                        cell("id", "entity-b"),
                                        cell("projectId", "project-b"),
                                        cell("title", "line 1\nline 2")))));
    }

    private QueryExportView.CellView cell(String field, String value) {
        return new QueryExportView.CellView(field, List.of(value));
    }
}
