package com.morpheus.architecture;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * The files Git tracks in the repository, for a check whose population is what the repository publishes rather than
 * what a working directory happens to hold: a local report that an ignore rule keeps out of every commit is not part
 * of it.
 */
public final class TrackedFiles {

    private TrackedFiles() {
    }

    /**
     * The files Git tracks, by the names Git stores ({@code -z}: a name that is not ASCII is not quoted), relative to
     * {@code root} and separated by {@code /}. Outside a checkout, or without Git, a check cannot conclude: on a
     * developer's machine it is skipped with that reason rather than passed, and in a build that is judged it fails.
     */
    public static List<String> list(Path root) throws IOException, InterruptedException {
        boolean checkout = Files.exists(root.resolve(".git"));
        Optional<Path> executable = gitExecutable(System.getenv("PATH"));
        if (System.getenv("CI") != null) {
            assertTrue(checkout && executable.isPresent(), "a build that is judged must list the tracked files: "
                    + "checkout=" + checkout + ", git=" + executable.isPresent());
        }
        assumeTrue(checkout, "not a Git checkout: tracked files cannot be listed");
        assumeTrue(executable.isPresent(), "git is not available: tracked files cannot be listed");
        Process git = new ProcessBuilder(executable.orElseThrow().toString(), "ls-files", "-z")
                .directory(root.toFile())
                .redirectErrorStream(true)
                .start();
        String output = new String(git.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertTrue(git.waitFor(60, TimeUnit.SECONDS) && git.exitValue() == 0,
                () -> "git ls-files failed: " + output);
        return Stream.of(output.split("\0")).filter(path -> !path.isEmpty()).toList();
    }

    /**
     * The Git executable as an absolute path, found on the search path here rather than left to the operating system:
     * the process is then started from a file this class named, not from whatever a bare command resolves to. An entry
     * of the search path that is not a pathname -- quoted, as a Windows shell accepts -- is passed over like a directory
     * that holds no Git.
     */
    public static Optional<Path> gitExecutable(String searchPath) {
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
}
