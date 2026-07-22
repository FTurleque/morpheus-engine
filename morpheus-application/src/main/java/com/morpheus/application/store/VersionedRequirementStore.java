package com.morpheus.application.store;

import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.version.EntityVersionId;
import com.morpheus.domain.version.SpecificationVersion;
import com.morpheus.domain.version.SpecificationVersionId;

import java.util.List;
import java.util.Optional;

/** Technology-neutral persistence port for the first versioned business-content vertical slice. */
public interface VersionedRequirementStore {
    void putSpecificationVersion(SpecificationVersion version);

    Optional<SpecificationVersion> findSpecificationVersion(SpecificationVersionId versionId);

    void bindSnapshotVersion(SnapshotSpecificationVersionBinding binding);

    Optional<SnapshotSpecificationVersionBinding> findSnapshotVersion(KnowledgeSnapshotId snapshotId);

    void putRequirementVersion(RequirementVersionRecord record);

    Optional<RequirementVersionRecord> findRequirementVersion(EntityVersionId entityVersionId);

    List<RequirementVersionRecord> listRequirementVersions(KnowledgeSnapshotId snapshotId);

    Optional<RequirementVersionRecord> currentRequirement(
            KnowledgeSnapshotId snapshotId,
            DomainIdentity entityIdentity);
}
