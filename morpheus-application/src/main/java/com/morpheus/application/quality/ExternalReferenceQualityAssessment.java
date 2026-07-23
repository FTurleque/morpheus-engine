package com.morpheus.application.quality;

import com.morpheus.application.traceability.ExternalTraceabilityView;

import java.util.List;
import java.util.Objects;

/** Quality projection for one persisted external traceability link. */
public record ExternalReferenceQualityAssessment(
        ExternalTraceabilityView view,
        List<QualityFinding> findings) {

    public ExternalReferenceQualityAssessment {
        Objects.requireNonNull(view, "view");
        findings = Objects.requireNonNull(findings, "findings").stream()
                .peek(item -> Objects.requireNonNull(item, "findings item"))
                .sorted()
                .toList();
    }
}
