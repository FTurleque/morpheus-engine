package com.morpheus.application.identity;

import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.provider.ProviderId;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PersistentEntityIdentityResolverTest {

    @Test
    void sameExternalKeyKeepsIdentityAcrossResolverInstances() {
        InMemoryIdentityStore store = new InMemoryIdentityStore();
        ProviderId provider = new ProviderId("openspec");

        DomainIdentity first = new PersistentEntityIdentityResolver(store)
                .resolve(provider, "requirement", "auth-session/session-expiration");
        DomainIdentity second = new PersistentEntityIdentityResolver(store)
                .resolve(provider, "requirement", "auth-session/session-expiration");

        assertEquals(first, second);
    }

    @Test
    void providerNamespaceKeepsEqualExternalIdsIndependent() {
        InMemoryIdentityStore store = new InMemoryIdentityStore();
        PersistentEntityIdentityResolver resolver = new PersistentEntityIdentityResolver(store);

        DomainIdentity openSpec = resolver.resolve(
                new ProviderId("openspec"), "requirement", "shared-id");
        DomainIdentity synthetic = resolver.resolve(
                new ProviderId("synthetic"), "requirement", "shared-id");

        assertNotEquals(openSpec, synthetic);
    }

    @Test
    void explicitContinuityAddsNewExternalIdAsAlias() {
        InMemoryIdentityStore store = new InMemoryIdentityStore();
        PersistentEntityIdentityResolver resolver = new PersistentEntityIdentityResolver(store);
        ProviderId provider = new ProviderId("openspec");

        DomainIdentity before = resolver.resolve(provider, "requirement", "old-key");
        DomainIdentity after = resolver.continueIdentity(provider, "requirement", "old-key", "new-key");

        assertEquals(before, after);
        assertEquals(before, resolver.resolve(provider, "requirement", "new-key"));
        assertEquals(before, resolver.continueIdentity(provider, "requirement", "old-key", "new-key"));
    }

    @Test
    void explicitContinuityRejectsConflictingOwnedNewKey() {
        InMemoryIdentityStore store = new InMemoryIdentityStore();
        PersistentEntityIdentityResolver resolver = new PersistentEntityIdentityResolver(store);
        ProviderId provider = new ProviderId("openspec");

        resolver.resolve(provider, "requirement", "old-key");
        resolver.resolve(provider, "requirement", "already-owned-key");

        assertThrows(
                IdentityCollisionException.class,
                () -> resolver.continueIdentity(provider, "requirement", "old-key", "already-owned-key"));
    }

    @Test
    void explicitContinuityRequiresPreviouslyKnownExternalIdentity() {
        InMemoryIdentityStore store = new InMemoryIdentityStore();
        PersistentEntityIdentityResolver resolver = new PersistentEntityIdentityResolver(store);
        ProviderId provider = new ProviderId("openspec");

        assertThrows(
                IdentityContinuityException.class,
                () -> resolver.continueIdentity(provider, "requirement", "unknown-old-key", "new-key"));
        assertEquals(Optional.empty(), store.find(new EntityIdentityKey(provider, "requirement", "unknown-old-key")));
        assertEquals(Optional.empty(), store.find(new EntityIdentityKey(provider, "requirement", "new-key")));
    }

    private static final class InMemoryIdentityStore implements EntityIdentityStore {
        private final Map<EntityIdentityKey, DomainIdentity> identities = new HashMap<>();

        @Override
        public Optional<DomainIdentity> find(EntityIdentityKey key) {
            return Optional.ofNullable(identities.get(key));
        }

        @Override
        public void put(EntityIdentityBinding binding) {
            DomainIdentity existing = identities.get(binding.key());
            if (existing != null && !existing.equals(binding.identity())) {
                throw new IdentityCollisionException("identity collision");
            }
            identities.putIfAbsent(binding.key(), binding.identity());
        }
    }
}
