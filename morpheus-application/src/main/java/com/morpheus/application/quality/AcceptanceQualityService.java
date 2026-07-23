package com.morpheus.application.quality;

import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.SnapshotBusinessContent;
import com.morpheus.application.store.SnapshotBusinessContentStore;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.domain.diagnostic.DiagnosticSeverity;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.specification.Specification;
import com.morpheus.domain.traceability.TraceabilityEntityKind;
import com.morpheus.domain.traceability.TraceabilityEntityRef;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Explicitly reports that acceptance coverage is unavailable until AcceptanceCriterion is normalized. */
public final class AcceptanceQualityService {
    private static final Comparator<Specification> SPECIFICATION_ORDER = Comparator.comparing(Specification::id);

    private final SpecificationKnowledgeStore snapshotStore;
    private final SnapshotBusinessContentStore contentStore;

    public AcceptanceQualityService(
            SpecificationKnowledgeStore snapshotStore,
            SnapshotBusinessContentStore contentStore) {
        this.snapshotStore = Objects.requireNonNull(snapshotStore, "snapshotStore");
        this.contentStore = Objects.requireNonNull(contentStore, "contentStore");
    }

    public Optional<AcceptanceCoverageAssessment> assessActive(ProjectSpecificationId projectId) {
        Objects.requireNonNull(projectId, "projectId");
        return snapshotStore.activeSnapshot(projectId).map(this::assessPublished);
    }

    public AcceptanceCoverageAssessment assessSnapshot(KnowledgeSnapshotId snapshotId) {
        Objects.requireNonNull(snapshotId, "snapshotId");
        KnowledgeSnapshotMetadata snapshot = snapshotStore.findSnapshot(snapshotId)
                .orElseThrow(() -> new KnowledgeStoreException("unknown knowledge snapshot: " + snapshotId));
        requirePublished(snapshot);
        return assessPublished(snapshot);
    }

    private AcceptanceCoverageAssessment assessPublished(KnowledgeSnapshotMetadata snapshot) {
        SnapshotBusinessContent content = contentStore.findSnapshotContent(snapshot.id())
                .orElseThrow(() -> new KnowledgeStoreException(
                        "published snapshot has no business-content projection: " + snapshot.id()));

        List<QualityFinding> findings = content.specifications().stream()
                .sorted(SPECIFICATION_ORDER)
                .map(specification -> new QualityFinding(
                        QualityFindingCode.ACCEPTANCE_COVERAGE_UNAVAILABLE,
                        DiagnosticSeverity.WARNING,
                        QualityEvidenceKind.DETERMINISTIC,
                        new TraceabilityEntityRef(
                                TraceabilityEntityKind.SPECIFICATION,
                                specification.id().value()),
                        "Acceptance-criterion coverage cannot be evaluated because AcceptanceCriterion is not normalized",
                        Map.of(
                                "specificationId", specification.id().toString(),
                                "snapshotId", snapshot.id().toString(),
                                "status", AcceptanceCoverageStatus.UNAVAILABLE_IN_NORMALIZED_MODEL.name()),
                        Optional.empty(),
                        List.of(specification.provenance().evidenceId())))
                .sorted()
                .toList();

        return new AcceptanceCoverageAssessment(
                snapshot,
                AcceptanceCoverageStatus.UNAVAILABLE_IN_NORMALIZED_MODEL,
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
