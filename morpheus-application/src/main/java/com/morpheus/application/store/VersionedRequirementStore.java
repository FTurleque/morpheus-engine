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
     * Returns every CURRENT requirement version for one snapshot in deterministic entity-version order. Production
     * adapters should override this method so the temporal predicate is applied by the persistence backend rather
     * than after loading historical/proposed versions into memory.
     *
     * <p>Unlike {@link #listCurrentRequirementVersions(KnowledgeSnapshotId, int)} this applies no row limit: it is
     * for readers that compute over every CURRENT requirement. A reader that returns part of them asks for
     * {@link #pageCurrentRequirementVersions}, and a reader that needs their number asks for
     * {@link #countCurrentRequirementVersions}.</p>
     *
     * <p>The default implementation is the port's fallback: it answers the same question from
     * {@link #listRequirementVersions(KnowledgeSnapshotId)}, correctly and without pushing the predicate down.</p>
     */
    default List<RequirementVersionRecord> listCurrentRequirementVersions(KnowledgeSnapshotId snapshotId) {
        Objects.requireNonNull(snapshotId, "snapshotId");
        return listRequirementVersions(snapshotId).stream()
                .filter(record -> record.entityVersion().temporalState() == TemporalState.CURRENT)
                .toList();
    }

    /**
     * Returns at most {@code limit} CURRENT requirement versions for one snapshot in deterministic entity-version
     * order. Production adapters should override this method so the temporal predicate and row limit are applied by
     * the persistence backend rather than after loading historical/proposed versions into memory.
     *
     * <p>The default implementation is the port's fallback: it answers the same question from
     * {@link #listRequirementVersions(KnowledgeSnapshotId)}, correctly and without pushing the predicate down.</p>
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

    /**
     * Returns one page of the CURRENT requirement versions of a snapshot that {@code criteria} matches, in
     * {@code order}, with the number of versions the criteria match. The page and the total describe the same state.
     * An offset at or beyond the total yields an empty page and the total.
     *
     * <p>The shipped stores evaluate the criteria, the order and the page themselves; the default is the port's
     * fallback for other implementations, and answers the same question from
     * {@link #listCurrentRequirementVersions(KnowledgeSnapshotId)}.</p>
     */
    default RequirementPage pageCurrentRequirementVersions(
            KnowledgeSnapshotId snapshotId,
            RequirementPageCriteria criteria,
            RequirementPageOrder order,
            int offset,
            int limit) {
        Objects.requireNonNull(snapshotId, "snapshotId");
        Objects.requireNonNull(criteria, "criteria");
        Objects.requireNonNull(order, "order");
        requireValidPage(offset, limit);
        List<RequirementVersionRecord> matches = listCurrentRequirementVersions(snapshotId).stream()
                .filter(record -> criteria.matches(record.entityVersion().content()))
                .sorted(order.comparator())
                .toList();
        int from = Math.min(offset, matches.size());
        int to = (int) Math.min((long) from + limit, matches.size());
        return new RequirementPage(matches.subList(from, to), matches.size());
    }

    /**
     * Returns the number of CURRENT requirement versions of a snapshot without handing them to the caller. The
     * shipped stores count in the backend; the default is the port's fallback.
     */
    default int countCurrentRequirementVersions(KnowledgeSnapshotId snapshotId) {
        Objects.requireNonNull(snapshotId, "snapshotId");
        return listCurrentRequirementVersions(snapshotId).size();
    }

    /** The bounds every implementation of {@link #pageCurrentRequirementVersions} enforces, refused by name. */
    static void requireValidPage(int offset, int limit) {
        if (offset < 0) {
            throw new IllegalArgumentException("requirement page offset must be greater than or equal to zero");
        }
        if (limit < 1) {
            throw new IllegalArgumentException("requirement page limit must be positive");
        }
    }

    Optional<RequirementVersionRecord> currentRequirement(
            KnowledgeSnapshotId snapshotId,
            DomainIdentity entityIdentity);
}
