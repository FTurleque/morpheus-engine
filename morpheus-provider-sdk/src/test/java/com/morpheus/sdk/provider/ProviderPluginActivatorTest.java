package com.morpheus.sdk.provider;

import com.morpheus.application.security.ExternalJarIntegrity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderPluginActivatorTest {
    @TempDir
    Path directory;

    @Test
    void rejectsRuntimeMetadataThatDoesNotMatchDeclarativeManifest() throws Exception {
        Path jar = directory.resolve("mismatch.jar");
        Properties properties = new Properties();
        properties.setProperty("plugin.id", "manifest-plugin");
        properties.setProperty("provider.id", "manifest-provider");
        properties.setProperty("plugin.version", "1.0.0");
        properties.setProperty("sdk.apiVersion", "1");
        properties.setProperty("morpheus.minVersion", "1.0.0");

        StringWriter metadata = new StringWriter();
        properties.store(metadata, null);
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            output.putNextEntry(new JarEntry(ProviderSdk.METADATA_PATH));
            output.write(metadata.toString().getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
            output.putNextEntry(new JarEntry("META-INF/services/" + MorpheusProviderPlugin.class.getName()));
            output.write((TestMismatchedProviderPlugin.class.getName() + "\n").getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }

        ProviderPluginCandidate candidate = new ProviderPluginDiscovery().discover(directory).candidates().getFirst();
        assertTrue(candidate.compatible());

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> new ProviderPluginActivator().activate(candidate, ExternalJarIntegrity.sha256(jar)));
        assertTrue(failure.getMessage().contains("activation failed"));
        assertTrue(failure.getCause().getMessage().contains("runtime metadata does not match"));
    }

    @Test
    void rejectsDigestMismatchBeforeCreatingPluginClassLoader() throws Exception {
        Path jar = directory.resolve("pinned.jar");
        Properties properties = new Properties();
        properties.setProperty("plugin.id", "manifest-plugin");
        properties.setProperty("provider.id", "manifest-provider");
        properties.setProperty("plugin.version", "1.0.0");
        properties.setProperty("sdk.apiVersion", "1");
        properties.setProperty("morpheus.minVersion", "1.0.0");

        StringWriter metadata = new StringWriter();
        properties.store(metadata, null);
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            output.putNextEntry(new JarEntry(ProviderSdk.METADATA_PATH));
            output.write(metadata.toString().getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }

        ProviderPluginCandidate candidate = new ProviderPluginDiscovery().discover(directory).candidates().getFirst();
        assertTrue(candidate.compatible());
        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> new ProviderPluginActivator().activate(candidate, "0".repeat(64)));
        assertTrue(failure.getMessage().contains("integrity mismatch"));
    }

    /**
     * The pin is what makes third-party code identifiable, so its absence is refused by name -- before the JAR is
     * copied, and therefore before a classloader can see it -- rather than surfacing as a null dereference.
     */
    @Test
    void aMissingPinIsRefusedByNameBeforeAnyCopyIsStaged() throws Exception {
        ProviderPluginCandidate candidate = compatibleCandidate();
        List<Path> stagedBefore = stagedPluginCopies();

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> new ProviderPluginActivator().activate(candidate, null));

        assertEquals("provider plugin activation requires a trusted SHA-256 pin", failure.getMessage());
        assertEquals(stagedBefore, stagedPluginCopies());
    }

    @Test
    void aBlankPinIsRefusedByNameBeforeAnyCopyIsStaged() throws Exception {
        ProviderPluginCandidate candidate = compatibleCandidate();
        List<Path> stagedBefore = stagedPluginCopies();

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> new ProviderPluginActivator().activate(candidate, " \t "));

        assertEquals("provider plugin activation requires a trusted SHA-256 pin", failure.getMessage());
        assertEquals(stagedBefore, stagedPluginCopies());
    }

    @Test
    void aPinOfTheWrongShapeIsRefusedWithTheExpectedShapeBeforeAnyCopyIsStaged() throws Exception {
        ProviderPluginCandidate candidate = compatibleCandidate();
        List<Path> stagedBefore = stagedPluginCopies();

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> new ProviderPluginActivator().activate(candidate, "a".repeat(63)));

        assertEquals("expected SHA-256 must contain exactly 64 hexadecimal characters", failure.getMessage());
        assertEquals(stagedBefore, stagedPluginCopies());
    }

    private ProviderPluginCandidate compatibleCandidate() throws IOException {
        Path jar = directory.resolve("pin-refusal.jar");
        Properties properties = new Properties();
        properties.setProperty("plugin.id", "manifest-plugin");
        properties.setProperty("provider.id", "manifest-provider");
        properties.setProperty("plugin.version", "1.0.0");
        properties.setProperty("sdk.apiVersion", "1");
        properties.setProperty("morpheus.minVersion", "1.0.0");

        StringWriter metadata = new StringWriter();
        properties.store(metadata, null);
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            output.putNextEntry(new JarEntry(ProviderSdk.METADATA_PATH));
            output.write(metadata.toString().getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
            output.putNextEntry(new JarEntry("META-INF/services/" + MorpheusProviderPlugin.class.getName()));
            output.write((TestMismatchedProviderPlugin.class.getName() + "\n").getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        ProviderPluginCandidate candidate = new ProviderPluginDiscovery().discover(directory).candidates().getFirst();
        assertTrue(candidate.compatible(), candidate.diagnostics().toString());
        return candidate;
    }

    private static List<Path> stagedPluginCopies() throws IOException {
        try (Stream<Path> entries = Files.list(Path.of(System.getProperty("java.io.tmpdir")))) {
            return entries
                    .filter(path -> path.getFileName().toString().startsWith("morpheus-trusted-plugin-"))
                    .sorted()
                    .toList();
        }
    }
}
