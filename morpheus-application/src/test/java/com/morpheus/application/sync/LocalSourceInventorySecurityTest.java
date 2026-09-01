package com.morpheus.application.sync;

import com.morpheus.domain.project.ProjectSpecificationId;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalSourceInventorySecurityTest {

    @TempDir
    Path tempDir;

    @Test
    void safeDefaultsIgnoreGeneratedAndRepositoryMetadataDirectories() throws Exception {
        Path workspace = tempDir.resolve("workspace");
        Files.createDirectories(workspace.resolve("openspec"));
        Files.createDirectories(workspace.resolve(".git"));
        Files.createDirectories(workspace.resolve("target"));
        Files.createDirectories(workspace.resolve("node_modules/pkg"));
        Files.writeString(workspace.resolve("openspec/spec.md"), "accepted");
        Files.writeString(workspace.resolve(".git/config"), "secret-ish repository metadata");
        Files.writeString(workspace.resolve("target/generated.md"), "generated");
        Files.writeString(workspace.resolve("node_modules/pkg/index.md"), "dependency");

        LocalSourceInventoryScanner scanner = new LocalSourceInventoryScanner();
        var result = scan(scanner, workspace);

        assertTrue(result.complete(), () -> "scan failures: " + result.failures());
        var entries = result.inventory().orElseThrow().entries();
        assertEquals(1, entries.size());
        assertEquals("openspec/spec.md", entries.getFirst().path().toString());
        assertFalse(scanner.policy().followSymbolicLinks());
        assertTrue(scanner.policy().ignoredDirectoryNames().contains(".git"));
        assertTrue(scanner.policy().ignoredDirectoryNames().contains("target"));
        assertTrue(scanner.policy().maxDirectories() > 0);
        assertTrue(scanner.policy().maxFiles() > 0);
        assertTrue(scanner.policy().maxFileBytes() > 0);
        assertTrue(scanner.policy().maxAggregateBytes() >= scanner.policy().maxFileBytes());
    }

    @Test
    void safeDefaultDoesNotTraverseExternalSymbolicLinkWhenPlatformSupportsLinks() throws Exception {
        Path workspace = tempDir.resolve("workspace-links");
        Path external = tempDir.resolve("external");
        Files.createDirectories(workspace);
        Files.createDirectories(external);
        Files.writeString(workspace.resolve("local.md"), "local");
        Files.writeString(external.resolve("outside.md"), "outside");

        boolean linkCreated = tryCreateSymbolicLink(workspace.resolve("external-link"), external);
        LocalSourceInventoryScanner scanner = new LocalSourceInventoryScanner();
        var result = scan(scanner, workspace);

        assertTrue(result.complete(), () -> "scan failures: " + result.failures());
        assertEquals(List.of("local.md"), result.inventory().orElseThrow().entries().stream()
                .map(entry -> entry.path().toString())
                .toList());
        assertFalse(scanner.policy().followSymbolicLinks());

        if (linkCreated) {
            assertTrue(Files.isSymbolicLink(workspace.resolve("external-link")));
        }
    }

    @Test
    void rejectsSourceRootThatIsItselfASymbolicLinkWithoutTraversingItsTarget() throws Exception {
        Path workspace = Files.createDirectories(tempDir.resolve("symlink-root-workspace"));
        Path target = Files.createDirectories(tempDir.resolve("symlink-root-target"));
        Files.writeString(target.resolve("inside.md"), "inside content");
        Path link = workspace.resolve("linked-root");
        if (!tryCreateSymbolicLink(link, target)) {
            Assumptions.assumeTrue(false, "symlinks not supported in this environment");
            return;
        }

        LocalSourceInventoryScanner scanner = new LocalSourceInventoryScanner();
        assertFalse(scanner.policy().followSymbolicLinks());
        SourceInventoryScanResult result = scan(scanner, workspace, List.of(Path.of("linked-root")));

        assertFalse(result.complete(), () -> "scan should be incomplete: " + result);
        assertTrue(result.inventory().isEmpty(), "a rejected symbolic-link root must never publish an inventory");
        assertTrue(result.failures().stream().anyMatch(failure ->
                        failure.message().contains("symbolic-link source root is not followed")),
                () -> "unexpected failures: " + result.failures());
    }

    @Test
    void rejectsSameSizeSameMtimeAtomicReplacementWithDifferentFileIdentity() throws Exception {
        Path workspace = Files.createDirectories(tempDir.resolve("same-metadata-replacement"));
        Path source = workspace.resolve("source.md");
        Path replacement = tempDir.resolve("replacement.md");
        Files.writeString(source, "AAAA", StandardCharsets.UTF_8);
        Files.writeString(replacement, "BBBB", StandardCharsets.UTF_8);
        alignReplacementMetadata(source, replacement);

        LocalSourceInventoryScanner scanner = mutationScanner((path, maxBytes) -> {
            replaceAtomically(replacement, path);
            return SourceFingerprint.ofFile(path, maxBytes);
        });

        SourceInventoryScanResult result = scan(scanner, workspace);

        assertMutationFailure(result, "source.md", "changed identity");
    }

    @Test
    void rejectsDeleteAndRecreateWithSameSizeAndMtimeAsDifferentFileIdentity() throws Exception {
        Path workspace = Files.createDirectories(tempDir.resolve("delete-recreate"));
        Path source = workspace.resolve("source.md");
        Path replacement = tempDir.resolve("recreated.md");
        Files.writeString(source, "1111", StandardCharsets.UTF_8);
        Files.writeString(replacement, "2222", StandardCharsets.UTF_8);
        alignReplacementMetadata(source, replacement);

        LocalSourceInventoryScanner scanner = mutationScanner((path, maxBytes) -> {
            Files.delete(path);
            Files.move(replacement, path, StandardCopyOption.REPLACE_EXISTING);
            return SourceFingerprint.ofFile(path, maxBytes);
        });

        SourceInventoryScanResult result = scan(scanner, workspace);

        assertMutationFailure(result, "source.md", "changed identity");
    }

    @Test
    void replacementBySymbolicLinkDuringScanProducesNoInventoryWhenLinksAreSupported() throws Exception {
        Path workspace = Files.createDirectories(tempDir.resolve("symlink-replacement"));
        Path source = workspace.resolve("source.md");
        Path target = tempDir.resolve("symlink-target.md");
        Files.writeString(source, "source", StandardCharsets.UTF_8);
        Files.writeString(target, "target", StandardCharsets.UTF_8);
        if (!symbolicLinksSupported(target)) {
            return;
        }

        LocalSourceInventoryScanner scanner = mutationScanner((path, maxBytes) -> {
            Files.delete(path);
            Files.createSymbolicLink(path, target);
            return SourceFingerprint.ofFile(path, maxBytes);
        });

        SourceInventoryScanResult result = scan(scanner, workspace);

        assertMutationFailure(result, "source.md", "regular non-symbolic");
    }

    @Test
    void stableFileStillProducesCompleteInventoryWithContentFingerprint() throws Exception {
        Path workspace = Files.createDirectories(tempDir.resolve("stable-file"));
        byte[] content = "stable-source".getBytes(StandardCharsets.UTF_8);
        Files.write(workspace.resolve("source.md"), content);

        SourceInventoryScanResult result = scan(new LocalSourceInventoryScanner(), workspace);

        assertTrue(result.complete(), () -> "scan failures: " + result.failures());
        assertTrue(result.failures().isEmpty());
        var entries = result.inventory().orElseThrow().entries();
        assertEquals(1, entries.size());
        assertEquals("source.md", entries.getFirst().path().toString());
        assertEquals(SourceFingerprint.ofBytes(content), entries.getFirst().fingerprint());
        assertEquals(content.length, entries.getFirst().sizeBytes());
    }

    @Test
    void rejectsDirectoryCountBeyondConfiguredBudgetEvenWithoutFiles() throws Exception {
        Path workspace = Files.createDirectories(tempDir.resolve("directory-count"));
        Files.createDirectory(workspace.resolve("one"));
        Files.createDirectory(workspace.resolve("two"));
        LocalSourceInventoryScanner scanner = new LocalSourceInventoryScanner(
                new SourceScanPolicy(Set.of(), false, 8, 2, 10, 16, 64));

        var result = scan(scanner, workspace);

        assertFalse(result.complete());
        assertTrue(result.failures().stream().anyMatch(failure -> failure.message().contains("directory count")));
    }

    @Test
    void rejectsFileCountBeyondConfiguredBudget() throws Exception {
        Path workspace = Files.createDirectories(tempDir.resolve("file-count"));
        Files.writeString(workspace.resolve("one.md"), "1");
        Files.writeString(workspace.resolve("two.md"), "2");
        LocalSourceInventoryScanner scanner = new LocalSourceInventoryScanner(
                new SourceScanPolicy(Set.of(), false, 8, 1, 16, 16));

        var result = scan(scanner, workspace);

        assertFalse(result.complete());
        assertTrue(result.failures().stream().anyMatch(failure -> failure.message().contains("file count")));
    }

    @Test
    void rejectsOversizedFileBeforeFingerprinting() throws Exception {
        Path workspace = Files.createDirectories(tempDir.resolve("file-size"));
        Files.writeString(workspace.resolve("large.md"), "12345");
        LocalSourceInventoryScanner scanner = new LocalSourceInventoryScanner(
                new SourceScanPolicy(Set.of(), false, 8, 10, 4, 16));

        var result = scan(scanner, workspace);

        assertFalse(result.complete());
        assertTrue(result.failures().stream().anyMatch(failure -> failure.message().contains("file exceeds size limit")));
    }

    @Test
    void rejectsAggregateBytesBeyondConfiguredBudget() throws Exception {
        Path workspace = Files.createDirectories(tempDir.resolve("aggregate-size"));
        Files.writeString(workspace.resolve("one.md"), "1234");
        Files.writeString(workspace.resolve("two.md"), "5678");
        LocalSourceInventoryScanner scanner = new LocalSourceInventoryScanner(
                new SourceScanPolicy(Set.of(), false, 8, 10, 4, 6));

        var result = scan(scanner, workspace);

        assertFalse(result.complete());
        assertTrue(result.failures().stream().anyMatch(failure -> failure.message().contains("aggregate bytes")));
    }

    @Test
    void rejectsTraversalBeyondConfiguredDepth() throws Exception {
        Path workspace = Files.createDirectories(tempDir.resolve("depth"));
        Path nested = Files.createDirectories(workspace.resolve("one/two"));
        Files.writeString(nested.resolve("deep.md"), "deep");
        LocalSourceInventoryScanner scanner = new LocalSourceInventoryScanner(
                new SourceScanPolicy(Set.of(), false, 1, 10, 16, 64));

        var result = scan(scanner, workspace);

        assertFalse(result.complete());
        assertTrue(result.failures().stream().anyMatch(failure -> failure.message().contains("depth exceeds limit")));
    }

    private LocalSourceInventoryScanner mutationScanner(LocalSourceInventoryScanner.FingerprintComputer computer) {
        return new LocalSourceInventoryScanner(SourceScanPolicy.safeDefaults(), computer);
    }

    private SourceInventoryScanResult scan(LocalSourceInventoryScanner scanner, Path workspace) {
        return scan(scanner, workspace, List.of());
    }

    private SourceInventoryScanResult scan(
            LocalSourceInventoryScanner scanner, Path workspace, List<Path> sourceRoots) {
        return scanner.scan(
                workspace,
                ProjectSpecificationId.generate(),
                Optional.empty(),
                Instant.parse("2026-07-26T18:00:00Z"),
                sourceRoots);
    }

    private void assertMutationFailure(SourceInventoryScanResult result, String source, String messageFragment) {
        assertFalse(result.complete());
        assertTrue(result.inventory().isEmpty(), "a failed mutation scan must never publish a partial inventory");
        assertEquals(1, result.failures().size(), () -> "unexpected failures: " + result.failures());
        SourceInventoryScanResult.Failure failure = result.failures().getFirst();
        assertEquals(Optional.of(source), failure.source());
        assertTrue(failure.message().contains(messageFragment), failure::message);
    }

    private void alignReplacementMetadata(Path first, Path second) throws IOException {
        FileTime commonMtime = FileTime.fromMillis(1_700_000_000_000L);
        Files.setLastModifiedTime(first, commonMtime);
        Files.setLastModifiedTime(second, commonMtime);

        BasicFileAttributes firstAttributes = SourceFingerprint.readAttributes(first);
        BasicFileAttributes secondAttributes = SourceFingerprint.readAttributes(second);
        if (firstAttributes.fileKey() == null && secondAttributes.fileKey() == null) {
            setCreationTime(first, FileTime.fromMillis(1_600_000_000_000L), commonMtime);
            setCreationTime(second, FileTime.fromMillis(1_650_000_000_000L), commonMtime);
            firstAttributes = SourceFingerprint.readAttributes(first);
            secondAttributes = SourceFingerprint.readAttributes(second);
        }

        assertEquals(firstAttributes.size(), secondAttributes.size());
        assertEquals(firstAttributes.lastModifiedTime(), secondAttributes.lastModifiedTime());
        if (firstAttributes.fileKey() == null && secondAttributes.fileKey() == null) {
            assertNotEquals(
                    firstAttributes.creationTime(),
                    secondAttributes.creationTime(),
                    "provider without fileKey must expose a distinguishable creationTime fallback");
        } else {
            assertNotEquals(firstAttributes.fileKey(), secondAttributes.fileKey());
        }
    }

    private void setCreationTime(Path path, FileTime creationTime, FileTime lastModifiedTime) throws IOException {
        BasicFileAttributeView view = Files.getFileAttributeView(
                path, BasicFileAttributeView.class, LinkOption.NOFOLLOW_LINKS);
        view.setTimes(lastModifiedTime, null, creationTime);
    }

    private void replaceAtomically(Path replacement, Path target) throws IOException {
        try {
            Files.move(
                    replacement,
                    target,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(replacement, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private boolean symbolicLinksSupported(Path target) throws IOException {
        Path probe = tempDir.resolve("symlink-probe");
        if (!tryCreateSymbolicLink(probe, target)) {
            return false;
        }
        Files.delete(probe);
        return true;
    }

    private boolean tryCreateSymbolicLink(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target);
            return true;
        } catch (UnsupportedOperationException | IOException | SecurityException unavailable) {
            return false;
        }
    }
}
