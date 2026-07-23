package com.morpheus.application.quality;

import com.morpheus.domain.change.ChangeProposal;

import java.util.List;
import java.util.Objects;

/** Snapshot-derived completeness facts for one normalized change proposal. */
public record ChangeCompletenessAssessment(
        ChangeProposal change,
        ChangeLifecycleFactAssessment lifecycleFacts,
        int currentRequirementCount,
        int constraintCount,
        int designDecisionCount,
        int implementationTaskCount,
        List<QualityFinding> findings) {

    public ChangeCompletenessAssessment {
        Objects.requireNonNull(change, "change");
        Objects.requireNonNull(lifecycleFacts, "lifecycleFacts");
        if (currentRequirementCount < 0 || constraintCount < 0
                || designDecisionCount < 0 || implementationTaskCount < 0) {
            throw new IllegalArgumentException("change completeness counts must be non-negative");
        }
        findings = Objects.requireNonNull(findings, "findings").stream()
                .peek(item -> Objects.requireNonNull(item, "findings item"))
                .sorted()
                .toList();
    }
}
