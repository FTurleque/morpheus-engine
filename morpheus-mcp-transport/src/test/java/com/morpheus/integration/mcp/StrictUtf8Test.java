package com.morpheus.integration.mcp;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StrictUtf8Test {

    @Test
    void validUtf8IsAcceptedByBothTransports() throws Exception {
        byte[] frame = "MORPHÉUS\n".getBytes(StandardCharsets.UTF_8);

        assertEquals("MORPHÉUS", BoundedStdioClientTransport.readUtf8LineBounded(
                new ByteArrayInputStream(frame), 64));
        assertEquals("MORPHÉUS", BoundedStdioServerTransportProvider.readUtf8LineBounded(
                new ByteArrayInputStream(frame), 64));
    }

    @Test
    void malformedUtf8IsRejectedByBothTransports() {
        byte[] malformed = {(byte) 0xC3, (byte) 0x28, (byte) '\n'};

        assertThrows(IOException.class, () -> BoundedStdioClientTransport.readUtf8LineBounded(
                new ByteArrayInputStream(malformed), 64));
        assertThrows(IOException.class, () -> BoundedStdioServerTransportProvider.readUtf8LineBounded(
                new ByteArrayInputStream(malformed), 64));
    }
}
