package com.morpheus.integration.mcp;

import io.modelcontextprotocol.spec.McpSchema.JSONRPCNotification;
import io.modelcontextprotocol.spec.McpSchema.JSONRPCRequest;
import io.modelcontextprotocol.spec.McpSchema.JSONRPCResponse;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The refusal names what was refused by protocol shape and method only. A response is never described by its id,
 * which the peer chose, and no shape is described by its payload.
 */
class OutboundMessageRefusedExceptionTest {

    @Test
    void eachProtocolShapeIsNamedByItsShapeAndMethodOnly() {
        assertEquals("notification notifications/test",
                OutboundMessageRefusedException.kindOf(
                        new JSONRPCNotification("notifications/test", Map.of("value", "secret-payload"))));
        assertEquals("request tools/call",
                OutboundMessageRefusedException.kindOf(
                        new JSONRPCRequest("tools/call", "peer-chosen-id", Map.of("value", "secret-payload"))));
        assertEquals("response",
                OutboundMessageRefusedException.kindOf(
                        JSONRPCResponse.result("peer-chosen-id", Map.of("value", "secret-payload"))));
    }

    /**
     * Why {@code kindOf} has no case for a response without an id: the SDK refuses to build one. If an SDK upgrade
     * lifts that refusal, this test fails and the missing case must be decided, not inherited silently.
     */
    @Test
    void theSdkStillRefusesAResponseWithoutAnId() {
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> new JSONRPCResponse("2.0", null, Map.of("value", "secret-payload"), null));
        assertTrue(refused.getMessage().contains("MUST include an ID"), refused.getMessage());
    }

    @Test
    void theMessageCarriesSizesAndShapeButNoPeerIdOrPayload() {
        String message = new OutboundMessageRefusedException(
                JSONRPCResponse.result("peer-chosen-id", Map.of("value", "secret-payload")), 4097, 4096)
                .getMessage();

        assertEquals("MCP STDIO outbound response of 4097 bytes exceeds the 4096-byte frame bound and was not sent",
                message);
        assertFalse(message.contains("peer-chosen-id"), message);
        assertFalse(message.contains("secret-payload"), message);
    }
}
