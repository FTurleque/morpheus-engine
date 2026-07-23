package com.morpheus.application.quality;

import com.morpheus.domain.decision.DesignDecision;

import java.util.List;
import java.util.Objects;

/** Deterministic quality view for one normalized design decision. */
public record DesignDecisionQualityAssessment(
        DesignDecision decision,
        boolean tracedByOwningChange,
        DecisionJustificationStatus justificationStatus,
        List<QualityFinding> findings) {

    public DesignDecisionQualityAssessment {
        Objects.requireNonNull(decision, "decision");
        Objects.requireNonNull(justificationStatus, "justificationStatus");
        findings = Objects.requireNonNull(findings, "findings").stream()
                .peek(item -> Objects.requireNonNull(item, "findings item"))
                .sorted()
                .toList();
    }
}
