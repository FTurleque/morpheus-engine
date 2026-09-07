package com.morpheus.architecture.d2;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DependencyCheckWorkflowContractTest {

    @Test
    void trustedUpdateRemovesAnUnavailableNvdSecretFromTheProcessEnvironment() throws IOException {
        String security = Files.readString(repoRoot().resolve(".github/workflows/security.yml"));

        int updateStart = security.indexOf("- name: Update Dependency-Check vulnerability database (trusted events)");
        int saveStart = security.indexOf("- name: Save trusted Dependency-Check database");
        assertTrue(updateStart >= 0 && saveStart > updateStart,
                "security workflow must keep a distinct trusted Dependency-Check update step");

        String trustedUpdate = security.substring(updateStart, saveStart);
        assertTrue(trustedUpdate.contains("NVD_API_KEY: ${{ secrets.NVD_API_KEY }}"),
                "trusted updates must still consume the configured GitHub secret when available");
        assertTrue(trustedUpdate.contains("if [[ -n \"${NVD_API_KEY:-}\" ]]"),
                "the secret must be tested before telling Dependency-Check to consume it");
        assertTrue(trustedUpdate.contains("-DnvdApiKeyEnvironmentVariable=NVD_API_KEY"),
                "a non-empty key must be passed by environment-variable name, never by value");
        assertTrue(trustedUpdate.contains("unset NVD_API_KEY"),
                "an unavailable GitHub secret is materialized as an empty variable and must be removed before Dependency-Check 13 runs");
        assertFalse(trustedUpdate.contains("-DnvdApiKey=${NVD_API_KEY}"),
                "the NVD secret value must never appear on the Maven command line");
    }

    private static Path repoRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isRegularFile(current.resolve("pom.xml")) && Files.isDirectory(current.resolve("distribution"))) {
            return current;
        }
        Path parent = current.getParent();
        if (parent != null && Files.isRegularFile(parent.resolve("pom.xml"))
                && Files.isDirectory(parent.resolve("distribution"))) {
            return parent;
        }
        throw new IllegalStateException("MORPHEUS repository root not found from " + current);
    }
}
