package com.morpheus.architecture.m19;

import com.morpheus.domain.identity.DomainIdentity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class M19LargeFixtureContractTest {

    @TempDir
    Path tempDir;

    @Test
    void sameSeedProducesTheSameLogicalSourceFixtureManifest() throws Exception {
        var first = M19LargeFixtureSupport.generateSourceFixture(tempDir.resolve("first"), 64, M19LargeFixtureSupport.SEED);
        var second = M19LargeFixtureSupport.generateSourceFixture(tempDir.resolve("second"), 64, M19LargeFixtureSupport.SEED);

        assertEquals(first, second);
        assertEquals(64, first.fileCount());
        assertTrue(first.totalBytes() >= 64L * 2_000L);
    }

    @Test
    void mutationChangesManifestWithoutChangingFileCount() throws Exception {
        Path root = tempDir.resolve("mutable");
        var before = M19LargeFixtureSupport.generateSourceFixture(root, 64, M19LargeFixtureSupport.SEED);

        M19LargeFixtureSupport.mutateDeterministically(root, 5, M19LargeFixtureSupport.SEED);
        var after = M19LargeFixtureSupport.manifest(root);

        assertEquals(before.fileCount(), after.fileCount());
        assertTrue(after.totalBytes() > before.totalBytes());
        assertNotEquals(before.sha256(), after.sha256());
    }

    @Test
    void deterministicIdentityIsStableAndRemainsAValidUuidV7() {
        DomainIdentity first = M19LargeFixtureSupport.deterministicIdentity(19, 42);
        DomainIdentity second = M19LargeFixtureSupport.deterministicIdentity(19, 42);
        DomainIdentity other = M19LargeFixtureSupport.deterministicIdentity(19, 43);

        assertEquals(first, second);
        assertNotEquals(first, other);
        assertEquals(7, first.value().version());
        assertEquals(2, first.value().variant());
    }
}
