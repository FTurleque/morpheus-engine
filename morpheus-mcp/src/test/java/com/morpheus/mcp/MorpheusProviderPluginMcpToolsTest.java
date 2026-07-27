package com.morpheus.mcp;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MorpheusProviderPluginMcpToolsTest {
    @Test
    void exposesExplicitDiscoveryAndProbeTools() {
        var specifications = new MorpheusProviderPluginMcpTools().specifications();
        Set<String> names = specifications.stream()
                .map(specification -> specification.tool().name())
                .collect(Collectors.toSet());

        assertEquals(2, specifications.size());
        assertTrue(names.contains(MorpheusProviderPluginMcpTools.DISCOVER_TOOL));
        assertTrue(names.contains(MorpheusProviderPluginMcpTools.PROBE_TOOL));
    }
}
