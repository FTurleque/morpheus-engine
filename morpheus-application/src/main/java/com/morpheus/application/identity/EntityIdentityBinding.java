package com.morpheus.application.identity;

import com.morpheus.domain.identity.DomainIdentity;

import java.util.Objects;

/** Immutable association between one provider-scoped external key and a MORPHEUS identity. */
public record EntityIdentityBinding(EntityIdentityKey key, DomainIdentity identity) {
    public EntityIdentityBinding {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(identity, "identity");
    }
}
