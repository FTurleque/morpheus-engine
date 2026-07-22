package com.morpheus.application.provider;

import com.morpheus.domain.diagnostic.DiagnosticCode;
import com.morpheus.domain.provider.ProviderCapability;
import com.morpheus.domain.provider.ProviderCapabilitySet;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.provider.ProviderProbeResult;
import com.morpheus.domain.provider.ProviderProbeStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderSelectionPolicyTest {
    private final ProviderSelectionPolicy policy = new ProviderSelectionPolicy();

    @Test
    void selectsProviderThatSatisfiesRequiredAndMostPreferredCapabilities() {
        var basic = supported("basic", false,
                ProviderCapability.DISCOVER_PROJECT,
                ProviderCapability.READ_CHANGES);
        var richer = supported("richer", false,
                ProviderCapability.DISCOVER_PROJECT,
                ProviderCapability.READ_CHANGES,
                ProviderCapability.READ_DESIGN_DECISIONS);

        var request = ProviderSelectionRequest.localOnly(
                Set.of(ProviderCapability.READ_CHANGES),
                Set.of(ProviderCapability.READ_DESIGN_DECISIONS));

        var result = policy.select(List.of(basic, richer), request);

        assertEquals("richer", result.selected().orElseThrow().providerId().value());
        assertFalse(result.diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.code() == DiagnosticCode.MISSING_REQUIRED_CAPABILITY));
    }

    @Test
    void prefersLocalProviderWhenCapabilitiesAreEquivalent() {
        var remote = supported("a-remote", true,
                ProviderCapability.DISCOVER_PROJECT,
                ProviderCapability.READ_CHANGES);
        var local = supported("z-local", false,
                ProviderCapability.DISCOVER_PROJECT,
                ProviderCapability.READ_CHANGES);

        var request = new ProviderSelectionRequest(
                Set.of(ProviderCapability.READ_CHANGES),
                Set.of(),
                Optional.empty(),
                true);

        var result = policy.select(List.of(remote, local), request);

        assertEquals("z-local", result.selected().orElseThrow().providerId().value());
    }

    @Test
    void remoteProviderRequiresExplicitOptIn() {
        var remote = supported("remote", true, ProviderCapability.READ_CHANGES);
        var request = ProviderSelectionRequest.localOnly(
                Set.of(ProviderCapability.READ_CHANGES),
                Set.of());

        var result = policy.select(List.of(remote), request);

        assertTrue(result.selected().isEmpty());
        assertTrue(result.diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.code() == DiagnosticCode.REMOTE_PROVIDER_REQUIRES_OPT_IN));
    }

    @Test
    void missingRequiredCapabilityFailsExplicitly() {
        var provider = supported("local", false, ProviderCapability.DISCOVER_PROJECT);
        var request = ProviderSelectionRequest.localOnly(
                Set.of(ProviderCapability.READ_CHANGES),
                Set.of());

        var result = policy.select(List.of(provider), request);

        assertTrue(result.selected().isEmpty());
        assertTrue(result.diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.code() == DiagnosticCode.MISSING_REQUIRED_CAPABILITY));
    }

    @Test
    void explicitProviderMustRemainCompatible() {
        var provider = supported("local", false, ProviderCapability.DISCOVER_PROJECT);
        var request = new ProviderSelectionRequest(
                Set.of(ProviderCapability.READ_CHANGES),
                Set.of(),
                Optional.of(new ProviderId("local")),
                false);

        var result = policy.select(List.of(provider), request);

        assertTrue(result.selected().isEmpty());
        assertTrue(result.diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.code() == DiagnosticCode.EXPLICIT_PROVIDER_INCOMPATIBLE));
    }

    @Test
    void equivalentProvidersAreReportedAndResolvedDeterministically() {
        var providerB = supported("b", false, ProviderCapability.READ_CHANGES);
        var providerA = supported("a", false, ProviderCapability.READ_CHANGES);
        var request = ProviderSelectionRequest.localOnly(
                Set.of(ProviderCapability.READ_CHANGES),
                Set.of());

        var result = policy.select(List.of(providerB, providerA), request);

        assertEquals("a", result.selected().orElseThrow().providerId().value());
        assertTrue(result.diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.code() == DiagnosticCode.MULTIPLE_PROVIDER_MATCHES));
    }

    private ProviderProbeResult supported(
            String id,
            boolean remote,
            ProviderCapability... capabilities) {
        return new ProviderProbeResult(
                new ProviderId(id),
                "test",
                ProviderProbeStatus.SUPPORTED,
                Optional.of("test-schema"),
                Optional.empty(),
                ProviderCapabilitySet.of(capabilities),
                remote,
                List.of());
    }
}
