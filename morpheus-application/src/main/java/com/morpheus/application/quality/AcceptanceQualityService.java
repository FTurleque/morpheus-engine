package com.morpheus.application.quality;

import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.SnapshotBusinessContent;
import com.morpheus.application.store.SnapshotBusinessContentStore;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.domain.acceptance.AcceptanceCriterion;
import com.morpheus.domain.acceptance.VerificationStatus;
import com.morpheus.domain.diagnostic.DiagnosticSeverity;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.traceability.TraceabilityEntityKind;
import com.morpheus.domain.traceability.TraceabilityEntityRef;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Deterministic snapshot-scoped acceptance-criterion verification coverage. */
public final class AcceptanceQualityService {
    private static final Comparator<AcceptanceCriterion> CRITERION_ORDER =
            Comparator.comparing(AcceptanceCriterion::id);

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

        List<AcceptanceCriterion> criteria = content.acceptanceCriteria().stream()
                .sorted(CRITERION_ORDER)
                .toList();
        if (criteria.isEmpty()) {
            return new AcceptanceCoverageAssessment(
                    snapshot,
                    AcceptanceCoverageStatus.NO_CRITERIA,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    1.0,
                    List.of());
        }

        int verified = 0;
        int partiallyVerified = 0;
        int failed = 0;
        int notVerified = 0;
        int unknown = 0;
        List<QualityFinding> findings = new ArrayList<>();

        for (AcceptanceCriterion criterion : criteria) {
            switch (criterion.verificationStatus()) {
                case VERIFIED -> verified++;
                case PARTIALLY_VERIFIED -> {
                    partiallyVerified++;
                    findings.add(finding(
                            snapshot,
                            criterion,
                            QualityFindingCode.ACCEPTANCE_CRITERION_PARTIALLY_VERIFIED,
                            DiagnosticSeverity.WARNING,
                            "Acceptance criterion is only partially verified"));
                }
                case FAILED -> {
                    failed++;
                    findings.add(finding(
                            snapshot,
                            criterion,
                            QualityFindingCode.ACCEPTANCE_CRITERION_FAILED,
                            DiagnosticSeverity.ERROR,
                            "Acceptance criterion verification failed"));
                }
                case NOT_VERIFIED -> {
                    notVerified++;
                    findings.add(finding(
                            snapshot,
                            criterion,
                            QualityFindingCode.ACCEPTANCE_CRITERION_NOT_VERIFIED,
                            DiagnosticSeverity.WARNING,
                            "Acceptance criterion has not been verified"));
                }
                case UNKNOWN -> {
                    unknown++;
                    findings.add(finding(
                            snapshot,
                            criterion,
                            QualityFindingCode.ACCEPTANCE_CRITERION_UNKNOWN,
                            DiagnosticSeverity.INFO,
                            "Acceptance criterion verification state is unknown"));
                }
            }
        }

        return new AcceptanceCoverageAssessment(
                snapshot,
                AcceptanceCoverageStatus.EVALUATED,
                criteria.size(),
                verified,
                partiallyVerified,
                failed,
                notVerified,
                unknown,
                (double) verified / criteria.size(),
                findings);
    }

    private QualityFinding finding(
            KnowledgeSnapshotMetadata snapshot,
            AcceptanceCriterion criterion,
            QualityFindingCode code,
            DiagnosticSeverity severity,
            String message) {
        return new QualityFinding(
                code,
                severity,
                QualityEvidenceKind.DETERMINISTIC,
                new TraceabilityEntityRef(
                        TraceabilityEntityKind.ACCEPTANCE_CRITERION,
                        criterion.id().value()),
                message,
                Map.of(
                        "acceptanceCriterionId", criterion.id().toString(),
                        "verificationStatus", criterion.verificationStatus().name(),
                        "snapshotId", snapshot.id().toString()),
                Optional.empty(),
                findingEvidence(criterion));
    }

    private List<EvidenceId> findingEvidence(AcceptanceCriterion criterion) {
        if (!criterion.verificationEvidenceIds().isEmpty()) {
            return criterion.verificationEvidenceIds();
        }
        return List.of(criterion.provenance().evidenceId());
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
