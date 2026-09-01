package com.morpheus.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MorpheusRemoteIdentityFileTest {
    @TempDir
    Path temp;

    @Test
    void generatedTokenIsReturnedOnceButOnlyItsHashIsPersisted() throws Exception {
        Path auth = temp.resolve("remote-auth.txt");
        MorpheusRemoteIdentityFile.GeneratedCredential credential =
                MorpheusRemoteIdentityFile.create(auth, "alice", MorpheusRemoteRole.ADMIN);

        String persisted = Files.readString(auth);
        assertFalse(persisted.contains(credential.token()));
        assertTrue(persisted.contains(MorpheusRemoteIdentityFile.sha256Hex(credential.token())));
        assertTrue(credential.expiresAt().isEmpty());

        List<MorpheusRemoteIdentityFile.Identity> identities = MorpheusRemoteIdentityFile.load(auth);
        assertEquals("alice", MorpheusRemoteIdentityFile.authenticate(identities, credential.token()).orElseThrow().principal());
        assertTrue(MorpheusRemoteIdentityFile.authenticate(identities, "wrong-token").isEmpty());
    }

    @Test
    void expiringCredentialIsPersistedAndRejectedAfterExpiry() throws Exception {
        Path auth = temp.resolve("expiring-auth.txt");
        Instant expiry = Instant.parse("2099-01-01T00:00:00Z");
        MorpheusRemoteIdentityFile.GeneratedCredential credential =
                MorpheusRemoteIdentityFile.create(auth, "future", MorpheusRemoteRole.READ, expiry);

        String persisted = Files.readString(auth);
        assertTrue(persisted.contains("|" + expiry));
        assertEquals(Optional.of(expiry), credential.expiresAt());
        assertEquals(Optional.of(expiry), MorpheusRemoteIdentityFile.load(auth).getFirst().expiresAt());
        assertTrue(MorpheusRemoteIdentityFile.authenticate(MorpheusRemoteIdentityFile.load(auth), credential.token()).isPresent());

        String expiredToken = "expired-token";
        Path expired = temp.resolve("expired-auth.txt");
        Files.writeString(expired,
                "expired|READ|" + MorpheusRemoteIdentityFile.sha256Hex(expiredToken)
                        + "|2000-01-01T00:00:00Z" + System.lineSeparator(),
                StandardCharsets.UTF_8);
        List<MorpheusRemoteIdentityFile.Identity> expiredIdentities = MorpheusRemoteIdentityFile.load(expired);
        assertTrue(expiredIdentities.getFirst().isExpiredAt(Instant.now()));
        assertTrue(MorpheusRemoteIdentityFile.authenticate(expiredIdentities, expiredToken).isEmpty());
    }

    @Test
    void legacyThreeFieldIdentityRemainsNonExpiring() throws Exception {
        String token = "legacy-token";
        Path auth = temp.resolve("legacy-auth.txt");
        Files.writeString(auth,
                "legacy|READ|" + MorpheusRemoteIdentityFile.sha256Hex(token) + System.lineSeparator(),
                StandardCharsets.UTF_8);

        MorpheusRemoteIdentityFile.Identity identity = MorpheusRemoteIdentityFile.load(auth).getFirst();
        assertTrue(identity.expiresAt().isEmpty());
        assertTrue(MorpheusRemoteIdentityFile.authenticate(List.of(identity), token).isPresent());
    }

    @Test
    void rotationPreservesExpiryUnlessExplicitlyReplaced() throws Exception {
        Path auth = temp.resolve("rotate-auth.txt");
        Instant firstExpiry = Instant.parse("2099-01-01T00:00:00Z");
        MorpheusRemoteIdentityFile.create(auth, "alice", MorpheusRemoteRole.ADMIN, firstExpiry);

        MorpheusRemoteIdentityFile.GeneratedCredential preserved = MorpheusRemoteIdentityFile.rotate(auth, "alice");
        assertEquals(Optional.of(firstExpiry), preserved.expiresAt());

        Instant replacement = Instant.parse("2099-06-01T00:00:00Z");
        MorpheusRemoteIdentityFile.GeneratedCredential replaced =
                MorpheusRemoteIdentityFile.rotate(auth, "alice", Optional.of(replacement));
        assertEquals(Optional.of(replacement), replaced.expiresAt());

        MorpheusRemoteIdentityFile.GeneratedCredential permanent =
                MorpheusRemoteIdentityFile.rotate(auth, "alice", Optional.empty());
        assertTrue(permanent.expiresAt().isEmpty());
        assertTrue(MorpheusRemoteIdentityFile.load(auth).getFirst().expiresAt().isEmpty());
    }

    @Test
    void rejectsPastExpiryAtCredentialCreation() {
        Path auth = temp.resolve("past-auth.txt");
        assertThrows(IllegalArgumentException.class,
                () -> MorpheusRemoteIdentityFile.create(
                        auth, "alice", MorpheusRemoteRole.READ, Instant.parse("2000-01-01T00:00:00Z")));
    }

    @Test
    void duplicatePrincipalAndMalformedHashesFailClosed() throws Exception {
        Path auth = temp.resolve("remote-auth.txt");
        MorpheusRemoteIdentityFile.create(auth, "alice", MorpheusRemoteRole.READ);
        assertThrows(IllegalArgumentException.class,
                () -> MorpheusRemoteIdentityFile.create(auth, "alice", MorpheusRemoteRole.ADMIN));

        Path malformed = temp.resolve("malformed.txt");
        Files.writeString(malformed, "bob|READ|not-a-hash\n");
        assertThrows(IllegalArgumentException.class, () -> MorpheusRemoteIdentityFile.load(malformed));

        Path malformedExpiry = temp.resolve("malformed-expiry.txt");
        Files.writeString(malformedExpiry, "bob|READ|" + "0".repeat(64) + "|tomorrow\n");
        assertThrows(IllegalArgumentException.class, () -> MorpheusRemoteIdentityFile.load(malformedExpiry));
    }

    @Test
    void rejectsOversizedIdentityFileAndAuditRead() throws Exception {
        Path auth = temp.resolve("oversized.txt");
        Files.writeString(auth, "x".repeat(MorpheusRemoteIdentityFile.MAX_FILE_BYTES + 1));

        IllegalArgumentException loadFailure = assertThrows(
                IllegalArgumentException.class,
                () -> MorpheusRemoteIdentityFile.load(auth));
        assertTrue(loadFailure.getMessage().contains("exceeds " + MorpheusRemoteIdentityFile.MAX_FILE_BYTES));

        IllegalArgumentException auditFailure = assertThrows(
                IllegalArgumentException.class,
                () -> MorpheusRemoteIdentityFile.audit(auth));
        assertTrue(auditFailure.getMessage().contains("exceeds " + MorpheusRemoteIdentityFile.MAX_FILE_BYTES));
    }

    @Test
    void rejectsMalformedUtf8InsteadOfAcceptingReplacementCharacters() throws Exception {
        Path auth = temp.resolve("invalid-utf8.txt");
        Files.write(auth, new byte[]{(byte) 0xC3, 0x28});

        assertThrows(IllegalArgumentException.class, () -> MorpheusRemoteIdentityFile.load(auth));
    }

    @Test
    void rejectsSymbolicIdentityFileWhenPlatformAllowsCreatingOne() throws Exception {
        Path target = temp.resolve("target-auth.txt");
        Files.writeString(
                target,
                "alice|READ|" + "0".repeat(64) + System.lineSeparator(),
                StandardCharsets.UTF_8);
        Path link = temp.resolve("linked-auth.txt");
        try {
            Files.createSymbolicLink(link, target.getFileName());
        } catch (Exception unavailable) {
            return;
        }

        assertThrows(IllegalArgumentException.class, () -> MorpheusRemoteIdentityFile.load(link));
    }

    @Test
    void identityEqualsComparesTokenHashByContentNotReference() {
        byte[] hashA = new byte[32];
        hashA[0] = 1;
        byte[] hashB = hashA.clone();
        assertFalse(hashA == hashB);

        MorpheusRemoteIdentityFile.Identity first =
                new MorpheusRemoteIdentityFile.Identity("alice", MorpheusRemoteRole.ADMIN, hashA, Optional.empty());
        MorpheusRemoteIdentityFile.Identity second =
                new MorpheusRemoteIdentityFile.Identity("alice", MorpheusRemoteRole.ADMIN, hashB, Optional.empty());

        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
    }

    @Test
    void identityEqualsFastPathsAndDistinguishingFields() {
        byte[] hash = new byte[32];
        hash[0] = 9;
        byte[] otherHash = new byte[32];
        otherHash[0] = 10;

        Instant expiry = Instant.parse("2099-01-01T00:00:00Z");
        Instant otherExpiry = Instant.parse("2099-06-01T00:00:00Z");

        MorpheusRemoteIdentityFile.Identity base =
                new MorpheusRemoteIdentityFile.Identity("alice", MorpheusRemoteRole.ADMIN, hash, Optional.of(expiry));

        assertEquals(base, base);
        assertFalse(base.equals("not an identity"));

        MorpheusRemoteIdentityFile.Identity differentPrincipal =
                new MorpheusRemoteIdentityFile.Identity("bob", MorpheusRemoteRole.ADMIN, hash, Optional.of(expiry));
        assertFalse(base.equals(differentPrincipal));

        MorpheusRemoteIdentityFile.Identity differentRole =
                new MorpheusRemoteIdentityFile.Identity("alice", MorpheusRemoteRole.READ, hash, Optional.of(expiry));
        assertFalse(base.equals(differentRole));

        MorpheusRemoteIdentityFile.Identity differentHash =
                new MorpheusRemoteIdentityFile.Identity("alice", MorpheusRemoteRole.ADMIN, otherHash, Optional.of(expiry));
        assertFalse(base.equals(differentHash));

        MorpheusRemoteIdentityFile.Identity differentExpiry =
                new MorpheusRemoteIdentityFile.Identity("alice", MorpheusRemoteRole.ADMIN, hash, Optional.of(otherExpiry));
        assertFalse(base.equals(differentExpiry));

        MorpheusRemoteIdentityFile.Identity sameValues =
                new MorpheusRemoteIdentityFile.Identity("alice", MorpheusRemoteRole.ADMIN, hash, Optional.of(expiry));
        assertTrue(base.equals(sameValues));
    }

    @Test
    void identityToStringContainsDecodedTokenHashBytesNotArrayReference() {
        byte[] hash = new byte[32];
        hash[0] = 1;
        hash[1] = 2;
        hash[2] = 3;

        MorpheusRemoteIdentityFile.Identity identity =
                new MorpheusRemoteIdentityFile.Identity("alice", MorpheusRemoteRole.ADMIN, hash);

        String text = identity.toString();
        assertTrue(text.contains("principal=alice"));
        assertTrue(text.contains("role=ADMIN"));
        assertTrue(text.contains("tokenHash=[1, 2, 3"));
        assertFalse(text.contains("[B@"));
    }
}
