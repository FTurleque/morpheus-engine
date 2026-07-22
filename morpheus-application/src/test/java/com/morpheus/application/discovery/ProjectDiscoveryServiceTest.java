package com.morpheus.application.discovery;

import com.morpheus.application.provider.ProviderSelectionPolicy;
import com.morpheus.application.provider.ProviderSelectionRequest;
import com.morpheus.application.provider.SpecificationProvider;
import com.morpheus.application.provider.SpecificationProviderRegistry;
import com.morpheus.domain.diagnostic.Diagnostic;
import com.morpheus.domain.diagnostic.DiagnosticCode;
import com.morpheus.domain.provider.ProviderCapability;
import com.morpheus.domain.provider.ProviderCapabilitySet;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.provider.ProviderProbeResult;
import com.morpheus.domain.provider.ProviderProbeStatus;
import com.morpheus.domain.source.SourceLocator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProjectDiscoveryServiceTest {

    @TempDir
    Path tempDir;

    private final ProviderSelectionRequest request = ProviderSelectionRequest.localOnly(
            Set.of(ProviderCapability.DISCOVER_PROJECT),
            Set.of());

    @Test
    void explicitSourceWinsOverGitAncestorSource() throws IOException {
        Path repo = Files.createDirectories(tempDir.resolve("repo"));
        Files.createDirectory(repo.resolve(".git"));
        Files.writeString(repo.resolve("spec.marker"), "root");
        Path module = Files.createDirectories(repo.resolve("module"));
        Files.writeString(module.resolve("spec.marker"), "module");

        ProjectDiscoveryResult result = service().discover(module, request);

        assertEquals(module.toAbsolutePath().normalize(), result.requestedPath());
        assertEquals(module.toAbsolutePath().normalize(), result.workspaceRoot());
        assertEquals(WorkspaceRootKind.EXPLICIT, result.workspaceRootKind());
        assertEquals("test", result.selectedProvider().orElseThrow().providerId().value());
        assertEquals(List.of(SourceLocator.file("spec.marker")),
                result.sources().stream().map(source -> source.locator()).toList());
    }

    @Test
    void fallsBackToGitAncestorWhenExplicitPathHasNoRecognizedSource() throws IOException {
        Path repo = Files.createDirectories(tempDir.resolve("repo-fallback"));
        Files.createDirectory(repo.resolve(".git"));
        Files.writeString(repo.resolve("spec.marker"), "root");
        Path nested = Files.createDirectories(repo.resolve("src/main/java"));

        ProjectDiscoveryResult result = service().discover(nested, request);

        assertEquals(nested.toAbsolutePath().normalize(), result.requestedPath());
        assertEquals(repo.toAbsolutePath().normalize(), result.workspaceRoot());
        assertEquals(WorkspaceRootKind.GIT_ANCESTOR, result.workspaceRootKind());
        assertTrue(result.resolvedFromGitAncestor());
        assertTrue(result.selectedProvider().isPresent());
        assertEquals(SourceLocator.file("spec.marker"), result.sources().getFirst().locator());
    }

    @Test
    void recognizedInvalidExplicitSourceIsNotMaskedByGitFallback() throws IOException {
        Path repo = Files.createDirectories(tempDir.resolve("repo-invalid"));
        Files.createDirectory(repo.resolve(".git"));
        Files.writeString(repo.resolve("spec.marker"), "valid-at-root");
        Path module = Files.createDirectories(repo.resolve("module"));
        Files.writeString(module.resolve("invalid.marker"), "invalid-here");

        ProjectDiscoveryResult result = service().discover(module, request);

        assertEquals(module.toAbsolutePath().normalize(), result.workspaceRoot());
        assertEquals(WorkspaceRootKind.EXPLICIT, result.workspaceRootKind());
        assertTrue(result.selectedProvider().isEmpty());
        assertTrue(result.diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.code() == DiagnosticCode.INVALID_SOURCE));
        assertEquals(SourceLocator.file("invalid.marker"), result.sources().getFirst().locator());
    }

    @Test
    void supportsNonGitWorkspace() throws IOException {
        Path workspace = Files.createDirectories(tempDir.resolve("plain"));
        Files.writeString(workspace.resolve("spec.marker"), "plain");

        ProjectDiscoveryResult result = service().discover(workspace, request);

        assertEquals(workspace.toAbsolutePath().normalize(), result.workspaceRoot());
        assertEquals(WorkspaceRootKind.EXPLICIT, result.workspaceRootKind());
        assertTrue(result.selectedProvider().isPresent());
        assertFalse(result.resolvedFromGitAncestor());
    }

    @Test
    void invalidWorkspacePathProducesStructuredDiagnostic() {
        Path missing = tempDir.resolve("missing");

        ProjectDiscoveryResult result = service().discover(missing, request);

        assertTrue(result.selectedProvider().isEmpty());
        assertTrue(result.sources().isEmpty());
        assertTrue(result.diagnostics().stream()
                .anyMatch(diagnostic -> diagnostic.code() == DiagnosticCode.INVALID_SOURCE));
    }

    @Test
    void repeatedDiscoveryIsDeterministic() throws IOException {
        Path workspace = Files.createDirectories(tempDir.resolve("deterministic"));
        Files.writeString(workspace.resolve("spec.marker"), "same");

        ProjectDiscoveryResult first = service().discover(workspace, request);
        ProjectDiscoveryResult second = service().discover(workspace, request);

        assertEquals(first.workspaceRoot(), second.workspaceRoot());
        assertEquals(first.workspaceRootKind(), second.workspaceRootKind());
        assertEquals(first.sources(), second.sources());
        assertEquals(first.selectedProvider(), second.selectedProvider());
        assertEquals(first.diagnostics(), second.diagnostics());
    }

    private ProjectDiscoveryService service() {
        var registry = new SpecificationProviderRegistry(
                List.of(new MarkerSpecificationProvider()),
                new ProviderSelectionPolicy());
        return new ProjectDiscoveryService(registry, new WorkspaceRootResolver());
    }

    private static final class MarkerSpecificationProvider implements SpecificationProvider {
        private static final ProviderId ID = new ProviderId("test");

        @Override
        public ProviderId id() {
            return ID;
        }

        @Override
        public String version() {
            return "test";
        }

        @Override
        public boolean remote() {
            return false;
        }

        @Override
        public ProviderProbeResult probe(Path workspaceRoot) {
            Path invalid = workspaceRoot.resolve("invalid.marker");
            if (Files.isRegularFile(invalid)) {
                Diagnostic diagnostic = Diagnostic.error(
                        DiagnosticCode.INVALID_SOURCE,
                        "Synthetic source is invalid.",
                        Map.of("provider", ID.value()));
                return new ProviderProbeResult(
                        ID,
                        version(),
                        ProviderProbeStatus.INVALID,
                        Optional.of("synthetic"),
                        Optional.empty(),
                        Optional.of(SourceLocator.file("invalid.marker")),
                        ProviderCapabilitySet.of(),
                        false,
                        List.of(diagnostic));
            }

            Path marker = workspaceRoot.resolve("spec.marker");
            if (Files.isRegularFile(marker)) {
                return new ProviderProbeResult(
                        ID,
                        version(),
                        ProviderProbeStatus.SUPPORTED,
                        Optional.of("synthetic"),
                        Optional.empty(),
                        Optional.of(SourceLocator.file("spec.marker")),
                        ProviderCapabilitySet.of(ProviderCapability.DISCOVER_PROJECT),
                        false,
                        List.of());
            }

            return new ProviderProbeResult(
                    ID,
                    version(),
                    ProviderProbeStatus.UNSUPPORTED,
                    Optional.empty(),
                    Optional.empty(),
                    ProviderCapabilitySet.of(),
                    false,
                    List.of());
        }
    }
}
