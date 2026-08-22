package com.morpheus.mcp;

import io.modelcontextprotocol.server.McpSyncServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    @Test
    void buildsBoundedServerWithInjectedStdioAndHandlesImmediateEof() {
        ByteArrayInputStream input = new ByteArrayInputStream(new byte[0]);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        McpSyncServer server = MorpheusMcpServer.build(
                tempDirectory.resolve("bounded-server.db"), input, output);
        try {
            assertNotNull(server);
        } finally {
            server.close();
        }
    }

    @Test
    void rejectsNullInjectedStdio() {
        Path database = tempDirectory.resolve("null-stdio.db");
        ByteArrayInputStream input = new ByteArrayInputStream(new byte[0]);
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        assertThrows(NullPointerException.class, () -> MorpheusMcpServer.build(database, null, output));
        assertThrows(NullPointerException.class, () -> MorpheusMcpServer.build(database, input, null));
    }
}
