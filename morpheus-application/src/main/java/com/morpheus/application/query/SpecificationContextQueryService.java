package com.morpheus.application.query;

import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.RequirementPage;
import com.morpheus.application.store.RequirementPageCriteria;
import com.morpheus.application.store.RequirementPageOrder;
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
import com.morpheus.domain.traceability.TraceabilityEntityKind;
import com.morpheus.domain.traceability.TraceabilityEntityRef;
import com.morpheus.domain.traceability.TraceabilityRelationType;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Aggregates bounded specification context from one ACTIVE snapshot without inventing relationships.
 *
 * <p>The requirements are a page the store cuts. The changes are those that affect any CURRENT requirement of the
 * specification, on the page or not: they are found from the changes' side, by following each change's
 * {@code AFFECTS} links to the requirement they name, so the specification's whole requirement population is never
 * loaded to build one page.</p>
 */
public final class SpecificationContextQueryService {
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

        RequirementPage page = requirementStore.pageCurrentRequirementVersions(
                snapshot.id(),
                RequirementPageCriteria.ofSpecification(specificationId),
                RequirementPageOrder.REQUIREMENT_ID,
                pageRequest.offset(),
                pageRequest.limit());
        List<Requirement> selectedRequirements = page.items().stream()
                .map(record -> record.entityVersion().content())
                .toList();
        SnapshotPage<Requirement> requirementPage = new SnapshotPage<>(
                snapshot, selectedRequirements, pageRequest, page.totalMatches(),
                page.hasMoreAfter(pageRequest.offset()));

        Set<RequirementId> selectedIds = selectedRequirements.stream()
                .map(Requirement::id)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        List<Scenario> scenarios = content.scenarios().stream()
                .filter(scenario -> scenario.requirementId().filter(selectedIds::contains).isPresent())
                .sorted(SCENARIO_ORDER)
                .toList();

        List<ChangeProposal> changes = content.changes().stream()
                .filter(change -> affectsTheSpecification(snapshot, change, specificationId))
                .sorted(CHANGE_ORDER)
                .toList();

        return new SpecificationContextResult(snapshot, specification, requirementPage, scenarios, changes);
    }

    private boolean affectsTheSpecification(
            KnowledgeSnapshotMetadata snapshot,
            ChangeProposal change,
            SpecificationId specificationId) {
        TraceabilityEntityRef changeRef = new TraceabilityEntityRef(TraceabilityEntityKind.CHANGE, change.id().value());
        return traceabilityStore.outgoing(snapshot.id(), changeRef, Set.of(TraceabilityRelationType.AFFECTS)).stream()
                .map(link -> link.target())
                .filter(target -> target.kind() == TraceabilityEntityKind.REQUIREMENT)
                .map(target -> requirementStore.currentRequirement(snapshot.id(), target.identity()))
                .flatMap(Optional::stream)
                .map(RequirementVersionRecord::entityVersion)
                .anyMatch(version -> version.content().specificationId().equals(specificationId));
    }
}
