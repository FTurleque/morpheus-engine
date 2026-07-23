package com.morpheus.application.history;

import com.morpheus.application.store.RequirementVersionRecord;
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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Derives an explicit RequirementDelta plan that reconstructs a RETIRED published projection. */
public final class RequirementLogicalRollbackService {
    private final SpecificationKnowledgeStore snapshotStore;
    private final PublishedSnapshotHistoryService historyService;
    private final RequirementSnapshotComparisonService comparisonService;

    public RequirementLogicalRollbackService(
            SpecificationKnowledgeStore snapshotStore,
            VersionedRequirementStore requirementStore) {
        this.snapshotStore = Objects.requireNonNull(snapshotStore, "snapshotStore");
        Objects.requireNonNull(requirementStore, "requirementStore");
        this.historyService = new PublishedSnapshotHistoryService(snapshotStore);
        this.comparisonService = new RequirementSnapshotComparisonService(snapshotStore, requirementStore);
    }

    public RequirementLogicalRollbackPlan plan(RequirementLogicalRollbackRequest request) {
        Objects.requireNonNull(request, "request");
        KnowledgeSnapshotMetadata active = snapshotStore.activeSnapshot(request.projectId())
                .orElseThrow(() -> new PublishedHistoryException(
                        "project has no ACTIVE snapshot: " + request.projectId()));

        KnowledgeSnapshotMetadata target = historyService.lineage(request.projectId()).stream()
                .filter(snapshot -> snapshot.id().equals(request.targetSnapshotId()))
                .findFirst()
                .orElseThrow(() -> new PublishedHistoryException(
                        "rollback target is not part of the current published lineage: " + request.targetSnapshotId()));
        if (target.state() != KnowledgeSnapshotState.RETIRED) {
            throw new PublishedHistoryException("logical rollback target must be RETIRED: " + target.id());
        }

        RequirementSnapshotComparison comparison = comparisonService.compare(active.id(), target.id());
        Set<DomainIdentity> changedIdentities = new TreeSet<>();
        comparison.differences().stream()
                .filter(difference -> difference.kind() != RequirementSnapshotChangeKind.UNCHANGED)
                .forEach(difference -> changedIdentities.add(difference.entityIdentity()));
        validateDeltaIds(request.deltaIdsByIdentity(), changedIdentities);

        List<RequirementDelta> deltas = new ArrayList<>();
        Map<String, SpecificationId> specificationIdsByKey = new TreeMap<>();
        for (RequirementSnapshotDifference difference : comparison.differences()) {
            if (difference.kind() == RequirementSnapshotChangeKind.UNCHANGED) {
                continue;
            }

            RollbackMaterialization materialization = materialize(difference);
            Requirement materialized = materialization.requirement();
            String specificationKey = specificationKey(request, materialized.specificationId());
            SpecificationId previous = specificationIdsByKey.put(specificationKey, materialized.specificationId());
            if (previous != null && !previous.equals(materialized.specificationId())) {
                throw new PublishedHistoryException(
                        "rollback specificationKey resolves to multiple SpecificationId values: " + specificationKey);
            }

            RequirementDeltaId deltaId = request.deltaIdsByIdentity().get(difference.entityIdentity());
            deltas.add(new RequirementDelta(
                    deltaId,
                    request.changeId(),
                    materialization.deltaKind(),
                    specificationKey,
                    materialized.id(),
                    materialized.key(),
                    materialized.title(),
                    materialization.statement(),
                    List.of(),
                    materialized.provenance()));
        }

        return new RequirementLogicalRollbackPlan(
                active,
                target,
                request.changeId(),
                deltas,
                specificationIdsByKey);
    }

    private static RollbackMaterialization materialize(RequirementSnapshotDifference difference) {
        return switch (difference.kind()) {
            case ADDED -> {
                Requirement target = content(difference.target(), "ADDED target");
                yield new RollbackMaterialization(
                        RequirementDeltaKind.ADDED,
                        target,
                        Optional.of(target.statement()));
            }
            case MODIFIED -> {
                Requirement source = content(difference.source(), "MODIFIED source");
                Requirement target = content(difference.target(), "MODIFIED target");
                if (!source.specificationId().equals(target.specificationId())) {
                    throw new PublishedHistoryException(
                            "cross-specification logical rollback requires a MOVED policy and is outside M3-S6: "
                                    + difference.entityIdentity());
                }
                yield new RollbackMaterialization(
                        RequirementDeltaKind.MODIFIED,
                        target,
                        Optional.of(target.statement()));
            }
            case REMOVED -> new RollbackMaterialization(
                    RequirementDeltaKind.REMOVED,
                    content(difference.source(), "REMOVED source"),
                    Optional.empty());
            case UNCHANGED -> throw new IllegalArgumentException(
                    "UNCHANGED differences cannot be materialized as rollback deltas");
        };
    }

    private static Requirement content(Optional<RequirementVersionRecord> record, String side) {
        return record.orElseThrow(() -> new PublishedHistoryException(side + " occurrence is missing"))
                .entityVersion()
                .content();
    }

    private static void validateDeltaIds(
            Map<DomainIdentity, RequirementDeltaId> deltaIdsByIdentity,
            Set<DomainIdentity> changedIdentities) {
        if (!deltaIdsByIdentity.keySet().equals(changedIdentities)) {
            Set<DomainIdentity> missing = new TreeSet<>(changedIdentities);
            missing.removeAll(deltaIdsByIdentity.keySet());
            Set<DomainIdentity> extra = new TreeSet<>(deltaIdsByIdentity.keySet());
            extra.removeAll(changedIdentities);
            throw new PublishedHistoryException(
                    "rollback RequirementDeltaId plan must match changed identities exactly; missing="
                            + missing + ", extra=" + extra);
        }
        Set<RequirementDeltaId> unique = new HashSet<>(deltaIdsByIdentity.values());
        if (unique.size() != deltaIdsByIdentity.size()) {
            throw new PublishedHistoryException("rollback RequirementDeltaId values must be unique");
        }
    }

    private static String specificationKey(
            RequirementLogicalRollbackRequest request,
            SpecificationId specificationId) {
        String key = request.specificationKeysById().get(specificationId);
        if (key == null || key.isBlank()) {
            throw new PublishedHistoryException(
                    "no explicit specificationKey mapping for rollback SpecificationId: " + specificationId);
        }
        return key.trim();
    }

    private record RollbackMaterialization(
            RequirementDeltaKind deltaKind,
            Requirement requirement,
            Optional<String> statement) {

        private RollbackMaterialization {
            Objects.requireNonNull(deltaKind, "deltaKind");
            Objects.requireNonNull(requirement, "requirement");
            Objects.requireNonNull(statement, "statement");
        }
    }
}
