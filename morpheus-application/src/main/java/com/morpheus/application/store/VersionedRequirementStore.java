package com.morpheus.application.store;

import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.temporal.TemporalState;
import com.morpheus.domain.version.EntityVersionId;
import com.morpheus.domain.version.SpecificationVersion;
import com.morpheus.domain.version.SpecificationVersionId;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Technology-neutral persistence port for the first versioned business-content vertical slice. */
public interface VersionedRequirementStore {
    void putSpecificationVersion(SpecificationVersion version);

    Optional<SpecificationVersion> findSpecificationVersion(SpecificationVersionId versionId);

    /**
     * Atomically reserves and returns the next durable project-local sequence, considering both stored versions and
     * earlier reservations. A failed candidate therefore consumes its sequence instead of allowing a concurrent or
     * later retry to create an ambiguous duplicate.
     */
    default long nextSpecificationVersionSequence(ProjectSpecificationId projectId) {
        throw new KnowledgeStoreException(
                "versioned requirement store does not support durable specification-version sequence allocation");
    }

    void bindSnapshotVersion(SnapshotSpecificationVersionBinding binding);

    Optional<SnapshotSpecificationVersionBinding> findSnapshotVersion(KnowledgeSnapshotId snapshotId);

    void putRequirementVersion(RequirementVersionRecord record);

    /** Persists one logical batch; adapters may override to provide an atomic optimized transaction. */
    default void putRequirementVersions(List<RequirementVersionRecord> records) {
        List.copyOf(records).forEach(this::putRequirementVersion);
    }

    Optional<RequirementVersionRecord> findRequirementVersion(EntityVersionId entityVersionId);

    List<RequirementVersionRecord> listRequirementVersions(KnowledgeSnapshotId snapshotId);

    /**
     * Returns at most {@code limit} CURRENT requirement versions for one snapshot in deterministic entity-version
     * order. Production adapters should override this method so the temporal predicate and row limit are applied by
     * the persistence backend rather than after loading historical/proposed versions into memory.
     *
     * <p>The default implementation preserves source compatibility for third-party/test adapters while keeping the
     * public semantic contract correct.</p>
     */
    default List<RequirementVersionRecord> listCurrentRequirementVersions(
            KnowledgeSnapshotId snapshotId,
            int limit) {
        Objects.requireNonNull(snapshotId, "snapshotId");
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive");
        }
        return listRequirementVersions(snapshotId).stream()
                .filter(record -> record.entityVersion().temporalState() == TemporalState.CURRENT)
                .limit(limit)
                .toList();
    }

    Optional<RequirementVersionRecord> currentRequirement(
            KnowledgeSnapshotId snapshotId,
            DomainIdentity entityIdentity);
}
