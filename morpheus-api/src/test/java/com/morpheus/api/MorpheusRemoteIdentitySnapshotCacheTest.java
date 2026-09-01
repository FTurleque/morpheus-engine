package com.morpheus.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MorpheusRemoteIdentitySnapshotCacheTest {
    @TempDir
    Path temp;

    @Test
    void repeatedAuthenticationReusesSecureSnapshotUntilFileChanges() {
        Path authFile = temp.resolve("remote-auth.txt");
        var credential = MorpheusRemoteIdentityFile.create(authFile, "reader", MorpheusRemoteRole.READ);
        AtomicInteger loads = new AtomicInteger();
        AtomicLong now = new AtomicLong(1_000_000L);
        MorpheusRemoteIdentitySnapshotCache cache = new MorpheusRemoteIdentitySnapshotCache(
                authFile,
                Duration.ofHours(1),
                path -> {
                    loads.incrementAndGet();
                    return MorpheusRemoteIdentityFile.load(path);
                },
                now::get);

        assertTrue(cache.authenticate(credential.token()).isPresent());
        assertFalse(cache.authenticate("invalid-token").isPresent());
        assertFalse(cache.authenticate("another-invalid-token").isPresent());
        assertEquals(1, loads.get(), "unchanged auth store should not repeat ACL-aware reloads");

        MorpheusRemoteIdentityFile.revoke(authFile, "reader");
        assertFalse(cache.authenticate(credential.token()).isPresent());
        assertEquals(2, loads.get(), "atomic identity mutation must invalidate the snapshot immediately");
    }

    @Test
    void unchangedSnapshotIsPeriodicallyRevalidatedFailClosed() {
        Path authFile = temp.resolve("revalidate-auth.txt");
        var credential = MorpheusRemoteIdentityFile.create(authFile, "reader", MorpheusRemoteRole.READ);
        AtomicInteger loads = new AtomicInteger();
        AtomicLong now = new AtomicLong();
        MorpheusRemoteIdentitySnapshotCache cache = new MorpheusRemoteIdentitySnapshotCache(
                authFile,
                Duration.ofNanos(10),
                path -> {
                    loads.incrementAndGet();
                    return MorpheusRemoteIdentityFile.load(path);
                },
                now::get);

        assertTrue(cache.authenticate(credential.token()).isPresent());
        now.set(9);
        assertTrue(cache.authenticate(credential.token()).isPresent());
        assertEquals(1, loads.get());

        now.set(10);
        assertTrue(cache.authenticate(credential.token()).isPresent());
        assertEquals(2, loads.get());
    }
}
