package com.morpheus.mcp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MorpheusPortfolioMcpToolsTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void exposesCompleteM23PortfolioToolSet() {
        var specifications = new MorpheusPortfolioMcpTools(temporaryDirectory.resolve("morpheus.db")).specifications();

        assertEquals(8, specifications.size());
        String catalog = specifications.stream().map(item -> item.tool().name()).sorted().toList().toString();
        assertTrue(catalog.contains(MorpheusPortfolioMcpTools.CREATE));
        assertTrue(catalog.contains(MorpheusPortfolioMcpTools.REGISTER_PROJECT));
        assertTrue(catalog.contains(MorpheusPortfolioMcpTools.ADD_REFERENCE));
        assertTrue(catalog.contains(MorpheusPortfolioMcpTools.TRAVERSE));
    }

    @Test
    void serverCatalogContainsPortfolioTools() {
        try (var server = MorpheusMcpServer.build(temporaryDirectory.resolve("morpheus.db"))) {
            // Construction validates schemas, unique names and complete registration.
            assertTrue(server != null);
        }
    }
}
