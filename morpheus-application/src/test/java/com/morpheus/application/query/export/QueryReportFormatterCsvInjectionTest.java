package com.morpheus.application.query.export;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * A CSV cell wrapped in double quotes is safely escaped as CSV, but Excel/LibreOffice still parse the quoted
 * content itself for a leading formula trigger once the file is opened as a spreadsheet. These tests assert the
 * final rendered CSV text -- not a private escaping helper -- because that final text is what a spreadsheet
 * actually reads.
 */
class QueryReportFormatterCsvInjectionTest {

    private final QueryReportFormatter formatter = new QueryReportFormatter();

    @Test
    void neutralizesAnEqualsFormulaTrigger() {
        assertEquals("\"col\"\n\"'=1+1\"\n", render("=1+1"));
    }

    @Test
    void neutralizesAPlusFormulaTrigger() {
        assertEquals("\"col\"\n\"'+1+1\"\n", render("+1+1"));
    }

    @Test
    void neutralizesAMinusFormulaTrigger() {
        assertEquals("\"col\"\n\"'-1+1\"\n", render("-1+1"));
    }

    @Test
    void neutralizesAnAtFormulaTrigger() {
        assertEquals("\"col\"\n\"'@SUM(A1:A2)\"\n", render("@SUM(A1:A2)"));
    }

    @Test
    void neutralizesATabLeadingAFormula() {
        assertEquals("\"col\"\n\"'\t=cmd\"\n", render("\t=cmd"));
    }

    @Test
    void neutralizesACarriageReturnLeadingAFormula() {
        assertEquals("\"col\"\n\"'\r=cmd\"\n", render("\r=cmd"));
    }

    @Test
    void neutralizesAFormulaBehindLeadingWhitespace() {
        assertEquals("\"col\"\n\"'   =cmd|'/C calc'!A0\"\n", render("   =cmd|'/C calc'!A0"));
    }

    @Test
    void neutralizesAFormulaBehindASingleLeadingSpace() {
        assertEquals("\"col\"\n\"' =cmd\"\n", render(" =cmd"));
    }

    @Test
    void leavesOrdinaryTextUntouched() {
        assertEquals("\"col\"\n\"just some ordinary text\"\n", render("just some ordinary text"));
    }

    @Test
    void leavesAValueStartingWithADigitUntouched() {
        assertEquals("\"col\"\n\"42 widgets\"\n", render("42 widgets"));
    }

    @Test
    void stillEscapesEmbeddedQuotesOnADangerousCell() {
        assertEquals("\"col\"\n\"'=1+\"\"2\"\"\"\n", render("=1+\"2\""));
    }

    @Test
    void leavesAnEmbeddedCommaUntouched() {
        assertEquals("\"col\"\n\"a, b\"\n", render("a, b"));
    }

    @Test
    void leavesAnEmbeddedNewlineUntouchedWhenNotLeading() {
        assertEquals("\"col\"\n\"line one\nline two\"\n", render("line one\nline two"));
    }

    @Test
    void neutralizesALeadingNewline() {
        assertEquals("\"col\"\n\"'\n=cmd\"\n", render("\n=cmd"));
    }

    @Test
    void preservesUtf8TextUntouched() {
        assertEquals("\"col\"\n\"café élève 日本語\"\n", render("café élève 日本語"));
    }

    @Test
    void neutralizesADangerousColumnHeaderTheSameWayAsARowValue() {
        QueryExportView view = new QueryExportView(
                1,
                "PROJECT",
                "project-1",
                "REQUIREMENT",
                List.of("=HYPERLINK(\"http://evil\")"),
                1,
                List.of(new QueryExportView.RowView(
                        "project-1",
                        "requirement-1",
                        List.of(new QueryExportView.CellView("=HYPERLINK(\"http://evil\")", List.of("safe"))))));

        String csv = formatter.render(view, QueryExportFormat.CSV).content();

        assertEquals("\"'=HYPERLINK(\"\"http://evil\"\")\"\n\"safe\"\n", csv);
    }

    @Test
    void jsonAndMarkdownAreNotAltered() {
        QueryExportView view = view("=1+1");

        assertFalse(formatter.render(view, QueryExportFormat.JSON).content().startsWith("'"));
        assertEquals("| col |\n| --- |\n| =1+1 |\n", formatter.render(view, QueryExportFormat.MARKDOWN).content());
    }

    private String render(String value) {
        return formatter.render(view(value), QueryExportFormat.CSV).content();
    }

    private QueryExportView view(String value) {
        return new QueryExportView(
                1,
                "PROJECT",
                "project-1",
                "REQUIREMENT",
                List.of("col"),
                1,
                List.of(new QueryExportView.RowView(
                        "project-1",
                        "requirement-1",
                        List.of(new QueryExportView.CellView("col", List.of(value))))));
    }
}
