package com.morpheus.application.query.export;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Complete bounded read-only export payload. */
public record QueryExport(QueryExportFormat format, String mediaType, String content) {
    public QueryExport {
        Objects.requireNonNull(format, "format");
        Objects.requireNonNull(mediaType, "mediaType");
        mediaType = mediaType.trim();
        if (mediaType.isEmpty()) {
            throw new IllegalArgumentException("mediaType must not be blank");
        }
        Objects.requireNonNull(content, "content");
    }

    public byte[] utf8() {
        return content.getBytes(StandardCharsets.UTF_8);
    }

    public int byteCount() {
        return utf8().length;
    }
}
