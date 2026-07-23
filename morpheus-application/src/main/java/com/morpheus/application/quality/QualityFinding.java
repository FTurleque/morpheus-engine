package com.morpheus.application.quality;

import com.morpheus.domain.diagnostic.DiagnosticSeverity;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.traceability.TraceabilityEntityRef;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Immutable machine-readable quality finding derived from one published snapshot. */
public record QualityFinding(
        QualityFindingCode code,
        DiagnosticSeverity severity,
        QualityEvidenceKind evidenceKind,
        TraceabilityEntityRef subject,
        String message,
        Map<String, String> details,
        Optional<Double> confidence,
        List<EvidenceId> evidenceIds) implements Comparable<QualityFinding> {

    private static final Comparator<QualityFinding> ORDER = Comparator
            .comparing(QualityFinding::subject)
            .thenComparing(QualityFinding::code)
            .thenComparing(QualityFinding::message);

    public QualityFinding {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(evidenceKind, "evidenceKind");
        Objects.requireNonNull(subject, "subject");
        message = requireNonBlank(message, "message");
        details = Map.copyOf(Objects.requireNonNull(details, "details"));
        confidence = Objects.requireNonNull(confidence, "confidence");
        evidenceIds = Objects.requireNonNull(evidenceIds, "evidenceIds").stream()
                .distinct()
                .sorted()
                .toList();

        if (evidenceKind == QualityEvidenceKind.HEURISTIC && confidence.isEmpty()) {
            throw new IllegalArgumentException("heuristic quality finding requires explicit confidence");
        }
        if (evidenceKind == QualityEvidenceKind.DETERMINISTIC && confidence.isPresent()) {
            throw new IllegalArgumentException("deterministic quality finding must not carry confidence");
        }
        confidence.ifPresent(value -> {
            if (!Double.isFinite(value) || value < 0.0 || value > 1.0) {
                throw new IllegalArgumentException("confidence must be finite and between 0.0 and 1.0");
            }
        });
    }

    @Override
    public int compareTo(QualityFinding other) {
        return ORDER.compare(this, other);
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
