package com.morpheus.integration.nexus;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One NEXUS operation is bounded as a whole, and a peer that stays within its request timeout is never cut short by
 * that bound (ADR-0106, amendment of 8 October 2026).
 */
class NexusMcpOperationDeadlineTest {

    @Test
    void aPeerAnsweringEachCallJustUnderItsTimeoutCompletesTheOperation() {
        Duration timeout = Duration.ofSeconds(3);
        try (NexusMcpContextGateway gateway = new NexusMcpContextGateway(
                javaExecutable(), serverArguments(2_500), Map.of(), timeout)) {
            assertEquals("morpheus-engine", gateway.listProjects().getFirst().name());
        }
    }

    @Test
    void anOperationPastItsDeadlineEndsWithTheDeadlineBeforeTheRequestTimeout() {
        Duration timeout = Duration.ofSeconds(30);
        Duration operationDeadline = Duration.ofSeconds(8);
        try (NexusMcpContextGateway gateway = new NexusMcpContextGateway(
                javaExecutable(), serverArguments(20_000), Map.of(), timeout, operationDeadline)) {
            long started = System.nanoTime();

            NexusIntegrationException failure = assertThrows(
                    NexusIntegrationException.class, gateway::listProjects);

            Duration waited = Duration.ofNanos(System.nanoTime() - started);
            assertEquals("NEXUS MCP operation exceeded its 8000 ms deadline", failure.getMessage());
            assertTrue(waited.compareTo(Duration.ofSeconds(15)) < 0,
                    () -> "the deadline must end the operation, not the request timeout; waited " + waited);
        }
    }

    private static String javaExecutable() {
        return Path.of(
                System.getProperty("java.home"),
                "bin",
                System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win") ? "java.exe" : "java")
                .toString();
    }

    private static List<String> serverArguments(long toolDelayMillis) {
        String testClasspath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        return List.of("-Dfixture.toolDelayMillis=" + toolDelayMillis, "-cp", testClasspath,
                FixtureNexusMcpServer.class.getName());
    }
}
