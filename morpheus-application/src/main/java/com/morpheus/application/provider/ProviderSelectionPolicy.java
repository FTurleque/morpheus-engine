package com.morpheus.application.provider;

import com.morpheus.domain.diagnostic.Diagnostic;
import com.morpheus.domain.diagnostic.DiagnosticCode;
import com.morpheus.domain.provider.ProviderCapability;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.provider.ProviderProbeResult;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** Deterministic capability-based provider selection policy owned by MORPHEUS. */
public final class ProviderSelectionPolicy {

    public ProviderSelectionResult select(
            List<ProviderProbeResult> probeResults,
            ProviderSelectionRequest request) {
        List<ProviderProbeResult> evaluated = List.copyOf(probeResults);
        List<Diagnostic> diagnostics = evaluated.stream()
                .flatMap(result -> result.diagnostics().stream())
                .collect(Collectors.toCollection(ArrayList::new));

        if (request.explicitProvider().isPresent()) {
            return selectExplicit(evaluated, request, diagnostics, request.explicitProvider().orElseThrow());
        }

        List<ProviderProbeResult> supported = evaluated.stream()
                .filter(ProviderProbeResult::supported)
                .toList();

        if (supported.isEmpty()) {
            addIfAbsent(diagnostics, Diagnostic.error(
                    DiagnosticCode.NO_PROVIDER_FOUND,
                    "No compatible specification provider was found.",
                    Map.of()));
            return new ProviderSelectionResult(Optional.empty(), evaluated, diagnostics);
        }

        List<ProviderProbeResult> eligible = supported.stream()
                .filter(result -> request.allowRemote() || !result.remote())
                .filter(result -> result.capabilities().containsAll(request.requiredCapabilities()))
                .sorted(selectionComparator(request.preferredCapabilities()))
                .toList();

        if (eligible.isEmpty()) {
            boolean remoteWouldMatch = supported.stream()
                    .anyMatch(result -> result.remote()
                            && result.capabilities().containsAll(request.requiredCapabilities()));

            if (!request.allowRemote() && remoteWouldMatch) {
                diagnostics.add(Diagnostic.error(
                        DiagnosticCode.REMOTE_PROVIDER_REQUIRES_OPT_IN,
                        "A compatible remote provider exists but remote providers require explicit opt-in.",
                        Map.of()));
            } else {
                diagnostics.add(Diagnostic.error(
                        DiagnosticCode.MISSING_REQUIRED_CAPABILITY,
                        "No provider satisfies all required capabilities.",
                        Map.of("required", joinCapabilities(request.requiredCapabilities()))));
            }
            return new ProviderSelectionResult(Optional.empty(), evaluated, diagnostics);
        }

        ProviderProbeResult selected = eligible.getFirst();
        long selectedPreferred = selected.capabilities().countMatches(request.preferredCapabilities());
        long equivalentTopMatches = eligible.stream()
                .filter(result -> result.capabilities().countMatches(request.preferredCapabilities()) == selectedPreferred)
                .filter(result -> result.remote() == selected.remote())
                .count();

        if (equivalentTopMatches > 1) {
            diagnostics.add(Diagnostic.warning(
                    DiagnosticCode.MULTIPLE_PROVIDER_MATCHES,
                    "Several providers are equivalent; MORPHEUS selected the lexicographically first provider id.",
                    Map.of("selectedProvider", selected.providerId().value())));
        }

        addOptionalCapabilityDiagnostics(selected, request.preferredCapabilities(), diagnostics);
        return new ProviderSelectionResult(Optional.of(selected), evaluated, diagnostics);
    }

    private ProviderSelectionResult selectExplicit(
            List<ProviderProbeResult> evaluated,
            ProviderSelectionRequest request,
            List<Diagnostic> diagnostics,
            ProviderId explicitProvider) {
        Optional<ProviderProbeResult> match = evaluated.stream()
                .filter(result -> result.providerId().equals(explicitProvider))
                .findFirst();

        if (match.isEmpty()) {
            diagnostics.add(Diagnostic.error(
                    DiagnosticCode.EXPLICIT_PROVIDER_INCOMPATIBLE,
                    "The explicitly configured provider is not registered.",
                    Map.of("provider", explicitProvider.value())));
            return new ProviderSelectionResult(Optional.empty(), evaluated, diagnostics);
        }

        ProviderProbeResult selected = match.orElseThrow();
        if (!selected.supported()
                || (!request.allowRemote() && selected.remote())
                || !selected.capabilities().containsAll(request.requiredCapabilities())) {
            diagnostics.add(Diagnostic.error(
                    DiagnosticCode.EXPLICIT_PROVIDER_INCOMPATIBLE,
                    "The explicitly configured provider cannot satisfy this request.",
                    Map.of(
                            "provider", explicitProvider.value(),
                            "required", joinCapabilities(request.requiredCapabilities()))));
            return new ProviderSelectionResult(Optional.empty(), evaluated, diagnostics);
        }

        addOptionalCapabilityDiagnostics(selected, request.preferredCapabilities(), diagnostics);
        return new ProviderSelectionResult(Optional.of(selected), evaluated, diagnostics);
    }

    private Comparator<ProviderProbeResult> selectionComparator(Set<ProviderCapability> preferred) {
        return Comparator
                .<ProviderProbeResult>comparingLong(result -> result.capabilities().countMatches(preferred))
                .reversed()
                .thenComparing(ProviderProbeResult::remote)
                .thenComparing(ProviderProbeResult::providerId);
    }

    private void addOptionalCapabilityDiagnostics(
            ProviderProbeResult selected,
            Set<ProviderCapability> preferred,
            List<Diagnostic> diagnostics) {
        Set<ProviderCapability> missing = preferred.stream()
                .filter(capability -> !selected.capabilities().contains(capability))
                .collect(Collectors.toSet());
        if (!missing.isEmpty()) {
            diagnostics.add(Diagnostic.warning(
                    DiagnosticCode.OPTIONAL_CAPABILITY_UNAVAILABLE,
                    "The selected provider does not expose every preferred capability.",
                    Map.of(
                            "provider", selected.providerId().value(),
                            "missing", joinCapabilities(missing))));
        }
    }

    private String joinCapabilities(Set<ProviderCapability> capabilities) {
        return capabilities.stream()
                .map(Enum::name)
                .sorted()
                .collect(Collectors.joining(","));
    }

    private void addIfAbsent(List<Diagnostic> diagnostics, Diagnostic diagnostic) {
        boolean exists = diagnostics.stream().anyMatch(existing -> existing.code() == diagnostic.code());
        if (!exists) {
            diagnostics.add(diagnostic);
        }
    }
}
