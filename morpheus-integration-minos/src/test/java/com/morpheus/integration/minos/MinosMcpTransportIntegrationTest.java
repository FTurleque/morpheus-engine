package com.morpheus.integration.minos;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MinosMcpTransportIntegrationTest {
    @Test
    void initializesListsRequiredToolsAndCallsBothToolsOverRealStdioProcess() {
        String java = Path.of(
                System.getProperty("java.home"),
                "bin",
                System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win") ? "java.exe" : "java")
                .toString();
        List<String> arguments = List.of(
                "-cp",
                System.getProperty("java.class.path"),
                FixtureMinosMcpServer.class.getName());

        try (MinosMcpCodeGateway gateway = new MinosMcpCodeGateway(
                java, arguments, Map.of(), Duration.ofSeconds(10))) {
            MinosCodeGateway.IndexStatus status = gateway.indexStatus("morpheus-engine");
            assertEquals("project-123", status.projectId());
            assertEquals("snapshot-abc", status.activeSnapshotId());

            List<MinosCodeGateway.Symbol> symbols = gateway.findSymbols(
                    "morpheus-engine", "symbol:RequirementService", 20);
            assertEquals(1, symbols.size());
            assertEquals("symbol:RequirementService", symbols.getFirst().symbolKey());
            assertEquals("com.morpheus.RequirementService", symbols.getFirst().qualifiedName());
            assertEquals("run-99", symbols.getFirst().origin().indexRunId());
        }
    }
}
