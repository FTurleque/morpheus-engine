package com.morpheus.integration.minos;

import com.morpheus.application.reference.ExternalReferenceResolverRegistry;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

/** Optional composition object used by CLI/API/MCP without exposing MINOS internals. */
public final class MinosIntegrationRuntime {
    private final MinosIntegrationSettings settings;
    private final ExternalReferenceResolverRegistry resolverRegistry;

    private MinosIntegrationRuntime(MinosIntegrationSettings settings) {
        this.settings = Objects.requireNonNull(settings, "settings");
        this.resolverRegistry = settings.enabled()
                ? new ExternalReferenceResolverRegistry(List.of(
                        new MinosMcpExternalReferenceResolver(() -> new MinosMcpCodeGateway(settings))))
                : new ExternalReferenceResolverRegistry(List.of());
    }

    public static MinosIntegrationRuntime resolve(Map<String, String> environment, Properties properties) {
        return new MinosIntegrationRuntime(MinosIntegrationSettings.resolve(environment, properties));
    }

    public static MinosIntegrationRuntime disabled() {
        return new MinosIntegrationRuntime(MinosIntegrationSettings.resolve(Map.of(), new Properties()));
    }

    public ExternalReferenceResolverRegistry resolverRegistry() {
        return resolverRegistry;
    }

    public Status status() {
        if (settings.state() == MinosIntegrationSettings.State.DISABLED) {
            return new Status("DISABLED", false, "MINOS integration is not configured", null,
                    settings.javaCommand(), settings.homeDirectory().map(Object::toString).orElse(null),
                    settings.timeout().toSeconds());
        }
        if (settings.state() == MinosIntegrationSettings.State.INVALID) {
            return new Status("INVALID", false, settings.configurationError().orElse("invalid MINOS configuration"),
                    settings.jarPath().map(Object::toString).orElse(null), settings.javaCommand(),
                    settings.homeDirectory().map(Object::toString).orElse(null), settings.timeout().toSeconds());
        }
        try (MinosCodeGateway ignored = new MinosMcpCodeGateway(settings)) {
            return new Status("AVAILABLE", true, "MINOS MCP server is reachable and compatible",
                    settings.jarPath().map(Object::toString).orElse(null), settings.javaCommand(),
                    settings.homeDirectory().map(Object::toString).orElse(null), settings.timeout().toSeconds());
        } catch (RuntimeException failure) {
            return new Status("UNAVAILABLE", true, safeMessage(failure),
                    settings.jarPath().map(Object::toString).orElse(null), settings.javaCommand(),
                    settings.homeDirectory().map(Object::toString).orElse(null), settings.timeout().toSeconds());
        }
    }

    public MinosIntegrationSettings settings() {
        return settings;
    }

    private static String safeMessage(RuntimeException failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    public record Status(
            String state,
            boolean configured,
            String message,
            String jarPath,
            String javaCommand,
            String homeDirectory,
            long timeoutSeconds) {
    }
}
