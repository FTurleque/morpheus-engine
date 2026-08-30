package com.morpheus.sdk.provider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderPluginDiscoveryReplacementTest {

    @TempDir
    Path directory;

    @Test
    void candidateReplacedBetweenSelectionAndMetadataReadIsRejected() throws Exception {
        Path candidate = directory.resolve("provider.jar");
        Path replacement = directory.resolve("replacement.tmp");
        writeMetadataOnlyJar(candidate, metadata("original"));
        writeMetadataOnlyJar(replacement, metadata("replacement-with-different-size"));

        ProviderPluginDiscovery discovery = new ProviderPluginDiscovery(jar ->
                Files.move(replacement, jar, StandardCopyOption.REPLACE_EXISTING));

        ProviderPluginDiscoveryResult result = discovery.discover(directory);

        assertEquals(1, result.candidates().size());
        ProviderPluginCandidate discovered = result.candidates().getFirst();
        assertEquals(ProviderPluginStatus.INVALID, discovered.status());
        assertTrue(discovered.diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.code().equals("PLUGIN_JAR_CHANGED_DURING_SCAN")));
    }

    private static Properties metadata(String pluginId) {
        Properties properties = new Properties();
        properties.setProperty("plugin.id", pluginId);
        properties.setProperty("provider.id", pluginId + "-provider");
        properties.setProperty("plugin.version", "1.0.0");
        properties.setProperty("sdk.apiVersion", "1");
        properties.setProperty("morpheus.minVersion", "1.0.0");
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
