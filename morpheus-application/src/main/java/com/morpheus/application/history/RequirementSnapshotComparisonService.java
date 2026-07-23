package com.morpheus.application.history;

import com.morpheus.application.store.RequirementVersionRecord;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.application.store.VersionedRequirementStore;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.temporal.TemporalState;

import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/** Compares two published Requirement projections by stable logical identity and normalized content. */
public final class RequirementSnapshotComparisonService {
    private final SpecificationKnowledgeStore snapshotStore;
    private final VersionedRequirementStore requirementStore;

    public RequirementSnapshotComparisonService(
            SpecificationKnowledgeStore snapshotStore,
            VersionedRequirementStore requirementStore) {
        this.snapshotStore = Objects.requireNonNull(snapshotStore, "snapshotStore");
        this.requirementStore = Objects.requireNonNull(requirementStore, "requirementStore");
    }

    public RequirementSnapshotComparison compare(
            KnowledgeSnapshotId sourceSnapshotId,
            KnowledgeSnapshotId targetSnapshotId) {
        KnowledgeSnapshotMetadata sourceSnapshot = requirePublished(sourceSnapshotId);
        KnowledgeSnapshotMetadata targetSnapshot = requirePublished(targetSnapshotId);
        if (!sourceSnapshot.projectId().equals(targetSnapshot.projectId())) {
            throw new PublishedHistoryException("snapshot comparison cannot cross project boundaries");
        }

        Map<DomainIdentity, RequirementVersionRecord> source = currentProjection(sourceSnapshotId);
        Map<DomainIdentity, RequirementVersionRecord> target = currentProjection(targetSnapshotId);
        Set<DomainIdentity> identities = new TreeSet<>(source.keySet());
        identities.addAll(target.keySet());

        var differences = new ArrayList<RequirementSnapshotDifference>();
        for (DomainIdentity identity : identities) {
            RequirementVersionRecord sourceRecord = source.get(identity);
            RequirementVersionRecord targetRecord = target.get(identity);
            if (sourceRecord == null) {
                differences.add(new RequirementSnapshotDifference(
                        identity,
                        RequirementSnapshotChangeKind.ADDED,
                        Optional.empty(),
                        Optional.of(targetRecord)));
            } else if (targetRecord == null) {
                differences.add(new RequirementSnapshotDifference(
                        identity,
                        RequirementSnapshotChangeKind.REMOVED,
                        Optional.of(sourceRecord),
                        Optional.empty()));
            } else {
                RequirementSnapshotChangeKind kind = sourceRecord.entityVersion().content()
                        .equals(targetRecord.entityVersion().content())
                        ? RequirementSnapshotChangeKind.UNCHANGED
                        : RequirementSnapshotChangeKind.MODIFIED;
                differences.add(new RequirementSnapshotDifference(
                        identity,
                        kind,
                        Optional.of(sourceRecord),
                        Optional.of(targetRecord)));
            }
        }

        return new RequirementSnapshotComparison(sourceSnapshot, targetSnapshot, differences);
    }

    private Map<DomainIdentity, RequirementVersionRecord> currentProjection(KnowledgeSnapshotId snapshotId) {
        Map<DomainIdentity, RequirementVersionRecord> current = new TreeMap<>();
        for (RequirementVersionRecord record : requirementStore.listRequirementVersions(snapshotId)) {
            if (record.entityVersion().temporalState() != TemporalState.CURRENT) {
                continue;
            }
            RequirementVersionRecord previous = current.put(record.entityVersion().entityIdentity(), record);
            if (previous != null) {
                throw new PublishedHistoryException(
                        "published snapshot contains multiple CURRENT occurrences for "
                                + record.entityVersion().entityIdentity());
            }
        }
        return current;
    }

    private KnowledgeSnapshotMetadata requirePublished(KnowledgeSnapshotId snapshotId) {
        Objects.requireNonNull(snapshotId, "snapshotId");
        KnowledgeSnapshotMetadata snapshot = snapshotStore.findSnapshot(snapshotId)
                .orElseThrow(() -> new PublishedHistoryException("snapshot not found: " + snapshotId));
        if (snapshot.state() != KnowledgeSnapshotState.ACTIVE
                && snapshot.state() != KnowledgeSnapshotState.RETIRED) {
            throw new PublishedHistoryException(
                    "snapshot comparison requires published snapshots but was "
                            + snapshot.state() + ": " + snapshotId);
        }
        if (requirementStore.findSnapshotVersion(snapshotId).isEmpty()) {
            throw new PublishedHistoryException("published snapshot has no specification version binding: " + snapshotId);
        }
        return snapshot;
    }
}