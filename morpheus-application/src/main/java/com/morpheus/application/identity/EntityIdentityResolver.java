package com.morpheus.application.identity;

import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.provider.ProviderId;

/** Resolves provider evidence keys to stable MORPHEUS identities without exposing provider IDs as domain IDs. */
public interface EntityIdentityResolver {
    DomainIdentity resolve(ProviderId providerId, String entityType, String externalId);

    /**
     * Explicitly declares continuity from one external identifier to another.
     * Implementations that do not support persistent aliasing may reject this operation.
     */
    default DomainIdentity continueIdentity(
            ProviderId providerId,
            String entityType,
            String previousExternalId,
            String newExternalId) {
        throw new UnsupportedOperationException("explicit identity continuity is not supported by this resolver");
    }
}
