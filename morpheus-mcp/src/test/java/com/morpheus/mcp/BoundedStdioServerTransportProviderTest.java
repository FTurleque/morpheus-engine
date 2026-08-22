package com.morpheus.mcp;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BoundedStdioServerTransportProviderTest {

    @Test
    void acceptsFrameAtExactByteLimit() throws Exception {
        String json = "{\"id\":1}";
        byte[] line = (json + "\n").getBytes(StandardCharsets.UTF_8);

        assertEquals(json, BoundedStdioServerTransportProvider.readUtf8LineBounded(
                new ByteArrayInputStream(line), json.getBytes(StandardCharsets.UTF_8).length));
    }

    @Test
    void rejectsFrameBeforeStringMaterializationPastLimit() {
        byte[] oversized = "12345\n".getBytes(StandardCharsets.UTF_8);

        assertThrows(BoundedStdioServerTransportProvider.MessageTooLargeException.class, () ->
                BoundedStdioServerTransportProvider.readUtf8LineBounded(
                        new ByteArrayInputStream(oversized), 4));
    }

    @Test
    void countsUtf8Bytes() {
        byte[] multibyte = "é\n".getBytes(StandardCharsets.UTF_8);

        assertThrows(BoundedStdioServerTransportProvider.MessageTooLargeException.class, () ->
                BoundedStdioServerTransportProvider.readUtf8LineBounded(
                        new ByteArrayInputStream(multibyte), 1));
    }

    @Test
    void returnsNullOnCleanEof() throws Exception {
        assertNull(BoundedStdioServerTransportProvider.readUtf8LineBounded(
                new ByteArrayInputStream(new byte[0]), 16));
    }
}
