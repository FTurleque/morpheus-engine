package com.morpheus.architecture.m20;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
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
    void bothVariantsVerifyTheSameSixThings() throws IOException {
        for (String script : QUALIFICATION_SCRIPTS) {
            String content = read(script);

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
        String procedure = read("docs/validation/RELEASE_QUALIFICATION.md");

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
