package com.morpheus.application.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExternalJarIntegrityTest {
    @TempDir
    Path temp;

    @Test
    void acceptsExactDigestAndRejectsSubstitution() throws Exception {
        Path jar = temp.resolve("plugin.jar");
        Files.writeString(jar, "trusted-content");
        String trusted = ExternalJarIntegrity.sha256(jar);
        assertEquals(jar.toAbsolutePath().normalize(), ExternalJarIntegrity.verifySha256(jar, trusted));

        Files.writeString(jar, "substituted-content");
        assertThrows(IllegalArgumentException.class, () -> ExternalJarIntegrity.verifySha256(jar, trusted));
    }

    @Test
    void stagesVerifiedCopyThatIsIndependentFromOriginalPathAfterVerification() throws Exception {
        Path jar = temp.resolve("plugin.jar");
        Files.writeString(jar, "trusted-content");
        String trusted = ExternalJarIntegrity.sha256(jar);

        Path staged = ExternalJarIntegrity.stageVerifiedCopy(jar, trusted);
        try {
            assertNotEquals(jar.toAbsolutePath().normalize(), staged);
            assertEquals(trusted, ExternalJarIntegrity.sha256(staged));
            assertEquals("trusted-content", Files.readString(staged));

            Files.writeString(jar, "substituted-after-staging");
            assertNotEquals(trusted, ExternalJarIntegrity.sha256(jar));
            assertEquals(trusted, ExternalJarIntegrity.sha256(staged));
            assertEquals("trusted-content", Files.readString(staged));
        } finally {
            Files.deleteIfExists(staged);
        }
    }

    @Test
    void rejectsMalformedPin() {
        assertThrows(IllegalArgumentException.class, () -> ExternalJarIntegrity.normalizeSha256("abc"));
    }

    @Test
    void rejectsMissingAndSymbolicFilesBeforeHashing() throws Exception {
        Path missing = temp.resolve("missing.jar");
        assertThrows(IllegalArgumentException.class, () -> ExternalJarIntegrity.verifySha256(missing, "0".repeat(64)));

        Path target = temp.resolve("target.jar");
        Files.writeString(target, "trusted-content");
        Path link = temp.resolve("linked.jar");
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | java.io.IOException failure) {
            return;
        }
        assertThrows(
                IllegalArgumentException.class,
                () -> ExternalJarIntegrity.verifySha256(link, ExternalJarIntegrity.sha256(target)));
    }
}
