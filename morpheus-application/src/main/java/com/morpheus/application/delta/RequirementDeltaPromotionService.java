package com.morpheus.application.delta;

import com.morpheus.application.snapshot.SnapshotLifecycleService;
import com.morpheus.application.snapshot.SnapshotValidationResult;
import com.morpheus.application.store.RequirementVersionRecord;
import com.morpheus.application.store.SnapshotSpecificationVersionBinding;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.application.store.VersionedRequirementStore;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.temporal.TemporalState;
import com.morpheus.domain.version.EntityVersionId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Explicitly promotes one applied candidate from BUILDING to READY eligibility without activating it. */
public final class RequirementDeltaPromotionService {
    private final SpecificationKnowledgeStore snapshotStore;
    private final VersionedRequirementStore requirementStore;
    private final SnapshotLifecycleService snapshotLifecycle;

    public RequirementDeltaPromotionService(
            SpecificationKnowledgeStore snapshotStore,
            VersionedRequirementStore requirementStore) {
        this.snapshotStore = Objects.requireNonNull(snapshotStore, "snapshotStore");
        this.requirementStore = Objects.requireNonNull(requirementStore, "requirementStore");
        this.snapshotLifecycle = new SnapshotLifecycleService(snapshotStore);
    }

    public RequirementDeltaPromotionResult promote(
            RequirementDeltaApplicationResult application,
            RequirementPromotionEvidence promotionEvidence) {
        Objects.requireNonNull(application, "application");
        Objects.requireNonNull(promotionEvidence, "promotionEvidence");

        KnowledgeSnapshotMetadata persisted = snapshotStore.findSnapshot(application.candidateSnapshot().id())
                .orElseThrow(() -> new RequirementDeltaApplicationException(
                        "candidate snapshot not found: " + application.candidateSnapshot().id()));
        if (!persisted.sameDefinitionAs(application.candidateSnapshot())) {
            throw new RequirementDeltaApplicationException("candidate snapshot definition differs from APPLY receipt");
        }
        if (persisted.state() != KnowledgeSnapshotState.BUILDING) {
            throw new RequirementDeltaApplicationException(
                    "only a BUILDING candidate can be promoted explicitly; actual=" + persisted.state());
        }

        SnapshotSpecificationVersionBinding binding = requirementStore.findSnapshotVersion(persisted.id())
                .orElseThrow(() -> new RequirementDeltaApplicationException(
                        "candidate snapshot has no specification version binding: " + persisted.id()));
        if (!binding.specificationVersionId().equals(application.specificationVersion().id())) {
            throw new RequirementDeltaApplicationException("candidate snapshot binding differs from APPLY receipt");
        }
        if (!requirementStore.findSpecificationVersion(application.specificationVersion().id())
                .filter(application.specificationVersion()::equals)
                .isPresent()) {
            throw new RequirementDeltaApplicationException("candidate specification version differs from APPLY receipt");
        }

        KnowledgeSnapshotMetadata promoted = snapshotLifecycle.validate(
                persisted.id(),
                ignored -> validatePersistedProjection(application));
        if (promoted.state() != KnowledgeSnapshotState.READY) {
            throw new RequirementDeltaApplicationException(
                    "candidate projection failed promotion validation and is now " + promoted.state());
        }

        return new RequirementDeltaPromotionResult(
                promoted,
                application.specificationVersion().id(),
                application.applicationEvidenceId(),
                promotionEvidence);
    }

    private SnapshotValidationResult validatePersistedProjection(RequirementDeltaApplicationResult application) {
        List<String> errors = new ArrayList<>();
        Map<EntityVersionId, RequirementVersionRecord> expected = byEntityVersionId(application.records(), errors, "APPLY receipt");
        List<RequirementVersionRecord> persisted = requirementStore.listRequirementVersions(application.candidateSnapshot().id());
        Map<EntityVersionId, RequirementVersionRecord> actual = byEntityVersionId(persisted, errors, "persisted candidate");

        if (!actual.equals(expected)) {
            errors.add("persisted candidate requirement projection differs from APPLY receipt");
        }
        for (RequirementVersionRecord record : persisted) {
            if (record.entityVersion().temporalState() != TemporalState.CURRENT) {
                errors.add("candidate contains non-CURRENT requirement occurrence: " + record.entityVersion().id());
            }
            if (!record.entityVersion().specificationVersionId().equals(application.specificationVersion().id())) {
                errors.add("candidate requirement belongs to another specification version: " + record.entityVersion().id());
            }
        }

        return errors.isEmpty() ? SnapshotValidationResult.valid() : SnapshotValidationResult.invalid(errors);
    }

    private Map<EntityVersionId, RequirementVersionRecord> byEntityVersionId(
            List<RequirementVersionRecord> records,
            List<String> errors,
            String source) {
        Map<EntityVersionId, RequirementVersionRecord> indexed = new HashMap<>();
        for (RequirementVersionRecord record : records) {
            RequirementVersionRecord previous = indexed.put(record.entityVersion().id(), record);
            if (previous != null) {
                errors.add(source + " contains duplicate EntityVersionId: " + record.entityVersion().id());
            }
        }
        return indexed;
    }
}
