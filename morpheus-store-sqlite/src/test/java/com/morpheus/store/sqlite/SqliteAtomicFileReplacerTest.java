package com.morpheus.store.sqlite;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteAtomicFileReplacerTest {
    @TempDir
    Path temp;

    @Test
    void atomicMoveUnsupportedFailsClosedWithoutFallback() throws Exception {
        Path source = temp.resolve("staged.db");
        Path target = temp.resolve("morpheus.db");
        Files.writeString(source, "staged");
        Files.writeString(target, "live");

        IOException failure = assertThrows(IOException.class, () -> SqliteAtomicFileReplacer.replace(
                source,
                target,
                (from, to, options) -> {
                    throw new AtomicMoveNotSupportedException(from.toString(), to.toString(), "test filesystem");
                }));

        assertTrue(failure.getMessage().contains("requires atomic file replacement"));
        assertTrue(Files.exists(source));
        assertTrue(Files.exists(target));
        assertFalse(Files.readString(target).equals("staged"));
    }
}
