package com.morpheus.sdk.provider;

import com.morpheus.application.security.ExternalJarIntegrity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

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
    @SuppressWarnings("removal") // Intentionally proves the deprecated unpinned entry point remains fail-closed until removal.
    void unpinnedActivationFailsClosed() throws Exception {
        Path jar = directory.resolve("unpinned.jar");
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
        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> new ProviderPluginActivator().activate(candidate));
        assertTrue(failure.getMessage().contains("trusted SHA-256 pin"));
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
}
