package com.morpheus.integration.minos;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One MINOS operation is bounded as a whole, and a peer that stays within its request timeout is never cut short by
 * that bound (ADR-0106, amendment of 8 October 2026).
 */
class MinosMcpOperationDeadlineTest {

    @Test
    void aPeerAnsweringEachCallJustUnderItsTimeoutCompletesTheOperation() {
        Duration timeout = Duration.ofSeconds(3);
        try (MinosMcpCodeGateway gateway = new MinosMcpCodeGateway(
                javaExecutable(), serverArguments("-Dfixture.toolDelayMillis=2500"), Map.of(), timeout)) {
            assertEquals("project-123", gateway.indexStatus("morpheus-engine").projectId());
            assertEquals(1, gateway.findSymbols("morpheus-engine", "symbol:RequirementService", 20).symbols().size());
        }
    }

    @Test
    void anOperationPastItsDeadlineEndsWithTheDeadlineBeforeTheRequestTimeout() {
        Duration timeout = Duration.ofSeconds(30);
        Duration operationDeadline = Duration.ofSeconds(8);
        try (MinosMcpCodeGateway gateway = new MinosMcpCodeGateway(
                javaExecutable(), serverArguments("-Dfixture.toolDelayMillis=20000"), Map.of(), timeout,
                operationDeadline)) {
            long started = System.nanoTime();

            MinosIntegrationException failure = assertThrows(
                    MinosIntegrationException.class, () -> gateway.indexStatus("morpheus-engine"));

            Duration waited = Duration.ofNanos(System.nanoTime() - started);
            assertEquals("MINOS MCP operation exceeded its 8000 ms deadline", failure.getMessage());
            assertTrue(waited.compareTo(Duration.ofSeconds(15)) < 0,
                    () -> "the deadline must end the operation, not the request timeout; waited " + waited);
        }
    }

    @Test
    void aPeerThatNeverAnswersIsCutAtTheOperationDeadlineDuringInitialization() {
        long started = System.nanoTime();

        MinosIntegrationException failure = assertThrows(MinosIntegrationException.class,
                () -> new MinosMcpCodeGateway(
                        javaExecutable(), serverArguments("-Dfixture.silent=true"), Map.of(), Duration.ofSeconds(30),
                        Duration.ofSeconds(4)));

        Duration waited = Duration.ofNanos(System.nanoTime() - started);
        assertEquals("MINOS MCP operation exceeded its 4000 ms deadline", failure.getMessage());
        assertTrue(waited.compareTo(Duration.ofSeconds(15)) < 0,
                () -> "the deadline must end the initialization, not the SDK's own timeout; waited " + waited);
    }

    @Test
    void aPeerThatExitsBeforeAnsweringFailsToStartWithoutBlamingTheDeadline() {
        MinosIntegrationException failure = assertThrows(MinosIntegrationException.class,
                () -> new MinosMcpCodeGateway(
                        javaExecutable(), serverArguments("-Dfixture.exitAtStart=true"), Map.of(),
                        Duration.ofSeconds(3)));

        assertEquals("cannot start or initialize MINOS MCP server", failure.getMessage());
    }

    @Test
    void aPeerThatDiesDuringACallIsACallFailureNotADeadline() {
        try (MinosMcpCodeGateway gateway = new MinosMcpCodeGateway(
                javaExecutable(), serverArguments("-Dfixture.exitOnCall=true"), Map.of(), Duration.ofSeconds(5))) {
            MinosIntegrationException failure = assertThrows(
                    MinosIntegrationException.class, () -> gateway.indexStatus("morpheus-engine"));

            assertTrue(failure.getMessage().startsWith("MINOS MCP call failed: "), failure.getMessage());
        }
    }

    private static String javaExecutable() {
        return Path.of(
                System.getProperty("java.home"),
                "bin",
                System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win") ? "java.exe" : "java")
                .toString();
    }

    private static List<String> serverArguments(String... fixtureProperties) {
        String testClasspath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        List<String> arguments = new ArrayList<>(List.of(fixtureProperties));
        arguments.addAll(List.of("-cp", testClasspath, FixtureMinosMcpServer.class.getName()));
        return arguments;
    }
}
