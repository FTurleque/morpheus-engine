package com.morpheus.mcp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MorpheusProviderPluginMcpExposureTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void providerFilesystemDiscoveryAndExecutableProbeAreNotModelFacingByDefault() {
        var specifications = new MorpheusProviderPluginMcpTools().specifications();

        assertTrue(specifications.isEmpty());
    }

    @Test
    void explicitServerConfiguredDiscoveryStillNeverExposesExecutableProbe() {
        var specifications = new MorpheusProviderPluginMcpTools(temporaryDirectory).specifications();
        Set<String> names = specifications.stream()
                .map(specification -> specification.tool().name())
                .collect(Collectors.toSet());

        assertEquals(1, specifications.size());
        assertTrue(names.contains(MorpheusProviderPluginMcpTools.DISCOVER_TOOL));
        assertFalse(names.contains(MorpheusProviderPluginMcpTools.RETIRED_PROBE_TOOL));
    }
}
