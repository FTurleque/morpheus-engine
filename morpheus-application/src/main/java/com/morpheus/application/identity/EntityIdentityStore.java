package com.morpheus.application.identity;

import com.morpheus.domain.identity.DomainIdentity;

import java.util.Optional;

/** Technology-neutral persistence port for provider-scoped identity bindings. */
public interface EntityIdentityStore {
    Optional<DomainIdentity> find(EntityIdentityKey key);

    void put(EntityIdentityBinding binding);
}
