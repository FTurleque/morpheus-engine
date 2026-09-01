package com.morpheus.application.files;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Objects;

/**
 * Canonical filesystem boundary for provider reads rooted in a MORPHEUS workspace.
 *
 * <p>The resolver rejects absolute/traversal paths, symbolic components, non-regular files and
 * any real path that escapes the canonical workspace root.</p>
 */
public final class SafeWorkspaceFileResolver {
    private static final int DEFAULT_MAX_UTF8_BYTES = 1024 * 1024;

    private final Path lexicalRoot;
    private final Path realRoot;
    private final ReadObserver readObserver;

    private SafeWorkspaceFileResolver(Path workspaceRoot, ReadObserver readObserver) throws IOException {
        this.lexicalRoot = Objects.requireNonNull(workspaceRoot, "workspaceRoot").toAbsolutePath().normalize();
        this.readObserver = Objects.requireNonNull(readObserver, "readObserver");
        if (Files.isSymbolicLink(lexicalRoot) || !Files.isDirectory(lexicalRoot, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("workspace root must be a real directory: " + lexicalRoot);
        }
        this.realRoot = lexicalRoot.toRealPath();
    }

    public static SafeWorkspaceFileResolver rootedAt(Path workspaceRoot) throws IOException {
        return new SafeWorkspaceFileResolver(workspaceRoot, ReadObserver.NONE);
    }

    static SafeWorkspaceFileResolver rootedAt(Path workspaceRoot, ReadObserver readObserver) throws IOException {
        return new SafeWorkspaceFileResolver(workspaceRoot, readObserver);
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
        return readUtf8(relativePath, DEFAULT_MAX_UTF8_BYTES);
    }

    /** Reads a confined strict UTF-8 file while refusing more than {@code maxBytes} before buffering the excess. */
    public String readUtf8(Path relativePath, int maxBytes) throws IOException {
        return decodeStrictUtf8(readBytes(relativePath, maxBytes), relativePath);
    }

    /**
     * Reads a confined binary file with the same race-resistant identity and content validation used for UTF-8 reads.
     */
    public byte[] readBytes(Path relativePath, int maxBytes) throws IOException {
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
            if (channel.size() != before.size()) {
                throw changedDuringRead(relativePath);
            }
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
                if (read > before.size() - total) {
                    throw changedDuringRead(relativePath);
                }
                output.write(buffer.array(), 0, read);
                total += read;
                buffer.clear();
            }
            if (total != before.size() || channel.size() != before.size()) {
                throw changedDuringRead(relativePath);
            }
            content = output.toByteArray();
        }

        readObserver.beforeFinalValidation(file);
        Path after = requireRegularFile(relativePath);
        BasicFileAttributes afterAttributes = Files.readAttributes(
                after, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!after.equals(file)
                || !sameIdentity(before, afterAttributes)
                || !contentStillMatches(after, afterAttributes, content)) {
            throw changedDuringRead(relativePath);
        }
        return content;
    }

    private boolean contentStillMatches(
            Path file,
            BasicFileAttributes expectedAttributes,
            byte[] expectedContent) throws IOException {
        int offset = 0;
        try (var channel = Files.newByteChannel(
                file, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            BasicFileAttributes verificationBefore = Files.readAttributes(
                    file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            if (!sameIdentity(expectedAttributes, verificationBefore)
                    || channel.size() != expectedContent.length) {
                return false;
            }
            ByteBuffer buffer = ByteBuffer.allocate(8192);
            int read;
            while ((read = channel.read(buffer)) != -1) {
                if (read == 0) {
                    continue;
                }
                if (offset > expectedContent.length - read) {
                    return false;
                }
                for (int index = 0; index < read; index++) {
                    if (buffer.array()[index] != expectedContent[offset + index]) {
                        return false;
                    }
                }
                offset += read;
                buffer.clear();
            }
            if (offset != expectedContent.length || channel.size() != expectedContent.length) {
                return false;
            }
        }
        BasicFileAttributes verificationAfter = Files.readAttributes(
                file, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        return sameIdentity(expectedAttributes, verificationAfter);
    }

    private String decodeStrictUtf8(byte[] content, Path relativePath) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content))
                    .toString();
        } catch (CharacterCodingException failure) {
            throw new IllegalArgumentException("workspace file is not valid UTF-8: " + relativePath, failure);
        }
    }

    private IllegalArgumentException inputLimitExceeded(Path relativePath, int maxBytes) {
        return new IllegalArgumentException(
                "workspace file exceeds maximum input size of " + maxBytes + " bytes: " + relativePath);
    }

    private IllegalArgumentException changedDuringRead(Path relativePath) {
        return new IllegalArgumentException("workspace file changed identity or metadata (including content) during read: "
                + relativePath);
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
        if (!before.isRegularFile() || before.isSymbolicLink()
                || !after.isRegularFile() || after.isSymbolicLink()) {
            return false;
        }
        if (before.size() != after.size()
                || !before.lastModifiedTime().equals(after.lastModifiedTime())
                || !before.creationTime().equals(after.creationTime())) {
            return false;
        }
        Object beforeKey = before.fileKey();
        Object afterKey = after.fileKey();
        if (beforeKey == null && afterKey == null) {
            return true;
        }
        return Objects.equals(beforeKey, afterKey);
    }

    @FunctionalInterface
    interface ReadObserver {
        ReadObserver NONE = file -> { };

        void beforeFinalValidation(Path file) throws IOException;
    }
}
