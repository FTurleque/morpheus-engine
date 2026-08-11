package com.morpheus.application.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
    void rejectsMalformedPin() {
        assertThrows(IllegalArgumentException.class, () -> ExternalJarIntegrity.normalizeSha256("abc"));
    }
}
