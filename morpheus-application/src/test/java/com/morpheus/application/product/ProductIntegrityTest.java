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
        String availableVersion = nextPatchVersion(ProductMetadata.version());
        Path manifest = tempDir.resolve("stable.properties");
        Files.writeString(manifest, String.join("\n",
                "version=" + availableVersion,
                "channel=stable",
                "artifactUri=https://example.invalid/morpheus-" + availableVersion + ".zip",
                "sha256=aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                ""));

        UpdateCheckResult result = new UpdateDiscoveryService().check(manifest.toUri());

        assertEquals(ProductMetadata.version(), result.currentVersion());
        assertEquals(availableVersion, result.availableVersion());
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
    void oversizedManifestIsRejectedBeforeParsing() throws IOException {
        Path manifest = tempDir.resolve("oversized.properties");
        Files.writeString(manifest, "x".repeat(UpdateDiscoveryService.MAX_MANIFEST_BYTES + 1));

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> new UpdateDiscoveryService().check(manifest.toUri()));
        assertTrue(failure.getMessage().contains("exceeds " + UpdateDiscoveryService.MAX_MANIFEST_BYTES));
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
    void semanticComparisonOrdersReleasePrereleaseAndBuildMetadata() {
        assertTrue(UpdateDiscoveryService.compareVersions("1.1.0", "1.0.9") > 0);
        assertTrue(UpdateDiscoveryService.compareVersions("1.0.0", "1.0.0-rc.2") > 0);
        assertTrue(UpdateDiscoveryService.compareVersions("1.0.0-rc.2", "1.0.0-rc.1") > 0);
        assertTrue(UpdateDiscoveryService.compareVersions("1.0.0-rc.10", "1.0.0-rc.2") > 0);
        assertTrue(UpdateDiscoveryService.compareVersions("1.0.0-beta", "1.0.0-2") > 0);
        assertEquals(0, UpdateDiscoveryService.compareVersions("1.0", "1.0.0"));
        assertEquals(0, UpdateDiscoveryService.compareVersions("1.0.0+build.7", "1.0.0+build.8"));
    }

    private String nextPatchVersion(String version) {
        String core = version.split("[+-]", 2)[0];
        String[] parts = core.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Expected semantic product version, got " + version);
        }
        int patch = Integer.parseInt(parts[2]);
        return parts[0] + "." + parts[1] + "." + (patch + 1);
    }
}
