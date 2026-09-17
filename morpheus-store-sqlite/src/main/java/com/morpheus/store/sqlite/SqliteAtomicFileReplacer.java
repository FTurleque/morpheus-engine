package com.morpheus.store.sqlite;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Objects;

/** Atomic replacement primitive used by destructive SQLite maintenance operations. */
final class SqliteAtomicFileReplacer {
    private SqliteAtomicFileReplacer() {
    }

    static void replace(Path source, Path target) throws IOException {
        replace(source, target, Files::move);
    }

    static void replace(Path source, Path target, MoveOperation move) throws IOException {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(move, "move");
        try {
            move.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException unsupported) {
            throw new IOException(
                    "SQLite restore requires atomic file replacement; target filesystem does not support ATOMIC_MOVE",
                    unsupported);
        }
    }

    @FunctionalInterface
    interface MoveOperation {
        Path move(Path source, Path target, CopyOption... options) throws IOException;
    }
}
