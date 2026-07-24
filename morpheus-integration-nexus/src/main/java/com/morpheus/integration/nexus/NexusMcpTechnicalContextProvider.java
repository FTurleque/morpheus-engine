package com.morpheus.integration.nexus;

import com.morpheus.application.context.TechnicalContextObservation;
import com.morpheus.application.context.TechnicalContextProvider;
import com.morpheus.application.context.TechnicalContextRequest;
import com.morpheus.application.reference.ExternalIntegrationStatus;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Optional NEXUS provider; external failures become explicit observations and never break MORPHEUS bootstrap. */
public final class NexusMcpTechnicalContextProvider implements TechnicalContextProvider {
    public static final String SYSTEM = "NEXUS";

    private final NexusIntegrationSettings settings;
    private final NexusContextGatewayFactory gateways;

    public NexusMcpTechnicalContextProvider(NexusIntegrationSettings settings) {
        this(settings, () -> new NexusMcpContextGateway(settings));
    }

    NexusMcpTechnicalContextProvider(NexusIntegrationSettings settings, NexusContextGatewayFactory gateways) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.gateways = Objects.requireNonNull(gateways, "gateways");
    }

    @Override
    public String system() {
        return SYSTEM;
    }

    @Override
    public ExternalIntegrationStatus status() {
        if (settings.state() == NexusIntegrationSettings.State.DISABLED) {
            return new ExternalIntegrationStatus(
                    SYSTEM, "DISABLED", false, "NEXUS integration is not configured", baseDetails());
        }
        if (settings.state() == NexusIntegrationSettings.State.INVALID) {
            return new ExternalIntegrationStatus(
                    SYSTEM, "INVALID", false,
                    settings.configurationError().orElse("invalid NEXUS integration configuration"), baseDetails());
        }
        try (NexusContextGateway gateway = gateways.open()) {
            var projects = gateway.listProjects();
            Map<String, String> details = new LinkedHashMap<>(baseDetails());
            details.put("projectCount", Integer.toString(projects.size()));
            return new ExternalIntegrationStatus(
                    SYSTEM, "AVAILABLE", true, "NEXUS MCP integration is available", details);
        } catch (RuntimeException failure) {
            return unavailable(failure);
        }
    }

    @Override
    public TechnicalContextObservation build(TechnicalContextRequest request) {
        Objects.requireNonNull(request, "request");
        if (!settings.enabled()) {
            return TechnicalContextObservation.unavailable(status());
        }
        try (NexusContextGateway gateway = gateways.open()) {
            var bundle = gateway.buildContext(request);
            Map<String, String> details = new LinkedHashMap<>(baseDetails());
            details.put("projectId", bundle.projectId());
            details.put("projectName", bundle.projectName());
            details.put("estimatedTokens", Integer.toString(bundle.estimatedTokens()));
            ExternalIntegrationStatus available = new ExternalIntegrationStatus(
                    SYSTEM, "AVAILABLE", true, "NEXUS technical context built successfully", details);
            return TechnicalContextObservation.available(available, bundle);
        } catch (RuntimeException failure) {
            return TechnicalContextObservation.unavailable(unavailable(failure));
        }
    }

    private ExternalIntegrationStatus unavailable(RuntimeException failure) {
        return new ExternalIntegrationStatus(
                SYSTEM,
                "UNAVAILABLE",
                true,
                "NEXUS integration is unavailable: " + safeMessage(failure),
                baseDetails());
    }

    private Map<String, String> baseDetails() {
        Map<String, String> details = new LinkedHashMap<>();
        details.put("javaCommand", settings.javaCommand());
        details.put("timeoutSeconds", Long.toString(settings.timeout().toSeconds()));
        settings.jarPath().ifPresent(path -> details.put("jar", path.toString()));
        settings.homeDirectory().ifPresent(path -> details.put("home", path.toString()));
        return Map.copyOf(details);
    }

    private String safeMessage(RuntimeException failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }
}
