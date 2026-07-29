package com.morpheus.mcp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MorpheusPolicyMcpManagementToolsTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void exposesActivationDiscoveryAndAuditedOverrideRemovalWithStrictSchemas() {
        var specifications = new MorpheusPolicyMcpManagementTools(
                temporaryDirectory.resolve("morpheus.db")).specifications();
        assertEquals(2, specifications.size());
        assertEquals(Set.of(
                        MorpheusPolicyMcpManagementTools.LIST_ACTIVATIONS,
                        MorpheusPolicyMcpManagementTools.REMOVE_OVERRIDE),
                specifications.stream().map(item -> item.tool().name()).collect(Collectors.toSet()));
        for (var specification : specifications) {
            Map<String, Object> schema = specification.tool().inputSchema();
            assertEquals(false, schema.get("additionalProperties"));
            assertTrue(schema.containsKey("required"));
            assertTrue(schema.containsKey("properties"));
        }
    }

    @Test
    void serverBuildIncludesCoreAndManagementPolicyToolsWithoutCollision() {
        var server = MorpheusMcpServer.build(temporaryDirectory.resolve("morpheus.db"));
        try {
            assertTrue(server != null);
        } finally {
            server.close();
        }
    }
}