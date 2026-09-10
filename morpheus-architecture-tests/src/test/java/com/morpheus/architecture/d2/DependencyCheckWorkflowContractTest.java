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

    /**
     * The cache key must answer whether this analyzer can read this database, and only the schema decides that.
     *
     * <p>The key used to carry the plugin version, with a restore-key falling back to the previous one. That
     * pair contradicted itself: if the version in the key meant anything the fallback discarded the meaning,
     * and if it meant nothing the key was decoration. Measured rather than assumed, it meant nothing --
     * Dependency-Check 12.2.2 and 13.0.0 both declare {@code data.version=5.6} and ship byte-identical
     * {@code data/initialize.sql} and {@code data/dbStatements.properties}, because upstream changed the schema
     * at 12.2.2 and not at 13.0.0. So the fallback was not the defect it looked like, and the plugin-versioned
     * key was: a routine bump orphaned a readable database, and the fallback added to compensate would have
     * matched an unreadable one just as willingly had the schema really moved.</p>
     *
     * <p>Keyed on the schema the key means exactly one thing, and nothing is left to fall back to: a schema
     * change yields a different key, no match, and an honest cold start. That is also the only outcome a
     * fallback could have produced anyway, since every scan runs {@code -DautoUpdate=false}, under which
     * Dependency-Check refuses a mismatched schema rather than migrating it.</p>
     */
    @Test
    void theTrustedCacheIsKeyedOnTheSchemaVersionAndNeverFallsBackAcrossSchemas() throws IOException {
        Path root = repoRoot();
        String pom = Files.readString(root.resolve("pom.xml"));
        String security = Files.readString(root.resolve(".github/workflows/security.yml"));

        Matcher schema = Pattern.compile(
                        "<dependency-check\\.data\\.version>([^<]+)</dependency-check\\.data\\.version>")
                .matcher(pom);
        assertTrue(schema.find(),
                "the root POM must pin the Dependency-Check H2 schema version the cache key is built from");
        String pinnedSchema = schema.group(1).trim();

        assertTrue(security.contains("DEPENDENCY_CHECK_SCHEMA_VERSION: '" + pinnedSchema + "'"),
                "security.yml must build its cache key from the schema version the root POM pins, so a bump of "
                        + "one cannot silently leave the other behind");

        String expectedKey = "key: dependency-check-schema${{ env.DEPENDENCY_CHECK_SCHEMA_VERSION }}-trusted-"
                + "${{ runner.os }}-${{ github.run_id }}";
        assertTrue(security.split(Pattern.quote(expectedKey), -1).length - 1 == 2,
                "the restore and the save must address the same schema-keyed cache entry");

        for (String line : security.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("key: dependency-check") || trimmed.startsWith("dependency-check-")) {
                assertTrue(trimmed.contains("DEPENDENCY_CHECK_SCHEMA_VERSION"),
                        () -> "every Dependency-Check cache key and restore-key must carry the schema version, "
                                + "so none of them can match a database this analyzer cannot read: " + trimmed);
            }
        }
        assertFalse(Pattern.compile("dependency-check-v[0-9]").matcher(security).find(),
                "no plugin-versioned cache key may survive: the plugin version is not the schema version and "
                        + "does not track it");
    }

    /**
     * Freshness must be a fact a refresh recorded, never an mtime the filesystem happened to leave behind.
     *
     * <p>The previous probe read {@code stat -c %Y} on whichever file {@code find ... -print -quit} reached
     * first, which is neither the database nor the newest file, and compared it to now. Two further mechanisms
     * made that number meaningless even had it picked the right file: {@code actions/cache} restores through
     * tar, which preserves mtimes, so the timestamp describes the last write by Dependency-Check rather than
     * the restore; and an incremental {@code update-only} that rewrites nothing leaves an old mtime on a
     * current database. The 54h age the first freshness report published was that artefact, not an age.</p>
     *
     * <p>The sentinel is written by the refresh itself, so its presence is evidence and its absence is the
     * absence of evidence -- which is not freshness. It carries the writing plugin version, which is what makes
     * a database refreshed by one analyzer and scanned by another visible at runtime instead of silent.</p>
     */
    @Test
    void freshnessIsReadFromARefreshSentinelAndNeverFromAFileModificationTime() throws IOException {
        Path root = repoRoot();
        String security = Files.readString(root.resolve(".github/workflows/security.yml"));
        String writer = Files.readString(root.resolve("scripts/write-dependency-check-sentinel.sh"));
        String reader = Files.readString(root.resolve("scripts/read-dependency-check-sentinel.sh"));

        assertFalse(security.contains("stat -c %Y"),
                "no freshness reading may go through a file modification time");
        assertFalse(security.contains("-print -quit"),
                "no freshness reading may depend on whichever file the directory walk reaches first");

        assertTrue(writer.contains("refreshedAtEpoch=$(date +%s)"),
                "the sentinel must record when the refresh actually completed");
        assertTrue(writer.contains("pluginVersion=${plugin_version}"),
                "the sentinel must record which analyzer refreshed the database, so cross-version reuse is "
                        + "visible at runtime rather than silent");
        assertTrue(writer.contains("schemaVersion=${schema_version}"),
                "the sentinel must record the schema the database was written against");
        assertTrue(writer.contains("sentinel=\"${data_dir}/dependency-check-refresh.sentinel\""),
                "the sentinel must live inside the cached data directory, so it travels with the cache it dates");

        int updateStart = security.indexOf("- name: Update Dependency-Check vulnerability database (trusted events)");
        int saveStart = security.indexOf("- name: Save trusted Dependency-Check database");
        String trustedUpdate = security.substring(updateStart, saveStart);
        int sentinelWrite = trustedUpdate.indexOf("bash ./scripts/write-dependency-check-sentinel.sh");
        assertTrue(sentinelWrite > trustedUpdate.indexOf("org.owasp:dependency-check-maven"),
                "the sentinel must be written after the refresh it certifies, never before it");
        assertTrue(sentinelWrite < trustedUpdate.indexOf("echo \"updated=true\""),
                "a refresh that publishes a cache must have dated it first, so no cache is ever saved without "
                        + "the sentinel that lets a later run judge its age");

        assertTrue(security.contains("path: target/dependency-check-data"),
                "the cached path must be the data directory the sentinel is written into");

        assertTrue(reader.contains("emit_and_exit MISSING") && reader.contains("emit_and_exit MALFORMED")
                        && reader.contains("emit_and_exit SCHEMA_MISMATCH"),
                "a sentinel that is absent, unusable or written against another schema must each be classified, "
                        + "never collapsed into an age");
        assertTrue(reader.contains("echo \"status=$1\""),
                "status must be the first fact emitted, so a caller that reads only the first line still fails "
                        + "closed rather than reading an age that was never established");

        int freshnessRefusals = security.split(Pattern.quote("if [[ \"${status}\" != 'OK' ]]; then"), -1).length - 1;
        assertTrue(freshnessRefusals == 2,
                "both freshness readings -- the pull-request check and the no-key fallback -- must refuse any "
                        + "status but OK, so a missing sentinel is treated as stale rather than as fresh");
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
