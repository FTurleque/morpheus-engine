package com.morpheus.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

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

    /** The one way the package names the traversal options: none of them. */
    private static final String NO_TRAVERSAL_OPTION = "EnumSet.noneOf(FileVisitOption.class)";
    private static final Pattern FOLLOWS_LINKS = Pattern.compile("(?<!NO)FOLLOW_LINKS");

    @Test
    void aSourceScanCannotBeAskedToFollowSymbolicLinks() throws IOException {
        List<Path> sources = javaSources(SYNC);
        assertTrue(sources.size() > 5, "too few sources of the sync package read (" + sources.size() + ")");
        for (Path source : sources) {
            String content = Files.readString(source);
            assertFalse(content.contains("followSymbolicLinks"),
                    () -> source.getFileName() + " must offer no setting for symbolic-link traversal");
            assertFalse(FOLLOWS_LINKS.matcher(content).find(),
                    () -> source.getFileName() + " must never walk a workspace following links");
            String withoutTheAllowedForm = content
                    .replace(NO_TRAVERSAL_OPTION, "")
                    .replace("import java.nio.file.FileVisitOption;", "");
            assertFalse(withoutTheAllowedForm.contains("FileVisitOption"),
                    () -> source.getFileName() + " may name the traversal options only as " + NO_TRAVERSAL_OPTION
                            + ": the type has a single constant, so any other set of it follows links");
        }
        String scanner = read(SYNC + "LocalSourceInventoryScanner.java");
        assertTrue(scanner.split(Pattern.quote("Files.isSymbolicLink("), -1).length - 1 >= 3,
                "the scanner must still refuse a symbolic root, a symbolic directory and a symbolic file");
    }

    @Test
    void anMcpPeerIsLaunchedFromItsVerifiedCopyOrNotAtAll() throws IOException {
        for (String gateway : List.of(
                "morpheus-integration-minos/src/main/java/com/morpheus/integration/minos/MinosMcpCodeGateway.java",
                "morpheus-integration-nexus/src/main/java/com/morpheus/integration/nexus/NexusMcpContextGateway.java")) {
            String content = read(gateway);
            assertTrue(content.contains("Path launchJar = staged.orElseThrow();"),
                    () -> gateway + " must launch the staged, verified copy and nothing else");
            assertFalse(content.contains("staged.orElse(") || content.contains("staged.orElseGet("),
                    () -> gateway + " must hold no path that launches anything but the verified copy");
        }
    }

    private static String read(String relative) throws IOException {
        return Files.readString(repositoryRoot().resolve(relative));
    }

    private static List<Path> javaSources(String relativeDirectory) throws IOException {
        try (Stream<Path> files = Files.walk(repositoryRoot().resolve(relativeDirectory))) {
            return files.filter(path -> path.toString().endsWith(".java")).sorted().toList();
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
