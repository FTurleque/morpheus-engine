package com.morpheus.provider.synthetic;

import com.morpheus.application.provider.SpecificationProvider;
import com.morpheus.domain.diagnostic.Diagnostic;
import com.morpheus.domain.diagnostic.DiagnosticCode;
import com.morpheus.domain.provider.ProviderCapability;
import com.morpheus.domain.provider.ProviderCapabilitySet;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.provider.ProviderProbeResult;
import com.morpheus.domain.provider.ProviderProbeStatus;
import com.morpheus.domain.source.SourceLocator;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Verification-only provider used to prove MORPHEUS is not locked to OpenSpec. */
public final class SyntheticSpecificationProvider implements SpecificationProvider {
    public static final ProviderId ID = new ProviderId("synthetic-json");
    public static final String PROVIDER_VERSION = "m16-s2-v1";
    public static final String SCHEMA = "morpheus-synthetic";
    public static final String SOURCE_FILE = "morpheus-spec.json";

    private static final ProviderCapabilitySet CAPABILITIES = ProviderCapabilitySet.of(
            ProviderCapability.DISCOVER_PROJECT,
            ProviderCapability.READ_CURRENT_SPECIFICATIONS,
            ProviderCapability.READ_CHANGES,
            ProviderCapability.READ_REQUIREMENTS,
            ProviderCapability.READ_CONSTRAINTS,
            ProviderCapability.READ_SCENARIOS,
            ProviderCapability.READ_ACCEPTANCE_CRITERIA);

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
        Path root = workspaceRoot.toAbsolutePath().normalize();
        Path source = root.resolve(SOURCE_FILE);
        if (!Files.isRegularFile(source)) {
            return new ProviderProbeResult(
                    ID,
                    PROVIDER_VERSION,
                    ProviderProbeStatus.UNSUPPORTED,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    ProviderCapabilitySet.of(),
                    false,
                    List.of());
        }

        try {
            Map<String, Object> payload = SyntheticJsonParser.parseObject(Files.readString(source, StandardCharsets.UTF_8));
            Object formatVersion = payload.get("format_version");
            if (formatVersion == null) {
                return invalid(source, "Synthetic source has no format_version");
            }
            return new ProviderProbeResult(
                    ID,
                    PROVIDER_VERSION,
                    ProviderProbeStatus.SUPPORTED,
                    Optional.of(SCHEMA),
                    Optional.of(formatVersion(formatVersion)),
                    Optional.of(SourceLocator.file(SOURCE_FILE)),
                    CAPABILITIES,
                    false,
                    List.of());
        } catch (IOException | IllegalArgumentException exception) {
            return invalid(source, "Synthetic source cannot be parsed: " + exception.getMessage());
        }
    }

    private String formatVersion(Object value) {
        if (value instanceof Number number) {
            double decimal = number.doubleValue();
            long integer = number.longValue();
            return decimal == integer ? Long.toString(integer) : number.toString();
        }
        return value.toString();
    }

    private ProviderProbeResult invalid(Path source, String message) {
        Diagnostic diagnostic = Diagnostic.error(
                DiagnosticCode.INVALID_SOURCE,
                message,
                Map.of("source", source.toString()));
        return new ProviderProbeResult(
                ID,
                PROVIDER_VERSION,
                ProviderProbeStatus.INVALID,
                Optional.of(SCHEMA),
                Optional.empty(),
                Optional.of(SourceLocator.file(SOURCE_FILE)),
                ProviderCapabilitySet.of(),
                false,
                List.of(diagnostic));
    }
}
