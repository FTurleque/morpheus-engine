package com.morpheus.application.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void aStagedCopyIsNamedWithoutAnyPartOfThePin() throws Exception {
        Path jar = temp.resolve("plugin.jar");
        Files.writeString(jar, "trusted-content");
        String trusted = ExternalJarIntegrity.sha256(jar);

        Path staged = ExternalJarIntegrity.stageVerifiedCopy(jar, trusted);
        try {
            String name = staged.getFileName().toString();
            assertTrue(name.startsWith("morpheus-trusted-plugin-"), name);
            assertFalse(name.contains(trusted.substring(0, 12)), name);
        } finally {
            Files.deleteIfExists(staged);
        }
    }

    /**
     * The explicit deletion by the caller stays the normal path. A JVM that exits before it runs -- normal exit
     * included -- must still not leave the verified copy behind; only a crash or a kill can.
     */
    @Test
    void aStagedCopyLeftOpenIsRemovedWhenItsJvmExits() throws Exception {
        Path jar = temp.resolve("plugin.jar");
        Files.writeString(jar, "trusted-content");
        String trusted = ExternalJarIntegrity.sha256(jar);

        String classPath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        Process child = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-Djava.io.tmpdir=" + System.getProperty("java.io.tmpdir"),
                "-cp", classPath,
                StagedCopyLeftOpen.class.getName(),
                jar.toString(),
                trusted)
                .redirectErrorStream(true)
                .start();
        String output = new String(child.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        assertTrue(child.waitFor(30, TimeUnit.SECONDS), "the child JVM must exit");
        assertEquals(0, child.exitValue(), output);

        Path staged = Path.of(output.lines().reduce((first, last) -> last).orElseThrow());
        assertTrue(staged.getFileName().toString().startsWith("morpheus-trusted-plugin-"), output);
        assertFalse(Files.exists(staged), "the staged copy must be deleted when its JVM exits: " + staged);
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
