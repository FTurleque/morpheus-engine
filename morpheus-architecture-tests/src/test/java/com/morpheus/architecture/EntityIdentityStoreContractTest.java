package com.morpheus.architecture;

import com.morpheus.application.identity.EntityIdentityBinding;
import com.morpheus.application.identity.EntityIdentityKey;
import com.morpheus.application.identity.EntityIdentityStore;
import com.morpheus.application.identity.IdentityCollisionException;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.store.memory.MemorySpecificationKnowledgeStore;
import com.morpheus.store.sqlite.SqliteEntityIdentityStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EntityIdentityStoreContractTest {

    @TempDir
    Path tempDir;

    @Test
    void sameBindingIsIdempotentAndReadableAcrossBackends() {
        assertOnEachBackend("idempotent.db", store -> {
            EntityIdentityKey key = new EntityIdentityKey(
                    new ProviderId("openspec"), "requirement", "auth-session/session-expiration");
            DomainIdentity identity = DomainIdentity.generate();
            EntityIdentityBinding binding = new EntityIdentityBinding(key, identity);

            store.put(binding);
            store.put(binding);

            assertEquals(identity, store.find(key).orElseThrow());
        });
    }

    @Test
    void sameExternalKeyCannotPointToTwoDomainIdentitiesAcrossBackends() {
        assertOnEachBackend("collision.db", store -> {
            EntityIdentityKey key = new EntityIdentityKey(
                    new ProviderId("openspec"), "requirement", "auth-session/session-expiration");
            store.put(new EntityIdentityBinding(key, DomainIdentity.generate()));

            assertThrows(
                    IdentityCollisionException.class,
                    () -> store.put(new EntityIdentityBinding(key, DomainIdentity.generate())));
        });
    }

    @Test
    void multipleExplicitAliasesMayPointToSameDomainIdentityAcrossBackends() {
        assertOnEachBackend("aliases.db", store -> {
            ProviderId provider = new ProviderId("openspec");
            DomainIdentity identity = DomainIdentity.generate();
            EntityIdentityKey oldKey = new EntityIdentityKey(provider, "requirement", "old-key");
            EntityIdentityKey newKey = new EntityIdentityKey(provider, "requirement", "new-key");

            store.put(new EntityIdentityBinding(oldKey, identity));
            store.put(new EntityIdentityBinding(newKey, identity));

            assertEquals(identity, store.find(oldKey).orElseThrow());
            assertEquals(identity, store.find(newKey).orElseThrow());
        });
    }

    private void assertOnEachBackend(String databaseName, Consumer<EntityIdentityStore> assertion) {
        assertion.accept(new MemorySpecificationKnowledgeStore());
        try (var sqlite = new SqliteEntityIdentityStore(tempDir.resolve(databaseName))) {
            assertion.accept(sqlite);
        }
    }
}
