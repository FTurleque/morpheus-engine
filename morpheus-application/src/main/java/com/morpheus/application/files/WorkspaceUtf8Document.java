package com.morpheus.application.files;

import java.util.Objects;

/**
 * A workspace file decoded as strict UTF-8, with the size and line count measured by the pass that decoded it.
 *
 * @param text the decoded text, without a leading byte order mark
 * @param utf8Bytes the encoded size of {@code text}, which excludes a dropped byte order mark
 * @param lines the number of lines {@link String#lines()} yields for {@code text}
 */
public record WorkspaceUtf8Document(String text, long utf8Bytes, long lines) {
    public WorkspaceUtf8Document {
        Objects.requireNonNull(text, "text");
        if (utf8Bytes < text.length()) {
            throw new IllegalArgumentException("a UTF-8 document has at least one byte per UTF-16 unit of its text");
        }
        if (lines < 0 || lines > text.length()) {
            throw new IllegalArgumentException("a UTF-8 document has between zero and one line per character");
        }
    }
}
