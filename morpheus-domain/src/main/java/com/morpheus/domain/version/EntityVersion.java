package com.morpheus.domain.version;

import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.temporal.TemporalState;

import java.util.Objects;

/**
 * One versioned occurrence of a logical MORPHEUS entity.
 *
 * <p>The logical entity identity remains stable while this occurrence gets its own identity,
 * specification version membership and temporal state.</p>
 */
public record EntityVersion<T>(
        EntityVersionId id,
        DomainIdentity entityIdentity,
        SpecificationVersionId specificationVersionId,
        TemporalState temporalState,
        T content) {

    public EntityVersion {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(entityIdentity, "entityIdentity");
        Objects.requireNonNull(specificationVersionId, "specificationVersionId");
        Objects.requireNonNull(temporalState, "temporalState");
        Objects.requireNonNull(content, "content");
    }
}
