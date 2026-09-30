package com.morpheus.integration.mcp;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The single STDIO framing rule both MCP transports read the wire through.
 *
 * <p>The bound has to hold on the byte that would break it, not on the string that would have been built from it:
 * an oversized frame must never be assembled, never be decoded, and never reach a JSON parser. The boundary is
 * therefore checked from both sides, in bytes rather than characters, and across a block refill so a frame that
 * straddles the reader's internal buffer is treated exactly like one that does not.</p>
 */
class BoundedStdioLineReaderTest {
    @Test
    void anEmptyStreamYieldsNoFrame() throws Exception {
        assertNull(reader("").readLine(16));
    }

    @Test
    void anEmptyLineIsAFrameRatherThanEndOfStream() throws Exception {
        BoundedStdioLineReader reader = reader("\n\n");

        assertEquals("", reader.readLine(16));
        assertEquals("", reader.readLine(16));
        assertNull(reader.readLine(16));
    }

    @Test
    void aSmallFrameIsReturnedWithoutItsDelimiter() throws Exception {
        assertEquals("{\"id\":1}", reader("{\"id\":1}\n").readLine(64));
    }

    @Test
    void aCrLfDelimiterIsStrippedWhole() throws Exception {
        assertEquals("{\"id\":1}", reader("{\"id\":1}\r\n").readLine(64));
        assertEquals("", reader("\r\n").readLine(64));
    }

    /** A lone carriage return inside the frame is content, not a delimiter. */
    @Test
    void onlyATrailingCarriageReturnIsStripped() throws Exception {
        assertEquals("a\rb", reader("a\rb\n").readLine(64));
    }

    @Test
    void aFrameOfExactlyTheLimitIsAccepted() throws Exception {
        assertEquals("12345", reader("12345\n").readLine(5));
    }

    @Test
    void oneByteBeyondTheLimitIsRefused() {
        assertThrows(MessageTooLargeException.class, () -> reader("123456\n").readLine(5));
    }

    /** The carriage return of a CRLF delimiter counts against the bound, exactly as it did byte by byte. */
    @Test
    void theCarriageReturnOfACrLfCountsTowardsTheLimit() throws Exception {
        assertEquals("12345", reader("12345\r\n").readLine(6));
        assertThrows(MessageTooLargeException.class, () -> reader("12345\r\n").readLine(5));
    }

    @Test
    void theLimitCountsUtf8BytesRatherThanCharacters() {
        MessageTooLargeException refusal =
                assertThrows(MessageTooLargeException.class, () -> reader("é\n").readLine(1));

        assertTrue(refusal.getMessage().contains("exceeds 1 bytes"));
    }

    @Test
    void validMultibyteUtf8SurvivesTheRoundTrip() throws Exception {
        assertEquals("MORPHÉUS ✓", reader("MORPHÉUS ✓\n").readLine(64));
    }

    @Test
    void malformedUtf8IsRefusedRatherThanReplaced() {
        byte[] malformed = {(byte) 0xC3, (byte) 0x28, (byte) '\n'};

        assertThrows(IOException.class, () -> new BoundedStdioLineReader(
                new ByteArrayInputStream(malformed)).readLine(64));
    }

    @Test
    void aFinalFrameWithoutANewlineIsStillDelivered() throws Exception {
        BoundedStdioLineReader reader = reader("first\nsecond");

        assertEquals("first", reader.readLine(64));
        assertEquals("second", reader.readLine(64));
        assertNull(reader.readLine(64));
    }

    @Test
    void anUnterminatedFinalFrameIsStillBounded() {
        assertThrows(MessageTooLargeException.class, () -> reader("123456").readLine(5));
    }

    @Test
    void successiveFramesAreReadInOrderWithoutLosingBufferedBytes() throws Exception {
        BoundedStdioLineReader reader = reader("one\ntwo\r\nthree\n\nfive\n");

        assertEquals(List.of("one", "two", "three", "", "five"), drain(reader, 64));
    }

    /**
     * Frames much larger than the reader's internal block force several refills per frame, which is where a
     * block-oriented reader could lose or duplicate bytes across the seam.
     */
    @Test
    void framesSpanningManyBlockRefillsAreReassembledExactly() throws Exception {
        List<String> expected = new ArrayList<>();
        StringBuilder wire = new StringBuilder();
        for (int index = 0; index < 8; index++) {
            String frame = Character.toString('a' + index).repeat(20_000 + index);
            expected.add(frame);
            wire.append(frame).append('\n');
        }

        assertEquals(expected, drain(reader(wire.toString()), 65_536));
    }

    /** A stream that hands over one byte at a time must produce the same frames as one that hands over blocks. */
    @Test
    void aTricklingStreamProducesTheSameFrames() throws Exception {
        byte[] wire = "one\ntwo\nthree".getBytes(StandardCharsets.UTF_8);

        BoundedStdioLineReader reader = new BoundedStdioLineReader(new InputStream() {
            private int index;

            @Override
            public int read() {
                return index < wire.length ? wire[index++] & 0xFF : -1;
            }

            @Override
            public int read(byte[] target, int off, int len) {
                if (index >= wire.length) return -1;
                target[off] = wire[index++];
                return 1;
            }
        });

        assertEquals(List.of("one", "two", "three"), drain(reader, 64));
    }

    @Test
    void aNonPositiveLimitIsRefusedBeforeAnythingIsRead() {
        assertThrows(IllegalArgumentException.class, () -> reader("frame\n").readLine(0));
        assertThrows(IllegalArgumentException.class, () -> reader("frame\n").readLine(-1));
        assertThrows(NullPointerException.class, () -> new BoundedStdioLineReader(null));
    }

    /** A refused frame stays refused: the reader must not resynchronise onto the remainder of an oversized one. */
    @Test
    void anOversizedFrameFailsTheStreamRatherThanBeingSkipped() {
        BoundedStdioLineReader reader = reader("123456\nsmall\n");

        assertThrows(MessageTooLargeException.class, () -> reader.readLine(5));
    }

    private static List<String> drain(BoundedStdioLineReader reader, int maxBytes) throws IOException {
        List<String> frames = new ArrayList<>();
        String frame;
        while ((frame = reader.readLine(maxBytes)) != null) {
            frames.add(frame);
        }
        return frames;
    }

    private static BoundedStdioLineReader reader(String wire) {
        return new BoundedStdioLineReader(
                new ByteArrayInputStream(wire.getBytes(StandardCharsets.UTF_8)));
    }
}
