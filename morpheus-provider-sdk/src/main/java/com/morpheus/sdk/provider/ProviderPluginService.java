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
    private final ProviderPluginProbeProcess probeProcess;

    public ProviderPluginService() {
        this(new ProviderPluginDiscovery(), new ProviderPluginActivator(), new ProviderPluginProbeProcess());
    }

    ProviderPluginService(ProviderPluginDiscovery discovery, ProviderPluginActivator activator) {
        this(discovery, activator, new ProviderPluginProbeProcess());
    }

    ProviderPluginService(
            ProviderPluginDiscovery discovery,
            ProviderPluginActivator activator,
            ProviderPluginProbeProcess probeProcess) {
        this.discovery = Objects.requireNonNull(discovery, "discovery");
        this.activator = Objects.requireNonNull(activator, "activator");
        this.probeProcess = Objects.requireNonNull(probeProcess, "probeProcess");
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
        Outcomes outcomes = new Outcomes(requestedPluginId, result.diagnostics());
        List<ProviderPluginCandidate> matches = result.candidates().stream()
                .filter(item -> item.metadata().map(metadata -> metadata.pluginId().equals(requestedPluginId)).orElse(false))
                .toList();
        if (matches.isEmpty()) {
            return outcomes.unselected(List.of(), ProviderPluginDiagnostic.error(
                    "PLUGIN_NOT_FOUND",
                    "Requested provider plugin was not discovered",
                    Map.of("pluginId", requestedPluginId, "directory", result.directory().toString())));
        }
        if (matches.size() > 1) {
            return outcomes.unselected(
                    matches.stream().flatMap(candidate -> candidate.diagnostics().stream()).toList(),
                    ProviderPluginDiagnostic.error(
                            "PLUGIN_ID_AMBIGUOUS",
                            "Multiple provider plugin JARs declare the requested plugin id; no plugin was activated",
                            Map.of(
                                    "pluginId", requestedPluginId,
                                    "matches", Integer.toString(matches.size()),
                                    "jars", matches.stream()
                                            .map(candidate -> candidate.jarPath().getFileName().toString())
                                            .sorted()
                                            .reduce((left, right) -> left + "," + right)
                                            .orElse(""))));
        }

        ProviderPluginCandidate selected = matches.getFirst();
        if (!selected.compatible()) {
            return outcomes.selected(selected, Optional.empty());
        }

        try {
            // Verify in the parent for precise fail-closed diagnostics. The child verifies again before loading,
            // so a path swap between this check and process launch cannot bypass the trusted SHA-256 pin.
            ExternalJarIntegrity.verifySha256(selected.jarPath(), trustedSha256);
        } catch (IllegalArgumentException integrityFailure) {
            return outcomes.selected(selected, Optional.empty(), ProviderPluginDiagnostic.error(
                    "PLUGIN_INTEGRITY_VERIFICATION_FAILED",
                    "Provider plugin was rejected before activation because its SHA-256 pin did not match",
                    Map.of(
                            "pluginId", requestedPluginId,
                            "reason", safeMessage(integrityFailure),
                            "reasonType", failureType(integrityFailure))));
        }

        // Past this point the pin has matched, so no failure may be reported as an integrity rejection.
        try {
            ProviderProbeResult probe = probeProcess.probe(selected, workspaceRoot, trustedSha256);
            return outcomes.selected(selected, Optional.of(probe));
        } catch (ProviderPluginProbeProcessException failure) {
            return outcomes.selected(selected, Optional.empty(), ProviderPluginDiagnostic.error(
                    failure.timeout() ? "PLUGIN_PROBE_TIMEOUT" : "PLUGIN_ACTIVATION_OR_PROBE_FAILED",
                    failure.timeout()
                            ? "Provider plugin probe exceeded its isolated execution deadline and was terminated"
                            : "Provider plugin activation or probe failed in its isolated process",
                    Map.of(
                            "pluginId", requestedPluginId,
                            "reason", safeMessage(failure),
                            "reasonType", failureType(failure))));
        } catch (RuntimeException | LinkageError failure) {
            return outcomes.selected(selected, Optional.empty(), ProviderPluginDiagnostic.error(
                    "PLUGIN_ACTIVATION_OR_PROBE_FAILED",
                    "Provider plugin activation or probe failed without terminating MORPHEUS",
                    Map.of(
                            "pluginId", requestedPluginId,
                            "reason", safeMessage(failure),
                            "reasonType", failureType(failure))));
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

    /**
     * Names the failure without locating it. {@code reason} relays an exception message, which for a filesystem
     * failure can be the pathname itself, so only the type crosses the remote boundary.
     */
    private static String failureType(Throwable failure) {
        return failure.getClass().getSimpleName();
    }

    /**
     * The single construction point of a probe outcome.
     *
     * <p>Diagnostics arrive in one stable order: the directory's, then the candidate's, then the branch's own. A
     * truncated scan is reported whatever became of the requested plugin — it matters most precisely when the plugin
     * was found, because that is when the operator can still act on it.</p>
     */
    private record Outcomes(String pluginId, List<ProviderPluginDiagnostic> directoryDiagnostics) {
        ProviderPluginProbeOutcome unselected(
                List<ProviderPluginDiagnostic> candidateDiagnostics, ProviderPluginDiagnostic branchDiagnostic) {
            return outcome("", Optional.empty(), Optional.empty(), candidateDiagnostics, List.of(branchDiagnostic));
        }

        ProviderPluginProbeOutcome selected(
                ProviderPluginCandidate candidate,
                Optional<ProviderProbeResult> probe,
                ProviderPluginDiagnostic... branchDiagnostics) {
            return outcome(
                    candidate.jarPath().toString(), candidate.metadata(), probe,
                    candidate.diagnostics(), List.of(branchDiagnostics));
        }

        private ProviderPluginProbeOutcome outcome(
                String jarPath,
                Optional<ProviderPluginMetadata> metadata,
                Optional<ProviderProbeResult> probe,
                List<ProviderPluginDiagnostic> candidateDiagnostics,
                List<ProviderPluginDiagnostic> branchDiagnostics) {
            List<ProviderPluginDiagnostic> diagnostics = new ArrayList<>(directoryDiagnostics);
            diagnostics.addAll(candidateDiagnostics);
            diagnostics.addAll(branchDiagnostics);
            return new ProviderPluginProbeOutcome(pluginId, jarPath, metadata, probe, diagnostics);
        }
    }
}
