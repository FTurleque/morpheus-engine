package com.morpheus.api;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MorpheusRemoteHttpServerStartupPolicyTest {
    private static final Instant NOW = Instant.parse("2030-01-01T00:00:00Z");

    @Test
    void rejectsAnEmptyIdentityStore() {
        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> MorpheusRemoteHttpServer.validateStartupIdentities(List.of(), NOW));

        assertEquals("remote auth file contains no identities", failure.getMessage());
    }

    @Test
    void rejectsAStoreWhoseOnlyAdminIsExpired() {
        var expiredAdmin = identity(
                "expired-admin",
                MorpheusRemoteRole.ADMIN,
                Optional.of(NOW.minusSeconds(1)));
        var activeWriter = identity(
                "writer",
                MorpheusRemoteRole.WRITE,
                Optional.of(NOW.plusSeconds(60)));

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> MorpheusRemoteHttpServer.validateStartupIdentities(
                        List.of(expiredAdmin, activeWriter),
                        NOW));

        assertEquals("remote auth file must contain at least one active ADMIN identity", failure.getMessage());
    }

    @Test
    void acceptsAtLeastOneActiveAdmin() {
        var expiredAdmin = identity(
                "expired-admin",
                MorpheusRemoteRole.ADMIN,
                Optional.of(NOW.minusSeconds(1)));
        var activeAdmin = identity(
                "active-admin",
                MorpheusRemoteRole.ADMIN,
                Optional.of(NOW.plusSeconds(60)));

        assertDoesNotThrow(() -> MorpheusRemoteHttpServer.validateStartupIdentities(
                List.of(expiredAdmin, activeAdmin),
                NOW));
    }

    @Test
    void acceptsANonExpiringAdmin() {
        var permanentAdmin = identity("permanent-admin", MorpheusRemoteRole.ADMIN, Optional.empty());

        assertDoesNotThrow(() -> MorpheusRemoteHttpServer.validateStartupIdentities(
                List.of(permanentAdmin),
                NOW));
    }

    private static MorpheusRemoteIdentityFile.Identity identity(
            String principal,
            MorpheusRemoteRole role,
            Optional<Instant> expiresAt) {
        return new MorpheusRemoteIdentityFile.Identity(principal, role, new byte[32], expiresAt);
    }
}
