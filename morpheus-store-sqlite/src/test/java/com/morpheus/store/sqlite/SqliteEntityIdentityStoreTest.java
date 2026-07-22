package com.morpheus.store.sqlite;

import com.morpheus.application.identity.PersistentEntityIdentityResolver;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.provider.ProviderId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SqliteEntityIdentityStoreTest {

    @TempDir
    Path tempDir;

    @Test
    void resolvedIdentitySurvivesStoreReopen() {
        Path database = tempDir.resolve("identity.db");
        ProviderId provider = new ProviderId("openspec");
        DomainIdentity first;

        try (var store = new SqliteEntityIdentityStore(database)) {
            first = new PersistentEntityIdentityResolver(store)
                    .resolve(provider, "requirement", "auth-session/session-expiration");
        }

        try (var reopened = new SqliteEntityIdentityStore(database)) {
            DomainIdentity second = new PersistentEntityIdentityResolver(reopened)
                    .resolve(provider, "requirement", "auth-session/session-expiration");
            assertEquals(first, second);
        }
    }
}
