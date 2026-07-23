package com.morpheus.application.quality;

import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.RequirementVersionRecord;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.application.store.TraceabilityStore;
import com.morpheus.application.store.VersionedRequirementStore;
import com.morpheus.domain.diagnostic.DiagnosticSeverity;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.requirement.Requirement;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.temporal.TemporalState;
import com.morpheus.domain.traceability.TraceabilityEntityKind;
import com.morpheus.domain.traceability.TraceabilityEntityRef;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Deterministic quality analysis of traceability coverage over published CURRENT requirements. */
public final class RequirementQualityService {
    private static final Comparator<RequirementVersionRecord> REQUIREMENT_ORDER = Comparator
            .comparing(record -> record.entityVersion().content().id());

    private final SpecificationKnowledgeStore snapshotStore;
    private final VersionedRequirementStore requirementStore;
    private final TraceabilityStore traceabilityStore;

    public RequirementQualityService(
            SpecificationKnowledgeStore snapshotStore,
            VersionedRequirementStore requirementStore,
            TraceabilityStore traceabilityStore) {
        this.snapshotStore = Objects.requireNonNull(snapshotStore, "snapshotStore");
        this.requirementStore = Objects.requireNonNull(requirementStore, "requirementStore");
        this.traceabilityStore = Objects.requireNonNull(traceabilityStore, "traceabilityStore");
    }

    public Optional<RequirementTraceabilityCoverage> assessActive(ProjectSpecificationId projectId) {
        Objects.requireNonNull(projectId, "projectId");
        return snapshotStore.activeSnapshot(projectId).map(this::assessPublished);
    }

    public RequirementTraceabilityCoverage assessSnapshot(KnowledgeSnapshotId snapshotId) {
        Objects.requireNonNull(snapshotId, "snapshotId");
        KnowledgeSnapshotMetadata snapshot = snapshotStore.findSnapshot(snapshotId)
                .orElseThrow(() -> new KnowledgeStoreException("unknown knowledge snapshot: " + snapshotId));
        requirePublished(snapshot);
        return assessPublished(snapshot);
    }

    private RequirementTraceabilityCoverage assessPublished(KnowledgeSnapshotMetadata snapshot) {
        List<RequirementVersionRecord> currentRequirements = requirementStore.listRequirementVersions(snapshot.id()).stream()
                .filter(record -> record.entityVersion().temporalState() == TemporalState.CURRENT)
                .sorted(REQUIREMENT_ORDER)
                .toList();

        int linked = 0;
        List<QualityFinding> findings = new ArrayList<>();
        for (RequirementVersionRecord record : currentRequirements) {
            Requirement requirement = record.entityVersion().content();
            TraceabilityEntityRef subject = new TraceabilityEntityRef(
                    TraceabilityEntityKind.REQUIREMENT,
                    requirement.id().value());

            boolean hasOutgoing = !traceabilityStore.outgoing(snapshot.id(), subject, Set.of()).isEmpty();
            boolean hasIncoming = !traceabilityStore.incoming(snapshot.id(), subject, Set.of()).isEmpty();
            if (hasOutgoing || hasIncoming) {
                linked++;
                continue;
            }

            findings.add(new QualityFinding(
                    QualityFindingCode.ORPHAN_REQUIREMENT,
                    DiagnosticSeverity.WARNING,
                    QualityEvidenceKind.DETERMINISTIC,
                    subject,
                    "Requirement has no direct traceability links in this published snapshot",
                    Map.of(
                            "requirementId", requirement.id().toString(),
                            "specificationId", requirement.specificationId().toString(),
                            "snapshotId", snapshot.id().toString()),
                    Optional.empty(),
                    List.of(requirement.provenance().evidenceId())));
        }

        int total = currentRequirements.size();
        int orphan = total - linked;
        double ratio = total == 0 ? 1.0 : (double) linked / total;
        return new RequirementTraceabilityCoverage(snapshot, total, linked, orphan, ratio, findings);
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
