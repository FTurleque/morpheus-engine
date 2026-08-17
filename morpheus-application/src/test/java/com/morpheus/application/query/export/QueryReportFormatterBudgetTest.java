package com.morpheus.application.query.export;

import com.morpheus.application.query.dsl.QueryBudgets;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryReportFormatterBudgetTest {

    private final QueryReportFormatter formatter = new QueryReportFormatter();

    @Test
    void everyFormatRefusesOutputBeyondTheUtf8BudgetDuringRendering() {
        String oversized = "x".repeat(QueryBudgets.MAX_EXPORT_BYTES);
        QueryExportView view = view(List.of(oversized));

        for (QueryExportFormat format : QueryExportFormat.values()) {
            QueryExportBudgetException failure = assertThrows(
                    QueryExportBudgetException.class,
                    () -> formatter.render(view, format),
                    format.name());
            assertTrue(failure.getMessage().contains(Integer.toString(QueryBudgets.MAX_EXPORT_BYTES)));
        }
    }

    @Test
    void streamingCsvAndMarkdownPreserveExistingEscapingSemantics() {
        QueryExportView view = view(List.of("a\"b", "c|d\nx\\y"));

        assertEquals(
                "\"body\"\n\"a\"\"b, c|d\nx\\y\"\n",
                formatter.render(view, QueryExportFormat.CSV).content());
        assertEquals(
                "| body |\n| --- |\n| a\"b, c\\|d<br>x\\\\y |\n",
                formatter.render(view, QueryExportFormat.MARKDOWN).content());
    }

    private QueryExportView view(List<String> values) {
        return new QueryExportView(
                1,
                "PROJECT",
                "project-1",
                "REQUIREMENT",
                List.of("body"),
                1,
                List.of(new QueryExportView.RowView(
                        "project-1",
                        "requirement-1",
                        List.of(new QueryExportView.CellView("body", values)))));
    }
}
