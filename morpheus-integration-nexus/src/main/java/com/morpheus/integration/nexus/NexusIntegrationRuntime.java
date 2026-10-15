package com.morpheus.integration.nexus;

import com.morpheus.application.context.TechnicalContextObservation;
import com.morpheus.application.context.TechnicalContextProvider;
import com.morpheus.application.context.TechnicalContextRequest;
import com.morpheus.application.operability.OperationalRecorder;
import com.morpheus.application.reference.ExternalIntegrationStatus;
import com.morpheus.application.reference.ExternalIntegrationStatusProvider;

import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/** Composition-friendly optional NEXUS runtime shared by CLI, MCP and HTTP adapters. */
public final class NexusIntegrationRuntime implements TechnicalContextProvider, ExternalIntegrationStatusProvider {
    private final NexusMcpTechnicalContextProvider provider;

    /** The NEXUS integration of {@code settings}; its peer failures are counted on {@code recorder}. */
    public NexusIntegrationRuntime(NexusIntegrationSettings settings, OperationalRecorder recorder) {
        this.provider = new NexusMcpTechnicalContextProvider(Objects.requireNonNull(settings, "settings"), recorder);
    }

    public static NexusIntegrationRuntime resolve(
            Map<String, String> environment, Properties properties, OperationalRecorder recorder) {
        return new NexusIntegrationRuntime(NexusIntegrationSettings.resolve(environment, properties), recorder);
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
