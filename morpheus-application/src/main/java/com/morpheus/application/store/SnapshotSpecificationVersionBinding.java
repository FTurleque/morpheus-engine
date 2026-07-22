package com.morpheus.application.store;

import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.version.SpecificationVersionId;

import java.util.Objects;

/** Explicit association between one technical snapshot and one logical specification version. */
public record SnapshotSpecificationVersionBinding(
        KnowledgeSnapshotId snapshotId,
        SpecificationVersionId specificationVersionId) {

    public SnapshotSpecificationVersionBinding {
        Objects.requireNonNull(snapshotId, "snapshotId");
        Objects.requireNonNull(specificationVersionId, "specificationVersionId");
    }
}
