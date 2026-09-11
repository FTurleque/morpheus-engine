package com.morpheus.integration.mcp;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Both MCP transports frame their wire through {@link BoundedStdioLineReader}, so strict UTF-8 is one property of
 * one reader rather than a rule each transport has to remember separately.
 */
class StrictUtf8Test {

    @Test
    void validUtf8IsAccepted() throws Exception {
        byte[] frame = "MORPHÉUS\n".getBytes(StandardCharsets.UTF_8);

        assertEquals("MORPHÉUS", new BoundedStdioLineReader(new ByteArrayInputStream(frame)).readLine(64));
    }

    @Test
    void malformedUtf8IsRejectedRatherThanReplaced() {
        byte[] malformed = {(byte) 0xC3, (byte) 0x28, (byte) '\n'};

        assertThrows(IOException.class,
                () -> new BoundedStdioLineReader(new ByteArrayInputStream(malformed)).readLine(64));
    }

    @Test
    void anIsolatedInvalidByteIsRefusedRatherThanDecodedToAReplacementCharacter() {
        byte[] invalid = {(byte) 0xFF, (byte) '\n'};

        assertThrows(IOException.class,
                () -> new BoundedStdioLineReader(new ByteArrayInputStream(invalid)).readLine(64));
    }
}
