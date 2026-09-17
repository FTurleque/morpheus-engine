package com.morpheus.api;

import com.morpheus.api.MorpheusRemoteIdentityFile.GeneratedCredential;
import com.morpheus.api.MorpheusRemoteIdentityFile.Identity;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Token material: what is generated, what is stored, and what a presented token is allowed to prove.
 *
 * <p>Every assertion here is about a secret, so each one is also an assertion that the secret did not leak: the
 * generated token exists only in the returned credential, and what crosses towards persistence is the verifier.</p>
 */
class RemoteIdentityCredentialServiceTest {
    @Test
    void aGeneratedTokenCarriesTheFullEntropyBudget() {
        GeneratedCredential credential = RemoteIdentityCredentialService.newCredential(
                "ops", MorpheusRemoteRole.ADMIN, Optional.empty());

        byte[] decoded = Base64.getUrlDecoder().decode(credential.token());

        assertEquals(RemoteIdentityCredentialService.TOKEN_BYTES, decoded.length);
        assertEquals("ops", credential.principal());
        assertEquals(MorpheusRemoteRole.ADMIN, credential.role());
        assertEquals(Optional.empty(), credential.expiresAt());
    }

    @Test
    void generatedTokensDoNotRepeat() {
        Set<String> tokens = new HashSet<>();
        for (int index = 0; index < 256; index++) {
            tokens.add(RemoteIdentityCredentialService
                    .newCredential("ops", MorpheusRemoteRole.READ, Optional.empty())
                    .token());
        }

        assertEquals(256, tokens.size());
    }

    @Test
    void onlyTheVerifierCrossesTowardsPersistence() {
        GeneratedCredential credential = RemoteIdentityCredentialService.newCredential(
                "ops", MorpheusRemoteRole.WRITE, Optional.of(Instant.parse("2030-01-01T00:00:00Z")));

        Identity identity = RemoteIdentityCredentialService.identity(credential);

        assertEquals(credential.principal(), identity.principal());
        assertEquals(credential.role(), identity.role());
        assertEquals(credential.expiresAt(), identity.expiresAt());
        assertArrayEqualsSha256(credential.token(), identity.tokenHash());
        assertFalse(identity.toString().contains(credential.token()));
        assertFalse(credential.toString().contains(credential.token()));
    }

    @Test
    void neitherRenderingDisclosesTokenOrVerifier() {
        GeneratedCredential credential = RemoteIdentityCredentialService.newCredential(
                "ops", MorpheusRemoteRole.ADMIN, Optional.empty());
        Identity identity = RemoteIdentityCredentialService.identity(credential);
        String verifier = HexFormat.of().formatHex(identity.tokenHash());

        assertFalse(identity.toString().contains(verifier));
        assertTrue(identity.toString().contains("tokenHash=<redacted>"));
        assertTrue(credential.toString().contains("token=<redacted>"));
        assertFalse(List.of(identity).toString().contains(verifier),
                "a collection dump must stay safe by construction");
    }

    @Test
    void theRightTokenAuthenticatesAndTheWrongOneDoesNot() {
        GeneratedCredential credential = RemoteIdentityCredentialService.newCredential(
                "ops", MorpheusRemoteRole.ADMIN, Optional.empty());
        List<Identity> identities = List.of(RemoteIdentityCredentialService.identity(credential));

        assertEquals(Optional.of(identities.get(0)),
                RemoteIdentityCredentialService.authenticate(identities, credential.token(), Instant.now()));
        assertEquals(Optional.empty(),
                RemoteIdentityCredentialService.authenticate(identities, "not-the-token", Instant.now()));
    }

    @Test
    void anEmptyBlankOrOversizedPresentedTokenIsRefusedWithoutBeingHashed() {
        List<Identity> identities = List.of(RemoteIdentityCredentialService.identity(
                RemoteIdentityCredentialService.newCredential("ops", MorpheusRemoteRole.READ, Optional.empty())));
        Instant now = Instant.now();

        assertEquals(Optional.empty(), RemoteIdentityCredentialService.authenticate(identities, null, now));
        assertEquals(Optional.empty(), RemoteIdentityCredentialService.authenticate(identities, "", now));
        assertEquals(Optional.empty(), RemoteIdentityCredentialService.authenticate(identities, "   ", now));
        assertEquals(Optional.empty(),
                RemoteIdentityCredentialService.authenticate(identities, "x".repeat(1025), now));
    }

    @Test
    void aPresentedTokenAtTheLengthBoundaryIsStillEvaluated() {
        String boundary = "x".repeat(1024);
        Identity identity = new Identity(
                "ops", MorpheusRemoteRole.READ, RemoteIdentityCredentialService.sha256Bytes(boundary));

        assertEquals(Optional.of(identity),
                RemoteIdentityCredentialService.authenticate(List.of(identity), boundary, Instant.now()));
    }

    @Test
    void anExpiredCredentialNeverAuthenticatesWhileAnActiveOneDoes() {
        Instant deadline = Instant.parse("2030-01-01T00:00:00Z");
        GeneratedCredential credential = RemoteIdentityCredentialService.newCredential(
                "ops", MorpheusRemoteRole.ADMIN, Optional.of(deadline));
        List<Identity> identities = List.of(RemoteIdentityCredentialService.identity(credential));

        assertTrue(RemoteIdentityCredentialService
                .authenticate(identities, credential.token(), deadline.minusSeconds(1)).isPresent());
        assertEquals(Optional.empty(), RemoteIdentityCredentialService
                .authenticate(identities, credential.token(), deadline));
        assertEquals(Optional.empty(), RemoteIdentityCredentialService
                .authenticate(identities, credential.token(), deadline.plusSeconds(1)));
    }

    @Test
    void eachIdentityInASnapshotAuthenticatesOnlyItsOwnToken() {
        GeneratedCredential first = RemoteIdentityCredentialService.newCredential(
                "first", MorpheusRemoteRole.READ, Optional.empty());
        GeneratedCredential second = RemoteIdentityCredentialService.newCredential(
                "second", MorpheusRemoteRole.ADMIN, Optional.empty());
        List<Identity> identities = List.of(
                RemoteIdentityCredentialService.identity(first),
                RemoteIdentityCredentialService.identity(second));
        Instant now = Instant.now();

        assertEquals("first",
                RemoteIdentityCredentialService.authenticate(identities, first.token(), now).orElseThrow().principal());
        assertEquals("second",
                RemoteIdentityCredentialService.authenticate(identities, second.token(), now).orElseThrow().principal());
    }

    /** An empty snapshot is not an open door: it authenticates nothing, including a well-formed token. */
    @Test
    void anEmptySnapshotAuthenticatesNothing() {
        GeneratedCredential credential = RemoteIdentityCredentialService.newCredential(
                "ops", MorpheusRemoteRole.ADMIN, Optional.empty());

        assertEquals(Optional.empty(),
                RemoteIdentityCredentialService.authenticate(List.of(), credential.token(), Instant.now()));
    }

    @Test
    void theHexVerifierMatchesTheByteVerifier() {
        assertEquals(
                HexFormat.of().formatHex(RemoteIdentityCredentialService.sha256Bytes("token")),
                RemoteIdentityCredentialService.sha256Hex("token"));
        assertNotEquals(
                RemoteIdentityCredentialService.sha256Hex("token"),
                RemoteIdentityCredentialService.sha256Hex("tokeo"));
    }

    @Test
    void hashingRefusesAbsentInputRatherThanProducingAConstant() {
        assertThrows(NullPointerException.class, () -> RemoteIdentityCredentialService.sha256Bytes(null));
        assertThrows(NullPointerException.class,
                () -> RemoteIdentityCredentialService.authenticate(null, "token", Instant.now()));
        assertThrows(NullPointerException.class,
                () -> RemoteIdentityCredentialService.authenticate(List.of(), "token", null));
    }

    private static void assertArrayEqualsSha256(String token, byte[] actual) {
        try {
            byte[] expected = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            assertTrue(MessageDigest.isEqual(expected, actual));
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }
    }
}
