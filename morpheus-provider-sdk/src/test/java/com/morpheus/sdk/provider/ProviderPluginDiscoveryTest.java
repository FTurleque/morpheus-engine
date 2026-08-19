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
    void discoveryRetainsOnlyDeterministicBoundedJarSetBeforeInspection() throws Exception {
        for (int index = 0; index <= ProviderSdk.MAX_PLUGIN_JARS; index++) {
            Files.write(directory.resolve("plugin-%03d.jar".formatted(index)), new byte[0]);
        }

        ProviderPluginDiscoveryResult result = new ProviderPluginDiscovery().discover(directory);

        assertEquals(ProviderSdk.MAX_PLUGIN_JARS, result.candidates().size());
        assertEquals("plugin-000.jar", result.candidates().getFirst().jarPath().getFileName().toString());
        assertEquals("plugin-255.jar", result.candidates().getLast().jarPath().getFileName().toString());
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.code().equals("PLUGIN_SCAN_LIMIT_REACHED")));
    }

    @Test
    void symbolicJarCannotEscapePluginDirectoryDuringDiscovery() throws Exception {
        Path plugins = Files.createDirectory(directory.resolve("plugins"));
        Path external = directory.resolve("external.jar");
        writeMetadataOnlyJar(external, metadata("external-plugin", 1, "1.0.0"));
        Path link = plugins.resolve("linked.jar");
        if (!createSymlink(link, external)) return;

        ProviderPluginDiscoveryResult result = new ProviderPluginDiscovery().discover(plugins);
        assertTrue(result.candidates().isEmpty());
    }

    @Test
    void symbolicPluginDirectoryIsRejected() throws Exception {
        Path real = Files.createDirectory(directory.resolve("real-plugins"));
        writeMetadataOnlyJar(real.resolve("provider.jar"), metadata("provider", 1, "1.0.0"));
        Path link = directory.resolve("linked-plugins");
        if (!createSymlink(link, real)) return;

        ProviderPluginDiscoveryResult result = new ProviderPluginDiscovery().discover(link);
        assertTrue(result.candidates().isEmpty());
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.code().equals("PLUGIN_PATH_NOT_DIRECTORY")));
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
        Path jar = directory.resolve("metadata-only.jar");
        writeMetadataOnlyJar(jar, metadata("metadata-only", 1, "1.0.0"));

        ProviderPluginProbeOutcome outcome = new ProviderPluginService()
                .probe(directory, "metadata-only", directory, ExternalJarIntegrity.sha256(jar));

        assertFalse(outcome.success());
        assertTrue(outcome.diagnostics().stream()
                .anyMatch(d -> d.code().equals("PLUGIN_ACTIVATION_OR_PROBE_FAILED")));
    }

    @Test
    void duplicatePluginIdIsRejectedInsteadOfSelectingFirstJarSilently() throws Exception {
        writeMetadataOnlyJar(directory.resolve("a-duplicate.jar"), metadata("duplicate-plugin", 1, "1.0.0"));
        writeMetadataOnlyJar(directory.resolve("b-duplicate.jar"), metadata("duplicate-plugin", 1, "1.0.0"));

        ProviderPluginProbeOutcome outcome = new ProviderPluginService()
                .probe(directory, "duplicate-plugin", directory, "0".repeat(64));

        assertFalse(outcome.success());
        assertTrue(outcome.diagnostics().stream().anyMatch(d -> d.code().equals("PLUGIN_ID_AMBIGUOUS")));
    }

    private static boolean createSymlink(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target);
            return true;
        } catch (UnsupportedOperationException | java.io.IOException | SecurityException unsupported) {
            return false;
        }
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
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(path))) {
            jar.putNextEntry(new JarEntry(ProviderSdk.METADATA_PATH));
            jar.write(bytes);
            jar.closeEntry();
        }
    }
}
