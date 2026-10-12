package com.morpheus.architecture.m20;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * A published release must be verifiable from outside the pipeline that published it.
 *
 * <p>{@code release.yml} builds, attests and publishes; nothing in it reads back what GitHub actually made
 * public. The qualification scripts are that second, independent read, and they are the artefact issue #185 is
 * waiting on -- not a substitute for it. #185 closes on a real tag, so the tooling here must be incapable of
 * manufacturing the evidence that would close it: it reads, verifies and fails closed, and it never creates a
 * tag, a release or an asset.</p>
 */
class ReleaseQualificationContractTest {
    private static final List<String> QUALIFICATION_SCRIPTS =
            List.of("scripts/verify-release-provenance.sh", "scripts/verify-release-provenance.ps1");

    @Test
    void theQualificationToolingShipsForBothSupportedPlatforms() {
        Path root = repoRoot();
        for (String script : QUALIFICATION_SCRIPTS) {
            assertTrue(Files.isRegularFile(root.resolve(script)),
                    () -> "release qualification must stay available on both platforms: " + script);
        }
    }

    @Test
    void bothVariantsPerformTheSameVerifications() throws IOException {
        for (String script : QUALIFICATION_SCRIPTS) {
            String content = read(script);

            assertTrue(content.contains("published SBOM describes version"),
                    () -> script + " must refuse an inventory that describes another version than the tag");
            assertTrue(content.contains("attributes components the published SBOM does not list"),
                    () -> script + " must compare what each archive attributes with the published inventory");
            assertTrue(content.contains("expected four attested artifacts"),
                    () -> script + " must verify the provenance of the inventory with that of the three artifacts");

            assertTrue(content.contains("merge-base --is-ancestor"),
                    () -> script + " must prove the tagged commit is reachable from main");
            assertTrue(content.contains("gh release view"),
                    () -> script + " must read the release GitHub actually published");
            assertTrue(content.contains("release is missing expected assets"),
                    () -> script + " must refuse a release with a missing asset");
            assertTrue(content.contains("release publishes unexpected assets"),
                    () -> script + " must refuse a release carrying anything beyond the expected set");
            assertTrue(content.contains("checksum mismatch for"),
                    () -> script + " must verify each published checksum against its artifact");
            assertTrue(content.contains("release-manifest.json"),
                    () -> script + " must reconcile the release manifests with the tag and commit");
            assertTrue(content.contains("gh attestation verify"),
                    () -> script + " must verify provenance publicly rather than trust the pipeline");
            assertTrue(content.contains("attestation bundle is empty"),
                    () -> script + " must require the preserved attestation bundles");
        }
    }

    /**
     * The release workflow and the two qualification scripts each state the asset set on purpose: the scripts are an
     * independent read, and a shared file would make them read the pipeline's own expectation. The price is three
     * lists that can drift, and a drift would only show on the day of a release, as a publication the qualification
     * refuses. This holds them equal.
     */
    @Test
    void theReleaseWorkflowAndBothQualificationScriptsExpectTheSameAssets() throws IOException {
        Map<String, Set<String>> lists = new TreeMap<>();
        lists.put(".github/workflows/release.yml",
                assetNames(read(".github/workflows/release.yml"), "expected=(", "\"release-assets/([^\"]+)\""));
        lists.put("scripts/verify-release-provenance.sh",
                assetNames(read("scripts/verify-release-provenance.sh"), "EXPECTED_ASSETS=(", "\"([^\"/]+)\""));
        lists.put("scripts/verify-release-provenance.ps1",
                assetNames(read("scripts/verify-release-provenance.ps1"), "$expected = @(", "\"([^\"/]+)\""));

        Set<String> reference = lists.get(".github/workflows/release.yml");
        assertTrue(reference.size() >= 4, "the release workflow must expect assets: " + reference);
        assertTrue(reference.contains("morpheus-<version>-sbom.cdx.json")
                        && reference.contains("morpheus-<version>-sbom.cdx.json.sha256"),
                "a release publishes the inventory of the tagged commit with its checksum: " + reference);
        lists.forEach((file, names) -> {
            Set<String> missing = new TreeSet<>(reference);
            missing.removeAll(names);
            Set<String> extra = new TreeSet<>(names);
            extra.removeAll(reference);
            assertTrue(missing.isEmpty() && extra.isEmpty(),
                    () -> file + " does not expect the asset set of the release workflow; missing " + missing
                            + ", in excess " + extra);
        });
    }

    /**
     * What a distribution discloses about the third-party code it carries is built by the packaging on both platforms
     * and refused when incomplete; the inventory is built by the release build from the tagged commit.
     */
    @Test
    void everyDistributionCarriesItsAttributionsAndTheReleaseBuildWritesTheInventory() throws IOException {
        for (String packaging : List.of("distribution/build-portable.sh", "distribution/build-portable.ps1")) {
            String content = read(packaging);
            assertTrue(content.contains("write-third-party-notices.py"),
                    () -> packaging + " must write the notices from the dependencies resolved by this build");
            assertTrue(content.contains("dependency:list") && content.contains("-DincludeScope=runtime"),
                    () -> packaging + " must derive the attributed components from the runtime classpath");
            assertTrue(content.contains("is missing or empty at the root of the image"),
                    () -> packaging + " must refuse an image without the product licence or the notices");
            assertTrue(content.contains("the embedded runtime carries no legal notice for"),
                    () -> packaging + " must refuse a runtime stripped of its legal notices");
        }
        String generator = read("scripts/write-third-party-notices.py");
        assertTrue(generator.contains("THIRD_PARTY_NOTICE_MISSING") && generator.contains("THIRD_PARTY_NOTICE_UNUSED"),
                "the notices generator must refuse a shipped component without attribution and a stale declaration");

        String releaseBuild = read("distribution/build-release.sh");
        assertTrue(releaseBuild.contains("cyclonedx:makeAggregateBom@m21-aggregate-sbom"),
                "the release build must write the inventory itself, from the tagged commit");
        assertTrue(releaseBuild.contains("-sbom.cdx.json") && releaseBuild.contains("Release SBOM was not produced"),
                "the release build must publish the inventory under its release name and refuse its absence");
    }

    /** The quoted names of the first parenthesised list that follows {@code opening}, with the version made neutral. */
    private static Set<String> assetNames(String content, String opening, String entry) {
        int start = content.indexOf(opening);
        assertTrue(start >= 0, () -> "no list opened by " + opening);
        int end = content.indexOf(")", start + opening.length());
        assertTrue(end > start, () -> "the list opened by " + opening + " is not closed");
        String block = content.substring(start + opening.length(), end);
        Set<String> names = new TreeSet<>();
        Matcher name = Pattern.compile(entry).matcher(block);
        while (name.find()) {
            names.add(name.group(1).replace("${version}", "<version>").replace("$VERSION", "<version>")
                    .replace("$version", "<version>"));
        }
        assertFalse(names.isEmpty(), () -> "the list opened by " + opening + " is empty");
        return names;
    }

    /**
     * The scripts must stay read-only against the release surface. A qualification tool that can create what it
     * checks is a tool that can close #185 without anything having been released.
     */
    @Test
    void theQualificationToolingCannotManufactureTheEvidenceItChecks() throws IOException {
        for (String script : QUALIFICATION_SCRIPTS) {
            String content = read(script);
            for (String mutation : List.of(
                    "gh release create",
                    "gh release upload",
                    "gh release edit",
                    "gh release delete",
                    "git tag",
                    "git push")) {
                assertFalse(content.contains(mutation),
                        () -> script + " must never " + mutation + ": it verifies a release, it does not make one");
            }
        }
    }

    @Test
    void theOperatorProcedureKeepsIssue185OpenUntilARealReleaseIsQualified() throws IOException {
        String procedure = read("docs/project/validation/RELEASE_QUALIFICATION.md");

        assertTrue(procedure.contains("scripts/verify-release-provenance.sh"));
        assertTrue(procedure.contains("scripts\\verify-release-provenance.ps1"));
        assertTrue(procedure.contains("Ne jamais créer un tag ou une release artificielle"),
                "the procedure must forbid fabricating the evidence that would close #185");
        assertTrue(procedure.contains("v1.2.0"),
                "the procedure must keep naming the last release that actually happened");
        assertFalse(procedure.contains("RELEASE QUALIFICATION PASS: v"),
                "a recorded pass belongs in a dated validation record, never in the procedure that produces it");
    }

    private static String read(String relative) throws IOException {
        return Files.readString(repoRoot().resolve(relative));
    }

    private static Path repoRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("contracts/public-surfaces.tsv"))
                    && Files.isRegularFile(current.resolve("pom.xml"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("MORPHEUS repository root not found");
    }
}
