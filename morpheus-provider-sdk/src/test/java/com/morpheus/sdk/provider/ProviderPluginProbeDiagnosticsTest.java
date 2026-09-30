package com.morpheus.sdk.provider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * A probe outcome carries the directory-level diagnostics of the discovery it was selected from, whichever branch
 * produced it: a truncated scan matters most when the requested plugin was found, because the operator can still act.
 */
class ProviderPluginProbeDiagnosticsTest {
    private static final String WRONG_PIN = "0".repeat(64);

    @TempDir
    Path temp;

    private Path directory;

    /**
     * The real path, so that the assertions below compare exactly the diagnostics under test: a temporary directory
     * reached through a link or a short name is a legitimate case for discovery, not one these tests exercise.
     */
    @BeforeEach
    void resolveDirectory() throws Exception {
        directory = temp.toRealPath();
    }

    @Test
    void aTruncatedScanIsReportedEvenWhenTheRequestedPluginWasFound() throws Exception {
        writeMetadataOnlyJar(directory.resolve("a-target.jar"), metadata("target-plugin", 999));
        fillBeyondScanLimit();

        ProviderPluginProbeOutcome outcome = new ProviderPluginService()
                .probe(directory, "target-plugin", directory, WRONG_PIN);

        assertFalse(outcome.success());
        assertEquals(List.of("PLUGIN_SCAN_LIMIT_REACHED", "SDK_API_VERSION_MISMATCH"), codes(outcome));
    }

    @Test
    void anAmbiguousPluginIdKeepsTheDirectoryAndCandidateDiagnostics() throws Exception {
        writeMetadataOnlyJar(directory.resolve("a-duplicate.jar"), metadata("duplicate-plugin", 999));
        writeMetadataOnlyJar(directory.resolve("b-duplicate.jar"), metadata("duplicate-plugin", 999));
        fillBeyondScanLimit();

        ProviderPluginProbeOutcome outcome = new ProviderPluginService()
                .probe(directory, "duplicate-plugin", directory, WRONG_PIN);

        assertFalse(outcome.success());
        assertEquals(
                List.of(
                        "PLUGIN_SCAN_LIMIT_REACHED",
                        "SDK_API_VERSION_MISMATCH",
                        "SDK_API_VERSION_MISMATCH",
                        "PLUGIN_ID_AMBIGUOUS"),
                codes(outcome));
    }

    @Test
    void theDiagnosticOrderIsStableAcrossOutcomes() throws Exception {
        writeMetadataOnlyJar(directory.resolve("a-compatible.jar"), metadata("compatible-plugin", 1));
        writeMetadataOnlyJar(directory.resolve("a-incompatible.jar"), metadata("incompatible-plugin", 999));
        fillBeyondScanLimit();
        ProviderPluginService service = new ProviderPluginService();

        assertEquals(
                List.of("PLUGIN_SCAN_LIMIT_REACHED", "PLUGIN_INTEGRITY_VERIFICATION_FAILED"),
                codes(service.probe(directory, "compatible-plugin", directory, WRONG_PIN)));
        assertEquals(
                List.of("PLUGIN_SCAN_LIMIT_REACHED", "SDK_API_VERSION_MISMATCH"),
                codes(service.probe(directory, "incompatible-plugin", directory, WRONG_PIN)));
        assertEquals(
                List.of("PLUGIN_SCAN_LIMIT_REACHED", "PLUGIN_NOT_FOUND"),
                codes(service.probe(directory, "absent-plugin", directory, WRONG_PIN)));
    }

    private void fillBeyondScanLimit() throws Exception {
        for (int index = 0; index < ProviderSdk.MAX_PLUGIN_JARS; index++) {
            Files.write(directory.resolve("z-filler-%03d.jar".formatted(index)), new byte[0]);
        }
    }

    private static List<String> codes(ProviderPluginProbeOutcome outcome) {
        return outcome.diagnostics().stream().map(ProviderPluginDiagnostic::code).toList();
    }

    private static Properties metadata(String pluginId, int sdkApiVersion) {
        Properties properties = new Properties();
        properties.setProperty("plugin.id", pluginId);
        properties.setProperty("provider.id", pluginId + "-provider");
        properties.setProperty("plugin.version", "1.0.0");
        properties.setProperty("sdk.apiVersion", Integer.toString(sdkApiVersion));
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
