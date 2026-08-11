package com.morpheus.application.files;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Canonical filesystem boundary for provider reads rooted in a MORPHEUS workspace.
 *
 * <p>The resolver rejects absolute/traversal paths, symbolic components, non-regular files and
 * any real path that escapes the canonical workspace root.</p>
 */
public final class SafeWorkspaceFileResolver {
    private final Path lexicalRoot;
    private final Path realRoot;

    private SafeWorkspaceFileResolver(Path workspaceRoot) throws IOException {
        this.lexicalRoot = Objects.requireNonNull(workspaceRoot, "workspaceRoot").toAbsolutePath().normalize();
        if (Files.isSymbolicLink(lexicalRoot) || !Files.isDirectory(lexicalRoot, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("workspace root must be a real directory: " + lexicalRoot);
        }
        this.realRoot = lexicalRoot.toRealPath();
    }

    public static SafeWorkspaceFileResolver rootedAt(Path workspaceRoot) throws IOException {
        return new SafeWorkspaceFileResolver(workspaceRoot);
    }

    public Path requireDirectory(Path relativePath) throws IOException {
        Path lexical = lexical(relativePath);
        rejectSymbolicComponents(lexical);
        if (!Files.isDirectory(lexical, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("workspace directory does not exist: " + relativePath);
        }
        return requireContainedRealPath(lexical, relativePath);
    }

    public Path requireRegularFile(Path relativePath) throws IOException {
        Path lexical = lexical(relativePath);
        rejectSymbolicComponents(lexical);
        if (!Files.isRegularFile(lexical, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("workspace file does not exist or is not regular: " + relativePath);
        }
        return requireContainedRealPath(lexical, relativePath);
    }

    public String readUtf8(Path relativePath) throws IOException {
        Path file = requireRegularFile(relativePath);
        String content = Files.readString(file, StandardCharsets.UTF_8);
        // Detect a path swap that changes canonical containment while the read is in progress.
        Path after = requireRegularFile(relativePath);
        if (!after.equals(file)) {
            throw new IllegalArgumentException("workspace file changed identity during read: " + relativePath);
        }
        return content;
    }

    public Path lexicalRoot() {
        return lexicalRoot;
    }

    public Path realRoot() {
        return realRoot;
    }

    private Path lexical(Path relativePath) {
        Objects.requireNonNull(relativePath, "relativePath");
        if (relativePath.isAbsolute()) {
            throw new IllegalArgumentException("workspace-relative path must not be absolute: " + relativePath);
        }
        Path lexical = lexicalRoot.resolve(relativePath).normalize();
        if (!lexical.startsWith(lexicalRoot)) {
            throw new IllegalArgumentException("path escapes workspace: " + relativePath);
        }
        return lexical;
    }

    private void rejectSymbolicComponents(Path candidate) throws IOException {
        Path relative = lexicalRoot.relativize(candidate);
        Path current = lexicalRoot;
        for (Path component : relative) {
            current = current.resolve(component);
            if (Files.isSymbolicLink(current)) {
                throw new IllegalArgumentException("symbolic workspace path is not allowed: " + relative);
            }
        }
    }

    private Path requireContainedRealPath(Path lexical, Path relativePath) throws IOException {
        Path real = lexical.toRealPath();
        if (!real.startsWith(realRoot)) {
            throw new IllegalArgumentException("canonical path escapes workspace: " + relativePath);
        }
        return real;
    }
}
