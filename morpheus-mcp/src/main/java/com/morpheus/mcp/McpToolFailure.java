package com.morpheus.mcp;

import io.modelcontextprotocol.spec.McpSchema;

/**
 * The one way a MORPHEUS MCP handler turns a refusal into a tool result (ADR-0102).
 *
 * <p>Eleven tool classes carried a byte-identical copy of {@code safeMessage} and built the error result in
 * two interchangeable shapes. Nothing arbitrated between them because the copies did not differ -- but eleven
 * copies of a thing that must not differ is exactly how the argument readers next to them diverged.</p>
 *
 * <p>A refusal reaches here in one of two forms, and both end in {@link #refusal(String)}, the only place in the
 * module that sets {@code isError(true)}: an exception the handler caught ({@link #result}), or a refusal the
 * service <em>returned</em> as a value whose body is already the structured answer. The second form is not an
 * exception in disguise: {@code apply_change_lifecycle_transition} answers a stale revision, a missing capability
 * or a missing confirmation with a result state, and the body keeps that state and its reason.</p>
 *
 * <p>{@link MorpheusProviderPluginMcpTools} does not use {@link #result}: it is a redaction boundary rather than
 * a mapping one, and answers with a stable failure code instead of the exception's own message. It still builds
 * its error through {@link #refusal(String)}.</p>
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
        return refusal(safeMessage(failure));
    }

    /** {@code body} is passed through unchanged: a structured refusal must not lose a field on its way to the agent. */
    static McpSchema.CallToolResult refusal(String body) {
        return McpSchema.CallToolResult.builder()
                .addTextContent(body)
                .isError(true)
                .build();
    }
}
