package com.morpheus.application.orchestration;

import com.morpheus.application.quality.QualityFactValue;
import com.morpheus.domain.reference.ExternalReferenceResolutionReason;
import com.morpheus.domain.reference.ExternalReferenceResolutionState;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Compact, JSON-safe UC-16 projection for JARVIS and other orchestration consumers. */
public record ChangeOrchestrationState(
        SnapshotView snapshot,
        ChangeView change,
        ChangeLifecycleObservation lifecycle,
        Map<String, QualityFactValue> observableFacts,
        List<String> missingArtifacts,
        List<String> unavailableFacts,
        AvailabilityView acceptanceCriteria,
        List<ConstraintView> applicableConstraints,
        AvailabilityView blockingConstraints,
        List<ExternalReferenceView> unresolvedLinks,
        List<QualityFindingView> qualityFindings,
        List<ChangeTransitionEvaluation> nextTransitions,
        boolean persisted) {

    public ChangeOrchestrationState {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(change, "change");
        Objects.requireNonNull(lifecycle, "lifecycle");
        observableFacts = Map.copyOf(Objects.requireNonNull(observableFacts, "observableFacts"));
        missingArtifacts = List.copyOf(Objects.requireNonNull(missingArtifacts, "missingArtifacts"));
        unavailableFacts = List.copyOf(Objects.requireNonNull(unavailableFacts, "unavailableFacts"));
        Objects.requireNonNull(acceptanceCriteria, "acceptanceCriteria");
        applicableConstraints = List.copyOf(Objects.requireNonNull(applicableConstraints, "applicableConstraints"));
        Objects.requireNonNull(blockingConstraints, "blockingConstraints");
        unresolvedLinks = List.copyOf(Objects.requireNonNull(unresolvedLinks, "unresolvedLinks"));
        qualityFindings = List.copyOf(Objects.requireNonNull(qualityFindings, "qualityFindings"));
        nextTransitions = List.copyOf(Objects.requireNonNull(nextTransitions, "nextTransitions"));
        if (persisted) {
            throw new IllegalArgumentException("M14 orchestration state is a live read-only projection and cannot be persisted");
        }
    }

    public record SnapshotView(String id, String projectId, String state, String createdAt) {
        public SnapshotView {
            id = requireNonBlank(id, "id");
            projectId = requireNonBlank(projectId, "projectId");
            state = requireNonBlank(state, "state");
            createdAt = requireNonBlank(createdAt, "createdAt");
        }
    }

    public record ChangeView(String id, Optional<String> key, String title, String intent) {
        public ChangeView {
            id = requireNonBlank(id, "id");
            key = Objects.requireNonNull(key, "key").map(String::trim).filter(value -> !value.isEmpty());
            title = requireNonBlank(title, "title");
            intent = requireNonBlank(intent, "intent");
        }
    }

    public record AvailabilityView(String status, String reason, int observedCount) {
        public AvailabilityView {
            status = requireNonBlank(status, "status");
            reason = requireNonBlank(reason, "reason");
            if (observedCount < 0) {
                throw new IllegalArgumentException("observedCount must be non-negative");
            }
        }
    }

    public record ConstraintView(String id, String statement) {
        public ConstraintView {
            id = requireNonBlank(id, "id");
            statement = requireNonBlank(statement, "statement");
        }
    }

    public record ExternalReferenceView(
            String id,
            String system,
            Optional<String> project,
            String resourceType,
            String externalId,
            Optional<String> revision,
            ExternalReferenceResolutionState resolutionState,
            ExternalReferenceResolutionReason resolutionReason) {
        public ExternalReferenceView {
            id = requireNonBlank(id, "id");
            system = requireNonBlank(system, "system");
            project = Objects.requireNonNull(project, "project");
            resourceType = requireNonBlank(resourceType, "resourceType");
            externalId = requireNonBlank(externalId, "externalId");
            revision = Objects.requireNonNull(revision, "revision");
            Objects.requireNonNull(resolutionState, "resolutionState");
            Objects.requireNonNull(resolutionReason, "resolutionReason");
        }
    }

    public record QualityFindingView(
            String code,
            String severity,
            String evidenceKind,
            String message,
            Map<String, String> details,
            Optional<Double> confidence,
            List<String> evidenceIds) {
        public QualityFindingView {
            code = requireNonBlank(code, "code");
            severity = requireNonBlank(severity, "severity");
            evidenceKind = requireNonBlank(evidenceKind, "evidenceKind");
            message = requireNonBlank(message, "message");
            details = Map.copyOf(Objects.requireNonNull(details, "details"));
            confidence = Objects.requireNonNull(confidence, "confidence");
            evidenceIds = List.copyOf(Objects.requireNonNull(evidenceIds, "evidenceIds"));
        }
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
