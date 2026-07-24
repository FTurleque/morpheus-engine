package com.morpheus.application.analysis.compact;

import com.morpheus.application.analysis.ChangeAnalysisSummary;
import com.morpheus.application.query.compact.CompactQueryTypes.ChangeView;
import com.morpheus.application.query.compact.CompactQueryTypes.ConstraintView;
import com.morpheus.application.query.compact.CompactQueryTypes.DesignDecisionView;
import com.morpheus.application.query.compact.CompactQueryTypes.ImplementationTaskView;
import com.morpheus.application.query.compact.CompactQueryTypes.ProvenanceView;
import com.morpheus.application.query.compact.CompactQueryTypes.QueryMetadata;
import com.morpheus.application.query.compact.CompactQueryTypes.RequirementView;
import com.morpheus.application.query.compact.CompactQueryTypes.SnapshotMetadata;
import com.morpheus.application.query.compact.CompactQueryTypes.TraceLinkView;
import com.morpheus.application.query.compact.CompactQueryTypes.TraceNodeView;
import com.morpheus.domain.diagnostic.DiagnosticSeverity;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/** Stable JSON-ready M8 exposure DTO. All domain identities are rendered as strings. */
public record CompactChangeAnalysisView(
        QueryMetadata metadata,
        SnapshotMetadata baselineSnapshot,
        ChangeView change,
        ChangeAnalysisSummary summary,
        List<RequirementImpactView> requirementImpacts,
        List<ConstraintView> constraints,
        List<DesignDecisionView> designDecisions,
        List<ImplementationTaskView> implementationTasks,
        List<DependencyImpactView> dependencyImpacts,
        String acceptanceCoverageStatus,
        List<AnalysisWarningView> warnings) {

    public CompactChangeAnalysisView {
        Objects.requireNonNull(metadata, "metadata");
        Objects.requireNonNull(baselineSnapshot, "baselineSnapshot");
        Objects.requireNonNull(change, "change");
        Objects.requireNonNull(summary, "summary");
        requirementImpacts = List.copyOf(Objects.requireNonNull(requirementImpacts, "requirementImpacts"));
        constraints = List.copyOf(Objects.requireNonNull(constraints, "constraints"));
        designDecisions = List.copyOf(Objects.requireNonNull(designDecisions, "designDecisions"));
        implementationTasks = List.copyOf(Objects.requireNonNull(implementationTasks, "implementationTasks"));
        dependencyImpacts = List.copyOf(Objects.requireNonNull(dependencyImpacts, "dependencyImpacts"));
        acceptanceCoverageStatus = requireNonBlank(acceptanceCoverageStatus, "acceptanceCoverageStatus");
        warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings"));
    }

    public record RequirementImpactView(
            String deltaId,
            String kind,
            String requirementId,
            String specificationKey,
            Optional<String> key,
            String title,
            Optional<String> statement,
            ProvenanceView provenance,
            Optional<RequirementView> currentRequirement,
            List<ScenarioView> currentScenarios,
            List<ScenarioView> proposedScenarios,
            List<String> changedFields,
            List<AnalysisWarningView> warnings) {
        public RequirementImpactView {
            deltaId = requireNonBlank(deltaId, "deltaId");
            kind = requireNonBlank(kind, "kind");
            requirementId = requireNonBlank(requirementId, "requirementId");
            specificationKey = requireNonBlank(specificationKey, "specificationKey");
            key = normalized(key, "key");
            title = requireNonBlank(title, "title");
            statement = normalized(statement, "statement");
            Objects.requireNonNull(provenance, "provenance");
            currentRequirement = Objects.requireNonNull(currentRequirement, "currentRequirement");
            currentScenarios = List.copyOf(Objects.requireNonNull(currentScenarios, "currentScenarios"));
            proposedScenarios = List.copyOf(Objects.requireNonNull(proposedScenarios, "proposedScenarios"));
            changedFields = Objects.requireNonNull(changedFields, "changedFields").stream().sorted().toList();
            warnings = List.copyOf(Objects.requireNonNull(warnings, "warnings"));
        }
    }

    public record ScenarioView(
            String id,
            Optional<String> requirementId,
            String title,
            List<String> preconditions,
            String action,
            String expectedOutcome,
            ProvenanceView provenance) {
        public ScenarioView {
            id = requireNonBlank(id, "id");
            requirementId = normalized(requirementId, "requirementId");
            title = requireNonBlank(title, "title");
            preconditions = List.copyOf(Objects.requireNonNull(preconditions, "preconditions"));
            action = requireNonBlank(action, "action");
            expectedOutcome = requireNonBlank(expectedOutcome, "expectedOutcome");
            Objects.requireNonNull(provenance, "provenance");
        }
    }

    public record DependencyImpactView(
            String originRequirementId,
            String direction,
            TraceNodeView impactedEntity,
            int depth,
            List<PathStepView> path) {
        public DependencyImpactView {
            originRequirementId = requireNonBlank(originRequirementId, "originRequirementId");
            direction = requireNonBlank(direction, "direction");
            Objects.requireNonNull(impactedEntity, "impactedEntity");
            if (depth <= 0) {
                throw new IllegalArgumentException("depth must be greater than zero");
            }
            path = List.copyOf(Objects.requireNonNull(path, "path"));
            if (path.size() != depth) {
                throw new IllegalArgumentException("path size must equal depth");
            }
        }
    }

    public record PathStepView(
            TraceNodeView from,
            TraceNodeView into,
            TraceLinkView link) {
        public PathStepView {
            Objects.requireNonNull(from, "from");
            Objects.requireNonNull(into, "into");
            Objects.requireNonNull(link, "link");
        }
    }

    public record AnalysisWarningView(
            String code,
            DiagnosticSeverity severity,
            Optional<String> requirementId,
            String message,
            Map<String, String> details) {
        public AnalysisWarningView {
            code = requireNonBlank(code, "code");
            Objects.requireNonNull(severity, "severity");
            requirementId = normalized(requirementId, "requirementId");
            message = requireNonBlank(message, "message");
            Objects.requireNonNull(details, "details");
            TreeMap<String, String> sorted = new TreeMap<>();
            details.forEach((key, value) -> sorted.put(
                    requireNonBlank(key, "details key"),
                    requireNonBlank(value, "details value")));
            details = Map.copyOf(sorted);
        }
    }

    private static Optional<String> normalized(Optional<String> value, String name) {
        return Objects.requireNonNull(value, name).map(String::trim).filter(candidate -> !candidate.isEmpty());
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
