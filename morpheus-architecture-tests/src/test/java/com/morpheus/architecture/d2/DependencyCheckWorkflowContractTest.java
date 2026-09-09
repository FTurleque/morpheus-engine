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
     * A fail-closed gate that nobody can see coming fails everything at once, with no warning and no diagnosis.
     *
     * <p>The refusal itself is correct and is asserted above; what this test adds is that the refusal is legible.
     * Without NVD_API_KEY the workflow cannot refresh the vulnerability feed at all, only reuse a cache some
     * earlier trusted event produced. While that cache is fresh nothing looks wrong, and the first visible symptom
     * is every pull request failing simultaneously the moment the freshness budget runs out. Three properties turn
     * that cliff into a slope, and each is pinned here because each is the kind of thing a later edit removes
     * without noticing: the age and remaining margin are published in the job summary along with which path
     * produced the database, an approaching expiry is announced before it fails, and the failure names its own
     * reason so an infrastructure outage cannot be read as a vulnerability.</p>
     */
    @Test
    void databaseFreshnessAndFailureReasonsAreObservableBeforeAndWhenTheyStopTheBuild() throws IOException {
        Path root = repoRoot();
        String security = Files.readString(root.resolve(".github/workflows/security.yml"));
        String report = Files.readString(root.resolve("scripts/report-dependency-check-cache.sh"));
        String classify = Files.readString(root.resolve("scripts/classify-dependency-check-failure.sh"));

        assertTrue(report.contains("GITHUB_STEP_SUMMARY"),
                "cache freshness must reach the job summary, not only the log of a job nobody opens while green");
        assertTrue(report.contains("| Obtained via | ${source_label} |"),
                "the summary must say whether the database came from an API-key refresh or from the fallback");
        assertTrue(report.contains("| Remaining before expiry |"),
                "the summary must publish the margin left before the freshness budget expires");
        assertTrue(report.contains("| Budget consumed | ${consumed_percent}% |"),
                "the summary must publish how much of the freshness budget is already spent");

        assertTrue(report.contains("if (( age_seconds * 3 >= max_age_seconds * 2 )); then"),
                "an approaching expiry must be announced at two thirds of the budget, not discovered at 100%");
        assertTrue(report.contains("::warning::Dependency-Check database has consumed"),
                "the approaching-expiry alert must be a visible annotation, not a plain log line");

        assertTrue(security.contains("bash ./scripts/report-dependency-check-cache.sh \"${age_seconds}\" "
                        + "\"trusted cache restored for this pull request\""),
                "pull-request scans must publish the freshness of the cache they were handed");
        assertTrue(security.contains("bash ./scripts/report-dependency-check-cache.sh \"${age_seconds}\" "
                        + "\"trusted cache fallback (NVD_API_KEY absent, no refresh performed)\""),
                "the fallback must say out loud that it refreshed nothing");
        assertTrue(security.contains("\"NVD API key refresh\""),
                "a real refresh must be reported through the same summary as the fallback, so the two paths are "
                        + "told apart by what the run says rather than by reading the workflow");

        int staleOccurrences = security.split("MORPHEUS_DEPENDENCY_CHECK_FAILURE=STALE_DATABASE", -1).length - 1;
        assertTrue(staleOccurrences >= 3,
                "every freshness refusal -- missing cache on a pull request, missing cache on a trusted event, "
                        + "and an expired cache -- must name STALE_DATABASE as its reason");
        assertTrue(security.contains("This is an infrastructure failure, not a vulnerability finding"),
                "a stale database must state that it is not a CVE finding");

        assertTrue(classify.contains("VULNERABILITY_THRESHOLD_EXCEEDED")
                        && classify.contains("SCAN_EXECUTION_FAILED"),
                "a scan failure must distinguish a crossed CVSS threshold from an analyzer that never finished");
        assertTrue(classify.contains(
                        "One or more dependencies were identified with vulnerabilities that have a CVSS score "
                                + "greater than or equal to"),
                "the threshold classification must key off what Dependency-Check actually prints when "
                        + "failBuildOnCVSS stops the build, never off a guess");
        assertTrue(classify.contains("is not STALE_DATABASE"),
                "the scan failure summary must state explicitly that it is not the stale-database failure");
        assertTrue(classify.trim().endsWith("exit 1"),
                "classifying a failure must never turn it into a success");

        assertTrue(security.contains(
                        "bash ./scripts/classify-dependency-check-failure.sh \"${log}\" "
                                + "\"product runtime dependencies\"")
                        && security.contains("bash ./scripts/classify-dependency-check-failure.sh \"${log}\" "
                                + "\"build and test dependencies\""),
                "both aggregate scans must classify their own failure");
        assertFalse(security.contains("continue-on-error"),
                "the security workflow must stay fail-closed: naming a failure must not make it survivable");
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
