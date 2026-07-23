package com.morpheus.application.query;

import com.morpheus.application.store.RequirementVersionRecord;
import com.morpheus.application.traceability.ExternalTraceabilityView;
import com.morpheus.application.traceability.TraceabilitySubgraph;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.ChangeProposal;
import com.morpheus.domain.constraint.Constraint;
import com.morpheus.domain.decision.DesignDecision;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.task.ImplementationTask;
import com.morpheus.domain.temporal.TemporalState;
import com.morpheus.domain.traceability.TraceabilityEntityKind;
import com.morpheus.domain.traceability.TraceabilityEntityRef;
import com.morpheus.domain.traceability.TraceabilityLink;
import com.morpheus.domain.traceability.TraceabilityRelationType;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Immutable snapshot-coherent aggregate returned by get_change_context. */
public record ChangeContextResult(
        KnowledgeSnapshotMetadata snapshot,
        ChangeId changeId,
        Optional<ChangeProposal> change,
        List<TraceabilityLink> affectedRequirementLinks,
        List<RequirementVersionRecord> affectedRequirements,
        List<Constraint> constraints,
        List<DesignDecision> designDecisions,
        List<ImplementationTask> implementationTasks,
        TraceabilitySubgraph subgraph,
        List<ExternalTraceabilityView> externalLinks) {

    public ChangeContextResult {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(changeId, "changeId");
        change = Objects.requireNonNull(change, "change");
        affectedRequirementLinks = Objects.requireNonNull(affectedRequirementLinks, "affectedRequirementLinks").stream()
                .sorted(Comparator.comparing(TraceabilityLink::id))
                .toList();
        affectedRequirements = Objects.requireNonNull(affectedRequirements, "affectedRequirements").stream()
                .sorted(Comparator.comparing(record -> record.entityVersion().content().id()))
                .toList();
        constraints = Objects.requireNonNull(constraints, "constraints").stream()
                .sorted(Comparator.comparing(Constraint::id))
                .toList();
        designDecisions = Objects.requireNonNull(designDecisions, "designDecisions").stream()
                .sorted(Comparator.comparing(DesignDecision::id))
                .toList();
        implementationTasks = Objects.requireNonNull(implementationTasks, "implementationTasks").stream()
                .sorted(Comparator.comparing(ImplementationTask::id))
                .toList();
        Objects.requireNonNull(subgraph, "subgraph");
        externalLinks = Objects.requireNonNull(externalLinks, "externalLinks").stream()
                .sorted(Comparator.comparing(view -> view.link().id()))
                .toList();

        TraceabilityEntityRef expectedRoot = new TraceabilityEntityRef(
                TraceabilityEntityKind.CHANGE,
                changeId.value());
        if (!expectedRoot.equals(subgraph.start())) {
            throw new IllegalArgumentException("change context subgraph must be rooted at the requested change identity");
        }

        change.ifPresent(value -> {
            if (!value.id().equals(changeId)) {
                throw new IllegalArgumentException("change context item must match requested change identity");
            }
        });

        for (TraceabilityLink link : affectedRequirementLinks) {
            if (!link.source().equals(expectedRoot)
                    || link.relationType() != TraceabilityRelationType.AFFECTS
                    || link.target().kind() != TraceabilityEntityKind.REQUIREMENT) {
                throw new IllegalArgumentException("affected requirement links must be direct CHANGE --AFFECTS--> REQUIREMENT links");
            }
        }

        for (RequirementVersionRecord requirement : affectedRequirements) {
            if (!requirement.snapshotId().equals(snapshot.id())) {
                throw new IllegalArgumentException("affected requirement must belong to the result snapshot");
            }
            if (requirement.entityVersion().temporalState() != TemporalState.CURRENT) {
                throw new IllegalArgumentException("affected requirement must be CURRENT");
            }
            boolean backedByLink = affectedRequirementLinks.stream().anyMatch(link ->
                    link.target().identity().equals(requirement.entityVersion().content().id().value()));
            if (!backedByLink) {
                throw new IllegalArgumentException("affected requirement must be backed by an AFFECTS link");
            }
        }

        if (constraints.stream().anyMatch(value -> !value.changeId().equals(changeId))) {
            throw new IllegalArgumentException("constraint belongs to another change");
        }
        if (designDecisions.stream().anyMatch(value -> !value.changeId().equals(changeId))) {
            throw new IllegalArgumentException("design decision belongs to another change");
        }
        if (implementationTasks.stream().anyMatch(value -> !value.changeId().equals(changeId))) {
            throw new IllegalArgumentException("implementation task belongs to another change");
        }
        if (externalLinks.stream().anyMatch(view -> !subgraph.links().contains(view.link()))) {
            throw new IllegalArgumentException("external traceability views must belong to the returned subgraph");
        }
    }
}
