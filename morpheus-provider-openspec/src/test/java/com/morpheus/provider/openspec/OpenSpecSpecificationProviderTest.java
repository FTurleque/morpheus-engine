package com.morpheus.provider.openspec;

import com.morpheus.domain.diagnostic.DiagnosticCode;
import com.morpheus.domain.provider.ProviderCapability;
import com.morpheus.domain.provider.ProviderProbeStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenSpecSpecificationProviderTest {
    private final OpenSpecSpecificationProvider provider = new OpenSpecSpecificationProvider();

    @Test
    void probesBasicM0FixtureAndExposesOnlyEffectiveCapabilities() {
        var result = provider.probe(fixture("openspec-basic"));

        assertEquals(ProviderProbeStatus.SUPPORTED, result.status());
        assertEquals("spec-driven", result.schema().orElseThrow());
        assertTrue(result.capabilities().contains(ProviderCapability.DISCOVER_PROJECT));
        assertTrue(result.capabilities().contains(ProviderCapability.READ_CURRENT_SPECIFICATIONS));
        assertTrue(result.capabilities().contains(ProviderCapability.READ_REQUIREMENTS));
        assertTrue(result.capabilities().contains(ProviderCapability.READ_SCENARIOS));
        assertTrue(result.capabilities().contains(ProviderCapability.READ_CHANGES));
        assertTrue(result.capabilities().contains(ProviderCapability.READ_DESIGN_DECISIONS));
        assertTrue(result.capabilities().contains(ProviderCapability.READ_IMPLEMENTATION_TASKS));
        assertFalse(result.capabilities().contains(ProviderCapability.READ_ACCEPTANCE_CRITERIA));
        assertTrue(result.diagnostics().isEmpty());
    }

    @Test
    void rejectsUnknownOpenSpecSchemaExplicitly() {
        var result = provider.probe(fixture("openspec-unsupported-schema"));

        assertEquals(ProviderProbeStatus.UNSUPPORTED, result.status());
        assertEquals("research-first", result.schema().orElseThrow());
        assertTrue(result.diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.code() == DiagnosticCode.UNSUPPORTED_PROVIDER_SCHEMA));
    }

    @Test
    void unrelatedWorkspaceIsSimplyUnsupported(@TempDir Path workspace) {
        var result = provider.probe(workspace);

        assertEquals(ProviderProbeStatus.UNSUPPORTED, result.status());
        assertTrue(result.capabilities().values().isEmpty());
        assertTrue(result.diagnostics().isEmpty());
    }

    @Test
    void configWithoutSchemaIsInvalid(@TempDir Path workspace) throws Exception {
        Path openspec = Files.createDirectories(workspace.resolve("openspec"));
        Files.writeString(openspec.resolve("config.yaml"), "context: missing schema\n");

        var result = provider.probe(workspace);

        assertEquals(ProviderProbeStatus.INVALID, result.status());
        assertTrue(result.diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.code() == DiagnosticCode.INVALID_SOURCE));
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
