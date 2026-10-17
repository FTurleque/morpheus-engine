package com.morpheus.application.sync;

import com.morpheus.application.operability.OperationalRecorder;
import com.morpheus.domain.project.ProjectSpecificationId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The workspace boundary of one scan: what it asks of the file system grows with the entries visited and not with
 * their depth, a segment replaced by a link while a file is read is still refused before the file is inventoried, and
 * nothing the scan remembered outlives it. The cost is a count of file-system queries, never a duration.
 */
class ScanWorkspaceBoundaryTest {
    private static final Instant NOW = Instant.parse("2026-10-17T10:00:00Z");
    private static final int QUERIES_PER_SEGMENT = 4;
    private static final int QUERIES_ON_THE_WORKSPACE_ROOT = 3;

    @TempDir
    Path temp;

    @Test
    void deepeningATreeAddsAConstantPerDirectoryAndNothingPerFile() throws Exception {
        long shallow = queriesBeforeReading(chainWithFiles("shallow", 4, 5));
        long deep = queriesBeforeReading(chainWithFiles("deep", 8, 5));

        assertEquals(QUERIES_PER_SEGMENT * 4L, deep - shallow,
                "four more directories must cost four queries each, whatever the files below them");
        assertEquals(QUERIES_ON_THE_WORKSPACE_ROOT + QUERIES_PER_SEGMENT * (1 + 4 + 5), shallow,
                "the workspace root once, its own empty segment as the source root, then one segment per directory "
                        + "and per file");
    }

    @Test
    void aDirectoryIsValidatedOnceWhateverNumberOfFilesItHolds() throws Exception {
        long few = queriesBeforeReading(chainWithFiles("few", 6, 5));
        long many = queriesBeforeReading(chainWithFiles("many", 6, 50));

        assertEquals(QUERIES_PER_SEGMENT * 45L, many - few,
                "forty-five more files must cost one segment each, not one walk of the six directories above");
    }

    @Test
    void theRevalidationAfterFingerprintingStaysAWalkOfTheWholePath() throws Exception {
        LocalSourceInventoryScanner scanner = chainWithFiles("revalidation", 6, 3);

        assertEquals(3L * (QUERIES_ON_THE_WORKSPACE_ROOT + QUERIES_PER_SEGMENT * (6 + 1)),
                scanner.boundaryQueriesOfLastScan().revalidating(),
                "each file is checked again from the file system, for every segment down to itself");
    }

    @Test
    void theSourceRootTakesTheFullWalkBeforeAnyEntryIsVisited() throws Exception {
        Path workspace = Files.createDirectories(temp.resolve("source-root"));
        Files.createDirectories(workspace.resolve("a/b/c"));
        LocalSourceInventoryScanner scanner = new LocalSourceInventoryScanner(OperationalRecorder.noop());

        SourceInventoryScanResult result = scanner.scan(workspace, ProjectSpecificationId.generate(),
                Optional.empty(), NOW, List.of(Path.of("a/b/c")));

        assertTrue(result.complete(), () -> "scan failures: " + result.failures());
        assertEquals(QUERIES_ON_THE_WORKSPACE_ROOT + QUERIES_PER_SEGMENT * 3,
                scanner.boundaryQueriesOfLastScan().beforeReading(),
                "three segments between the workspace root and the source root, each walked once");
    }

    @Test
    void anOrdinaryTreeProducesTheInventoryOfItsFiles() throws Exception {
        Path workspace = Files.createDirectories(temp.resolve("ordinary"));
        Files.createDirectories(workspace.resolve("docs/deep/er"));
        Files.createDirectories(workspace.resolve("specs"));
        List<Path> files = List.of(
                workspace.resolve("root.md"),
                workspace.resolve("docs/one.md"),
                workspace.resolve("docs/deep/two.md"),
                workspace.resolve("docs/deep/er/three.md"),
                workspace.resolve("specs/four.md"));
        for (Path file : files) {
            Files.writeString(file, "content of " + file.getFileName(), StandardCharsets.UTF_8);
        }

        SourceInventoryScanResult result = new LocalSourceInventoryScanner(OperationalRecorder.noop()).scan(
                workspace, ProjectSpecificationId.generate(), Optional.empty(), NOW, List.of());

        List<SourceInventory.Entry> expected = new ArrayList<>();
        for (Path file : files) {
            byte[] bytes = Files.readAllBytes(file);
            expected.add(new SourceInventory.Entry(
                    new SourcePath(workspace.relativize(file).toString()), SourceFingerprint.ofBytes(bytes), bytes.length));
        }
        expected.sort(Comparator.comparing(SourceInventory.Entry::path));
        assertTrue(result.complete(), () -> "scan failures: " + result.failures());
        assertEquals(expected, result.inventory().orElseThrow().entries());
    }

    @Test
    @DisabledOnOs(value = OS.WINDOWS, disabledReason = "a directory being walked is held open and cannot be moved")
    void anAncestorReplacedByALinkInsideTheWorkspaceWhileAFileIsReadIsRefused() throws Exception {
        verifyAnAncestorReplacedWhileAFileIsRead("inside", true);
    }

    @Test
    @DisabledOnOs(value = OS.WINDOWS, disabledReason = "a directory being walked is held open and cannot be moved")
    void anAncestorReplacedByALinkOutsideTheWorkspaceWhileAFileIsReadIsRefused() throws Exception {
        verifyAnAncestorReplacedWhileAFileIsRead("outside", false);
    }

    @Test
    void aDirectoryReplacedByALinkBetweenTwoScansOfTheSameScannerIsRefusedByTheSecond() throws Exception {
        Path workspace = Files.createDirectories(temp.resolve("two-scans"));
        Path docs = Files.createDirectories(workspace.resolve("area/docs"));
        Files.writeString(docs.resolve("doc.md"), "doc");
        Path inside = Files.createDirectories(workspace.resolve("elsewhere/docs"));
        Files.writeString(inside.resolve("doc.md"), "doc");
        LocalSourceInventoryScanner scanner = new LocalSourceInventoryScanner(OperationalRecorder.noop());
        ProjectSpecificationId project = ProjectSpecificationId.generate();

        SourceInventoryScanResult first = scanner.scan(
                workspace, project, Optional.empty(), NOW, List.of(Path.of("area/docs")));
        assertTrue(first.complete(), () -> "scan failures: " + first.failures());

        Files.move(workspace.resolve("area"), temp.resolve("two-scans-moved-area"));
        assumeTrue(createSymlink(workspace.resolve("area"), inside.getParent()),
                "symbolic links cannot be created in this environment");

        SourceInventoryScanResult second = scanner.scan(
                workspace, project, Optional.empty(), NOW, List.of(Path.of("area/docs")));

        assertFalse(second.complete());
        assertTrue(second.inventory().isEmpty());
        assertTrue(second.failures().stream().anyMatch(failure ->
                failure.code() == SourceInventoryScanResult.Failure.Code.WORKSPACE_BOUNDARY_ESCAPED),
                () -> "unexpected failures: " + second.failures());
    }

    @Test
    void theWatcherChecksADirectoryItAlreadyCheckedAgainWhenAnEventArrives() throws Exception {
        Path workspace = Files.createDirectories(temp.resolve("watched"));
        Path watched = Files.createDirectories(workspace.resolve("area"));
        Path outside = Files.createDirectories(temp.resolve("watched-outside"));

        try (LocalSourceWatcher watcher = new LocalSourceWatcher(
                workspace, List.of(), SourceScanPolicy.safeDefaults(), OperationalRecorder.noop())) {
            Path moved = temp.resolve("watched-moved-area");
            Files.move(watched, moved);
            assumeTrue(createSymlink(watched, outside), "symbolic links cannot be created in this environment");
            Files.writeString(moved.resolve("event.md"), "event");

            List<SourceWatchSignal> signals = new ArrayList<>();
            for (int attempt = 0; attempt < 20 && signals.isEmpty(); attempt++) {
                signals.addAll(watcher.poll(Duration.ofMillis(250)));
            }

            assertFalse(signals.isEmpty(), "the event must be reported");
            assertTrue(signals.stream().allMatch(signal -> signal.kind() == SourceWatchSignal.Kind.OVERFLOW),
                    () -> "a path below a directory replaced by a link must not be reported as a path: " + signals);
        }
    }

    private void verifyAnAncestorReplacedWhileAFileIsRead(String name, boolean linkInsideTheWorkspace) throws Exception {
        Path workspace = Files.createDirectories(temp.resolve("replace-" + name));
        Path file = workspace.resolve("area/docs/doc.md");
        Files.createDirectories(file.getParent());
        Files.writeString(file, "doc");
        Path replacement = linkInsideTheWorkspace
                ? Files.createDirectories(workspace.resolve("elsewhere/docs"))
                : Files.createDirectories(temp.resolve("replace-" + name + "-outside/docs"));
        Files.writeString(replacement.resolve("doc.md"), "doc");
        Path linkTarget = replacement.getParent();
        assumeTrue(createSymlink(temp.resolve("replace-" + name + "-probe"), linkTarget),
                "symbolic links cannot be created in this environment");

        AtomicBoolean replaced = new AtomicBoolean();
        LocalSourceInventoryScanner scanner = new LocalSourceInventoryScanner(
                SourceScanPolicy.safeDefaults(),
                (path, maxBytes) -> {
                    Files.move(workspace.resolve("area"), temp.resolve("replace-" + name + "-moved"));
                    Files.createSymbolicLink(workspace.resolve("area"), linkTarget);
                    replaced.set(true);
                    return SourceFingerprint.ofFile(path, maxBytes);
                },
                OperationalRecorder.noop());

        SourceInventoryScanResult result = scanner.scan(
                workspace, ProjectSpecificationId.generate(), Optional.empty(), NOW, List.of(Path.of("area/docs")));

        assertTrue(replaced.get(), "the ancestor must have been replaced while the file was being read");
        assertFalse(result.complete());
        assertTrue(result.inventory().isEmpty(), "the file must not be in an inventory");
        assertTrue(result.failures().stream().anyMatch(failure ->
                        failure.source().equals(Optional.of("area/docs/doc.md"))
                                && failure.message().contains("symbolic link")),
                () -> "the replaced path must be reported as crossing a symbolic link: " + result.failures());
    }

    private LocalSourceInventoryScanner chainWithFiles(String name, int depth, int files) throws IOException {
        Path workspace = Files.createDirectories(temp.resolve(name));
        Path bottom = workspace;
        for (int level = 0; level < depth; level++) {
            bottom = bottom.resolve("d" + level);
        }
        Files.createDirectories(bottom);
        for (int index = 0; index < files; index++) {
            Files.writeString(bottom.resolve("f" + index + ".md"), "file " + index);
        }
        LocalSourceInventoryScanner scanner = new LocalSourceInventoryScanner(OperationalRecorder.noop());
        SourceInventoryScanResult result = scanner.scan(
                workspace, ProjectSpecificationId.generate(), Optional.empty(), NOW, List.of());
        assertTrue(result.complete(), () -> "scan failures: " + result.failures());
        assertEquals(files, result.inventory().orElseThrow().entries().size());
        return scanner;
    }

    private static long queriesBeforeReading(LocalSourceInventoryScanner scanner) {
        return scanner.boundaryQueriesOfLastScan().beforeReading();
    }

    private static boolean createSymlink(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target);
            return true;
        } catch (UnsupportedOperationException | IOException | SecurityException unavailable) {
            return false;
        }
    }
}
