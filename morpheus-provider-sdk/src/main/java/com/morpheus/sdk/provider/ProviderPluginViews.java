package com.morpheus.sdk.provider;

import com.morpheus.domain.diagnostic.Diagnostic;
import com.morpheus.domain.diagnostic.DiagnosticCode;
import com.morpheus.domain.diagnostic.DiagnosticSeverity;
import com.morpheus.domain.provider.ProviderCapability;
import com.morpheus.domain.provider.ProviderProbeResult;
import com.morpheus.domain.provider.ProviderProbeStatus;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Transport-safe immutable views without java.nio.Path implementation details.
 *
 * <p>The local views ({@link #discovery}) carry the server's full pathnames: an operator reading CLI output uses
 * them, and the surfaces that render them do not leave the machine. The remote views project instead of redact.</p>
 *
 * <p><strong>Why projection rather than key removal.</strong> Dropping a list of suspicious keys protects only
 * against the disclosures already known by name. It leaves the ones that arrive under an innocent name — a
 * {@code reason} holding a filesystem exception's message, which for {@link java.nio.file.AccessDeniedException}
 * <em>is</em> the pathname — and it silently admits every field added later. Each remote record below therefore
 * names the fields it carries, so an internal type that grows a new member does not grow the remote surface with
 * it. {@link RemoteTextPolicy} is the second gate on the values those fields carry, because a probe result is
 * authored by third-party plugin code rather than by MORPHEUS.</p>
 */
public final class ProviderPluginViews {
    private static final String FIELD_DIAGNOSTICS = "diagnostics";
    private static final String FIELD_METADATA = "metadata";

    /**
     * Diagnostic detail keys that may reach a remote caller.
     *
     * <p>An allowlist rather than a denylist: a detail added later reaches a remote caller only once someone has
     * considered whether it should. Every value is still checked by {@link RemoteTextPolicy}, because a key being
     * remote-safe by intent does not make an arbitrary value remote-safe in fact.</p>
     *
     * <p>{@code reason} is deliberately absent — it relays an exception message. Producers pair it with
     * {@code reasonType}, the exception's class name, which names the failure without locating it.</p>
     */
    private static final Set<String> REMOTE_SAFE_DETAIL_KEYS = Set.of(
            "pluginId",
            "pluginSdkApiVersion",
            "runtimeSdkApiVersion",
            "runtimeVersion",
            "minimumVersion",
            "maximumVersion",
            "limit",
            "limitBytes",
            "sizeBytes",
            "matches",
            "jars",
            "reasonType");

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
     * which is what identifies a plugin to an administrator, and drop the absolute pathname.</p>
     */
    public static RemoteDiscoveryView remoteDiscovery(ProviderPluginDiscoveryResult result) {
        Objects.requireNonNull(result, "result");
        return new RemoteDiscoveryView(
                result.candidates().stream().map(ProviderPluginViews::remoteCandidate).toList(),
                remoteDiagnostics(result.diagnostics()),
                result.compatibleCount());
    }

    /** Probe outcome for transports that leave the machine; keeps the JAR name and drops its absolute pathname. */
    public static RemoteProbeView remoteProbe(ProviderPluginProbeOutcome outcome) {
        Objects.requireNonNull(outcome, "outcome");
        return new RemoteProbeView(
                outcome.pluginId(),
                remoteJarName(outcome.jarPath()),
                outcome.metadata(),
                outcome.probe().map(ProviderPluginViews::remoteProbeResult),
                remoteDiagnostics(outcome.diagnostics()));
    }

    private static RemoteCandidateView remoteCandidate(ProviderPluginCandidate candidate) {
        return new RemoteCandidateView(
                fileNameOf(candidate.jarPath()),
                candidate.metadata(),
                candidate.status(),
                remoteDiagnostics(candidate.diagnostics()));
    }

    /**
     * A probe outcome reports the JAR by name. A pathname with no file name component is a filesystem root, which
     * names no plugin and locates the server, so it yields no name at all.
     */
    private static String remoteJarName(String jarPath) {
        if (jarPath.isBlank()) {
            return "";
        }
        Path fileName = Path.of(jarPath).getFileName();
        return fileName == null ? "" : fileName.toString();
    }

    /** A filesystem root has no file name; fall back to its own rendering rather than dereferencing null. */
    private static String fileNameOf(Path path) {
        Path fileName = path.getFileName();
        return fileName == null ? path.toString() : fileName.toString();
    }

    /**
     * Projects a probe result produced by third-party plugin code.
     *
     * <p>Every free-text member here is chosen by the plugin, so each is relayed only when
     * {@link RemoteTextPolicy} finds no filesystem location in it. The source locator keeps its scheme, which
     * says what kind of source was recognized, and keeps its value only when that value is not a location on the
     * server. Capabilities and status are enumerations and cannot carry text at all.</p>
     */
    private static RemoteProbeResultView remoteProbeResult(ProviderProbeResult probe) {
        return new RemoteProbeResultView(
                remoteText(probe.providerId().value()),
                remoteText(probe.providerVersion()),
                probe.status(),
                probe.schema().flatMap(ProviderPluginViews::remoteOptionalText),
                probe.formatVersion().flatMap(ProviderPluginViews::remoteOptionalText),
                probe.sourceLocator().map(locator -> new RemoteSourceView(
                        remoteText(locator.scheme()),
                        remoteOptionalText(locator.value()))),
                new TreeSet<>(probe.capabilities().values()),
                probe.remote(),
                probe.diagnostics().stream().map(ProviderPluginViews::remoteProbeDiagnostic).toList());
    }

    /**
     * Projects a diagnostic authored by third-party plugin code.
     *
     * <p>{@code code} and {@code severity} are enumerations, so they are structurally incapable of carrying a
     * pathname and are relayed as they are. The details map is not: its keys as well as its values are chosen by
     * the plugin, so no key on it can be allowlisted in advance and the map is dropped. The same reasoning drops
     * {@code source}, which is a free-text locator.</p>
     */
    private static RemoteProbeDiagnosticView remoteProbeDiagnostic(Diagnostic diagnostic) {
        return new RemoteProbeDiagnosticView(
                diagnostic.code(),
                diagnostic.severity(),
                remoteMessage(diagnostic.message(), diagnostic.code().name()));
    }

    private static List<RemoteProviderDiagnostic> remoteDiagnostics(List<ProviderPluginDiagnostic> diagnostics) {
        return diagnostics.stream().map(ProviderPluginViews::remoteDiagnostic).toList();
    }

    private static RemoteProviderDiagnostic remoteDiagnostic(ProviderPluginDiagnostic diagnostic) {
        Map<String, String> retained = new LinkedHashMap<>();
        diagnostic.details().forEach((key, value) -> {
            if (REMOTE_SAFE_DETAIL_KEYS.contains(key) && RemoteTextPolicy.isRemoteSafe(value)) {
                retained.put(key, value);
            }
        });
        return new RemoteProviderDiagnostic(
                diagnostic.severity(),
                diagnostic.code(),
                remoteMessage(diagnostic.message(), diagnostic.code()),
                retained);
    }

    /** Falls back to the stable code, so a message that cannot be relayed still identifies the failure. */
    private static String remoteMessage(String message, String code) {
        return RemoteTextPolicy.isRemoteSafe(message) ? message : code;
    }

    private static String remoteText(String value) {
        return RemoteTextPolicy.isRemoteSafe(value) ? value : "";
    }

    private static Optional<String> remoteOptionalText(String value) {
        return RemoteTextPolicy.isRemoteSafe(value) ? Optional.of(value) : Optional.empty();
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
            diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, FIELD_DIAGNOSTICS));
        }
    }

    public record RemoteDiscoveryView(
            List<RemoteCandidateView> candidates,
            List<RemoteProviderDiagnostic> diagnostics,
            long compatibleCount) {
        public RemoteDiscoveryView {
            candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates"));
            diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, FIELD_DIAGNOSTICS));
        }
    }

    /**
     * Plugin metadata is relayed whole because it cannot express a pathname: {@link ProviderPluginMetadata}
     * validates its identifier against {@code [a-z0-9][a-z0-9._-]*} and its versions against a semantic-version
     * pattern, so no member of it admits a separator, a drive letter or a scheme.
     */
    public record RemoteCandidateView(
            String jarName,
            Optional<ProviderPluginMetadata> metadata,
            ProviderPluginStatus status,
            List<RemoteProviderDiagnostic> diagnostics) {
        public RemoteCandidateView {
            jarName = requireText(jarName, "jarName");
            Objects.requireNonNull(metadata, FIELD_METADATA);
            Objects.requireNonNull(status, "status");
            diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, FIELD_DIAGNOSTICS));
        }
    }

    public record RemoteProbeView(
            String pluginId,
            String jarName,
            Optional<ProviderPluginMetadata> metadata,
            Optional<RemoteProbeResultView> probe,
            List<RemoteProviderDiagnostic> diagnostics) {
        public RemoteProbeView {
            pluginId = requireText(pluginId, "pluginId");
            jarName = jarName == null ? "" : jarName;
            Objects.requireNonNull(metadata, FIELD_METADATA);
            Objects.requireNonNull(probe, "probe");
            diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, FIELD_DIAGNOSTICS));
        }
    }

    /** Remote projection of a probe result. Every member is named here rather than inherited from the domain. */
    public record RemoteProbeResultView(
            String providerId,
            String providerVersion,
            ProviderProbeStatus status,
            Optional<String> schema,
            Optional<String> formatVersion,
            Optional<RemoteSourceView> source,
            Set<ProviderCapability> capabilities,
            boolean remote,
            List<RemoteProbeDiagnosticView> diagnostics) {
        public RemoteProbeResultView {
            Objects.requireNonNull(providerId, "providerId");
            Objects.requireNonNull(providerVersion, "providerVersion");
            Objects.requireNonNull(status, "status");
            Objects.requireNonNull(schema, "schema");
            Objects.requireNonNull(formatVersion, "formatVersion");
            Objects.requireNonNull(source, "source");
            capabilities = Set.copyOf(Objects.requireNonNull(capabilities, "capabilities"));
            diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, FIELD_DIAGNOSTICS));
        }
    }

    /** The scheme says what kind of source was recognized; the value appears only when it locates nothing. */
    public record RemoteSourceView(String scheme, Optional<String> value) {
        public RemoteSourceView {
            Objects.requireNonNull(scheme, "scheme");
            Objects.requireNonNull(value, "value");
        }
    }

    public record RemoteProbeDiagnosticView(DiagnosticCode code, DiagnosticSeverity severity, String message) {
        public RemoteProbeDiagnosticView {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(severity, "severity");
            Objects.requireNonNull(message, "message");
        }
    }

    /** Remote projection of a MORPHEUS-authored plugin diagnostic; details are allowlisted, never filtered. */
    public record RemoteProviderDiagnostic(
            ProviderPluginDiagnostic.Severity severity,
            String code,
            String message,
            Map<String, String> details) {
        public RemoteProviderDiagnostic {
            Objects.requireNonNull(severity, "severity");
            code = requireText(code, "code");
            Objects.requireNonNull(message, "message");
            details = Map.copyOf(Objects.requireNonNull(details, "details"));
        }
    }

    public record CandidateView(
            String jarPath,
            Optional<ProviderPluginMetadata> metadata,
            ProviderPluginStatus status,
            List<ProviderPluginDiagnostic> diagnostics) {
        public CandidateView {
            jarPath = requireText(jarPath, "jarPath");
            Objects.requireNonNull(metadata, FIELD_METADATA);
            Objects.requireNonNull(status, "status");
            diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, FIELD_DIAGNOSTICS));
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
