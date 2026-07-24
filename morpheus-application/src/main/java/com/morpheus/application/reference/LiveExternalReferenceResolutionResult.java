package com.morpheus.application.reference;

import com.morpheus.domain.reference.ExternalReference;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;

import java.util.Objects;

/** Non-persisted observation of one snapshot-scoped external reference. */
public record LiveExternalReferenceResolutionResult(
        KnowledgeSnapshotMetadata snapshot,
        ExternalReference storedReference,
        ExternalReference observedReference) {

    public LiveExternalReferenceResolutionResult {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(storedReference, "storedReference");
        Objects.requireNonNull(observedReference, "observedReference");
        if (!storedReference.id().equals(observedReference.id())) {
            throw new IllegalArgumentException("live resolution must preserve ExternalReferenceId");
        }
        if (!storedReference.ownerId().equals(observedReference.ownerId())) {
            throw new IllegalArgumentException("live resolution must preserve reference owner");
        }
        if (!storedReference.target().equals(observedReference.target())) {
            throw new IllegalArgumentException("live resolution must preserve external target coordinates");
        }
    }
}
