package com.morpheus.mcp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.morpheus.application.product.ProductMetadata;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class MorpheusProductMcpToolsTest {
    @Test
    void mcpServerVersionComesFromSharedProductMetadata() {
        assertEquals(ProductMetadata.version(), MorpheusMcpServer.SERVER_VERSION);
    }

    @Test
    void exposesExplicitReadOnlyProductTools() {
        var specifications = new MorpheusProductMcpTools().specifications();
        Set<String> names = specifications.stream()
                .map(specification -> specification.tool().name())
                .collect(Collectors.toSet());

        assertEquals(2, specifications.size());
        assertTrue(names.contains(MorpheusProductMcpTools.INFO_TOOL));
        assertTrue(names.contains(MorpheusProductMcpTools.UPDATE_TOOL));
    }
}
