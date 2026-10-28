package com.morpheus.application.files;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The size and line count measured while decoding are the ones a second traversal of the decoded text would give,
 * including when a line terminator or a surrogate pair straddles two decoded chunks.
 */
class WorkspaceUtf8DocumentMeasureTest {
    private static final String BYTE_ORDER_MARK = "﻿";

    @TempDir
    Path workspace;

    @Test
    void theDecodingPassMeasuresWhatTheDecodedTextHolds() throws Exception {
        String chunkMinusOne = "x".repeat(8191);
        List<String> contents = List.of(
                "",
                "a",
                "\n",
                "\r",
                "\r\n",
                "a\r\n",
                "a\n\nb",
                "\r\r\n\n",
                "line\rnext",
                "é€😀\nx",
                BYTE_ORDER_MARK,
                BYTE_ORDER_MARK + "a\nb",
                BYTE_ORDER_MARK + BYTE_ORDER_MARK + "a",
                chunkMinusOne + "\r\ny",
                chunkMinusOne + "\r",
                chunkMinusOne + "\n\n",
                "😀".repeat(5_000) + "\n");
        SafeWorkspaceFileResolver resolver = SafeWorkspaceFileResolver.rootedAt(workspace);

        for (int index = 0; index < contents.size(); index++) {
            String content = contents.get(index);
            Path file = Path.of("case-" + index + ".md");
            Files.write(workspace.resolve(file), content.getBytes(StandardCharsets.UTF_8));

            WorkspaceUtf8Document document = resolver.readUtf8Document(file, 1024 * 1024);

            String expected = content.startsWith(BYTE_ORDER_MARK) ? content.substring(1) : content;
            assertEquals(expected, document.text(), "case " + index);
            assertEquals(expected.getBytes(StandardCharsets.UTF_8).length, document.utf8Bytes(), "case " + index);
            assertEquals(expected.lines().count(), document.lines(), "case " + index);
            assertEquals(expected, resolver.readUtf8(file, 1024 * 1024), "case " + index);
        }
    }

    @Test
    void aDocumentCannotBeSmallerThanItsText() {
        IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class,
                () -> new WorkspaceUtf8Document("abc", 2, 1));

        assertEquals("a UTF-8 document has at least one byte per UTF-16 unit of its text", refusal.getMessage());
    }

    @Test
    void aDocumentCannotHaveMoreLinesThanCharacters() {
        IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class,
                () -> new WorkspaceUtf8Document("a", 1, 2));

        assertEquals("a UTF-8 document has between zero and one line per character", refusal.getMessage());
    }
}
