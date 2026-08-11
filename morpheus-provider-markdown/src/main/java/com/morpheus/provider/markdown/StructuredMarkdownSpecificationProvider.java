package com.morpheus.provider.markdown;

import com.morpheus.application.provider.SpecificationProvider;
import com.morpheus.application.files.SafeWorkspaceFileResolver;
import com.morpheus.domain.diagnostic.Diagnostic;
import com.morpheus.domain.diagnostic.DiagnosticCode;
import com.morpheus.domain.provider.ProviderCapability;
import com.morpheus.domain.provider.ProviderCapabilitySet;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.provider.ProviderProbeResult;
import com.morpheus.domain.provider.ProviderProbeStatus;
import com.morpheus.domain.source.SourceLocator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Local provider for fenced MORPHEUS records embedded in ordinary Markdown. */
public final class StructuredMarkdownSpecificationProvider implements SpecificationProvider {
    public static final ProviderId ID = new ProviderId("structured-markdown");
    public static final String PROVIDER_VERSION = "m18-v1";
    public static final String SCHEMA = "morpheus-markdown-v1";
    public static final String SOURCE_FILE = "morpheus/specification.md";

    @Override
    public ProviderId id() {
        return ID;
    }

    @Override
    public String version() {
        return PROVIDER_VERSION;
    }

    @Override
    public boolean remote() {
        return false;
    }

    @Override
    public ProviderProbeResult probe(Path workspaceRoot) {
        Path source = workspaceRoot.toAbsolutePath().normalize().resolve(SOURCE_FILE);
        if (!Files.exists(source)) {
            return result(ProviderProbeStatus.UNSUPPORTED, ProviderCapabilitySet.of(), List.of());
        }
        if (!Files.isRegularFile(source) || !Files.isReadable(source)) {
            Diagnostic diagnostic = Diagnostic.error(
                    DiagnosticCode.INVALID_SOURCE,
                    "Structured Markdown source is not a readable regular file",
                    Map.of("provider", ID.value(), "source", SOURCE_FILE));
            return result(ProviderProbeStatus.INVALID, ProviderCapabilitySet.of(), List.of(diagnostic));
        }
        try {
            SafeWorkspaceFileResolver.rootedAt(workspaceRoot).requireRegularFile(Path.of(SOURCE_FILE));
        } catch (java.io.IOException | IllegalArgumentException exception) {
            Diagnostic diagnostic = Diagnostic.error(
                    DiagnosticCode.INVALID_SOURCE,
                    "Structured Markdown source is not safely confined to the workspace",
                    Map.of("provider", ID.value(), "source", SOURCE_FILE));
            return result(ProviderProbeStatus.INVALID, ProviderCapabilitySet.of(), List.of(diagnostic));
        }
        ProviderCapabilitySet capabilities = ProviderCapabilitySet.copyOf(EnumSet.of(
                ProviderCapability.DISCOVER_PROJECT,
                ProviderCapability.READ_CURRENT_SPECIFICATIONS,
                ProviderCapability.READ_REQUIREMENTS,
                ProviderCapability.READ_SCENARIOS,
                ProviderCapability.READ_CHANGES,
                ProviderCapability.READ_CONSTRAINTS,
                ProviderCapability.READ_DESIGN_DECISIONS,
                ProviderCapability.READ_ACCEPTANCE_CRITERIA,
                ProviderCapability.READ_IMPLEMENTATION_TASKS));
        return result(ProviderProbeStatus.SUPPORTED, capabilities, List.of());
    }

    private ProviderProbeResult result(
            ProviderProbeStatus status,
            ProviderCapabilitySet capabilities,
            List<Diagnostic> diagnostics) {
        return new ProviderProbeResult(
                ID,
                PROVIDER_VERSION,
                status,
                Optional.of(SCHEMA),
                Optional.empty(),
                Optional.of(SourceLocator.file(SOURCE_FILE)),
                capabilities,
                false,
                diagnostics);
    }
}
