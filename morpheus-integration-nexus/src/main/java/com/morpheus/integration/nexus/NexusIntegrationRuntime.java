package com.morpheus.integration.nexus;

import com.morpheus.application.context.TechnicalContextObservation;
import com.morpheus.application.context.TechnicalContextProvider;
import com.morpheus.application.context.TechnicalContextRequest;
import com.morpheus.application.reference.ExternalIntegrationStatus;
import com.morpheus.application.reference.ExternalIntegrationStatusProvider;

import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/** Composition-friendly optional NEXUS runtime shared by CLI, MCP and HTTP adapters. */
public final class NexusIntegrationRuntime implements TechnicalContextProvider, ExternalIntegrationStatusProvider {
    private final NexusMcpTechnicalContextProvider provider;

    public NexusIntegrationRuntime(NexusIntegrationSettings settings) {
        this.provider = new NexusMcpTechnicalContextProvider(Objects.requireNonNull(settings, "settings"));
    }

    public static NexusIntegrationRuntime resolve(Map<String, String> environment, Properties properties) {
        return new NexusIntegrationRuntime(NexusIntegrationSettings.resolve(environment, properties));
    }

    @Override
    public String system() {
        return provider.system();
    }

    @Override
    public ExternalIntegrationStatus status() {
        return provider.status();
    }

    @Override
    public TechnicalContextObservation build(TechnicalContextRequest request) {
        return provider.build(request);
    }
}
