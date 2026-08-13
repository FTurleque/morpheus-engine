package com.morpheus.sdk.provider;

import com.morpheus.application.security.ExternalJarIntegrity;
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

    /**
     * Unpinned executable activation is intentionally rejected. Public adapters must provide a trusted SHA-256 pin.
     */
    @Deprecated(forRemoval = true)
    public ProviderPluginProbeOutcome probe(Path pluginDirectory, String pluginId, Path workspaceRoot) {
        Objects.requireNonNull(pluginDirectory, "pluginDirectory");
        Objects.requireNonNull(workspaceRoot, "workspaceRoot");
        requireText(pluginId, "pluginId");
        throw new IllegalArgumentException("provider plugin probe requires a trusted SHA-256 pin");
    }

    public ProviderPluginProbeOutcome probe(
            Path pluginDirectory, String pluginId, Path workspaceRoot, String expectedSha256) {
        Objects.requireNonNull(pluginDirectory, "pluginDirectory");
        Objects.requireNonNull(workspaceRoot, "workspaceRoot");
        String requestedPluginId = requireText(pluginId, "pluginId");
        String trustedSha256 = ExternalJarIntegrity.normalizeSha256(expectedSha256);
        ProviderPluginDiscoveryResult result = discovery.discover(pluginDirectory);
        List<ProviderPluginCandidate> matches = result.candidates().stream()
                .filter(item -> item.metadata().map(metadata -> metadata.pluginId().equals(requestedPluginId)).orElse(false))
                .toList();
        if (matches.isEmpty()) {
            List<ProviderPluginDiagnostic> diagnostics = new ArrayList<>(result.diagnostics());
            diagnostics.add(ProviderPluginDiagnostic.error(
                    "PLUGIN_NOT_FOUND",
                    "Requested provider plugin was not discovered",
                    Map.of("pluginId", requestedPluginId, "directory", result.directory().toString())));
            return new ProviderPluginProbeOutcome(requestedPluginId, "", Optional.empty(), Optional.empty(), diagnostics);
        }
        if (matches.size() > 1) {
            return new ProviderPluginProbeOutcome(
                    requestedPluginId,
                    "",
                    Optional.empty(),
                    Optional.empty(),
                    List.of(ProviderPluginDiagnostic.error(
                            "PLUGIN_ID_AMBIGUOUS",
                            "Multiple provider plugin JARs declare the requested plugin id; no plugin was activated",
                            Map.of(
                                    "pluginId", requestedPluginId,
                                    "matches", Integer.toString(matches.size()),
                                    "jars", matches.stream()
                                            .map(candidate -> candidate.jarPath().getFileName().toString())
                                            .sorted()
                                            .reduce((left, right) -> left + "," + right)
                                            .orElse("")))));
        }

        ProviderPluginCandidate selected = matches.getFirst();
        if (!selected.compatible()) {
            return new ProviderPluginProbeOutcome(
                    requestedPluginId,
                    selected.jarPath().toString(),
                    selected.metadata(),
                    Optional.empty(),
                    selected.diagnostics());
        }

        try (ProviderPluginActivation activation = activator.activate(selected, trustedSha256)) {
            ProviderProbeResult probe = Objects.requireNonNull(
                    activation.provider().probe(workspaceRoot.toAbsolutePath().normalize()),
                    "provider probe result");
            return new ProviderPluginProbeOutcome(
                    requestedPluginId,
                    selected.jarPath().toString(),
                    selected.metadata(),
                    Optional.of(probe),
                    selected.diagnostics());
        } catch (IllegalArgumentException integrityFailure) {
            List<ProviderPluginDiagnostic> diagnostics = new ArrayList<>(selected.diagnostics());
            diagnostics.add(ProviderPluginDiagnostic.error(
                    "PLUGIN_INTEGRITY_VERIFICATION_FAILED",
                    "Provider plugin was rejected before activation because its SHA-256 pin did not match",
                    Map.of("pluginId", requestedPluginId, "reason", safeMessage(integrityFailure))));
            return new ProviderPluginProbeOutcome(
                    requestedPluginId,
                    selected.jarPath().toString(),
                    selected.metadata(),
                    Optional.empty(),
                    diagnostics);
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
