package com.morpheus.cli.command;

import com.morpheus.application.analysis.ChangeAnalysisWarning;
import com.morpheus.application.analysis.ChangeAnalysisWarningCode;
import com.morpheus.domain.diagnostic.DiagnosticSeverity;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MorpheusChangeCliTest {

    @Test
    void theWarningLinesListOneCodePerWarningAndEachDistinctTruncationReasonOnce() {
        List<ChangeAnalysisWarning> warnings = List.of(
                warning(ChangeAnalysisWarningCode.ACCEPTANCE_CRITERIA_UNAVAILABLE, Map.of("status", "UNAVAILABLE")),
                warning(ChangeAnalysisWarningCode.TRACEABILITY_TRAVERSAL_TRUNCATED,
                        Map.of("direction", "DEPENDENCY", "truncationReason", "NODE_BUDGET_REACHED:1000")),
                warning(ChangeAnalysisWarningCode.TRACEABILITY_TRAVERSAL_TRUNCATED,
                        Map.of("direction", "DEPENDENT", "truncationReason", "NODE_BUDGET_REACHED:1000")),
                warning(ChangeAnalysisWarningCode.TRACEABILITY_TRAVERSAL_TRUNCATED,
                        Map.of("direction", "DEPENDENT", "truncationReason", "DEPTH_BUDGET_REACHED:2")));

        assertEquals(List.of(
                        "warningCodes=ACCEPTANCE_CRITERIA_UNAVAILABLE,TRACEABILITY_TRAVERSAL_TRUNCATED,"
                                + "TRACEABILITY_TRAVERSAL_TRUNCATED,TRACEABILITY_TRAVERSAL_TRUNCATED",
                        "truncationReason=DEPTH_BUDGET_REACHED:2",
                        "truncationReason=NODE_BUDGET_REACHED:1000"),
                MorpheusChangeCli.analysisWarningLines(warnings));
        assertEquals(List.of("warningCodes="), MorpheusChangeCli.analysisWarningLines(List.of()));
    }

    private static ChangeAnalysisWarning warning(ChangeAnalysisWarningCode code, Map<String, String> details) {
        return new ChangeAnalysisWarning(code, DiagnosticSeverity.WARNING, Optional.empty(), "message", details);
    }
}
