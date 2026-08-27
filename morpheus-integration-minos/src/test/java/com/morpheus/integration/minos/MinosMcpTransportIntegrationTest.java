package com.morpheus.integration.minos;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MinosMcpTransportIntegrationTest {
    @Test
    void initializesListsRequiredToolsAndCallsBothToolsOverRealStdioProcess() {
        String javaExecutable = javaExecutable();
        List<String> arguments = serverArguments();

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

    @Test
    void rejectsSymbolPayloadThatExceedsRequestedLimit() {
        try (MinosMcpCodeGateway gateway = new MinosMcpCodeGateway(
                javaExecutable(), serverArguments(), Map.of(), Duration.ofSeconds(10))) {
            assertThrows(MinosIntegrationException.class,
                    () -> gateway.findSymbols("morpheus-engine", "fixture:too-many", 1));
        }
    }

    @Test
    void rejectsOversizedTextBeforeJsonDeserialization() {
        String oversized = "x".repeat(MinosMcpCodeGateway.MAX_MCP_RESPONSE_BYTES + 1);
        assertThrows(MinosIntegrationException.class,
                () -> MinosMcpCodeGateway.requireBoundedResponse(oversized, "fixture"));
    }

    private String javaExecutable() {
        return Path.of(
                System.getProperty("java.home"),
                "bin",
                System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win") ? "java.exe" : "java")
                .toString();
    }

    private List<String> serverArguments() {
        String testClasspath = System.getProperty(
                "surefire.test.class.path",
                System.getProperty("java.class.path"));
        return List.of("-cp", testClasspath, FixtureMinosMcpServer.class.getName());
    }
}
