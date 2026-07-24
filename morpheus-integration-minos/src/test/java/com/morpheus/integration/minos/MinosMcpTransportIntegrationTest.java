package com.morpheus.integration.minos;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MinosMcpTransportIntegrationTest {
    @Test
    void initializesListsRequiredToolsAndCallsBothToolsOverRealStdioProcess() {
        String javaExecutable = Path.of(
                System.getProperty("java.home"),
                "bin",
                System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win") ? "java.exe" : "java")
                .toString();
        String testClasspath = System.getProperty(
                "surefire.test.class.path",
                System.getProperty("java.class.path"));
        List<String> arguments = List.of(
                "-cp",
                testClasspath,
                FixtureMinosMcpServer.class.getName());

        try (MinosMcpCodeGateway gateway = new MinosMcpCodeGateway(
                javaExecutable, arguments, Map.of(), Duration.ofSeconds(10))) {
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
