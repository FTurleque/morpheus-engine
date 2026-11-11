package com.morpheus.mcp.server;

import com.morpheus.application.operability.OperationalRecorder;
import com.morpheus.application.reference.ExternalReferenceResolverRegistry;
import com.morpheus.mcp.MorpheusMcpToolCatalog;
import com.morpheus.mcp.MorpheusRequirementMcpTools;
import io.modelcontextprotocol.server.McpSyncServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MorpheusMcpServerContractTest {

    @TempDir
    Path tempDirectory;

    @Test
    void buildsSdkSpecificationsForEveryM10Tool() {
        MorpheusMcpToolCatalog catalog = new MorpheusMcpToolCatalog();
        List<String> catalogued = catalog.tools().stream().map(MorpheusMcpToolCatalog.ToolDefinition::name).toList();

        var specifications = MorpheusMcpServer.toolSpecifications(
                        tempDirectory.resolve("morpheus.db"),
                        new ExternalReferenceResolverRegistry(List.of()),
                        MorpheusMcpServer.unconfiguredTechnicalContext(),
                        MorpheusMcpServer.deniedWriteCapability(),
                        OperationalRecorder.noop())
                .stream()
                .filter(specification -> catalogued.contains(specification.tool().name()))
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

        NullPointerException missingInput = assertThrows(
                NullPointerException.class, () -> MorpheusMcpServer.build(database, null, output));
        assertEquals("inputStream", missingInput.getMessage());
        NullPointerException missingOutput = assertThrows(
                NullPointerException.class, () -> MorpheusMcpServer.build(database, input, null));
        assertEquals("outputStream", missingOutput.getMessage());
    }

    @Test
    void aCatalogueToolServedByTwoClassesStopsTheServerFromBeingBuilt() {
        var once = new MorpheusRequirementMcpTools(tempDirectory.resolve("twice.db"), OperationalRecorder.noop()).specifications();
        var twice = new ArrayList<>(once);
        twice.addAll(once);

        IllegalStateException refused = assertThrows(IllegalStateException.class, () -> MorpheusMcpServer.byName(twice));

        assertEquals("catalogue tool served by two capability classes: " + once.getFirst().tool().name(), refused.getMessage());
        assertEquals(List.copyOf(once), List.copyOf(MorpheusMcpServer.byName(once).values()));
    }
}
