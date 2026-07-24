package com.morpheus.application.query;

import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.RequirementVersionRecord;
import com.morpheus.application.store.SnapshotBusinessContent;
import com.morpheus.application.store.SnapshotBusinessContentStore;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.application.store.TraceabilityStore;
import com.morpheus.application.store.VersionedRequirementStore;
import com.morpheus.domain.change.ChangeProposal;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.requirement.Requirement;
import com.morpheus.domain.requirement.RequirementId;
import com.morpheus.domain.scenario.Scenario;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.specification.Specification;
import com.morpheus.domain.specification.SpecificationId;
import com.morpheus.domain.temporal.TemporalState;
import com.morpheus.domain.traceability.TraceabilityEntityKind;
import com.morpheus.domain.traceability.TraceabilityEntityRef;
import com.morpheus.domain.traceability.TraceabilityRelationType;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Aggregates bounded specification context from one ACTIVE snapshot without inventing relationships. */
public final class SpecificationContextQueryService {
    private static final Comparator<Requirement> REQUIREMENT_ORDER = Comparator.comparing(Requirement::id);
    private static final Comparator<Scenario> SCENARIO_ORDER = Comparator.comparing(Scenario::id);
    private static final Comparator<ChangeProposal> CHANGE_ORDER = Comparator.comparing(ChangeProposal::id);

    private final SpecificationKnowledgeStore snapshotStore;
    private final SnapshotBusinessContentStore contentStore;
    private final VersionedRequirementStore requirementStore;
    private final TraceabilityStore traceabilityStore;

    public SpecificationContextQueryService(
            SpecificationKnowledgeStore snapshotStore,
            SnapshotBusinessContentStore contentStore,
            VersionedRequirementStore requirementStore,
            TraceabilityStore traceabilityStore) {
        this.snapshotStore = Objects.requireNonNull(snapshotStore, "snapshotStore");
        this.contentStore = Objects.requireNonNull(contentStore, "contentStore");
        this.requirementStore = Objects.requireNonNull(requirementStore, "requirementStore");
        this.traceabilityStore = Objects.requireNonNull(traceabilityStore, "traceabilityStore");
    }

    public Optional<SpecificationContextResult> active(
            ProjectSpecificationId projectId,
            SpecificationId specificationId,
            PageRequest pageRequest) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(specificationId, "specificationId");
        Objects.requireNonNull(pageRequest, "pageRequest");
        return snapshotStore.activeSnapshot(projectId)
                .map(snapshot -> context(snapshot, specificationId, pageRequest));
    }

    private SpecificationContextResult context(
            KnowledgeSnapshotMetadata snapshot,
            SpecificationId specificationId,
            PageRequest pageRequest) {
        SnapshotBusinessContent content = contentStore.findSnapshotContent(snapshot.id())
                .orElseThrow(() -> new KnowledgeStoreException(
                        "published snapshot has no business-content projection: " + snapshot.id()));
        Specification specification = content.specifications().stream()
                .filter(candidate -> candidate.id().equals(specificationId))
                .findFirst()
                .orElseThrow(() -> new KnowledgeStoreException("specification not found: " + specificationId));

        List<Requirement> allRequirements = requirementStore.listRequirementVersions(snapshot.id()).stream()
                .map(RequirementVersionRecord::entityVersion)
                .filter(version -> version.temporalState() == TemporalState.CURRENT)
                .map(version -> version.content())
                .filter(requirement -> requirement.specificationId().equals(specificationId))
                .sorted(REQUIREMENT_ORDER)
                .toList();

        int totalMatches = allRequirements.size();
        int from = Math.min(pageRequest.offset(), totalMatches);
        int to = (int) Math.min((long) from + pageRequest.limit(), totalMatches);
        List<Requirement> selectedRequirements = allRequirements.subList(from, to);
        SnapshotPage<Requirement> requirementPage = new SnapshotPage<>(
                snapshot, selectedRequirements, pageRequest, totalMatches, to < totalMatches);

        Set<RequirementId> selectedIds = selectedRequirements.stream()
                .map(Requirement::id)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        List<Scenario> scenarios = content.scenarios().stream()
                .filter(scenario -> scenario.requirementId().filter(selectedIds::contains).isPresent())
                .sorted(SCENARIO_ORDER)
                .toList();

        Set<com.morpheus.domain.identity.DomainIdentity> allRequirementIdentities = allRequirements.stream()
                .map(requirement -> requirement.id().value())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        Set<com.morpheus.domain.identity.DomainIdentity> affectedChangeIdentities = new HashSet<>();
        for (Requirement requirement : allRequirements) {
            TraceabilityEntityRef requirementRef = new TraceabilityEntityRef(
                    TraceabilityEntityKind.REQUIREMENT, requirement.id().value());
            traceabilityStore.incoming(snapshot.id(), requirementRef, Set.of(TraceabilityRelationType.AFFECTS)).stream()
                    .map(link -> link.source())
                    .filter(source -> source.kind() == TraceabilityEntityKind.CHANGE)
                    .map(TraceabilityEntityRef::identity)
                    .forEach(affectedChangeIdentities::add);
        }
        List<ChangeProposal> changes = content.changes().stream()
                .filter(change -> affectedChangeIdentities.contains(change.id().value()))
                .sorted(CHANGE_ORDER)
                .toList();

        // Keep the complete specification-level relationship set while bounding requirement/scenario payloads.
        if (allRequirementIdentities.isEmpty() && !changes.isEmpty()) {
            throw new KnowledgeStoreException("AFFECTS relationship resolved without a CURRENT specification requirement");
        }
        return new SpecificationContextResult(snapshot, specification, requirementPage, scenarios, changes);
    }
}
