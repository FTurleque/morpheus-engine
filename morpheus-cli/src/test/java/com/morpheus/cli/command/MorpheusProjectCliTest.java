package com.morpheus.cli.command;

import com.morpheus.application.ingestion.BoundedDiagnostics;
import com.morpheus.domain.diagnostic.Diagnostic;
import com.morpheus.domain.diagnostic.DiagnosticCode;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MorpheusProjectCliTest {

    @Test
    void theSyncTextNamesTheTruncationReasonWhenTheDiagnosticsWereBounded() {
        List<Diagnostic> many = new ArrayList<>();
        for (int index = 0; index <= BoundedDiagnostics.MAX_ITEMS; index++) {
            many.add(Diagnostic.warning(
                    DiagnosticCode.PARTIAL_INGESTION,
                    "skipped",
                    Map.of("requirement", "Requirement " + index)));
        }

        List<String> lines = MorpheusProjectCli.diagnosticLines(
                BoundedDiagnostics.local(many));

        assertEquals(BoundedDiagnostics.MAX_ITEMS + 1, lines.size());
        assertEquals("diagnostic=WARNING PARTIAL_INGESTION - {requirement=Requirement 0} skipped", lines.getFirst());
        assertEquals("truncationReason=DIAGNOSTIC_LIMIT_REACHED:"
                + BoundedDiagnostics.MAX_ITEMS, lines.getLast());
    }
}
