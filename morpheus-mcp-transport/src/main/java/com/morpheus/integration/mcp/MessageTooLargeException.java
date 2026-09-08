package com.morpheus.integration.mcp;

import java.io.IOException;

/**
 * A peer sent an STDIO frame past the configured byte bound.
 *
 * <p>It is distinct from an ordinary I/O failure because the transports react to it differently from a broken
 * pipe: a peer that oversteps a declared bound is failed closed rather than retried.</p>
 */
final class MessageTooLargeException extends IOException {
    MessageTooLargeException(int maximum) {
        super("MCP STDIO frame exceeds " + maximum + " bytes");
    }
}
