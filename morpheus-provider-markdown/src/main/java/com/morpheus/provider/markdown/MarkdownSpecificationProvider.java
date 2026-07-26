package com.morpheus.provider.markdown;

import com.morpheus.application.provider.SpecificationProvider;
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
import java.util.Optional;

/** Real local read-only provider for the M18 Structured Markdown format. */
public final class MarkdownSpecificationProvider implements SpecificationProvider {
    public static final ProviderId ID = new ProviderId("markdown");
    public static final String PROVIDER_VERSION = "0.1.0-SNAPSHOT";
    public static final String FORMAT_VERSION = "1";
    public static final String SCHEMA = "morpheus-structured-markdown";
    public static final String ROOT = ".morpheus/specs";

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
        Path specsRoot = root.resolve(ROOT);
        if (!Files.isDirectory(specsRoot)) {
            return new ProviderProbeResult(
                    ID,
                    PROVIDER_VERSION,
                    ProviderProbeStatus.UNSUPPORTED,
                    Optional.of(SCHEMA),
                    Optional.of(FORMAT_VERSION),
                    Optional.empty(),
                    ProviderCapabilitySet.of(),
                    false,
                    List.of());
        }

        return new ProviderProbeResult(
                ID,
                PROVIDER_VERSION,
                ProviderProbeStatus.SUPPORTED,
                Optional.of(SCHEMA),
                Optional.of(FORMAT_VERSION),
                Optional.of(SourceLocator.file(ROOT)),
                ProviderCapabilitySet.copyOf(EnumSet.of(
                        ProviderCapability.DISCOVER_PROJECT,
                        ProviderCapability.READ_CURRENT_SPECIFICATIONS,
                        ProviderCapability.READ_REQUIREMENTS,
                        ProviderCapability.READ_SCENARIOS)),
                false,
                List.of());
    }
}
