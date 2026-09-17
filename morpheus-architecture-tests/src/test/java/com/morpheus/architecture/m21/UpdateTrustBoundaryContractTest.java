package com.morpheus.architecture.m21;

import com.morpheus.application.product.UpdateTrustLevel;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Ratchets the boundary between update discovery and a future trusted installation workflow. */
class UpdateTrustBoundaryContractTest {

    @Test
    void discoveryCarriesProvenanceReferenceButCannotClaimPublisherVerification() throws IOException {
        Path root = repoRoot();
        String result = Files.readString(root.resolve(
                "morpheus-application/src/main/java/com/morpheus/application/product/UpdateCheckResult.java"));
        String discovery = Files.readString(root.resolve(
                "morpheus-application/src/main/java/com/morpheus/application/product/UpdateDiscoveryService.java"));
        String cli = Files.readString(root.resolve(
                "morpheus-cli/src/main/java/com/morpheus/cli/MorpheusProductCli.java"));

        assertEquals(List.of(UpdateTrustLevel.DISCOVERY_ONLY), List.of(UpdateTrustLevel.values()),
                "adding a verified trust state requires an explicit verifier and an architecture review");
        assertTrue(result.contains("Optional<URI> attestationUri"),
                "discovery results must preserve the provenance reference required by remote manifests");
        assertTrue(result.contains("UpdateTrustLevel trustLevel"),
                "the public discovery result must expose its trust classification");
        assertTrue(discovery.contains("manifest.attestationUri()"),
                "the discovery service must not discard provenance metadata after validating the manifest");
        assertTrue(discovery.contains("UpdateTrustLevel.DISCOVERY_ONLY"),
                "ordinary discovery must never claim cryptographic provenance verification");
        assertTrue(cli.contains("trustLevel="),
                "human-readable update output must expose the trust classification");
        assertTrue(cli.contains("action=none (discovery is read-only; MORPHEUS never auto-installs updates)"),
                "the CLI must keep the discovery-only boundary explicit");
    }

    private static Path repoRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("pom.xml"))
                    && Files.isDirectory(current.resolve("morpheus-application"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("MORPHEUS repository root not found");
    }
}
