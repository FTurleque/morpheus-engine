package com.morpheus.integration.minos;

import com.morpheus.application.operability.OperationalRecorder;
import com.morpheus.application.reference.ExternalIntegrationStatus;
import com.morpheus.application.reference.ExternalIntegrationStatusProvider;
import com.morpheus.application.reference.ExternalReferenceResolverRegistry;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/** Optional composition object used by CLI/API/MCP without exposing MINOS internals. */
public final class MinosIntegrationRuntime implements ExternalIntegrationStatusProvider {
    private final MinosIntegrationSettings settings;
    private final ExternalReferenceResolverRegistry resolverRegistry;
    private final OperationalRecorder recorder;

    private MinosIntegrationRuntime(MinosIntegrationSettings settings, OperationalRecorder recorder) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.recorder = Objects.requireNonNull(recorder, "recorder");
        this.resolverRegistry = settings.enabled()
                ? new ExternalReferenceResolverRegistry(List.of(
                        new MinosMcpExternalReferenceResolver(() -> new MinosMcpCodeGateway(settings, recorder), recorder)))
                : new ExternalReferenceResolverRegistry(List.of());
    }

    /** The MINOS integration the environment configures; its peer failures are counted on {@code recorder}. */
    public static MinosIntegrationRuntime resolve(
            Map<String, String> environment, Properties properties, OperationalRecorder recorder) {
        return new MinosIntegrationRuntime(MinosIntegrationSettings.resolve(environment, properties), recorder);
    }

    public ExternalReferenceResolverRegistry resolverRegistry() {
        return resolverRegistry;
    }

    @Override
    public ExternalIntegrationStatus status() {
        if (settings.state() == MinosIntegrationSettings.State.DISABLED) {
            return status("DISABLED", false, "MINOS integration is not configured");
        }
        if (settings.state() == MinosIntegrationSettings.State.INVALID) {
            return status("INVALID", false, settings.configurationError().orElse("invalid MINOS configuration"));
        }
        try (MinosCodeGateway ignored = new MinosMcpCodeGateway(settings, recorder)) {
            return status("AVAILABLE", true, "MINOS MCP server is reachable and compatible");
        } catch (RuntimeException failure) {
            return status("UNAVAILABLE", true, safeMessage(failure));
        }
    }

    public MinosIntegrationSettings settings() {
        return settings;
    }

    private ExternalIntegrationStatus status(String state, boolean configured, String message) {
        Map<String, String> details = new LinkedHashMap<>();
        settings.jarPath().ifPresent(value -> details.put("jarPath", value.toString()));
        details.put("javaCommand", settings.javaCommand());
        settings.homeDirectory().ifPresent(value -> details.put("homeDirectory", value.toString()));
        details.put("timeoutSeconds", Long.toString(settings.timeout().toSeconds()));
        return new ExternalIntegrationStatus("MINOS", state, configured, message, details);
    }

    private static String safeMessage(RuntimeException failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }
}
