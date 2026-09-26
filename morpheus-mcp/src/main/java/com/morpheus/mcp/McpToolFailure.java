package com.morpheus.mcp;

import io.modelcontextprotocol.spec.McpSchema;

/**
 * The one way a MORPHEUS MCP handler turns a refusal into a tool result (ADR-0102).
 *
 * <p>Eleven tool classes carried a byte-identical copy of {@code safeMessage} and built the error result in
 * two interchangeable shapes. Nothing arbitrated between them because the copies did not differ -- but eleven
 * copies of a thing that must not differ is exactly how the argument readers next to them diverged.</p>
 *
 * <p>{@link MorpheusProviderPluginMcpTools} deliberately does not use this class: it is a redaction boundary
 * rather than a mapping one, and answers with a stable failure code instead of the exception's own message.</p>
 */
final class McpToolFailure {
    private McpToolFailure() {
    }

    /** An exception with no message would answer an agent with an empty string; its type is at least a fact. */
    static String safeMessage(RuntimeException failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    static McpSchema.CallToolResult result(RuntimeException failure) {
        return McpSchema.CallToolResult.builder()
                .addTextContent(safeMessage(failure))
                .isError(true)
                .build();
    }
}
