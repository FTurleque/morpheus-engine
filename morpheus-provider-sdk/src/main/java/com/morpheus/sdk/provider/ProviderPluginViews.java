package com.morpheus.sdk.provider;

import com.morpheus.domain.provider.ProviderProbeResult;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Transport-safe immutable views without java.nio.Path implementation details. */
public final class ProviderPluginViews {
    /** Diagnostic detail keys whose values are server filesystem locations. */
    private static final Set<String> PATH_DETAIL_KEYS = Set.of("directory", "jarPath", "path", "workspace");

    private ProviderPluginViews() {
    }

    public static DiscoveryView discovery(ProviderPluginDiscoveryResult result) {
        Objects.requireNonNull(result, "result");
        return new DiscoveryView(
                result.directory().toString(),
                result.candidates().stream().map(ProviderPluginViews::candidate).toList(),
                result.diagnostics(),
                result.compatibleCount());
    }

    /**
     * Discovery view for transports that leave the machine.
     *
     * <p>The plugin directory is server-configured for remote callers, so echoing its absolute pathname back tells
     * the caller nothing it needs and discloses the server's filesystem layout. Candidates keep the JAR file name,
     * which is what identifies a plugin to an administrator, and drop the absolute pathname. Diagnostic details are
     * filtered through the same rule so a path cannot re-enter through them.
     */
    public static RemoteDiscoveryView remoteDiscovery(ProviderPluginDiscoveryResult result) {
        Objects.requireNonNull(result, "result");
        return new RemoteDiscoveryView(
                result.candidates().stream().map(ProviderPluginViews::remoteCandidate).toList(),
                redactAll(result.diagnostics()),
                result.compatibleCount());
    }

    private static RemoteCandidateView remoteCandidate(ProviderPluginCandidate candidate) {
        return new RemoteCandidateView(
                fileNameOf(candidate.jarPath()),
                candidate.metadata(),
                candidate.status(),
                redactAll(candidate.diagnostics()));
    }

    /** Probe outcome for transports that leave the machine; keeps the JAR name and drops its absolute pathname. */
    public static RemoteProbeView remoteProbe(ProviderPluginProbeOutcome outcome) {
        Objects.requireNonNull(outcome, "outcome");
        String jarPath = outcome.jarPath();
        String jarName = jarPath.isBlank() ? "" : fileNameOf(Path.of(jarPath));
        return new RemoteProbeView(
                outcome.pluginId(),
                jarName,
                outcome.metadata(),
                outcome.probe(),
                redactAll(outcome.diagnostics()));
    }

    /** A filesystem root has no file name; fall back to its own rendering rather than dereferencing null. */
    private static String fileNameOf(Path path) {
        Path fileName = path.getFileName();
        return fileName == null ? path.toString() : fileName.toString();
    }

    private static List<ProviderPluginDiagnostic> redactAll(List<ProviderPluginDiagnostic> diagnostics) {
        return diagnostics.stream().map(ProviderPluginViews::redact).toList();
    }

    private static ProviderPluginDiagnostic redact(ProviderPluginDiagnostic diagnostic) {
        Map<String, String> retained = new LinkedHashMap<>();
        diagnostic.details().forEach((key, value) -> {
            if (!PATH_DETAIL_KEYS.contains(key)) {
                retained.put(key, value);
            }
        });
        if (retained.size() == diagnostic.details().size()) {
            return diagnostic;
        }
        return new ProviderPluginDiagnostic(
                diagnostic.severity(), diagnostic.code(), diagnostic.message(), retained);
    }

    private static CandidateView candidate(ProviderPluginCandidate candidate) {
        return new CandidateView(
                candidate.jarPath().toString(),
                candidate.metadata(),
                candidate.status(),
                candidate.diagnostics());
    }

    public record DiscoveryView(
            String directory,
            List<CandidateView> candidates,
            List<ProviderPluginDiagnostic> diagnostics,
            long compatibleCount) {
        public DiscoveryView {
            directory = requireText(directory, "directory");
            candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
            diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        }
    }

    public record RemoteDiscoveryView(
            List<RemoteCandidateView> candidates,
            List<ProviderPluginDiagnostic> diagnostics,
            long compatibleCount) {
        public RemoteDiscoveryView {
            candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
            diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        }
    }

    public record RemoteCandidateView(
            String jarName,
            Optional<ProviderPluginMetadata> metadata,
            ProviderPluginStatus status,
            List<ProviderPluginDiagnostic> diagnostics) {
        public RemoteCandidateView {
            jarName = requireText(jarName, "jarName");
            metadata = Objects.requireNonNull(metadata, "metadata");
            Objects.requireNonNull(status, "status");
            diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        }
    }

    public record RemoteProbeView(
            String pluginId,
            String jarName,
            Optional<ProviderPluginMetadata> metadata,
            Optional<ProviderProbeResult> probe,
            List<ProviderPluginDiagnostic> diagnostics) {
        public RemoteProbeView {
            pluginId = requireText(pluginId, "pluginId");
            jarName = jarName == null ? "" : jarName;
            metadata = Objects.requireNonNull(metadata, "metadata");
            probe = Objects.requireNonNull(probe, "probe");
            diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        }
    }

    public record CandidateView(
            String jarPath,
            Optional<ProviderPluginMetadata> metadata,
            ProviderPluginStatus status,
            List<ProviderPluginDiagnostic> diagnostics) {
        public CandidateView {
            jarPath = requireText(jarPath, "jarPath");
            metadata = Objects.requireNonNull(metadata, "metadata");
            Objects.requireNonNull(status, "status");
            diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
        }
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }
}
