package com.morpheus.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two security decisions are held by the shape of the code, not by a guard several lines away.
 *
 * <p>The source scan could be asked to follow symbolic links by a parameter its own constructor refused, and the two
 * MCP gateways launched "the verified copy, or else the configured JAR" behind a check that made the second half
 * unreachable. Both were safe, and both were one relaxed guard away from not being: a branch nobody can reach is a
 * branch nobody tests. These assertions are textual because their intention is "this must not be written here" --
 * what they forbid is an expression, which no rule on dependencies can name.</p>
 */
class UnreachableSecurityBranchArchitectureTest {
    private static final String SYNC = "morpheus-application/src/main/java/com/morpheus/application/sync/";

    @Test
    void aSourceScanCannotBeAskedToFollowSymbolicLinks() throws IOException {
        for (String source : List.of(SYNC + "SourceScanPolicy.java", SYNC + "LocalSourceInventoryScanner.java")) {
            String content = read(source);
            assertFalse(content.contains("followSymbolicLinks"),
                    () -> source + " must offer no setting for symbolic-link traversal");
            assertFalse(content.contains("FileVisitOption.FOLLOW_LINKS"),
                    () -> source + " must never walk a workspace following links");
        }
        assertTrue(read(SYNC + "LocalSourceInventoryScanner.java").contains("Files.isSymbolicLink("),
                "the scanner must still refuse symbolic roots, directories and files");
    }

    @Test
    void anMcpPeerIsLaunchedFromItsVerifiedCopyOrNotAtAll() throws IOException {
        for (String gateway : List.of(
                "morpheus-integration-minos/src/main/java/com/morpheus/integration/minos/MinosMcpCodeGateway.java",
                "morpheus-integration-nexus/src/main/java/com/morpheus/integration/nexus/NexusMcpContextGateway.java")) {
            String content = read(gateway);
            assertTrue(content.contains("Path launchJar = staged.orElseThrow();"),
                    () -> gateway + " must launch the staged, verified copy and nothing else");
            assertFalse(content.contains("orElse(jar)"),
                    () -> gateway + " must hold no path that launches the configured JAR unverified");
        }
    }

    private static String read(String relative) throws IOException {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("contracts/public-surfaces.tsv"))
                    && Files.isRegularFile(current.resolve("pom.xml"))) {
                return Files.readString(current.resolve(relative));
            }
            current = current.getParent();
        }
        throw new IOException("cannot locate MORPHEUS repository root from " + Path.of("").toAbsolutePath());
    }
}
