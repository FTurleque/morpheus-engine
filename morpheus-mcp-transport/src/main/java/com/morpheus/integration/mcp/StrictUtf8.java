package com.morpheus.integration.mcp;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

/** Strict UTF-8 decoding for MCP STDIO frames. Malformed or unmappable input is rejected fail-closed. */
final class StrictUtf8 {
    private StrictUtf8() {
    }

    static String decode(byte[] bytes, int length) throws IOException {
        Objects.requireNonNull(bytes, "bytes");
        if (length < 0 || length > bytes.length) throw new IllegalArgumentException("invalid UTF-8 byte length");
        return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes, 0, length))
                .toString();
    }
}
