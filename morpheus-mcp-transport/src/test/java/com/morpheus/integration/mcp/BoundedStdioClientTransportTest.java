package com.morpheus.integration.mcp;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BoundedStdioClientTransportTest {

    @Test
    void acceptsFrameAtExactByteLimitAndStripsCrLfDelimiter() throws Exception {
        String json = "{\"id\":1}";
        byte[] line = (json + "\r\n").getBytes(StandardCharsets.UTF_8);

        assertEquals(json, BoundedStdioClientTransport.readUtf8LineBounded(
                new ByteArrayInputStream(line), json.getBytes(StandardCharsets.UTF_8).length + 1));
    }

    @Test
    void rejectsFrameBeforeCreatingStringPastByteLimit() {
        byte[] oversized = "12345\n".getBytes(StandardCharsets.UTF_8);

        assertThrows(BoundedStdioClientTransport.MessageTooLargeException.class, () ->
                BoundedStdioClientTransport.readUtf8LineBounded(new ByteArrayInputStream(oversized), 4));
    }

    @Test
    void countsUtf8BytesRatherThanCharacters() {
        byte[] multibyte = "é\n".getBytes(StandardCharsets.UTF_8);

        assertThrows(BoundedStdioClientTransport.MessageTooLargeException.class, () ->
                BoundedStdioClientTransport.readUtf8LineBounded(new ByteArrayInputStream(multibyte), 1));
    }

    @Test
    void returnsNullForCleanEndOfStream() throws Exception {
        assertNull(BoundedStdioClientTransport.readUtf8LineBounded(
                new ByteArrayInputStream(new byte[0]), 16));
    }
}
