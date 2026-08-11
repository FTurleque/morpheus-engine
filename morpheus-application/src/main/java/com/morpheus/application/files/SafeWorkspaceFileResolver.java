package com.morpheus.application.files;

import java.io.IOException;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
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
        return readUtf8(relativePath, Integer.MAX_VALUE);
    }

    /** Reads a confined UTF-8 file while refusing more than {@code maxBytes} before buffering the excess. */
    public String readUtf8(Path relativePath, int maxBytes) throws IOException {
        if (maxBytes < 1) {
            throw new IllegalArgumentException("maxBytes must be positive");
        }
        Path file = requireRegularFile(relativePath);
        BasicFileAttributes before = Files.readAttributes(
                file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (before.size() > maxBytes) {
            throw inputLimitExceeded(relativePath, maxBytes);
        }
        byte[] content;
        try (var channel = Files.newByteChannel(
                file, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS);
             var output = new ByteArrayOutputStream()) {
            ByteBuffer buffer = ByteBuffer.allocate(8192);
            int total = 0;
            int read;
            while ((read = channel.read(buffer)) != -1) {
                if (read == 0) {
                    continue;
                }
                if (total > maxBytes - read) {
                    throw inputLimitExceeded(relativePath, maxBytes);
                }
                output.write(buffer.array(), 0, read);
                total += read;
                buffer.clear();
            }
            content = output.toByteArray();
        }
        // Detect replacement or mutation while the read was in progress.
        Path after = requireRegularFile(relativePath);
        BasicFileAttributes afterAttributes = Files.readAttributes(
                after, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!after.equals(file) || !sameIdentity(before, afterAttributes)) {
            throw new IllegalArgumentException("workspace file changed identity during read: " + relativePath);
        }
        return new String(content, StandardCharsets.UTF_8);
    }

    private IllegalArgumentException inputLimitExceeded(Path relativePath, int maxBytes) {
        return new IllegalArgumentException(
                "workspace file exceeds maximum input size of " + maxBytes + " bytes: " + relativePath);
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
        for (Path component : relativePath) {
            if (component.toString().equals("..")) {
                throw new IllegalArgumentException("workspace traversal is not allowed: " + relativePath);
            }
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
            Path noFollow = current.toRealPath(LinkOption.NOFOLLOW_LINKS);
            Path followed = current.toRealPath();
            if (Files.isSymbolicLink(current) || !noFollow.equals(followed)) {
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

    private boolean sameIdentity(BasicFileAttributes before, BasicFileAttributes after) {
        if (before.fileKey() != null && after.fileKey() != null
                && !before.fileKey().equals(after.fileKey())) {
            return false;
        }
        return before.size() == after.size()
                && before.lastModifiedTime().equals(after.lastModifiedTime());
    }
}
