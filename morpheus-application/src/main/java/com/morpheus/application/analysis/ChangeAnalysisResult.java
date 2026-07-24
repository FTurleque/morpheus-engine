package com.morpheus.application.analysis;

import com.morpheus.domain.change.ChangeProposal;
import com.morpheus.domain.constraint.Constraint;
import com.morpheus.domain.decision.DesignDecision;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.task.ImplementationTask;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/** Complete deterministic functional/documentary impact view for one proposed change against one published baseline. */
public record ChangeAnalysisResult(
        KnowledgeSnapshotMetadata baselineSnapshot,
        ChangeProposal change,
        List<RequirementChangeImpact> requirementImpacts,
        List<Constraint> constraints,
        List<DesignDecision> designDecisions,
        List<ImplementationTask> implementationTasks,
        List<ChangeDependencyImpact> dependencyImpacts,
        boolean acceptanceCriteriaAvailable,
        List<ChangeAnalysisWarning> warnings,
        ChangeAnalysisSummary summary) {

    public ChangeAnalysisResult {
        Objects.requireNonNull(baselineSnapshot, "baselineSnapshot");
        Objects.requireNonNull(change, "change");
        requirementImpacts = Objects.requireNonNull(requirementImpacts, "requirementImpacts").stream()
                .peek(item -> Objects.requireNonNull(item, "requirementImpacts item"))
                .sorted(Comparator.comparing(item -> item.delta().requirementId().toString()))
                .toList();
        constraints = Objects.requireNonNull(constraints, "constraints").stream()
                .sorted(Comparator.comparing(item -> item.id().toString()))
                .toList();
        designDecisions = Objects.requireNonNull(designDecisions, "designDecisions").stream()
                .sorted(Comparator.comparing(item -> item.id().toString()))
                .toList();
        implementationTasks = Objects.requireNonNull(implementationTasks, "implementationTasks").stream()
                .sorted(Comparator.comparing(item -> item.id().toString()))
                .toList();
        dependencyImpacts = Objects.requireNonNull(dependencyImpacts, "dependencyImpacts").stream()
                .peek(item -> Objects.requireNonNull(item, "dependencyImpacts item"))
                .sorted(Comparator.comparing((ChangeDependencyImpact item) -> item.originRequirementId().toString())
                        .thenComparing(ChangeDependencyImpact::direction)
                        .thenComparing(item -> item.impactedEntity().toString())
                        .thenComparingInt(ChangeDependencyImpact::depth))
                .toList();
        warnings = Objects.requireNonNull(warnings, "warnings").stream()
                .peek(item -> Objects.requireNonNull(item, "warnings item"))
                .sorted(Comparator.comparing((ChangeAnalysisWarning warning) -> warning.code().name())
                        .thenComparing(warning -> warning.requirementId().map(Object::toString).orElse(""))
                        .thenComparing(ChangeAnalysisWarning::message))
                .toList();
        Objects.requireNonNull(summary, "summary");
    }
}
