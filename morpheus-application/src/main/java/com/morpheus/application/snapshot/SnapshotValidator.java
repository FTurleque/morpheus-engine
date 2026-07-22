package com.morpheus.application.snapshot;

import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;

/** Validates one non-published knowledge snapshot before it may become READY. */
@FunctionalInterface
public interface SnapshotValidator {
    SnapshotValidationResult validate(KnowledgeSnapshotMetadata snapshot);
}
