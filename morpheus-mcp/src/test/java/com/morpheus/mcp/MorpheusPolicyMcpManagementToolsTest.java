package com.morpheus.mcp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MorpheusPolicyMcpManagementToolsTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void exposesOnlyReadOnlyActivationDiscoveryWithStrictSchema() {
        var specifications = new MorpheusPolicyMcpManagementTools(
                temporaryDirectory.resolve("morpheus.db")).specifications();

        assertEquals(1, specifications.size());
        assertEquals(MorpheusPolicyMcpManagementTools.LIST_ACTIVATIONS, specifications.getFirst().tool().name());
        Map<String, Object> schema = specifications.getFirst().tool().inputSchema();
        assertEquals(false, schema.get("additionalProperties"));
        assertTrue(schema.containsKey("required"));
        assertTrue(schema.containsKey("properties"));
    }

    @Test
    void serverBuildIncludesCoreAndReadOnlyPolicyManagementToolsWithoutCollision() {
        var server = MorpheusMcpServer.build(
                temporaryDirectory.resolve("morpheus.db"),
                java.io.InputStream.nullInputStream(),
                java.io.OutputStream.nullOutputStream());
        try {
            assertTrue(server != null);
        } finally {
            server.close();
        }
    }
}
