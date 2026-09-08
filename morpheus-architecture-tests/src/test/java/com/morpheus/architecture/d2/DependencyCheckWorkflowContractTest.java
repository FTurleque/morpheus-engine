package com.morpheus.architecture.d2;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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

    /**
     * The workflow must scan with the Dependency-Check the repository pins, not a version of its own.
     *
     * <p>The goal coordinates in {@code security.yml} carry the version explicitly, so a POM bump that forgets
     * the workflow leaves CI scanning with a different analyzer than the one the build declares -- and the
     * 13.0.0 workaround below is tied to one specific upstream defect, so running a different version silently
     * would make that workaround either useless or wrong. Deriving the expectation from the POM is what keeps
     * the two from drifting apart the way the D2 validators already did.</p>
     */
    @Test
    void everyDependencyCheckInvocationUsesTheVersionTheRootPomPins() throws IOException {
        Path root = repoRoot();
        String pom = Files.readString(root.resolve("pom.xml"));
        String security = Files.readString(root.resolve(".github/workflows/security.yml"));

        Matcher property = Pattern.compile(
                        "<dependency-check\\.maven\\.plugin\\.version>([^<]+)</dependency-check\\.maven\\.plugin\\.version>")
                .matcher(pom);
        assertTrue(property.find(), "the root POM must pin the Dependency-Check version");
        String pinned = property.group(1).trim();

        Matcher invocations = Pattern.compile("dependency-check-maven:([0-9][^:]*):").matcher(security);
        int found = 0;
        while (invocations.find()) {
            assertTrue(pinned.equals(invocations.group(1)),
                    () -> "security.yml invokes Dependency-Check " + invocations.group(1)
                            + " while the root POM pins " + pinned);
            found++;
        }
        assertTrue(found >= 3,
                "security.yml must keep its update-only and both aggregate scans on the pinned version");
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
