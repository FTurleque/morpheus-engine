package com.morpheus.provider.openspec;

import com.morpheus.application.discovery.ProjectDiscoveryService;
import com.morpheus.application.discovery.WorkspaceRootKind;
import com.morpheus.application.provider.ProviderSelectionPolicy;
import com.morpheus.application.provider.ProviderSelectionRequest;
import com.morpheus.application.provider.SpecificationProviderRegistry;
import com.morpheus.domain.diagnostic.DiagnosticCode;
import com.morpheus.domain.provider.ProviderCapability;
import com.morpheus.domain.source.SourceLocator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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

        Path fixture = fixture("openspec-basic");
        var result = service.discover(fixture, request);

        assertEquals(fixture.toAbsolutePath().normalize(), result.requestedPath());
        assertEquals(fixture.toAbsolutePath().normalize(), result.workspaceRoot());
        assertEquals(WorkspaceRootKind.EXPLICIT, result.workspaceRootKind());
        assertEquals("openspec", result.selectedProvider().orElseThrow().providerId().value());
        assertEquals("spec-driven", result.selectedProvider().orElseThrow().schema().orElseThrow());
        assertEquals(SourceLocator.file("openspec/config.yaml"), result.sources().getFirst().locator());
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
        assertEquals(SourceLocator.file("openspec/config.yaml"), result.sources().getFirst().locator());
        assertTrue(result.diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.code() == DiagnosticCode.UNSUPPORTED_PROVIDER_SCHEMA));
        assertTrue(result.diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.code() == DiagnosticCode.NO_PROVIDER_FOUND));
    }

    @Test
    void discoversOpenSpecFromGitAncestorWhenInvokedFromNestedDirectory(@TempDir Path repo) throws Exception {
        Files.createDirectory(repo.resolve(".git"));
        Path openspec = Files.createDirectories(repo.resolve("openspec"));
        Files.writeString(openspec.resolve("config.yaml"), "schema: spec-driven\n");
        Path nested = Files.createDirectories(repo.resolve("src/main/java"));

        var result = service().discover(
                nested,
                ProviderSelectionRequest.localOnly(
                        Set.of(ProviderCapability.DISCOVER_PROJECT),
                        Set.of()));

        assertEquals(nested.toAbsolutePath().normalize(), result.requestedPath());
        assertEquals(repo.toAbsolutePath().normalize(), result.workspaceRoot());
        assertEquals(WorkspaceRootKind.GIT_ANCESTOR, result.workspaceRootKind());
        assertEquals("openspec", result.selectedProvider().orElseThrow().providerId().value());
        assertEquals(SourceLocator.file("openspec/config.yaml"), result.sources().getFirst().locator());
    }

    @Test
    void invalidExplicitOpenSpecIsNotMaskedByValidGitRootOpenSpec(@TempDir Path repo) throws Exception {
        Files.createDirectory(repo.resolve(".git"));
        Path rootOpenSpec = Files.createDirectories(repo.resolve("openspec"));
        Files.writeString(rootOpenSpec.resolve("config.yaml"), "schema: spec-driven\n");

        Path module = Files.createDirectories(repo.resolve("module"));
        Path moduleOpenSpec = Files.createDirectories(module.resolve("openspec"));
        Files.writeString(moduleOpenSpec.resolve("config.yaml"), "schema: research-first\n");

        var result = service().discover(
                module,
                ProviderSelectionRequest.localOnly(
                        Set.of(ProviderCapability.DISCOVER_PROJECT),
                        Set.of()));

        assertEquals(module.toAbsolutePath().normalize(), result.workspaceRoot());
        assertEquals(WorkspaceRootKind.EXPLICIT, result.workspaceRootKind());
        assertTrue(result.selectedProvider().isEmpty());
        assertTrue(result.diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.code() == DiagnosticCode.UNSUPPORTED_PROVIDER_SCHEMA));
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
