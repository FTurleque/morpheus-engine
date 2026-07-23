package com.morpheus.application.delta;

import com.morpheus.application.store.RequirementVersionRecord;
import com.morpheus.application.store.SnapshotSpecificationVersionBinding;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.application.store.VersionedRequirementStore;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.requirement.Requirement;
import com.morpheus.domain.requirement.RequirementDelta;
import com.morpheus.domain.requirement.RequirementDeltaId;
import com.morpheus.domain.requirement.RequirementDeltaKind;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.specification.SpecificationId;
import com.morpheus.domain.temporal.TemporalState;
import com.morpheus.domain.version.EntityVersion;
import com.morpheus.domain.version.EntityVersionId;
import com.morpheus.domain.version.SpecificationVersion;
import com.morpheus.domain.version.SpecificationVersionId;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/** Builds and persists one non-observable candidate requirement baseline from normalized deltas. */
public final class RequirementDeltaApplicationService {
    private final SpecificationKnowledgeStore snapshotStore;
    private final VersionedRequirementStore requirementStore;

    public RequirementDeltaApplicationService(
            SpecificationKnowledgeStore snapshotStore,
            VersionedRequirementStore requirementStore) {
        this.snapshotStore = Objects.requireNonNull(snapshotStore, "snapshotStore");
        this.requirementStore = Objects.requireNonNull(requirementStore, "requirementStore");
    }

    public RequirementDeltaApplicationResult apply(RequirementDeltaApplicationPlan plan) {
        Objects.requireNonNull(plan, "plan");
        if (plan.deltas().isEmpty()) {
            throw new RequirementDeltaApplicationException("explicit delta application requires at least one delta");
        }

        KnowledgeSnapshotMetadata activeSnapshot = snapshotStore.activeSnapshot(plan.projectId())
                .orElseThrow(() -> new RequirementDeltaApplicationException(
                        "project has no ACTIVE snapshot: " + plan.projectId()));
        SnapshotSpecificationVersionBinding activeBinding = requirementStore.findSnapshotVersion(activeSnapshot.id())
                .orElseThrow(() -> new RequirementDeltaApplicationException(
                        "ACTIVE snapshot has no specification version binding: " + activeSnapshot.id()));
        SpecificationVersion activeVersion = requirementStore.findSpecificationVersion(activeBinding.specificationVersionId())
                .orElseThrow(() -> new RequirementDeltaApplicationException(
                        "ACTIVE specification version not found: " + activeBinding.specificationVersionId()));

        validateCandidateOwnership(plan, activeSnapshot, activeVersion);

        List<RequirementVersionRecord> activeRecords = requirementStore.listRequirementVersions(activeSnapshot.id());
        Map<DomainIdentity, RequirementVersionRecord> baseline = currentBaseline(activeRecords);
        Map<DomainIdentity, RequirementDelta> deltasByIdentity = validateAndIndexDeltas(plan, baseline);
        Map<DomainIdentity, Requirement> candidateContent = buildCandidateContent(plan, baseline, deltasByIdentity);
        Map<DomainIdentity, EntityVersionId> candidateEntityVersionIds = validateEntityVersionIds(
                plan, candidateContent.keySet(), activeRecords);

        List<RequirementVersionRecord> candidateRecords = buildCandidateRecords(
                plan.specificationVersion().id(),
                plan.candidateSnapshot(),
                candidateContent,
                candidateEntityVersionIds);
        List<AppliedRequirementDelta> appliedDeltas = buildAppliedDeltaReceipts(
                deltasByIdentity,
                candidateEntityVersionIds);

        validateExistingCandidateRecords(plan.candidateSnapshot(), candidateRecords);
        verifyActiveSnapshotDidNotChange(plan, activeSnapshot);

        requirementStore.putSpecificationVersion(plan.specificationVersion());
        snapshotStore.putSnapshot(plan.candidateSnapshot());
        requirementStore.bindSnapshotVersion(new SnapshotSpecificationVersionBinding(
                plan.candidateSnapshot().id(),
                plan.specificationVersion().id()));
        candidateRecords.forEach(requirementStore::putRequirementVersion);

        return new RequirementDeltaApplicationResult(
                plan.specificationVersion(),
                plan.candidateSnapshot(),
                candidateRecords,
                appliedDeltas,
                plan.applicationEvidenceId());
    }

    private void validateCandidateOwnership(
            RequirementDeltaApplicationPlan plan,
            KnowledgeSnapshotMetadata activeSnapshot,
            SpecificationVersion activeVersion) {
        SpecificationVersion candidateVersion = plan.specificationVersion();
        KnowledgeSnapshotMetadata candidateSnapshot = plan.candidateSnapshot();

        if (!candidateVersion.projectId().equals(plan.projectId())) {
            throw new RequirementDeltaApplicationException("candidate specification version belongs to another project");
        }
        if (!candidateVersion.predecessor().equals(Optional.of(activeVersion.id()))) {
            throw new RequirementDeltaApplicationException(
                    "candidate specification version predecessor must be the ACTIVE specification version");
        }
        if (!candidateSnapshot.projectId().equals(plan.projectId())) {
            throw new RequirementDeltaApplicationException("candidate snapshot belongs to another project");
        }
        if (candidateSnapshot.state() != KnowledgeSnapshotState.BUILDING) {
            throw new RequirementDeltaApplicationException("candidate snapshot must start in BUILDING");
        }
        if (candidateSnapshot.id().equals(activeSnapshot.id())) {
            throw new RequirementDeltaApplicationException("candidate snapshot must be distinct from the ACTIVE snapshot");
        }
        if (!candidateSnapshot.predecessorId().equals(Optional.of(activeSnapshot.id()))) {
            throw new RequirementDeltaApplicationException("candidate snapshot predecessor must be the ACTIVE snapshot");
        }
    }

    private Map<DomainIdentity, RequirementVersionRecord> currentBaseline(List<RequirementVersionRecord> activeRecords) {
        Map<DomainIdentity, RequirementVersionRecord> baseline = new TreeMap<>();
        for (RequirementVersionRecord record : activeRecords) {
            if (record.entityVersion().temporalState() != TemporalState.CURRENT) {
                continue;
            }
            RequirementVersionRecord previous = baseline.put(record.entityVersion().entityIdentity(), record);
            if (previous != null) {
                throw new RequirementDeltaApplicationException(
                        "ACTIVE baseline contains multiple CURRENT occurrences for " + record.entityVersion().entityIdentity());
            }
        }
        return baseline;
    }

    private Map<DomainIdentity, RequirementDelta> validateAndIndexDeltas(
            RequirementDeltaApplicationPlan plan,
            Map<DomainIdentity, RequirementVersionRecord> baseline) {
        Set<RequirementDeltaId> deltaIds = new HashSet<>();
        Map<DomainIdentity, RequirementDelta> deltasByIdentity = new TreeMap<>();

        for (RequirementDelta delta : plan.deltas()) {
            if (!deltaIds.add(delta.id())) {
                throw new RequirementDeltaApplicationException("duplicate RequirementDeltaId in application batch: " + delta.id());
            }

            DomainIdentity identity = delta.requirementId().value();
            RequirementDelta previous = deltasByIdentity.put(identity, delta);
            if (previous != null) {
                throw new RequirementDeltaApplicationException(
                        "multiple deltas for one logical requirement are ambiguous: " + delta.requirementId());
            }

            SpecificationId resolvedSpecificationId = resolveSpecificationId(plan, delta);
            RequirementVersionRecord current = baseline.get(identity);
            switch (delta.kind()) {
                case ADDED -> {
                    if (current != null) {
                        throw new RequirementDeltaApplicationException(
                                "ADDED requirement already exists in ACTIVE baseline: " + delta.requirementId());
                    }
                }
                case MODIFIED, REMOVED -> {
                    if (current == null) {
                        throw new RequirementDeltaApplicationException(
                                delta.kind() + " requirement is absent from ACTIVE baseline: " + delta.requirementId());
                    }
                    if (!current.entityVersion().content().specificationId().equals(resolvedSpecificationId)) {
                        throw new RequirementDeltaApplicationException(
                                "delta specificationKey resolves to a different specification for " + delta.requirementId());
                    }
                }
            }
        }
        return deltasByIdentity;
    }

    private SpecificationId resolveSpecificationId(
            RequirementDeltaApplicationPlan plan,
            RequirementDelta delta) {
        SpecificationId resolved = plan.specificationIdsByKey().get(delta.specificationKey());
        if (resolved == null) {
            throw new RequirementDeltaApplicationException(
                    "no explicit SpecificationId mapping for specificationKey: " + delta.specificationKey());
        }
        return resolved;
    }

    private Map<DomainIdentity, Requirement> buildCandidateContent(
            RequirementDeltaApplicationPlan plan,
            Map<DomainIdentity, RequirementVersionRecord> baseline,
            Map<DomainIdentity, RequirementDelta> deltasByIdentity) {
        Map<DomainIdentity, Requirement> candidate = new TreeMap<>();
        baseline.forEach((identity, record) -> candidate.put(identity, record.entityVersion().content()));

        for (Map.Entry<DomainIdentity, RequirementDelta> entry : deltasByIdentity.entrySet()) {
            DomainIdentity identity = entry.getKey();
            RequirementDelta delta = entry.getValue();
            switch (delta.kind()) {
                case ADDED -> candidate.put(identity, requirementFromDelta(plan, delta));
                case MODIFIED -> {
                    Requirement baselineRequirement = baseline.get(identity).entityVersion().content();
                    Requirement modified = requirementFromDelta(plan, delta);
                    if (!modified.id().equals(baselineRequirement.id())) {
                        throw new RequirementDeltaApplicationException(
                                "MODIFIED delta must preserve RequirementId: " + delta.requirementId());
                    }
                    candidate.put(identity, modified);
                }
                case REMOVED -> candidate.remove(identity);
            }
        }
        return candidate;
    }

    private Requirement requirementFromDelta(
            RequirementDeltaApplicationPlan plan,
            RequirementDelta delta) {
        String statement = delta.statement().orElseThrow(() -> new RequirementDeltaApplicationException(
                delta.kind() + " delta requires a statement when materialized: " + delta.id()));
        return new Requirement(
                delta.requirementId(),
                resolveSpecificationId(plan, delta),
                delta.key(),
                delta.title(),
                statement,
                delta.provenance());
    }

    private Map<DomainIdentity, EntityVersionId> validateEntityVersionIds(
            RequirementDeltaApplicationPlan plan,
            Set<DomainIdentity> candidateIdentities,
            List<RequirementVersionRecord> activeRecords) {
        if (!plan.entityVersionIds().keySet().equals(candidateIdentities)) {
            Set<DomainIdentity> missing = new HashSet<>(candidateIdentities);
            missing.removeAll(plan.entityVersionIds().keySet());
            Set<DomainIdentity> extra = new HashSet<>(plan.entityVersionIds().keySet());
            extra.removeAll(candidateIdentities);
            throw new RequirementDeltaApplicationException(
                    "candidate EntityVersionId plan must match resulting identities exactly; missing=" + missing + ", extra=" + extra);
        }

        Set<EntityVersionId> ids = new HashSet<>(plan.entityVersionIds().values());
        if (ids.size() != plan.entityVersionIds().size()) {
            throw new RequirementDeltaApplicationException("candidate EntityVersionId values must be unique");
        }

        Set<EntityVersionId> activeIds = new HashSet<>();
        activeRecords.forEach(record -> activeIds.add(record.entityVersion().id()));
        ids.stream().filter(activeIds::contains).findFirst().ifPresent(reused -> {
            throw new RequirementDeltaApplicationException(
                    "candidate must create new EntityVersionId values instead of reusing ACTIVE occurrence " + reused);
        });
        return new HashMap<>(plan.entityVersionIds());
    }

    private List<RequirementVersionRecord> buildCandidateRecords(
            SpecificationVersionId specificationVersionId,
            KnowledgeSnapshotMetadata candidateSnapshot,
            Map<DomainIdentity, Requirement> candidateContent,
            Map<DomainIdentity, EntityVersionId> entityVersionIds) {
        List<RequirementVersionRecord> records = new ArrayList<>();
        candidateContent.forEach((identity, requirement) -> records.add(new RequirementVersionRecord(
                candidateSnapshot.id(),
                new EntityVersion<>(
                        entityVersionIds.get(identity),
                        identity,
                        specificationVersionId,
                        TemporalState.CURRENT,
                        requirement))));
        return List.copyOf(records);
    }

    private List<AppliedRequirementDelta> buildAppliedDeltaReceipts(
            Map<DomainIdentity, RequirementDelta> deltasByIdentity,
            Map<DomainIdentity, EntityVersionId> entityVersionIds) {
        List<AppliedRequirementDelta> receipts = new ArrayList<>();
        deltasByIdentity.forEach((identity, delta) -> receipts.add(new AppliedRequirementDelta(
                delta.id(),
                delta.changeId(),
                delta.kind(),
                delta.requirementId(),
                delta.provenance().evidenceId(),
                delta.kind() == RequirementDeltaKind.REMOVED
                        ? Optional.empty()
                        : Optional.of(entityVersionIds.get(identity)))));
        return List.copyOf(receipts);
    }

    private void validateExistingCandidateRecords(
            KnowledgeSnapshotMetadata candidateSnapshot,
            List<RequirementVersionRecord> expectedRecords) {
        Map<EntityVersionId, RequirementVersionRecord> expected = new HashMap<>();
        expectedRecords.forEach(record -> expected.put(record.entityVersion().id(), record));

        for (RequirementVersionRecord existing : requirementStore.listRequirementVersions(candidateSnapshot.id())) {
            RequirementVersionRecord expectedRecord = expected.get(existing.entityVersion().id());
            if (!existing.equals(expectedRecord)) {
                throw new RequirementDeltaApplicationException(
                        "candidate snapshot already contains a requirement occurrence outside this application plan: "
                                + existing.entityVersion().id());
            }
        }
    }

    private void verifyActiveSnapshotDidNotChange(
            RequirementDeltaApplicationPlan plan,
            KnowledgeSnapshotMetadata expectedActive) {
        KnowledgeSnapshotMetadata currentActive = snapshotStore.activeSnapshot(plan.projectId())
                .orElseThrow(() -> new RequirementDeltaApplicationException("ACTIVE snapshot disappeared during delta application"));
        if (!currentActive.id().equals(expectedActive.id())) {
            throw new RequirementDeltaApplicationException("ACTIVE snapshot changed during delta application");
        }
    }
}
