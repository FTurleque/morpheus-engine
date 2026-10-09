package com.morpheus.architecture.d2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Two repository-wide text rules that were written down but held only for some files.
 *
 * <p>Every GitHub Action is pinned by a 40-character commit SHA ({@code .claude/rules/security.md}). The D2 gate
 * checks the actions it names one by one, so an action it does not name could be added under a mutable tag. Here
 * every {@code uses:} line of every workflow is held, whatever the action.</p>
 *
 * <p>Every PowerShell script stays pure ASCII unless it carries a UTF-8 BOM ({@code .claude/rules/tooling.md}):
 * Windows PowerShell 5.1 decodes a BOM-less file in the ANSI code page, and one non-ASCII byte was enough to stop
 * both hooks from parsing for a whole revision. Only the post-edit hook warned about it, and only locally.</p>
 */
class RepositoryTextHygieneContractTest {

    private static final Pattern USES = Pattern.compile("(?m)^\\s*(?:-\\s*)?uses:\\s*(\\S+)");
    private static final Pattern PINNED = Pattern.compile("[^@\\s]+@[0-9a-f]{40}");
    private static final Set<String> NOT_SOURCES = Set.of(
            ".git", "target", "validation-output", "dist", "node_modules", ".idea", "worktrees");

    @Test
    void everyWorkflowActionIsPinnedByACommitSha() throws IOException {
        assertEquals(List.of(), unpinnedActions(repoRoot().resolve(".github/workflows")),
                "every uses: of a workflow names a local action or a 40-character commit SHA, never a mutable tag");
    }

    @Test
    void theWorkflowPinRuleRefusesAMutableTagAndAcceptsAShaAndALocalAction(@TempDir Path workflows) throws IOException {
        Files.writeString(workflows.resolve("planted.yml"), String.join("\n",
                "jobs:",
                "  build:",
                "    steps:",
                "      - uses: actions/checkout@" + "a".repeat(40) + " # v6.0.1",
                "      - uses: ./.github/actions/local",
                "      - uses: some-vendor/some-action@v1",
                "      - uses: actions/cache@main",
                ""));
        assertEquals(List.of(
                        "planted.yml: some-vendor/some-action@v1",
                        "planted.yml: actions/cache@main"),
                unpinnedActions(workflows));
    }

    @Test
    void everyPowerShellScriptIsAsciiUnlessItCarriesABom() throws IOException {
        List<String> scripts = new ArrayList<>();
        assertEquals(List.of(), nonAsciiScripts(repoRoot(), scripts),
                "Windows PowerShell 5.1 reads a BOM-less script in the ANSI code page; keep .ps1 files ASCII");
        assertFalse(scripts.isEmpty(), "the walk found no PowerShell script at all");
    }

    @Test
    void theAsciiRuleRefusesANonAsciiByteWithoutBomAndAcceptsABom(@TempDir Path root) throws IOException {
        Files.write(root.resolve("plain.ps1"), "Write-Output 'ok'\r\n".getBytes(StandardCharsets.US_ASCII));
        Files.write(root.resolve("dash.ps1"), "Write-Output 'a — b'\r\n".getBytes(StandardCharsets.UTF_8));
        byte[] body = "Write-Output 'a — b'\r\n".getBytes(StandardCharsets.UTF_8);
        byte[] withBom = new byte[body.length + 3];
        withBom[0] = (byte) 0xEF;
        withBom[1] = (byte) 0xBB;
        withBom[2] = (byte) 0xBF;
        System.arraycopy(body, 0, withBom, 3, body.length);
        Files.write(root.resolve("bom.ps1"), withBom);
        Files.createDirectories(root.resolve("target"));
        Files.write(root.resolve("target/ignored.ps1"), body);

        List<String> scripts = new ArrayList<>();
        assertEquals(List.of("dash.ps1"), nonAsciiScripts(root, scripts));
        assertEquals(3, scripts.size());
    }

    private static List<String> unpinnedActions(Path workflows) throws IOException {
        List<String> unpinned = new ArrayList<>();
        List<Path> files;
        try (var listing = Files.list(workflows)) {
            files = listing.filter(path -> path.getFileName().toString().matches(".+\\.ya?ml")).sorted().toList();
        }
        for (Path workflow : files) {
            Matcher matcher = USES.matcher(Files.readString(workflow));
            while (matcher.find()) {
                String action = matcher.group(1);
                if (!action.startsWith("./") && !PINNED.matcher(action).matches()) {
                    unpinned.add(workflow.getFileName() + ": " + action);
                }
            }
        }
        return unpinned;
    }

    private static List<String> nonAsciiScripts(Path root, List<String> seen) throws IOException {
        List<String> offending = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attributes) {
                Path name = dir.getFileName();
                return !dir.equals(root) && name != null && NOT_SOURCES.contains(name.toString())
                        ? FileVisitResult.SKIP_SUBTREE
                        : FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                if (!file.toString().endsWith(".ps1")) {
                    return FileVisitResult.CONTINUE;
                }
                String relative = root.relativize(file).toString().replace('\\', '/');
                seen.add(relative);
                if (isNonAsciiWithoutBom(Files.readAllBytes(file))) {
                    offending.add(relative);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        offending.sort(String::compareTo);
        return offending;
    }

    private static boolean isNonAsciiWithoutBom(byte[] bytes) {
        boolean bom = bytes.length >= 3
                && bytes[0] == (byte) 0xEF && bytes[1] == (byte) 0xBB && bytes[2] == (byte) 0xBF;
        if (bom) {
            return false;
        }
        for (byte b : bytes) {
            if (b < 0) {
                return true;
            }
        }
        return false;
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
