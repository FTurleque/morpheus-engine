package com.morpheus.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

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

        List<MorpheusRemoteIdentityFile.Identity> identities = MorpheusRemoteIdentityFile.load(auth);
        assertEquals("alice", MorpheusRemoteIdentityFile.authenticate(identities, credential.token()).orElseThrow().principal());
        assertTrue(MorpheusRemoteIdentityFile.authenticate(identities, "wrong-token").isEmpty());
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
}
