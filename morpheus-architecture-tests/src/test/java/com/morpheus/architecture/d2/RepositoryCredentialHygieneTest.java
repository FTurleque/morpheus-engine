package com.morpheus.architecture.d2;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The repository ignores the files that hold credentials or key material for a MORPHEUS server, and refuses one that
 * is tracked.
 *
 * <p>{@code SECURITY.md} forbids committing identity files and keystores, and the remote server accepts any path for
 * both, the working tree included. A sentence with no guard: an ignore rule keeps such a file out of {@code git add},
 * and the tracked-file check catches what an ignore rule cannot -- a forced addition, or a file older than the rule.</p>
 */
class RepositoryCredentialHygieneTest {
    /** The ignore pattern, and the same pattern as an expression over a tracked path. */
    private record Credential(String ignorePattern, Pattern trackedPath) {
    }

    private static final List<Credential> CREDENTIALS = List.of(
            new Credential("remote-auth*.txt", Pattern.compile("(?:^|/)remote-auth[^/]*\\.txt$")),
            new Credential("*.p12", Pattern.compile("\\.p12$")),
            new Credential("*.jks", Pattern.compile("\\.jks$")),
            new Credential("*.pfx", Pattern.compile("\\.pfx$")),
            new Credential("*.pem", Pattern.compile("\\.pem$")),
            new Credential("*.key", Pattern.compile("\\.key$")));

    @Test
    void theRepositoryIgnoresIdentityFilesAndKeyMaterial() throws IOException {
        List<String> ignored = Files.readAllLines(repositoryRoot().resolve(".gitignore")).stream()
                .map(String::strip)
                .toList();

        for (Credential credential : CREDENTIALS) {
            assertTrue(ignored.contains(credential.ignorePattern()),
                    () -> ".gitignore must keep the line " + credential.ignorePattern()
                            + ": SECURITY.md forbids committing such a file and nothing else stops it");
        }
    }

    @Test
    void noTrackedFileIsAnIdentityFileOrKeyMaterial() throws IOException, InterruptedException {
        Path root = repositoryRoot();
        List<String> tracked = trackedFiles(root);
        assertTrue(tracked.size() > 100, "too few tracked files listed (" + tracked.size()
                + "): the rule would pass on nothing");

        List<String> offenders = new ArrayList<>();
        for (String path : tracked) {
            for (Credential credential : CREDENTIALS) {
                if (credential.trackedPath().matcher(path).find()) {
                    offenders.add(path + " (matches " + credential.ignorePattern() + ")");
                }
            }
        }
        assertTrue(offenders.isEmpty(),
                () -> "tracked file(s) that hold credentials or key material by their name; rename a fixture or "
                        + "generate it at test time: " + offenders);
    }

    @Test
    void aTrackedPathIsMatchedByItsNameWhereverItSits() {
        assertTrue(matchesAny("server.p12"));
        assertTrue(matchesAny("fixtures/tls/test.pem"));
        assertTrue(matchesAny("config/remote-auth.txt"));
        assertTrue(matchesAny("remote-auth-staging.txt"));
        assertFalse(matchesAny("docs/user/TEAM_REMOTE_SERVER.md"));
        assertFalse(matchesAny("src/main/java/com/morpheus/KeyStoreLoader.java"));
        assertFalse(matchesAny("notes/not-remote-auth.txt.md"));
    }

    @Test
    void aSearchPathEntryThatIsNotAPathnameIsPassedOver(@TempDir Path directory) throws IOException {
        Path git = Files.createFile(directory.resolve("git"));
        Path gitForWindows = Files.createFile(directory.resolve("git.exe"));
        assumeTrue(git.toFile().setExecutable(true) && gitForWindows.toFile().setExecutable(true),
                "this file system cannot mark a file executable");
        String malformed = "\"quoted entry\"" + File.pathSeparator + "nul\0entry";

        Optional<Path> found = gitExecutable(malformed + File.pathSeparator + directory);

        assertTrue(found.isPresent(), "the entry after the malformed ones must still be searched");
        assertEquals(directory.toAbsolutePath(), found.orElseThrow().getParent());
        assertEquals(Optional.empty(), gitExecutable(malformed));
        assertEquals(Optional.empty(), gitExecutable(null));
    }

    private static boolean matchesAny(String path) {
        return CREDENTIALS.stream().anyMatch(credential -> credential.trackedPath().matcher(path).find());
    }

    /**
     * The files Git tracks. Outside a checkout, or without Git, the check cannot conclude: it is skipped with that
     * reason rather than passed.
     */
    private static List<String> trackedFiles(Path root) throws IOException, InterruptedException {
        assumeTrue(Files.exists(root.resolve(".git")), "not a Git checkout: tracked files cannot be listed");
        Optional<Path> executable = gitExecutable(System.getenv("PATH"));
        assumeTrue(executable.isPresent(), "git is not available: tracked files cannot be listed");
        Process git = new ProcessBuilder(executable.orElseThrow().toString(), "ls-files")
                .directory(root.toFile())
                .redirectErrorStream(true)
                .start();
        String output = new String(git.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(git.waitFor(60, TimeUnit.SECONDS) && git.exitValue() == 0,
                () -> "git ls-files failed: " + output);
        return output.lines().map(String::strip).filter(line -> !line.isEmpty()).toList();
    }

    /**
     * The Git executable as an absolute path, found on the search path here rather than left to the operating system:
     * the process is then started from a file this test named, not from whatever a bare command resolves to. An entry
     * of the search path that is not a pathname -- quoted, as a Windows shell accepts -- is passed over like a directory
     * that holds no Git.
     */
    private static Optional<Path> gitExecutable(String searchPath) {
        if (searchPath == null) {
            return Optional.empty();
        }
        for (String directory : searchPath.split(File.pathSeparator)) {
            if (directory.isBlank()) {
                continue;
            }
            for (String name : List.of("git.exe", "git")) {
                try {
                    Path candidate = Path.of(directory).resolve(name).toAbsolutePath();
                    if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                        return Optional.of(candidate);
                    }
                } catch (InvalidPathException notAPathname) {
                    break;
                }
            }
        }
        return Optional.empty();
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
