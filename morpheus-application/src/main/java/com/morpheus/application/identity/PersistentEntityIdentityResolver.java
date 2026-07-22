package com.morpheus.application.identity;

import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.provider.ProviderId;

import java.util.Objects;
import java.util.Optional;

/**
 * Stable entity identity resolver backed by an {@link EntityIdentityStore}.
 *
 * <p>No title, path or content similarity participates in identity resolution. Continuity across an
 * external-id change must be declared explicitly through {@link #continueIdentity}.
 */
public final class PersistentEntityIdentityResolver implements EntityIdentityResolver {
    private final EntityIdentityStore store;

    public PersistentEntityIdentityResolver(EntityIdentityStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    @Override
    public DomainIdentity resolve(ProviderId providerId, String entityType, String externalId) {
        EntityIdentityKey key = new EntityIdentityKey(providerId, entityType, externalId);
        Optional<DomainIdentity> existing = store.find(key);
        if (existing.isPresent()) {
            return existing.orElseThrow();
        }

        DomainIdentity generated = DomainIdentity.generate();
        try {
            store.put(new EntityIdentityBinding(key, generated));
            return generated;
        } catch (IdentityCollisionException collision) {
            // A concurrent resolver may have won the first-write race. The persisted binding is authoritative.
            return store.find(key).orElseThrow(() -> collision);
        }
    }

    @Override
    public DomainIdentity continueIdentity(
            ProviderId providerId,
            String entityType,
            String previousExternalId,
            String newExternalId) {
        EntityIdentityKey previousKey = new EntityIdentityKey(providerId, entityType, previousExternalId);
        EntityIdentityKey newKey = new EntityIdentityKey(providerId, entityType, newExternalId);

        DomainIdentity identity = store.find(previousKey)
                .orElseGet(() -> resolve(providerId, entityType, previousExternalId));

        Optional<DomainIdentity> existingNew = store.find(newKey);
        if (existingNew.isPresent()) {
            DomainIdentity existingIdentity = existingNew.orElseThrow();
            if (!existingIdentity.equals(identity)) {
                throw new IdentityCollisionException(
                        "external identity key already belongs to another MORPHEUS identity: " + newKey);
            }
            return identity;
        }

        try {
            store.put(new EntityIdentityBinding(newKey, identity));
            return identity;
        } catch (IdentityCollisionException collision) {
            DomainIdentity winner = store.find(newKey).orElseThrow(() -> collision);
            if (!winner.equals(identity)) {
                throw collision;
            }
            return identity;
        }
    }
}
