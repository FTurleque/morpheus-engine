package com.morpheus.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.morpheus.application.security.ServerLocationDisclosure;
import io.modelcontextprotocol.spec.McpSchema;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * What a client of the STDIO server reads when a handler maps a caught refusal. The text is the failure's own,
 * unfiltered: a refusal of the application that names a path, here the one written by
 * {@code SafeWorkspaceFileResolver} for a workspace that is not a directory, reaches the client as it is, although
 * the filter of the remote boundary would replace it with the type name. Whether a local tool result may name a
 * server path is an open question of {@code mcp-tool-failure-contract}; this class records the answer the code
 * gives today, so that changing it is a visible decision.
 */
class McpToolFailureTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void aRefusalThatNamesAPathReachesTheClientUnfiltered() {
        String message = "workspace root must be a real directory: " + temporaryDirectory.toAbsolutePath();

        McpSchema.CallToolResult result = McpToolFailure.result(new IllegalArgumentException(message));

        assertTrue(result.isError());
        assertEquals(message, McpToolCall.text(result));
        assertFalse(ServerLocationDisclosure.isSafeToRelay(message),
                "the remote filter refuses this text: the local result is the one that keeps it");
    }

    @Test
    void aRefusalWithABlankMessageIsNamedByItsType() {
        McpSchema.CallToolResult result = McpToolFailure.result(new IllegalArgumentException("  "));

        assertTrue(result.isError());
        assertEquals("IllegalArgumentException", McpToolCall.text(result));
    }
}
