package com.morpheus.mcp;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MorpheusProviderPluginMcpToolsTest {
    @Test
    void exposesOnlyProviderMetadataDiscovery() {
        var specifications = new MorpheusProviderPluginMcpTools().specifications();

        assertEquals(1, specifications.size());
        assertEquals(MorpheusProviderPluginMcpTools.DISCOVER_TOOL, specifications.getFirst().tool().name());
    }
}
