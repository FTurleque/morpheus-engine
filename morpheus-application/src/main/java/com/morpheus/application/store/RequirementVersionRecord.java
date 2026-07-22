package com.morpheus.application.store;

import com.morpheus.domain.requirement.Requirement;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.version.EntityVersion;

import java.util.Objects;

/** Persistable membership of one versioned requirement occurrence in a technical snapshot. */
public record RequirementVersionRecord(
        KnowledgeSnapshotId snapshotId,
        EntityVersion<Requirement> entityVersion) {

    public RequirementVersionRecord {
        Objects.requireNonNull(snapshotId, "snapshotId");
        Objects.requireNonNull(entityVersion, "entityVersion");
        if (!entityVersion.entityIdentity().equals(entityVersion.content().id().value())) {
            throw new IllegalArgumentException("entityVersion identity must match requirement identity");
        }
    }
}
