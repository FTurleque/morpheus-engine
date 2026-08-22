package com.morpheus.integration.mcp;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BoundedStdioServerTransportProviderTest {

    @Test
    void acceptsInboundFrameAtConfiguredBoundary() throws Exception {
        String json = "{\"jsonrpc\":\"2.0\"}";
        byte[] frame = (json + "\n").getBytes(StandardCharsets.UTF_8);

        assertEquals(json, BoundedStdioServerTransportProvider.readUtf8LineBounded(
                new ByteArrayInputStream(frame), json.getBytes(StandardCharsets.UTF_8).length));
    }

    @Test
    void rejectsInboundFrameBeforeMaterializingPastByteLimit() {
        byte[] frame = "12345\n".getBytes(StandardCharsets.UTF_8);

        assertThrows(BoundedStdioServerTransportProvider.MessageTooLargeException.class, () ->
                BoundedStdioServerTransportProvider.readUtf8LineBounded(new ByteArrayInputStream(frame), 4));
    }

    @Test
    void countsUtf8BytesInsteadOfCharacters() {
        byte[] frame = "é\n".getBytes(StandardCharsets.UTF_8);

        assertThrows(BoundedStdioServerTransportProvider.MessageTooLargeException.class, () ->
                BoundedStdioServerTransportProvider.readUtf8LineBounded(new ByteArrayInputStream(frame), 1));
    }
}
