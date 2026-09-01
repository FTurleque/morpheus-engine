package com.morpheus.application.product;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpdateManifestTrustTest {
    private static final String SHA256 = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    @TempDir
    Path tempDir;

    @Test
    void remoteManifestRejectsFileArtifactEvenWhenAttestationIsHttps() {
        UpdateManifest manifest = new UpdateManifest(
                "1.2.2",
                "stable",
                tempDir.resolve("morpheus.zip").toUri(),
                SHA256,
                java.util.Optional.of(URI.create("https://github.com/example/morpheus/attestations/123")));

        URI remoteManifestUri = URI.create("https://updates.example.invalid/stable.properties");
        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> manifest.requireRemoteTrust(remoteManifestUri));

        assertTrue(failure.getMessage().contains("artifactUri must use https"));
    }

    @Test
    void relativeArtifactAndAttestationUrisAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new UpdateManifest(
                "1.2.2", "stable", URI.create("morpheus.zip"), SHA256));

        URI validArtifactUri = URI.create("https://downloads.example.invalid/morpheus.zip");
        java.util.Optional<URI> relativeAttestationUri = java.util.Optional.of(URI.create("attestation.jsonl"));
        assertThrows(IllegalArgumentException.class, () -> new UpdateManifest(
                "1.2.2",
                "stable",
                validArtifactUri,
                SHA256,
                relativeAttestationUri));
    }

    @Test
    void fileManifestParsesOptionalAttestationAndRejectsMalformedUri() throws IOException {
        Path valid = tempDir.resolve("valid.properties");
        Files.writeString(valid, String.join("\n",
                "version=1.2.2",
                "channel=stable",
                "artifactUri=https://downloads.example.invalid/morpheus.zip",
                "sha256=" + SHA256,
                "attestationUri=https://github.com/example/morpheus/attestations/123",
                ""));

        UpdateManifest manifest = new UpdateDiscoveryService().readManifest(valid.toUri());
        assertEquals(
                URI.create("https://github.com/example/morpheus/attestations/123"),
                manifest.attestationUri().orElseThrow());

        Path invalid = tempDir.resolve("invalid.properties");
        Files.writeString(invalid, String.join("\n",
                "version=1.2.2",
                "channel=stable",
                "artifactUri=https://downloads.example.invalid/morpheus.zip",
                "sha256=" + SHA256,
                "attestationUri=::not-a-uri::",
                ""));

        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> new UpdateDiscoveryService().readManifest(invalid.toUri()));
        assertTrue(failure.getMessage().contains("invalid update manifest URI property: attestationUri"));
    }
}
