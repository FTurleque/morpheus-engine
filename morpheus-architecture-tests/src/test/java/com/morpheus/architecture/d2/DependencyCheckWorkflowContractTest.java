package com.morpheus.architecture.d2;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DependencyCheckWorkflowContractTest {

    @Test
    void trustedUpdateUsesApiKeyWhenPresentAndFreshCacheWhenUpstreamAnonymousUpdateIsBroken() throws IOException {
        String security = Files.readString(repoRoot().resolve(".github/workflows/security.yml"));

        int updateStart = security.indexOf("- name: Update Dependency-Check vulnerability database (trusted events)");
        int saveStart = security.indexOf("- name: Save trusted Dependency-Check database");
        int scanStart = security.indexOf("- name: Run OWASP Dependency-Check scan");
        assertTrue(updateStart >= 0 && saveStart > updateStart && scanStart > saveStart,
                "security workflow must keep trusted update, cache save, then aggregate scan in that order");

        String trustedUpdate = security.substring(updateStart, saveStart);
        assertTrue(trustedUpdate.contains("id: dependency-check-update"),
                "trusted update step must expose whether a database refresh actually occurred");
        assertTrue(trustedUpdate.contains("NVD_API_KEY: ${{ secrets.NVD_API_KEY }}"),
                "trusted updates must still consume the configured GitHub secret when available");
        assertTrue(trustedUpdate.contains("if [[ -n \"${NVD_API_KEY:-}\" ]]"),
                "the secret must be tested before Dependency-Check consumes it");
        assertTrue(trustedUpdate.contains("-DnvdApiKeyEnvironmentVariable=NVD_API_KEY"),
                "a non-empty key must be passed by environment-variable name, never by value");
        assertTrue(trustedUpdate.contains("org.owasp:dependency-check-maven:13.0.0:update-only"),
                "trusted updates with a key must continue to refresh the NVD database");
        assertFalse(trustedUpdate.contains("-DnvdApiKey=${NVD_API_KEY}"),
                "the NVD secret value must never appear on the Maven command line");

        assertTrue(trustedUpdate.contains("upstream bug #8715"),
                "the temporary anonymous-update workaround must remain explicitly tied to the upstream defect");
        assertTrue(trustedUpdate.contains("upstream fix #8716"),
                "the workflow must document the upstream fix that allows removal of this workaround");
        assertTrue(trustedUpdate.contains("No trusted Dependency-Check database is available"),
                "missing cache must fail closed when the anonymous update cannot run");
        assertTrue(trustedUpdate.contains("Trusted Dependency-Check database is stale"),
                "stale cache must fail closed when the anonymous update cannot run");
        assertTrue(trustedUpdate.contains("DEPENDENCY_CHECK_MAX_CACHE_AGE_HOURS"),
                "the trusted fallback must use the same freshness budget as pull-request scans");

        String saveStep = security.substring(saveStart, scanStart);
        assertTrue(saveStep.contains("steps.dependency-check-update.outputs.updated == 'true'"),
                "the workflow must publish a new trusted cache only after a real NVD refresh");
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
