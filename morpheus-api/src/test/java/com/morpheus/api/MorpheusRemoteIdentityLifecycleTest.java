package com.morpheus.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MorpheusRemoteIdentityLifecycleTest {
    @TempDir
    Path temp;

    @Test
    void rotateInvalidatesOldTokenAndPersistsOnlyHash() throws Exception {
        Path auth = temp.resolve("remote-auth.txt");
        var admin = MorpheusRemoteIdentityFile.create(auth, "admin", MorpheusRemoteRole.ADMIN);
        var reader = MorpheusRemoteIdentityFile.create(auth, "reader", MorpheusRemoteRole.READ);

        var rotated = MorpheusRemoteIdentityFile.rotate(auth, "reader");
        var loaded = MorpheusRemoteIdentityFile.load(auth);

        assertTrue(MorpheusRemoteIdentityFile.authenticate(loaded, rotated.token()).isPresent());
        assertTrue(MorpheusRemoteIdentityFile.authenticate(loaded, reader.token()).isEmpty());
        String persisted = Files.readString(auth);
        assertFalse(persisted.contains(admin.token()));
        assertFalse(persisted.contains(reader.token()));
        assertFalse(persisted.contains(rotated.token()));
    }

    @Test
    void cannotRevokeOrDemoteLastAdmin() {
        Path auth = temp.resolve("remote-auth.txt");
        MorpheusRemoteIdentityFile.create(auth, "admin", MorpheusRemoteRole.ADMIN);
        MorpheusRemoteIdentityFile.create(auth, "reader", MorpheusRemoteRole.READ);

        assertThrows(IllegalArgumentException.class, () -> MorpheusRemoteIdentityFile.revoke(auth, "admin"));
        assertThrows(IllegalArgumentException.class,
                () -> MorpheusRemoteIdentityFile.changeRole(auth, "admin", MorpheusRemoteRole.WRITE));
    }

    @Test
    void roleChangePreservesCredentialAndSecondAdminAllowsRevocation() {
        Path auth = temp.resolve("remote-auth.txt");
        var adminOne = MorpheusRemoteIdentityFile.create(auth, "admin-one", MorpheusRemoteRole.ADMIN);
        MorpheusRemoteIdentityFile.create(auth, "admin-two", MorpheusRemoteRole.ADMIN);
        var user = MorpheusRemoteIdentityFile.create(auth, "user", MorpheusRemoteRole.READ);

        MorpheusRemoteIdentityFile.changeRole(auth, "user", MorpheusRemoteRole.WRITE);
        var afterRole = MorpheusRemoteIdentityFile.load(auth);
        var identity = MorpheusRemoteIdentityFile.authenticate(afterRole, user.token()).orElseThrow();
        assertEquals(MorpheusRemoteRole.WRITE, identity.role());

        MorpheusRemoteIdentityFile.revoke(auth, "admin-one");
        var afterRevoke = MorpheusRemoteIdentityFile.load(auth);
        assertTrue(MorpheusRemoteIdentityFile.authenticate(afterRevoke, adminOne.token()).isEmpty());
        assertEquals(2, afterRevoke.size());
    }
}
