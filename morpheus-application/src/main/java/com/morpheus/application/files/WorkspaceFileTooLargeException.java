package com.morpheus.application.files;

import java.nio.file.Path;
import java.util.Objects;

/**
 * A workspace read refused because the file exceeds the byte bound the caller asked for.
 *
 * <p>Callers that translate a size overrun into their own budget failure recognize it by this type, never by a
 * phrase in the message: a message is free to change, and an unrelated failure can quote a pathname containing the
 * same words. It stays an {@link IllegalArgumentException} so every caller that already treats a refused read as
 * one keeps doing so.</p>
 */
public final class WorkspaceFileTooLargeException extends IllegalArgumentException {
    private final transient Path relativePath;
    private final int maximumBytes;

    WorkspaceFileTooLargeException(Path relativePath, int maximumBytes) {
        super("workspace file exceeds maximum input size of " + maximumBytes + " bytes: " + relativePath);
        this.relativePath = Objects.requireNonNull(relativePath, "relativePath");
        this.maximumBytes = maximumBytes;
    }

    public Path relativePath() {
        return relativePath;
    }

    public int maximumBytes() {
        return maximumBytes;
    }
}
