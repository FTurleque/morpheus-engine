package com.morpheus.application.product;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProductIntegrityTest {
    @TempDir
    Path tempDir;

    @Test
    void productMetadataUsesBuildVersionWhenProvidedByMaven() {
        String mavenVersion = System.getProperty("morpheus.project.version");
        if (mavenVersion != null && !mavenVersion.isBlank()) {
            assertEquals(mavenVersion, ProductMetadata.version());
        }
        assertEquals("MORPHEUS", ProductMetadata.current().name());
        assertEquals("v1", ProductMetadata.current().apiVersion());
        assertEquals("stable", ProductMetadata.current().updateChannel());
    }

    @Test
    void explicitFileManifestCanReportANewerVersion() throws IOException {
        Path manifest = tempDir.resolve("stable.properties");
        Files.writeString(manifest, String.join("\n",
                "version=1.0.1",
                "channel=stable",
                "artifactUri=https://example.invalid/morpheus-1.0.1.zip",
                "sha256=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                ""));

        UpdateCheckResult result = new UpdateDiscoveryService().check(manifest.toUri());

        assertEquals(ProductMetadata.version(), result.currentVersion());
        assertEquals("1.0.1", result.availableVersion());
        assertEquals("stable", result.channel());
        assertTrue(result.updateAvailable());
    }

    @Test
    void discoveryNeverTreatsSameVersionAsAnUpdate() throws IOException {
        Path manifest = tempDir.resolve("same.properties");
        Files.writeString(manifest, String.join("\n",
                "version=" + ProductMetadata.version(),
                "channel=stable",
                "artifactUri=https://example.invalid/morpheus.zip",
                "sha256=bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb",
                ""));

        UpdateCheckResult result = new UpdateDiscoveryService().check(manifest.toUri());

        assertFalse(result.updateAvailable());
    }

    @Test
    void unsupportedManifestSchemesAreRejectedBeforeAnyIo() {
        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> new UpdateDiscoveryService().check(URI.create("ftp://example.invalid/update.properties")));
        assertTrue(failure.getMessage().contains("unsupported update manifest scheme"));
    }

    @Test
    void sha256IsValidated() {
        assertThrows(IllegalArgumentException.class, () -> new UpdateManifest(
                "1.0.1", "stable", URI.create("https://example.invalid/morpheus.zip"), "not-a-sha"));
    }

    @Test
    void semanticComparisonTreatsReleaseAsNewerThanPrerelease() {
        assertTrue(UpdateDiscoveryService.compareVersions("1.1.0", "1.0.9") > 0);
        assertTrue(UpdateDiscoveryService.compareVersions("1.0.0", "1.0.0-rc1") > 0);
        assertEquals(0, UpdateDiscoveryService.compareVersions("1.0", "1.0.0"));
    }
}
