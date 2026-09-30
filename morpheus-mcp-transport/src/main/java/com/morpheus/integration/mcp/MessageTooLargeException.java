package com.morpheus.integration.mcp;

import java.io.IOException;

/**
 * An STDIO frame is past the configured byte bound in a direction where the only safe reaction is to fail closed.
 *
 * <p>Inbound, this is the defensive case: a peer sent a frame past the declared bound, and a peer that oversteps
 * one is failed closed rather than retried. It is distinct from an ordinary I/O failure because the transports
 * react to it differently from a broken pipe.</p>
 *
 * <p>Outbound, the frame is MORPHEUS's own and has its own treatment in both transports: an oversized server
 * response is answered with a {@code MCP_RESPONSE_TOO_LARGE} error, and any other oversized message is refused to its
 * sender with an {@link OutboundMessageRefusedException}; the session survives either way. This exception is raised
 * outbound only by {@link BoundedStdioServerTransportProvider}, when the substitute error itself cannot fit the
 * bound.</p>
 */
final class MessageTooLargeException extends IOException {
    MessageTooLargeException(int maximum) {
        super("MCP STDIO frame exceeds " + maximum + " bytes");
    }
}
