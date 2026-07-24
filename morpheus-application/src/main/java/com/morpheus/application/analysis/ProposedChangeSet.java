package com.morpheus.application.analysis;

import com.morpheus.application.ingestion.NormalizedProjectContent;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.ChangeProposal;
import com.morpheus.domain.constraint.Constraint;
import com.morpheus.domain.decision.DesignDecision;
import com.morpheus.domain.requirement.RequirementDelta;
import com.morpheus.domain.requirement.RequirementId;
import com.morpheus.domain.task.ImplementationTask;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Immutable proposed-change input extracted from one normalized provider read without mutating CURRENT state. */
public record ProposedChangeSet(
        ChangeProposal change,
        List<RequirementDelta> requirementDeltas,
        List<Constraint> constraints,
        List<DesignDecision> designDecisions,
        List<ImplementationTask> implementationTasks) {

    public ProposedChangeSet {
        Objects.requireNonNull(change, "change");
        requirementDeltas = canonical(requirementDeltas, delta -> delta.id().toString(), "requirementDeltas");
        constraints = canonical(constraints, constraint -> constraint.id().toString(), "constraints");
        designDecisions = canonical(designDecisions, decision -> decision.id().toString(), "designDecisions");
        implementationTasks = canonical(implementationTasks, task -> task.id().toString(), "implementationTasks");

        Set<RequirementId> affectedRequirements = new HashSet<>();
        for (RequirementDelta delta : requirementDeltas) {
            requireOwnedByChange(delta.changeId(), change.id(), "requirement delta", delta.id());
            if (!affectedRequirements.add(delta.requirementId())) {
                throw new IllegalArgumentException(
                        "multiple deltas for one logical requirement are ambiguous: " + delta.requirementId());
            }
        }
        constraints.forEach(item -> requireOwnedByChange(item.changeId(), change.id(), "constraint", item.id()));
        designDecisions.forEach(item -> requireOwnedByChange(item.changeId(), change.id(), "design decision", item.id()));
        implementationTasks.forEach(item -> requireOwnedByChange(item.changeId(), change.id(), "implementation task", item.id()));
    }

    public static ProposedChangeSet from(NormalizedProjectContent content, ChangeId changeId) {
        Objects.requireNonNull(content, "content");
        Objects.requireNonNull(changeId, "changeId");
        ChangeProposal change = content.changes().stream()
                .filter(candidate -> candidate.id().equals(changeId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("normalized content does not contain change: " + changeId));
        return new ProposedChangeSet(
                change,
                content.requirementDeltas().stream().filter(item -> item.changeId().equals(changeId)).toList(),
                content.constraints().stream().filter(item -> item.changeId().equals(changeId)).toList(),
                content.designDecisions().stream().filter(item -> item.changeId().equals(changeId)).toList(),
                content.tasks().stream().filter(item -> item.changeId().equals(changeId)).toList());
    }

    private static <T> List<T> canonical(
            List<T> source,
            java.util.function.Function<T, String> identity,
            String name) {
        Objects.requireNonNull(source, name);
        return source.stream()
                .peek(item -> Objects.requireNonNull(item, name + " item"))
                .sorted(Comparator.comparing(identity))
                .toList();
    }

    private static void requireOwnedByChange(ChangeId actual, ChangeId expected, String kind, Object id) {
        if (!actual.equals(expected)) {
            throw new IllegalArgumentException(kind + " belongs to another change: " + id);
        }
    }
}
