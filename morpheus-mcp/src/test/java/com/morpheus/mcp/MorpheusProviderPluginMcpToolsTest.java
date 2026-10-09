package com.morpheus.mcp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MorpheusProviderPluginMcpToolsTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void defaultServerConfigurationExposesNoProviderFilesystemTool() {
        assertEquals(List.of(), new MorpheusProviderPluginMcpTools().specifications());
    }

    @Test
    void configuredDiscoveryHasNoCallerControlledFilesystemPath() {
        var specifications = new MorpheusProviderPluginMcpTools(temporaryDirectory).specifications();

        assertEquals(1, specifications.size());
        assertEquals(MorpheusProviderPluginMcpTools.DISCOVER_TOOL, specifications.getFirst().tool().name());
        Map<String, Object> schema = specifications.getFirst().tool().inputSchema();
        assertEquals(Map.of(), schema.get("properties"));
        assertEquals(List.of(), schema.get("required"));
        assertEquals(false, schema.get("additionalProperties"));
    }
}
