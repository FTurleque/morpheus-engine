package com.morpheus.integration.nexus;

import com.morpheus.application.context.TechnicalContextOptions;
import com.morpheus.application.context.TechnicalContextRequest;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NexusMcpTransportIntegrationTest {
    @Test
    void initializesListsRequiredToolsAndBuildsBudgetedContextOverRealStdioProcess() {
        try (NexusMcpContextGateway gateway = new NexusMcpContextGateway(
                javaExecutable(), serverArguments(), Map.of(), Duration.ofSeconds(10))) {
            var projects = gateway.listProjects();
            assertEquals(1, projects.size());
            assertEquals("morpheus-engine", projects.getFirst().name());
            assertEquals("READY", projects.getFirst().indexStatus());

            var bundle = gateway.buildContext(new TechnicalContextRequest(
                    "Change: CHG-1 Session expiration",
                    new TechnicalContextOptions(
                            "morpheus-engine",
                            3456,
                            Set.of("FILE", "SYMBOL"),
                            Map.of("language", "java"),
                            true)));

            assertEquals("nexus-project-id", bundle.projectId());
            assertEquals("morpheus-engine", bundle.projectName());
            assertEquals(3456, bundle.tokenBudget());
            assertEquals(111, bundle.estimatedTokens());
            assertTrue(bundle.explain());
            assertEquals(1, bundle.items().size());
            assertEquals("SYMBOL", bundle.items().getFirst().type());
            assertEquals(0.91, bundle.items().getFirst().score());
            assertFalse(bundle.items().getFirst().truncated());
            assertEquals(List.of("target/generated.txt"), bundle.excluded());
            assertEquals("hybrid", bundle.metadata().get("strategy"));
        }
    }

    @Test
    void rejectsOversizedTextBeforeJsonDeserialization() {
        String oversized = "x".repeat(NexusMcpContextGateway.MAX_MCP_RESPONSE_BYTES + 1);
        assertThrows(NexusIntegrationException.class,
                () -> NexusMcpContextGateway.requireBoundedResponse(oversized, "fixture"));
    }

    @Test
    void rejectsExternalCollectionCardinalityAboveConfiguredBudgets() {
        assertThrows(NexusIntegrationException.class,
                () -> NexusMcpContextGateway.requireCardinality(
                        NexusMcpContextGateway.MAX_CONTEXT_ITEMS + 1,
                        NexusMcpContextGateway.MAX_CONTEXT_ITEMS,
                        "context items"));
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
        return List.of("-cp", testClasspath, FixtureNexusMcpServer.class.getName());
    }
}
