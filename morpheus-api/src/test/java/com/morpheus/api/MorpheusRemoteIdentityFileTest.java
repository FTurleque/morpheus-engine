package com.morpheus.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
}
