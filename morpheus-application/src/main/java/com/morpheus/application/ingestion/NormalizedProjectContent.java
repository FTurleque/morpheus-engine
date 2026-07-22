package com.morpheus.application.ingestion;

import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.ChangeProposal;
import com.morpheus.domain.constraint.Constraint;
import com.morpheus.domain.constraint.ConstraintId;
import com.morpheus.domain.decision.DesignDecision;
import com.morpheus.domain.decision.DesignDecisionId;
import com.morpheus.domain.diagnostic.Diagnostic;
import com.morpheus.domain.evidence.Evidence;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.project.ProjectSpecification;
import com.morpheus.domain.requirement.Requirement;
import com.morpheus.domain.requirement.RequirementId;
import com.morpheus.domain.scenario.Scenario;
import com.morpheus.domain.specification.Specification;
import com.morpheus.domain.specification.SpecificationId;
import com.morpheus.domain.task.ImplementationTask;
import com.morpheus.domain.task.TaskId;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Coherent provider-neutral content produced by one M2 normalization pass. */
public record NormalizedProjectContent(
        ProjectSpecification project,
        List<Specification> specifications,
        List<Requirement> requirements,
        List<Scenario> scenarios,
        List<ChangeProposal> changes,
        List<Constraint> constraints,
        List<DesignDecision> designDecisions,
        List<ImplementationTask> tasks,
        List<Evidence> evidence,
        List<Diagnostic> diagnostics) {

    public NormalizedProjectContent(
            ProjectSpecification project,
            List<Specification> specifications,
            List<Requirement> requirements,
            List<Scenario> scenarios,
            List<Evidence> evidence,
            List<Diagnostic> diagnostics) {
        this(project, specifications, requirements, scenarios, List.of(), List.of(), List.of(), List.of(), evidence, diagnostics);
    }

    public NormalizedProjectContent {
        Objects.requireNonNull(project, "project");
        specifications = List.copyOf(Objects.requireNonNull(specifications, "specifications"));
        requirements = List.copyOf(Objects.requireNonNull(requirements, "requirements"));
        scenarios = List.copyOf(Objects.requireNonNull(scenarios, "scenarios"));
        changes = List.copyOf(Objects.requireNonNull(changes, "changes"));
        constraints = List.copyOf(Objects.requireNonNull(constraints, "constraints"));
        designDecisions = List.copyOf(Objects.requireNonNull(designDecisions, "designDecisions"));
        tasks = List.copyOf(Objects.requireNonNull(tasks, "tasks"));
        evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence"));
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));

        Set<SpecificationId> specificationIds = new HashSet<>();
        for (Specification specification : specifications) {
            if (!specification.projectId().equals(project.id())) {
                throw new IllegalArgumentException("specification belongs to another project: " + specification.id());
            }
            if (!specificationIds.add(specification.id())) {
                throw new IllegalArgumentException("duplicate specification identity: " + specification.id());
            }
        }

        Set<RequirementId> requirementIds = new HashSet<>();
        for (Requirement requirement : requirements) {
            if (!specificationIds.contains(requirement.specificationId())) {
                throw new IllegalArgumentException("requirement references unknown specification: " + requirement.id());
            }
            if (!requirementIds.add(requirement.id())) {
                throw new IllegalArgumentException("duplicate requirement identity: " + requirement.id());
            }
        }

        scenarios.forEach(scenario -> scenario.requirementId().ifPresent(requirementId -> {
            if (!requirementIds.contains(requirementId)) {
                throw new IllegalArgumentException("scenario references unknown requirement: " + scenario.id());
            }
        }));

        Set<ChangeId> changeIds = new HashSet<>();
        for (ChangeProposal change : changes) {
            if (!change.projectId().equals(project.id())) {
                throw new IllegalArgumentException("change belongs to another project: " + change.id());
            }
            if (!changeIds.add(change.id())) {
                throw new IllegalArgumentException("duplicate change identity: " + change.id());
            }
        }

        Set<ConstraintId> constraintIds = new HashSet<>();
        for (Constraint constraint : constraints) {
            requireKnownChange(constraint.changeId(), changeIds, "constraint", constraint.id());
            if (!constraintIds.add(constraint.id())) {
                throw new IllegalArgumentException("duplicate constraint identity: " + constraint.id());
            }
        }

        Set<DesignDecisionId> decisionIds = new HashSet<>();
        for (DesignDecision decision : designDecisions) {
            requireKnownChange(decision.changeId(), changeIds, "design decision", decision.id());
            if (!decisionIds.add(decision.id())) {
                throw new IllegalArgumentException("duplicate design decision identity: " + decision.id());
            }
        }

        Set<TaskId> taskIds = new HashSet<>();
        for (ImplementationTask task : tasks) {
            requireKnownChange(task.changeId(), changeIds, "task", task.id());
            if (!taskIds.add(task.id())) {
                throw new IllegalArgumentException("duplicate task identity: " + task.id());
            }
        }

        Set<EvidenceId> evidenceIds = new HashSet<>();
        evidence.forEach(item -> {
            if (!evidenceIds.add(item.id())) {
                throw new IllegalArgumentException("duplicate evidence identity: " + item.id());
            }
        });

        specifications.forEach(item -> requireEvidence(item.provenance().evidenceId(), evidenceIds));
        requirements.forEach(item -> requireEvidence(item.provenance().evidenceId(), evidenceIds));
        scenarios.forEach(item -> requireEvidence(item.provenance().evidenceId(), evidenceIds));
        changes.forEach(item -> requireEvidence(item.provenance().evidenceId(), evidenceIds));
        constraints.forEach(item -> requireEvidence(item.provenance().evidenceId(), evidenceIds));
        designDecisions.forEach(item -> requireEvidence(item.provenance().evidenceId(), evidenceIds));
        tasks.forEach(item -> requireEvidence(item.provenance().evidenceId(), evidenceIds));
    }

    private static void requireKnownChange(ChangeId changeId, Set<ChangeId> changeIds, String type, Object id) {
        if (!changeIds.contains(changeId)) {
            throw new IllegalArgumentException(type + " references unknown change: " + id);
        }
    }

    private static void requireEvidence(EvidenceId evidenceId, Set<EvidenceId> evidenceIds) {
        if (!evidenceIds.contains(evidenceId)) {
            throw new IllegalArgumentException("provenance references unknown evidence: " + evidenceId);
        }
    }
}
