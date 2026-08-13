package com.morpheus.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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
        assertEquals(List.of(
                        MorpheusRemoteIdentityFile.Mutation.CREATE,
                        MorpheusRemoteIdentityFile.Mutation.CREATE,
                        MorpheusRemoteIdentityFile.Mutation.ROTATE),
                MorpheusRemoteIdentityFile.audit(auth).stream().map(MorpheusRemoteIdentityFile.AuditRecord::mutation).toList());
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

    @Test
    void concurrentMutationsKeepACompleteParseableSnapshotAndAudit() throws Exception {
        Path auth = temp.resolve("concurrent-auth.txt");
        MorpheusRemoteIdentityFile.create(auth, "admin", MorpheusRemoteRole.ADMIN);
        try (var executor = Executors.newFixedThreadPool(8)) {
            for (int index = 0; index < 32; index++) {
                int identity = index;
                executor.submit(() -> MorpheusRemoteIdentityFile.create(
                        auth, "reader-" + identity, MorpheusRemoteRole.READ));
            }
            executor.shutdown();
            assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS));
        }

        assertEquals(33, MorpheusRemoteIdentityFile.load(auth).size());
        assertEquals(33, MorpheusRemoteIdentityFile.audit(auth).size());
        assertEquals(33, MorpheusRemoteIdentityFile.load(auth).stream().map(i -> i.principal()).distinct().count());
    }

    @Test
    void auditWindowCompactsBeforeItCanBlockCredentialRotation() throws Exception {
        Path auth = temp.resolve("rolling-auth.txt");
        String principal = "a".repeat(128);
        MorpheusRemoteIdentityFile.create(auth, principal, MorpheusRemoteRole.ADMIN);

        MorpheusRemoteIdentityFile.GeneratedCredential latest = null;
        for (int index = 0; index < MorpheusRemoteIdentityFile.MAX_AUDIT_RECORDS + 64; index++) {
            latest = MorpheusRemoteIdentityFile.rotate(auth, principal);
        }

        assertTrue(latest != null);
        assertTrue(MorpheusRemoteIdentityFile.authenticate(MorpheusRemoteIdentityFile.load(auth), latest.token()).isPresent());
        assertEquals(MorpheusRemoteIdentityFile.MAX_AUDIT_RECORDS, MorpheusRemoteIdentityFile.audit(auth).size());
        assertTrue(Files.size(auth) <= MorpheusRemoteIdentityFile.MAX_FILE_BYTES);
        assertEquals(MorpheusRemoteIdentityFile.Mutation.ROTATE,
                MorpheusRemoteIdentityFile.audit(auth).getLast().mutation());
    }
}
