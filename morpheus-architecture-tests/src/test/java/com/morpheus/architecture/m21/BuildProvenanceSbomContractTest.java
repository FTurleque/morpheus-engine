package com.morpheus.architecture.m21;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A build provenance file states the digest of the SBOM it was written beside, or is not written.
 *
 * <p>Both variants of the script wrote {@code sbomSha256=missing} when the SBOM was absent, and nothing read that
 * value: a provenance file that vouches for no bill of materials looked like any other. Every validator that calls the
 * script checks for the SBOM just before, so the fallback was unreachable from them -- and one relaxed check away from
 * being written. The script refuses by name instead. The assertions are textual: the targets are a {@code .sh} and a
 * {@code .ps1}, where text is the only hold.</p>
 */
class BuildProvenanceSbomContractTest {
    private static final String REFUSAL = "BUILD_PROVENANCE_SBOM_MISSING";
    private static final List<String> VARIANTS =
            List.of("scripts/write-build-provenance.sh", "scripts/write-build-provenance.ps1");

    @Test
    void neitherVariantWritesAProvenanceFileWithoutTheSbomItVouchesFor() throws IOException {
        for (String variant : VARIANTS) {
            String script = Files.readString(repositoryRoot().resolve(variant));

            assertTrue(script.contains("sbomSha256="), () -> variant + " must still state the digest of the SBOM");
            assertTrue(script.contains(REFUSAL),
                    () -> variant + " must refuse by name when the SBOM is absent: " + REFUSAL);
            assertFalse(script.contains("=missing") || script.contains("\"missing\""),
                    () -> variant + " must not write a placeholder where the digest of the SBOM belongs");
        }
    }

    private static Path repositoryRoot() throws IOException {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("contracts/public-surfaces.tsv"))
                    && Files.isRegularFile(current.resolve("pom.xml"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IOException("cannot locate MORPHEUS repository root from " + Path.of("").toAbsolutePath());
    }
}
