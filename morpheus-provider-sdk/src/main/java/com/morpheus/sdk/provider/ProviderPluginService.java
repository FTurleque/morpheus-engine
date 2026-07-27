package com.morpheus.sdk.provider;

import com.morpheus.domain.provider.ProviderProbeResult;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** High-level explicit plugin platform service used by public adapters. */
public final class ProviderPluginService {
    private final ProviderPluginDiscovery discovery;
    private final ProviderPluginActivator activator;

    public ProviderPluginService() {
        this(new ProviderPluginDiscovery(), new ProviderPluginActivator());
    }

    ProviderPluginService(ProviderPluginDiscovery discovery, ProviderPluginActivator activator) {
        this.discovery = Objects.requireNonNull(discovery, "discovery");
        this.activator = Objects.requireNonNull(activator, "activator");
    }

    public ProviderPluginDiscoveryResult discover(Path pluginDirectory) {
        return discovery.discover(Objects.requireNonNull(pluginDirectory, "pluginDirectory"));
    }

    public ProviderPluginProbeOutcome probe(Path pluginDirectory, String pluginId, Path workspaceRoot) {
        Objects.requireNonNull(pluginDirectory, "pluginDirectory");
        Objects.requireNonNull(workspaceRoot, "workspaceRoot");
        String requestedPluginId = requireText(pluginId, "pluginId");
        ProviderPluginDiscoveryResult result = discovery.discover(pluginDirectory);
        Optional<ProviderPluginCandidate> candidate = result.candidates().stream()
                .filter(item -> item.metadata().map(metadata -> metadata.pluginId().equals(requestedPluginId)).orElse(false))
                .findFirst();
        if (candidate.isEmpty()) {
            List<ProviderPluginDiagnostic> diagnostics = new ArrayList<>(result.diagnostics());
            diagnostics.add(ProviderPluginDiagnostic.error(
                    "PLUGIN_NOT_FOUND",
                    "Requested provider plugin was not discovered",
                    Map.of("pluginId", requestedPluginId, "directory", result.directory().toString())));
            return new ProviderPluginProbeOutcome(requestedPluginId, "", Optional.empty(), Optional.empty(), diagnostics);
        }

        ProviderPluginCandidate selected = candidate.get();
        if (!selected.compatible()) {
            return new ProviderPluginProbeOutcome(
                    requestedPluginId,
                    selected.jarPath().toString(),
                    selected.metadata(),
                    Optional.empty(),
                    selected.diagnostics());
        }

        try (ProviderPluginActivation activation = activator.activate(selected)) {
            ProviderProbeResult probe = Objects.requireNonNull(
                    activation.provider().probe(workspaceRoot.toAbsolutePath().normalize()),
                    "provider probe result");
            return new ProviderPluginProbeOutcome(
                    requestedPluginId,
                    selected.jarPath().toString(),
                    selected.metadata(),
                    Optional.of(probe),
                    selected.diagnostics());
        } catch (RuntimeException | LinkageError failure) {
            List<ProviderPluginDiagnostic> diagnostics = new ArrayList<>(selected.diagnostics());
            diagnostics.add(ProviderPluginDiagnostic.error(
                    "PLUGIN_ACTIVATION_OR_PROBE_FAILED",
                    "Provider plugin activation or probe failed without terminating MORPHEUS",
                    Map.of("pluginId", requestedPluginId, "reason", safeMessage(failure))));
            return new ProviderPluginProbeOutcome(
                    requestedPluginId,
                    selected.jarPath().toString(),
                    selected.metadata(),
                    Optional.empty(),
                    diagnostics);
        }
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private static String safeMessage(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }
}
