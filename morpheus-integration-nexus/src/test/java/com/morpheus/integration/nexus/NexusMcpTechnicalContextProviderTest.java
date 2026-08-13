package com.morpheus.integration.nexus;

import com.morpheus.application.context.TechnicalContextBundle;
import com.morpheus.application.context.TechnicalContextItem;
import com.morpheus.application.context.TechnicalContextOptions;
import com.morpheus.application.context.TechnicalContextRequest;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NexusMcpTechnicalContextProviderTest {

    @Test
    void disabledProviderReturnsExplicitAbsenceWithoutOpeningGateway() {
        NexusIntegrationSettings settings = new NexusIntegrationSettings(
                Optional.empty(), "java", Optional.empty(), Duration.ofSeconds(20), Optional.empty());
        NexusMcpTechnicalContextProvider provider = new NexusMcpTechnicalContextProvider(
                settings, () -> { throw new AssertionError("gateway must not be opened"); });

        var status = provider.status();
        var observation = provider.build(new TechnicalContextRequest(
                "Implement session expiration",
                TechnicalContextOptions.defaults("nexus-project")));

        assertEquals("DISABLED", status.state());
        assertEquals("DISABLED", observation.status().state());
        assertTrue(observation.bundle().isEmpty());
    }

    @Test
    void configuredProviderPassesProjectBudgetSourcesConstraintsAndExplainUnchanged() {
        NexusIntegrationSettings settings = configured();
        AtomicReference<TechnicalContextRequest> captured = new AtomicReference<>();
        NexusMcpTechnicalContextProvider provider = new NexusMcpTechnicalContextProvider(settings, () -> new NexusContextGateway() {
            @Override
            public List<ProjectInfo> listProjects() {
                return List.of(new ProjectInfo("project-1", "nexus-project", "READY"));
            }

            @Override
            public TechnicalContextBundle buildContext(TechnicalContextRequest request) {
                captured.set(request);
                return bundle(request);
            }

            @Override
            public void close() {
            }
        });

        TechnicalContextOptions options = new TechnicalContextOptions(
                "nexus-project", 3456, Set.of("FILE", "SYMBOL", "TEST"), Map.of("language", "java"), true);
        TechnicalContextRequest request = new TechnicalContextRequest("Change: CHG-1 Session expiration", options);
        var observation = provider.build(request);

        assertEquals(request, captured.get());
        assertEquals("AVAILABLE", observation.status().state());
        assertTrue(observation.bundle().isPresent());
        assertEquals(3456, observation.bundle().orElseThrow().tokenBudget());
        assertEquals(321, observation.bundle().orElseThrow().estimatedTokens());
        assertEquals(0.92, observation.bundle().orElseThrow().items().getFirst().score());
        assertTrue(observation.bundle().orElseThrow().explain());
    }

    @Test
    void gatewayFailureBecomesUnavailableObservationInsteadOfEscaping() {
        NexusMcpTechnicalContextProvider provider = new NexusMcpTechnicalContextProvider(
                configured(), () -> { throw new NexusIntegrationException("runner offline"); });

        var observation = provider.build(new TechnicalContextRequest(
                "Requirement: REQ-1 Authentication",
                TechnicalContextOptions.defaults("nexus-project")));

        assertEquals("UNAVAILABLE", observation.status().state());
        assertTrue(observation.status().configured());
        assertFalse(observation.bundle().isPresent());
        assertTrue(observation.status().message().contains("runner offline"));
    }

    private NexusIntegrationSettings configured() {
        return new NexusIntegrationSettings(
                Optional.of(Path.of("nexus-runner.jar")),
                "java",
                Optional.empty(),
                Duration.ofSeconds(20),
                Optional.of("0".repeat(64)),
                Optional.empty());
    }

    private TechnicalContextBundle bundle(TechnicalContextRequest request) {
        return new TechnicalContextBundle(
                "project-1",
                request.options().externalProject(),
                request.query(),
                request.options().explain(),
                12,
                request.options().tokenBudget(),
                321,
                List.of(new TechnicalContextItem(
                        "SYMBOL",
                        "src/main/java/SessionService.java",
                        "SessionService",
                        10,
                        42,
                        "class SessionService {}",
                        0.92,
                        Map.of("lexical", 0.4, "structural", 0.52),
                        List.of("matches requirement intent"),
                        321,
                        false)),
                List.of("src/generated/Generated.java"),
                Map.of("strategy", "hybrid"));
    }
}
