package com.morpheus.application.provider;

import com.morpheus.domain.diagnostic.Diagnostic;
import com.morpheus.domain.provider.ProviderProbeResult;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record ProviderSelectionResult(
        Optional<ProviderProbeResult> selected,
        List<ProviderProbeResult> evaluated,
        List<Diagnostic> diagnostics) {

    public ProviderSelectionResult {
        selected = Objects.requireNonNull(selected, "selected");
        evaluated = List.copyOf(Objects.requireNonNull(evaluated, "evaluated"));
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
    }
}
