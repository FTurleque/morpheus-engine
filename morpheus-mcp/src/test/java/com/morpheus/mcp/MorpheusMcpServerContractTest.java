package com.morpheus.mcp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MorpheusMcpServerContractTest {

    @TempDir
    Path tempDirectory;

    @Test
    void buildsSdkSpecificationsForEveryM10Tool() {
        MorpheusMcpToolCatalog catalog = new MorpheusMcpToolCatalog();
        MorpheusMcpToolService service = new MorpheusMcpToolService(tempDirectory.resolve("morpheus.db"));

        var specifications = catalog.tools().stream()
                .map(tool -> MorpheusMcpServer.tool(tool, service))
                .toList();

        assertEquals(14, specifications.size());
        specifications.forEach(specification -> assertNotNull(specification.tool()));
    }
}
