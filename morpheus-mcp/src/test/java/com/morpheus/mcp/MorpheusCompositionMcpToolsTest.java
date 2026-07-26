package com.morpheus.mcp;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MorpheusCompositionMcpToolsTest {
    @TempDir
    Path tempDirectory;

    @Test
    void exposesTwoReadOnlyCompositionToolSpecifications() {
        var specifications = new MorpheusCompositionMcpTools(tempDirectory.resolve("morpheus.db")).specifications();

        assertEquals(2, specifications.size());
        specifications.forEach(specification -> assertNotNull(specification.tool()));
    }
}
