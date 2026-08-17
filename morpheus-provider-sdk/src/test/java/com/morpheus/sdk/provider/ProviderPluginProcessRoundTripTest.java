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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderPluginProcessRoundTripTest {
    @TempDir
    Path directory;

    @Test
    void successfulProbeCrossesIsolatedJvmBoundary() throws Exception {
        Path jar = isolatedPluginJar();
        ProviderPluginService service = new ProviderPluginService(
                new ProviderPluginDiscovery(),
                new ProviderPluginActivator(),
                new ProviderPluginProbeProcess(Duration.ofSeconds(10)));

        ProviderPluginProbeOutcome outcome = service.probe(
                directory,
                "isolated-plugin",
                directory,
                ExternalJarIntegrity.sha256(jar));

        assertTrue(outcome.success(), () -> outcome.diagnostics().toString());
        assertEquals("isolated-provider", outcome.probe().orElseThrow().providerId().value());
        assertEquals("fixture", outcome.probe().orElseThrow().schema().orElseThrow());
    }

    private Path isolatedPluginJar() throws Exception {
        Path jar = directory.resolve("isolated.jar");
        Properties properties = new Properties();
        properties.setProperty("plugin.id", "isolated-plugin");
        properties.setProperty("provider.id", "isolated-provider");
        properties.setProperty("plugin.version", "1.0.0");
        properties.setProperty("sdk.apiVersion", Integer.toString(ProviderSdk.API_VERSION));
        properties.setProperty("morpheus.minVersion", "1.0.0");

        StringWriter metadata = new StringWriter();
        properties.store(metadata, null);
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar))) {
            output.putNextEntry(new JarEntry(ProviderSdk.METADATA_PATH));
            output.write(metadata.toString().getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
            output.putNextEntry(new JarEntry("META-INF/services/" + MorpheusProviderPlugin.class.getName()));
            output.write((TestIsolatedProviderPlugin.class.getName() + "\n").getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return jar;
    }
}
