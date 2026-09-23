package com.morpheus.architecture;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * A failure is classified by what it is, never by what its message happens to say.
 *
 * <p>Two consumers of {@code SafeWorkspaceFileResolver} once told a size overrun from any other refused read by
 * looking for {@code "exceeds maximum input size"} in the exception message. No test pinned that phrase, another
 * producer in the repository uses it too, and a file whose own name contained it was reported as over budget when
 * it had failed for a different reason. The resolver now raises {@code WorkspaceFileTooLargeException} and the
 * consumers catch that type.</p>
 *
 * <p>Textual by nature (ADR-0103): a string handed to {@code contains} leaves no dependency in the bytecode, so no
 * ArchUnit rule can see it.</p>
 */
class ErrorClassificationByTypeArchitectureTest {
    private static final Pattern MESSAGE_INSPECTION = Pattern.compile(
            "getMessage\\(\\)\\s*\\.\\s*(?:contains|startsWith|endsWith|matches|equals)\\(");
    private static final Pattern SIZE_PHRASE_INSPECTION = Pattern.compile(
            "\\.\\s*(?:contains|startsWith|endsWith|matches|equals)\\(\\s*\"[^\"]*exceeds maximum input size");

    private static final Path RESOLVER = Path.of(
            "morpheus-application/src/main/java/com/morpheus/application/files/SafeWorkspaceFileResolver.java");
    private static final List<Path> SIZE_CONSUMERS = List.of(
            Path.of("morpheus-application/src/main/java/com/morpheus/application/read/ProviderIngestionBudget.java"),
            Path.of("morpheus-api/src/main/java/com/morpheus/api/RemoteIdentityFileStore.java"));

    @Test
    void noProductionSourceClassifiesAFailureByInspectingItsMessage() throws IOException {
        Path root = repoRoot();
        List<String> offenders = new ArrayList<>();
        for (Path file : productionSources(root)) {
            String source = Files.readString(file);
            if (MESSAGE_INSPECTION.matcher(source).find() || SIZE_PHRASE_INSPECTION.matcher(source).find()) {
                offenders.add(root.relativize(file).toString().replace('\\', '/'));
            }
        }
        assertTrue(offenders.isEmpty(),
                () -> "a failure must be classified by its type, not by a phrase in its message: " + offenders);
    }

    @Test
    void aWorkspaceSizeOverrunIsRaisedAndRecognizedByItsOwnType() throws IOException {
        Path root = repoRoot();
        assertTrue(Files.readString(root.resolve(RESOLVER)).contains("new WorkspaceFileTooLargeException("),
                "the resolver must raise a size overrun as WorkspaceFileTooLargeException");
        for (Path consumer : SIZE_CONSUMERS) {
            assertTrue(Files.readString(root.resolve(consumer)).contains("catch (WorkspaceFileTooLargeException "),
                    () -> consumer + " must recognize a size overrun by catching WorkspaceFileTooLargeException");
        }
    }

    private static List<Path> productionSources(Path root) throws IOException {
        try (Stream<Path> tree = Files.walk(root)) {
            return tree.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(".java"))
                    .filter(path -> path.toString().replace('\\', '/').contains("/src/main/java/"))
                    .filter(path -> !path.toString().replace('\\', '/').contains("/target/"))
                    .sorted()
                    .toList();
        }
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
