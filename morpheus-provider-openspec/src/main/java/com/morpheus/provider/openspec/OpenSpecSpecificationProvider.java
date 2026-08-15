package com.morpheus.provider.openspec;

import com.morpheus.application.provider.SpecificationProvider;
import com.morpheus.application.files.SafeWorkspaceFileResolver;
import com.morpheus.application.read.ProviderIngestionBudget;
import com.morpheus.domain.diagnostic.Diagnostic;
import com.morpheus.domain.diagnostic.DiagnosticCode;
import com.morpheus.domain.provider.ProviderCapability;
import com.morpheus.domain.provider.ProviderCapabilitySet;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.provider.ProviderProbeResult;
import com.morpheus.domain.provider.ProviderProbeStatus;
import com.morpheus.domain.source.SourceLocator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Read-only M1 provider probe for OpenSpec projects using the {@code spec-driven} schema. */
public final class OpenSpecSpecificationProvider implements SpecificationProvider {
    public static final ProviderId ID = new ProviderId("openspec");
    public static final String PROVIDER_VERSION = "m1-v1";
    public static final String SUPPORTED_SCHEMA = "spec-driven";

    private static final Pattern SCHEMA_PATTERN = Pattern.compile("^schema:\\s*([^#\\s]+)");
    private static final SourceLocator CONFIG_LOCATOR = SourceLocator.file("openspec/config.yaml");

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
        return probe(workspaceRoot, null);
    }

    ProviderProbeResult probe(Path workspaceRoot, ProviderIngestionBudget.Session budget) {
        Path root = workspaceRoot.toAbsolutePath().normalize();
        Path openspecRoot = root.resolve("openspec");
        Path config = openspecRoot.resolve("config.yaml");

        if (!Files.isDirectory(openspecRoot) || !Files.exists(config)) {
            return result(
                    ProviderProbeStatus.UNSUPPORTED,
                    Optional.empty(),
                    Optional.empty(),
                    ProviderCapabilitySet.of(),
                    List.of());
        }

        if (!Files.isRegularFile(config) || !Files.isReadable(config)) {
            return invalid(config, "OpenSpec config.yaml is not a readable regular file.");
        }

        final Optional<String> schema;
        try {
            Path relativeConfig = Path.of("openspec/config.yaml");
            String configText = budget == null
                    ? SafeWorkspaceFileResolver.rootedAt(root).readUtf8(
                            relativeConfig,
                            Math.toIntExact(ProviderIngestionBudget.DEFAULT.maxDocumentBytes()))
                    : budget.readDocument(relativeConfig);
            schema = readSchema(configText);
        } catch (IOException | IllegalArgumentException exception) {
            return invalid(config, "OpenSpec config.yaml could not be read.");
        }

        if (schema.isEmpty()) {
            return invalid(config, "OpenSpec config.yaml does not declare a schema.");
        }

        if (!SUPPORTED_SCHEMA.equals(schema.orElseThrow())) {
            Diagnostic diagnostic = Diagnostic.error(
                    DiagnosticCode.UNSUPPORTED_PROVIDER_SCHEMA,
                    "The OpenSpec schema is not supported by this MORPHEUS provider.",
                    Map.of(
                            "provider", ID.value(),
                            "schema", schema.orElseThrow(),
                            "supportedSchema", SUPPORTED_SCHEMA));
            return result(
                    ProviderProbeStatus.UNSUPPORTED,
                    schema,
                    Optional.of(CONFIG_LOCATOR),
                    ProviderCapabilitySet.of(),
                    List.of(diagnostic));
        }

        return result(
                ProviderProbeStatus.SUPPORTED,
                schema,
                Optional.of(CONFIG_LOCATOR),
                detectCapabilities(openspecRoot),
                List.of());
    }

    private ProviderCapabilitySet detectCapabilities(Path openspecRoot) {
        Set<ProviderCapability> capabilities = EnumSet.of(ProviderCapability.DISCOVER_PROJECT);

        if (Files.isDirectory(openspecRoot.resolve("specs"))) {
            capabilities.add(ProviderCapability.READ_CURRENT_SPECIFICATIONS);
            capabilities.add(ProviderCapability.READ_REQUIREMENTS);
            capabilities.add(ProviderCapability.READ_SCENARIOS);
        }

        Path changesRoot = openspecRoot.resolve("changes");
        if (Files.isDirectory(changesRoot)) {
            capabilities.add(ProviderCapability.READ_CHANGES);
            capabilities.add(ProviderCapability.READ_DESIGN_DECISIONS);
            capabilities.add(ProviderCapability.READ_IMPLEMENTATION_TASKS);
        }

        if (Files.isDirectory(changesRoot.resolve("archive"))) {
            capabilities.add(ProviderCapability.READ_HISTORY);
            capabilities.add(ProviderCapability.READ_ARCHIVES);
        }

        return ProviderCapabilitySet.copyOf(capabilities);
    }

    private Optional<String> readSchema(String configText) {
        return configText.lines()
                .map(SCHEMA_PATTERN::matcher)
                .filter(Matcher::find)
                .map(matcher -> matcher.group(1).trim())
                .findFirst();
    }

    private ProviderProbeResult invalid(Path source, String message) {
        Diagnostic diagnostic = new Diagnostic(
                DiagnosticCode.INVALID_SOURCE,
                com.morpheus.domain.diagnostic.DiagnosticSeverity.ERROR,
                message,
                Map.of("provider", ID.value()),
                Optional.of(source.toString()));
        return result(
                ProviderProbeStatus.INVALID,
                Optional.empty(),
                Optional.of(CONFIG_LOCATOR),
                ProviderCapabilitySet.of(),
                List.of(diagnostic));
    }

    private ProviderProbeResult result(
            ProviderProbeStatus status,
            Optional<String> schema,
            Optional<SourceLocator> sourceLocator,
            ProviderCapabilitySet capabilities,
            List<Diagnostic> diagnostics) {
        return new ProviderProbeResult(
                ID,
                PROVIDER_VERSION,
                status,
                schema,
                Optional.empty(),
                sourceLocator,
                capabilities,
                false,
                diagnostics);
    }
}
