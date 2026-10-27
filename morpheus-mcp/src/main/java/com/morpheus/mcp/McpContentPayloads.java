package com.morpheus.mcp;

import com.morpheus.application.foundation.PayloadMap;
import com.morpheus.application.quality.ChangeCompletenessAssessment;
import com.morpheus.application.quality.QualityFinding;
import com.morpheus.application.query.SnapshotPage;

import java.util.List;

/**
 * The page envelope and the readiness shapes the catalogue tools answer with, written once for all of them.
 *
 * <p>A domain type the HTTP adapter also shows is projected by {@code PublicPayloads}, not here.</p>
 */
final class McpContentPayloads {

    private McpContentPayloads() {
    }

    static Object page(SnapshotPage<?> page, List<?> items) {
        return PagedEnvelope.snapshotPage(page, items);
    }

    static Object lifecycleFacts(ChangeCompletenessAssessment assessment) {
        var facts = assessment.lifecycleFacts();
        return PayloadMap.of(
                "requirementsIdentified", facts.requirementsIdentified().name(),
                "criticalConstraintsKnown", facts.criticalConstraintsKnown().name(),
                "acceptanceCriteriaDefined", facts.acceptanceCriteriaDefined().name(),
                "designRequired", facts.designRequired().name(),
                "designDecisionsAvailable", facts.designDecisionsAvailable().name(),
                "planPresent", facts.planPresent().name(),
                "knownBlocker", facts.knownBlocker().name(),
                "blockingAcceptanceCriterionFailed", facts.blockingAcceptanceCriterionFailed().name(),
                "blockingAcceptanceCriterionUnverified", facts.blockingAcceptanceCriterionUnverified().name());
    }

    static Object finding(QualityFinding finding) {
        return PayloadMap.of(
                "code", finding.code().name(),
                "severity", finding.severity().name(),
                "evidenceKind", finding.evidenceKind().name(),
                "subjectKind", finding.subject().kind().name(),
                "subjectId", finding.subject().identity().toString(),
                "message", finding.message(),
                "details", finding.details(),
                "confidence", finding.confidence().map(Object::toString).orElse(""),
                "evidenceIds", finding.evidenceIds().stream().map(Object::toString).toList());
    }
}
