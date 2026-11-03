package com.morpheus.architecture;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * The files of the repository a suite reads when it walks the whole tree, walked in one place.
 *
 * <p>A gate that walks the root sees everything below it, and a working directory holds more than the repository:
 * build output, generated distributions, and copies of the repository itself. An agent's workspace under
 * {@code .claude/worktrees} is a whole checkout, so a gate that counted the reactor POMs found three times as many,
 * and a gate that looks for a forbidden occurrence could find it, or miss it, in a copy that carries another
 * revision. Continuous integration starts from a fresh checkout and sees neither.</p>
 *
 * <p>The walk leaves out a directory named for generated output, the workspace copies under
 * {@code .claude/worktrees}, and any directory below the root that carries its own {@code .git}, file or directory,
 * which is what a Git worktree or a nested checkout is wherever it was created. The rest of {@code .claude} is read:
 * its hooks are PowerShell scripts held to the ASCII rule and its pages carry links. Symbolic links are not followed.
 * {@code RepositoryTreeOwnershipTest} refuses a walk of the root anywhere else in the test sources.</p>
 */
public final class RepositoryTree {

    /** Directories that are produced by a build, a tool or a validator, never part of the repository. */
    static final Set<String> GENERATED = Set.of(".git", "target", "dist", "validation-output", "node_modules", ".idea");

    /** Where an agent's workspace copy of the repository lives, relative to the root. */
    static final Path WORKSPACE_COPIES = Path.of(".claude", "worktrees");

    private RepositoryTree() {
    }

    /**
     * Every regular file of the repository at {@code root}, sorted, outside the directories this class leaves out. The
     * {@code .git} file of a root that is itself a worktree is a pointer to Git's data, not a file of the repository.
     */
    public static List<Path> files(Path root) {
        List<Path> files = new ArrayList<>();
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                    return directory.equals(root) || !isLeftOut(root, directory)
                            ? FileVisitResult.CONTINUE
                            : FileVisitResult.SKIP_SUBTREE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                    if (attributes.isRegularFile() && !file.getFileName().toString().equals(".git")) {
                        files.add(file);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        files.sort(null);
        return files;
    }

    private static boolean isLeftOut(Path root, Path directory) {
        return GENERATED.contains(directory.getFileName().toString())
                || root.relativize(directory).equals(WORKSPACE_COPIES)
                || Files.exists(directory.resolve(".git"), LinkOption.NOFOLLOW_LINKS);
    }
}
