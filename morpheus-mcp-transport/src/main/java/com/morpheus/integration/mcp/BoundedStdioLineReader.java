package com.morpheus.integration.mcp;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

/**
 * Newline framing for MCP STDIO, bounded before anything is materialized.
 *
 * <p>Both transports read the same wire format, so they read it through the same code: a second copy of a framing
 * rule is a second place for a bound to drift. The reader owns the bytes it has read ahead of the current frame,
 * which is what lets it scan a block at a time instead of one {@code read()} call per byte -- measured at roughly
 * nine times faster on 4 KiB frames -- without ever letting an unread byte escape to a different consumer.</p>
 *
 * <p>The bound counts the bytes of the frame itself, excluding the newline that ends it and including a carriage
 * return that precedes one. A frame of exactly {@code maxBytes} is accepted; the byte after it is refused before
 * it is appended, so an oversized frame is never assembled in memory and never decoded.</p>
 */
final class BoundedStdioLineReader {
    private static final int BLOCK_BYTES = 8192;

    private final InputStream input;
    private final byte[] block = new byte[BLOCK_BYTES];
    private int position;
    private int limit;
    private boolean endOfStream;

    BoundedStdioLineReader(InputStream input) {
        this.input = Objects.requireNonNull(input, "input");
    }

    /**
     * Reads one frame, or {@code null} once the stream ends with nothing buffered.
     *
     * <p>A final frame that the peer did not terminate with a newline is still returned: the peer exiting is not a
     * reason to discard a message it finished writing.</p>
     */
    String readLine(int maxBytes) throws IOException {
        if (maxBytes < 1) throw new IllegalArgumentException("maxBytes must be positive");
        ByteArrayOutputStream frame = new ByteArrayOutputStream(Math.min(maxBytes, BLOCK_BYTES));
        boolean terminated = false;
        while (!terminated) {
            if (position == limit && !fill()) break;
            int newline = indexOfNewline();
            int end = newline < 0 ? limit : newline;
            int available = end - position;
            if (frame.size() + available > maxBytes) {
                throw new MessageTooLargeException(maxBytes);
            }
            frame.write(block, position, available);
            position = newline < 0 ? limit : newline + 1;
            terminated = newline >= 0;
        }
        if (!terminated && frame.size() == 0) return null;

        byte[] bytes = frame.toByteArray();
        int length = bytes.length;
        if (length > 0 && bytes[length - 1] == '\r') length--;
        return StrictUtf8.decode(bytes, length);
    }

    private int indexOfNewline() {
        for (int index = position; index < limit; index++) {
            if (block[index] == '\n') return index;
        }
        return -1;
    }

    /** Refills the block, remembering the end of the stream so a finished reader never blocks on it again. */
    private boolean fill() throws IOException {
        if (endOfStream) return false;
        int read;
        do {
            read = input.read(block, 0, block.length);
        } while (read == 0);
        position = 0;
        limit = Math.max(read, 0);
        if (read < 0) endOfStream = true;
        return read > 0;
    }
}
