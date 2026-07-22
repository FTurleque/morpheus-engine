package com.morpheus.application.identity;

import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.provider.ProviderId;

/** Resolves provider evidence keys to stable MORPHEUS identities without exposing provider IDs as domain IDs. */
public interface EntityIdentityResolver {
    DomainIdentity resolve(ProviderId providerId, String entityType, String externalId);
}
