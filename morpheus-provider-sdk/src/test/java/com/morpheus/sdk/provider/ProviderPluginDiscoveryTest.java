package com.morpheus.sdk.provider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderPluginDiscoveryTest {
    @TempDir
    Path directory;

    @Test
    void discoveryIsMetadataOnlyDeterministicAndDoesNotRequireLoadablePluginClasses() throws Exception {
        writeMetadataOnlyJar(directory.resolve("b-provider.jar"), metadata("b-plugin", 1, "1.0.0"));
        writeMetadataOnlyJar(directory.resolve("a-provider.jar"), metadata("a-plugin", 1, "1.0.0"));

        ProviderPluginDiscoveryResult result = new ProviderPluginDiscovery().discover(directory);

        assertEquals(2, result.candidates().size());
        assertEquals("a-provider.jar", result.candidates().get(0).jarPath().getFileName().toString());
        assertEquals("b-provider.jar", result.candidates().get(1).jarPath().getFileName().toString());
        assertTrue(result.candidates().stream().allMatch(ProviderPluginCandidate::compatible));
        assertEquals(2, result.compatibleCount());
    }

    @Test
    void incompatibleSdkVersionIsVisibleButNotActivable() throws Exception {
        writeMetadataOnlyJar(directory.resolve("future.jar"), metadata("future-plugin", 999, "1.0.0"));

        ProviderPluginCandidate candidate = new ProviderPluginDiscovery().discover(directory).candidates().getFirst();

        assertEquals(ProviderPluginStatus.INCOMPATIBLE, candidate.status());
        assertFalse(candidate.compatible());
        assertTrue(candidate.diagnostics().stream().anyMatch(d -> d.code().equals("SDK_API_VERSION_MISMATCH")));
    }

    @Test
    void missingOptionalPluginDirectoryIsNonFatal() {
        ProviderPluginDiscoveryResult result = new ProviderPluginDiscovery().discover(directory.resolve("missing"));

        assertTrue(result.candidates().isEmpty());
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.code().equals("PLUGIN_DIRECTORY_NOT_FOUND")));
    }

    @Test
    void activationFailureIsReturnedAsDiagnosticInsteadOfEscaping() throws Exception {
        writeMetadataOnlyJar(directory.resolve("metadata-only.jar"), metadata("metadata-only", 1, "1.0.0"));

        ProviderPluginProbeOutcome outcome = new ProviderPluginService()
                .probe(directory, "metadata-only", directory);

        assertFalse(outcome.success());
        assertTrue(outcome.diagnostics().stream()
                .anyMatch(d -> d.code().equals("PLUGIN_ACTIVATION_OR_PROBE_FAILED")));
    }

    private static Properties metadata(String pluginId, int sdkApiVersion, String minimumVersion) {
        Properties properties = new Properties();
        properties.setProperty("plugin.id", pluginId);
        properties.setProperty("provider.id", pluginId + "-provider");
        properties.setProperty("plugin.version", "1.0.0");
        properties.setProperty("sdk.apiVersion", Integer.toString(sdkApiVersion));
        properties.setProperty("morpheus.minVersion", minimumVersion);
        return properties;
    }

    private static void writeMetadataOnlyJar(Path path, Properties properties) throws Exception {
        StringWriter metadata = new StringWriter();
        properties.store(metadata, null);
        byte[] bytes = metadata.toString().getBytes(StandardCharsets.UTF_8);
        try (JarOutputStream jar = new JarOutputStream(java.nio.file.Files.newOutputStream(path))) {
            jar.putNextEntry(new JarEntry(ProviderSdk.METADATA_PATH));
            jar.write(bytes);
            jar.closeEntry();
        }
    }
}
