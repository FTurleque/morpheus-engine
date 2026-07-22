package com.morpheus.provider.openspec;

import com.morpheus.application.discovery.ProjectDiscoveryService;
import com.morpheus.application.provider.ProviderSelectionPolicy;
import com.morpheus.application.provider.ProviderSelectionRequest;
import com.morpheus.application.provider.SpecificationProviderRegistry;
import com.morpheus.domain.diagnostic.DiagnosticCode;
import com.morpheus.domain.provider.ProviderCapability;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenSpecDiscoveryIntegrationTest {

    @Test
    void discoversAndSelectsOpenSpecForTheBasicM0Fixture() {
        ProjectDiscoveryService service = service();
        var request = ProviderSelectionRequest.localOnly(
                Set.of(
                        ProviderCapability.DISCOVER_PROJECT,
                        ProviderCapability.READ_CHANGES),
                Set.of(ProviderCapability.READ_DESIGN_DECISIONS));

        var result = service.discover(fixture("openspec-basic"), request);

        assertEquals("openspec", result.selectedProvider().orElseThrow().providerId().value());
        assertEquals("spec-driven", result.selectedProvider().orElseThrow().schema().orElseThrow());
        assertTrue(result.diagnostics().isEmpty());
    }

    @Test
    void acceptanceCriteriaCannotBeInventedFromOpenSpecScenarios() {
        ProjectDiscoveryService service = service();
        var request = ProviderSelectionRequest.localOnly(
                Set.of(ProviderCapability.READ_ACCEPTANCE_CRITERIA),
                Set.of());

        var result = service.discover(fixture("openspec-basic"), request);

        assertTrue(result.selectedProvider().isEmpty());
        assertTrue(result.diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.code() == DiagnosticCode.MISSING_REQUIRED_CAPABILITY));
    }

    @Test
    void unsupportedOpenSpecSchemaRemainsExplicit() {
        ProjectDiscoveryService service = service();
        var request = ProviderSelectionRequest.localOnly(
                Set.of(ProviderCapability.DISCOVER_PROJECT),
                Set.of());

        var result = service.discover(fixture("openspec-unsupported-schema"), request);

        assertTrue(result.selectedProvider().isEmpty());
        assertTrue(result.diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.code() == DiagnosticCode.UNSUPPORTED_PROVIDER_SCHEMA));
        assertTrue(result.diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.code() == DiagnosticCode.NO_PROVIDER_FOUND));
    }

    private ProjectDiscoveryService service() {
        var registry = new SpecificationProviderRegistry(
                List.of(new OpenSpecSpecificationProvider()),
                new ProviderSelectionPolicy());
        return new ProjectDiscoveryService(registry);
    }

    private Path fixture(String name) {
        Path current = Path.of("").toAbsolutePath().normalize();
        Path fromRoot = current.resolve("experiments/m0/fixtures").resolve(name);
        if (Files.isDirectory(fromRoot)) {
            return fromRoot;
        }

        Path fromModule = current.resolve("../experiments/m0/fixtures").normalize().resolve(name);
        if (Files.isDirectory(fromModule)) {
            return fromModule;
        }

        throw new IllegalStateException("M0 fixture not found: " + name + " from " + current);
    }
}
