package com.morpheus.application.store;

import com.morpheus.domain.acceptance.AcceptanceCriterion;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.ChangeProposal;
import com.morpheus.domain.constraint.Constraint;
import com.morpheus.domain.decision.DesignDecision;
import com.morpheus.domain.evidence.Evidence;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.scenario.Scenario;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.specification.Specification;
import com.morpheus.domain.task.ImplementationTask;
import com.morpheus.domain.version.SpecificationVersionId;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable snapshot-owned projection for non-Requirement normalized business content. */
public record SnapshotBusinessContent(
        KnowledgeSnapshotId snapshotId,
        SpecificationVersionId specificationVersionId,
        List<Specification> specifications,
        List<Scenario> scenarios,
        List<ChangeProposal> changes,
        List<Constraint> constraints,
        List<DesignDecision> designDecisions,
        List<ImplementationTask> tasks,
        List<AcceptanceCriterion> acceptanceCriteria,
        List<Evidence> evidence) {

    /** Compatibility constructor for pre-M15 callers. */
    public SnapshotBusinessContent(
            KnowledgeSnapshotId snapshotId,
            SpecificationVersionId specificationVersionId,
            List<Specification> specifications,
            List<Scenario> scenarios,
            List<ChangeProposal> changes,
            List<Constraint> constraints,
            List<DesignDecision> designDecisions,
            List<ImplementationTask> tasks,
            List<Evidence> evidence) {
        this(
                snapshotId,
                specificationVersionId,
                specifications,
                scenarios,
                changes,
                constraints,
                designDecisions,
                tasks,
                List.of(),
                evidence);
    }

    public SnapshotBusinessContent {
        Objects.requireNonNull(snapshotId, "snapshotId");
        Objects.requireNonNull(specificationVersionId, "specificationVersionId");
        specifications = canonical(specifications, item -> item.id().toString(), "specifications");
        scenarios = canonical(scenarios, item -> item.id().toString(), "scenarios");
        changes = canonical(changes, item -> item.id().toString(), "changes");
        constraints = canonical(constraints, item -> item.id().toString(), "constraints");
        designDecisions = canonical(designDecisions, item -> item.id().toString(), "designDecisions");
        tasks = canonical(tasks, item -> item.id().toString(), "tasks");
        acceptanceCriteria = canonical(
                acceptanceCriteria, item -> item.id().toString(), "acceptanceCriteria");
        evidence = canonical(evidence, item -> item.id().toString(), "evidence");

        Set<EvidenceId> evidenceIds = new HashSet<>();
        evidence.forEach(item -> evidenceIds.add(item.id()));
        specifications.forEach(item -> requireEvidence(item.provenance().evidenceId(), evidenceIds));
        scenarios.forEach(item -> requireEvidence(item.provenance().evidenceId(), evidenceIds));
        changes.forEach(item -> requireEvidence(item.provenance().evidenceId(), evidenceIds));
        constraints.forEach(item -> requireEvidence(item.provenance().evidenceId(), evidenceIds));
        designDecisions.forEach(item -> requireEvidence(item.provenance().evidenceId(), evidenceIds));
        tasks.forEach(item -> requireEvidence(item.provenance().evidenceId(), evidenceIds));
        acceptanceCriteria.forEach(item -> {
            requireEvidence(item.provenance().evidenceId(), evidenceIds);
            item.verificationEvidenceIds().forEach(evidenceId -> requireEvidence(evidenceId, evidenceIds));
        });

        Set<ChangeId> changeIds = new HashSet<>();
        changes.forEach(item -> changeIds.add(item.id()));
        constraints.forEach(item -> requireKnownChange(item.changeId(), changeIds, "constraint", item.id()));
        designDecisions.forEach(item -> requireKnownChange(item.changeId(), changeIds, "design decision", item.id()));
        tasks.forEach(item -> requireKnownChange(item.changeId(), changeIds, "task", item.id()));
        acceptanceCriteria.forEach(item -> item.changeId().ifPresent(changeId ->
                requireKnownChange(changeId, changeIds, "acceptance criterion", item.id())));
    }

    private static <T> List<T> canonical(
            List<T> source,
            java.util.function.Function<T, String> identity,
            String name) {
        Objects.requireNonNull(source, name);
        List<T> copy = source.stream()
                .peek(item -> Objects.requireNonNull(item, name + " item"))
                .sorted(Comparator.comparing(identity))
                .toList();
        Set<String> seen = new HashSet<>();
        for (T item : copy) {
            String id = identity.apply(item);
            if (!seen.add(id)) {
                throw new IllegalArgumentException("duplicate " + name + " identity: " + id);
            }
        }
        return List.copyOf(copy);
    }

    private static void requireEvidence(EvidenceId evidenceId, Set<EvidenceId> knownEvidence) {
        if (!knownEvidence.contains(evidenceId)) {
            throw new IllegalArgumentException("provenance references unknown evidence: " + evidenceId);
        }
    }

    private static void requireKnownChange(ChangeId changeId, Set<ChangeId> knownChanges, String type, Object id) {
        if (!knownChanges.contains(changeId)) {
            throw new IllegalArgumentException(type + " references unknown change: " + id);
        }
    }
}
