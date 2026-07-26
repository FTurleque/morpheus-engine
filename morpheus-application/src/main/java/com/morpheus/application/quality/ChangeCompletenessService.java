package com.morpheus.application.quality;

import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.RequirementVersionRecord;
import com.morpheus.application.store.SnapshotBusinessContent;
import com.morpheus.application.store.SnapshotBusinessContentStore;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.application.store.TraceabilityStore;
import com.morpheus.application.store.VersionedRequirementStore;
import com.morpheus.domain.change.ChangeProposal;
import com.morpheus.domain.diagnostic.DiagnosticSeverity;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.temporal.TemporalState;
import com.morpheus.domain.traceability.TraceabilityEntityKind;
import com.morpheus.domain.traceability.TraceabilityEntityRef;
import com.morpheus.domain.traceability.TraceabilityRelationType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Deterministic snapshot-derived completeness analysis for normalized change proposals. */
public final class ChangeCompletenessService {
    private static final Comparator<ChangeProposal> CHANGE_ORDER =
            Comparator.comparing(change -> change.id().toString());

    private final SpecificationKnowledgeStore snapshotStore;
    private final SnapshotBusinessContentStore contentStore;
    private final VersionedRequirementStore requirementStore;
    private final TraceabilityStore traceabilityStore;

    public ChangeCompletenessService(
            SpecificationKnowledgeStore snapshotStore,
            SnapshotBusinessContentStore contentStore,
            VersionedRequirementStore requirementStore,
            TraceabilityStore traceabilityStore) {
        this.snapshotStore = Objects.requireNonNull(snapshotStore, "snapshotStore");
        this.contentStore = Objects.requireNonNull(contentStore, "contentStore");
        this.requirementStore = Objects.requireNonNull(requirementStore, "requirementStore");
        this.traceabilityStore = Objects.requireNonNull(traceabilityStore, "traceabilityStore");
    }

    public Optional<ChangeCompletenessReport> assessActive(ProjectSpecificationId projectId) {
        Objects.requireNonNull(projectId, "projectId");
        return snapshotStore.activeSnapshot(projectId).map(this::assessPublished);
    }

    public ChangeCompletenessReport assessSnapshot(KnowledgeSnapshotId snapshotId) {
        Objects.requireNonNull(snapshotId, "snapshotId");
        KnowledgeSnapshotMetadata snapshot = snapshotStore.findSnapshot(snapshotId)
                .orElseThrow(() -> new KnowledgeStoreException("unknown knowledge snapshot: " + snapshotId));
        requirePublished(snapshot);
        return assessPublished(snapshot);
    }

    private ChangeCompletenessReport assessPublished(KnowledgeSnapshotMetadata snapshot) {
        SnapshotBusinessContent content = contentStore.findSnapshotContent(snapshot.id())
                .orElseThrow(() -> new KnowledgeStoreException(
                        "published snapshot has no business-content projection: " + snapshot.id()));

        Set<DomainIdentity> currentRequirementIdentities = requirementStore.listRequirementVersions(snapshot.id()).stream()
                .filter(record -> record.entityVersion().temporalState() == TemporalState.CURRENT)
                .map(RequirementVersionRecord::entityVersion)
                .map(version -> version.content().id().value())
                .collect(Collectors.toUnmodifiableSet());

        List<ChangeCompletenessAssessment> assessments = content.changes().stream()
                .sorted(CHANGE_ORDER)
                .map(change -> assessChange(snapshot, content, change, currentRequirementIdentities))
                .toList();
        return new ChangeCompletenessReport(snapshot, assessments);
    }

    private ChangeCompletenessAssessment assessChange(
            KnowledgeSnapshotMetadata snapshot,
            SnapshotBusinessContent content,
            ChangeProposal change,
            Set<DomainIdentity> currentRequirementIdentities) {
        TraceabilityEntityRef changeRef = new TraceabilityEntityRef(
                TraceabilityEntityKind.CHANGE,
                change.id().value());

        int currentRequirementCount = traceabilityStore
                .outgoing(snapshot.id(), changeRef, Set.of(TraceabilityRelationType.AFFECTS))
                .stream()
                .map(link -> link.target())
                .filter(target -> target.kind() == TraceabilityEntityKind.REQUIREMENT)
                .map(TraceabilityEntityRef::identity)
                .filter(currentRequirementIdentities::contains)
                .distinct()
                .toList()
                .size();

        int constraintCount = (int) content.constraints().stream()
                .filter(item -> item.changeId().equals(change.id()))
                .count();
        int designDecisionCount = (int) content.designDecisions().stream()
                .filter(item -> item.changeId().equals(change.id()))
                .count();
        int implementationTaskCount = (int) content.tasks().stream()
                .filter(item -> item.changeId().equals(change.id()))
                .count();
        int acceptanceCriterionCount = (int) content.acceptanceCriteria().stream()
                .filter(item -> item.changeId().filter(change.id()::equals).isPresent())
                .count();

        ChangeLifecycleFactAssessment facts = new ChangeLifecycleFactAssessment(
                QualityFactValue.of(currentRequirementCount > 0),
                QualityFactValue.UNAVAILABLE,
                QualityFactValue.of(acceptanceCriterionCount > 0),
                QualityFactValue.UNAVAILABLE,
                QualityFactValue.of(designDecisionCount > 0),
                implementationTaskCount > 0 ? QualityFactValue.TRUE : QualityFactValue.UNAVAILABLE,
                QualityFactValue.UNAVAILABLE,
                QualityFactValue.UNAVAILABLE,
                QualityFactValue.UNAVAILABLE);

        List<QualityFinding> findings = new ArrayList<>();
        if (currentRequirementCount == 0) {
            findings.add(new QualityFinding(
                    QualityFindingCode.CHANGE_WITHOUT_CURRENT_REQUIREMENT,
                    DiagnosticSeverity.WARNING,
                    QualityEvidenceKind.DETERMINISTIC,
                    changeRef,
                    "Change has no AFFECTS link resolving to a CURRENT requirement in this published snapshot",
                    Map.of(
                            "changeId", change.id().toString(),
                            "snapshotId", snapshot.id().toString()),
                    Optional.empty(),
                    List.of(change.provenance().evidenceId())));
        }

        List<String> unavailableFacts = facts.unavailableFacts();
        if (!unavailableFacts.isEmpty()) {
            findings.add(new QualityFinding(
                    QualityFindingCode.CHANGE_COMPLETENESS_PARTIALLY_OBSERVABLE,
                    DiagnosticSeverity.INFO,
                    QualityEvidenceKind.DETERMINISTIC,
                    changeRef,
                    "Change lifecycle completeness is only partially observable from normalized snapshot data",
                    Map.of(
                            "changeId", change.id().toString(),
                            "snapshotId", snapshot.id().toString(),
                            "unavailableFacts", String.join(",", unavailableFacts)),
                    Optional.empty(),
                    List.of(change.provenance().evidenceId())));
        }

        return new ChangeCompletenessAssessment(
                change,
                facts,
                currentRequirementCount,
                constraintCount,
                designDecisionCount,
                implementationTaskCount,
                findings);
    }

    private void requirePublished(KnowledgeSnapshotMetadata snapshot) {
        if (snapshot.state() != KnowledgeSnapshotState.ACTIVE
                && snapshot.state() != KnowledgeSnapshotState.RETIRED) {
            throw new KnowledgeStoreException(
                    "quality analysis requires an ACTIVE or RETIRED snapshot: "
                            + snapshot.id() + " is " + snapshot.state());
        }
    }
}
