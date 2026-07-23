package com.morpheus.application.delta;

import com.morpheus.application.store.RequirementVersionRecord;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.version.SpecificationVersion;

import java.util.List;
import java.util.Objects;

/** Immutable receipt describing the exact candidate projection produced by APPLY. */
public record RequirementDeltaApplicationResult(
        SpecificationVersion specificationVersion,
        KnowledgeSnapshotMetadata candidateSnapshot,
        List<RequirementVersionRecord> records,
        List<AppliedRequirementDelta> appliedDeltas,
        EvidenceId applicationEvidenceId) {

    public RequirementDeltaApplicationResult {
        Objects.requireNonNull(specificationVersion, "specificationVersion");
        Objects.requireNonNull(candidateSnapshot, "candidateSnapshot");
        records = List.copyOf(Objects.requireNonNull(records, "records"));
        appliedDeltas = List.copyOf(Objects.requireNonNull(appliedDeltas, "appliedDeltas"));
        Objects.requireNonNull(applicationEvidenceId, "applicationEvidenceId");
    }
}
