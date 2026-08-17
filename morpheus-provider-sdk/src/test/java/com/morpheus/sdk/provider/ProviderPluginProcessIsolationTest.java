package com.morpheus.sdk.provider;

import com.morpheus.application.security.ExternalJarIntegrity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderPluginProcessIsolationTest {
    @TempDir
    Path directory;

    @Test
    void nonCooperativeProbeIsTerminatedWithoutBlockingMorpheus() throws Exception {
        Path jar = blockingPluginJar();
        ProviderPluginService service = new ProviderPluginService(
                new ProviderPluginDiscovery(),
                new ProviderPluginActivator(),
                new ProviderPluginProbeProcess(Duration.ofSeconds(3)));

        assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
            ProviderPluginProbeOutcome outcome = service.probe(
                    directory,
                    "blocking-plugin",
                    directory,
                    ExternalJarIntegrity.sha256(jar));

            assertFalse(outcome.success());
            assertTrue(outcome.diagnostics().stream()
                    .anyMatch(diagnostic -> diagnostic.code().equals("PLUGIN_PROBE_TIMEOUT")));
        });
    }

    private Path blockingPluginJar() throws Exception {
        Path jar = directory.resolve("blocking.jar");
        Properties properties = new Properties();
        properties.setProperty("plugin.id", "blocking-plugin");
        properties.setProperty("provider.id", "blocking-provider");
        properties.setProperty("plugin.version", "1.0.0");
        properties.setProperty("sdk.apiVersion", ProviderSdk.API_VERSION);
        properties.setProperty("morpheus.minVersion", "1.0.0");

        StringWriter metadata = new StringWriter();
        properties.store(metadata, null);
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            output.putNextEntry(new JarEntry(ProviderSdk.METADATA_PATH));
            output.write(metadata.toString().getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
            output.putNextEntry(new JarEntry("META-INF/services/" + MorpheusProviderPlugin.class.getName()));
            output.write((TestBlockingProviderPlugin.class.getName() + "\n").getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return jar;
    }
}
