package com.morpheus.integration.mcp;

import io.modelcontextprotocol.spec.McpSchema.JSONRPCMessage;
import io.modelcontextprotocol.spec.McpSchema.JSONRPCNotification;
import io.modelcontextprotocol.spec.McpSchema.JSONRPCRequest;

/**
 * An outbound message past the frame bound that has no pending peer request to answer, refused to its local sender.
 *
 * <p>Both STDIO transports raise it, and neither closes the session for it: outbound, the frame is MORPHEUS's own, so
 * overstepping the bound is a MORPHEUS defect the peer must not pay for (ADR-0106). It is deliberately not a
 * {@link MessageTooLargeException}, which stays the inbound, fail-closed signal.</p>
 */
final class OutboundMessageRefusedException extends IllegalStateException {
    OutboundMessageRefusedException(JSONRPCMessage message, int producedBytes, int maximum) {
        super("MCP STDIO outbound " + kindOf(message) + " of " + producedBytes + " bytes exceeds the " + maximum
                + "-byte frame bound and was not sent");
    }

    /**
     * Names what was refused by its protocol shape and method only, never by a payload fragment or a peer id. The SDK
     * refuses to build a response without an id, so a response needs no further distinction.
     */
    static String kindOf(JSONRPCMessage message) {
        if (message instanceof JSONRPCNotification notification) return "notification " + notification.method();
        if (message instanceof JSONRPCRequest request) return "request " + request.method();
        return "response";
    }
}
