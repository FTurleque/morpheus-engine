package com.morpheus.application.analysis;

import com.morpheus.domain.diagnostic.DiagnosticSeverity;
import com.morpheus.domain.requirement.RequirementId;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/** Structured warning attached to a deterministic change analysis. */
public record ChangeAnalysisWarning(
        ChangeAnalysisWarningCode code,
        DiagnosticSeverity severity,
        Optional<RequirementId> requirementId,
        String message,
        Map<String, String> details) {

    public ChangeAnalysisWarning {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(severity, "severity");
        requirementId = Objects.requireNonNull(requirementId, "requirementId");
        message = requireNonBlank(message, "message");
        Objects.requireNonNull(details, "details");
        TreeMap<String, String> canonical = new TreeMap<>();
        details.forEach((key, value) -> canonical.put(requireNonBlank(key, "details key"), requireNonBlank(value, "details value")));
        details = Map.copyOf(canonical);
    }

    private static String requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return normalized;
    }
}
