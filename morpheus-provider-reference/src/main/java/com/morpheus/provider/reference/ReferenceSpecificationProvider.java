package com.morpheus.provider.reference;

import com.morpheus.application.provider.SpecificationProvider;
import com.morpheus.domain.provider.ProviderCapability;
import com.morpheus.domain.provider.ProviderCapabilitySet;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.provider.ProviderProbeResult;
import com.morpheus.domain.provider.ProviderProbeStatus;
import com.morpheus.domain.source.SourceLocator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** Minimal external provider used as the M22 plugin authoring template. */
public final class ReferenceSpecificationProvider implements SpecificationProvider {
    public static final ProviderId ID = new ProviderId("reference-plugin");
    public static final String VERSION = "1.0.0";
    public static final String MARKER_FILE = "morpheus-reference.spec";

    @Override
    public ProviderId id() {
        return ID;
    }

    @Override
    public String version() {
        return VERSION;
    }

    @Override
    public boolean remote() {
        return false;
    }

    @Override
    public ProviderProbeResult probe(Path workspaceRoot) {
        Path root = workspaceRoot.toAbsolutePath().normalize();
        Path marker = root.resolve(MARKER_FILE);
        if (!Files.isRegularFile(marker)) {
            return new ProviderProbeResult(
                    ID,
                    VERSION,
                    ProviderProbeStatus.UNSUPPORTED,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    ProviderCapabilitySet.of(),
                    false,
                    List.of());
        }
        return new ProviderProbeResult(
                ID,
                VERSION,
                ProviderProbeStatus.SUPPORTED,
                Optional.of("morpheus-reference"),
                Optional.of("1"),
                Optional.of(SourceLocator.file(MARKER_FILE)),
                ProviderCapabilitySet.of(ProviderCapability.DISCOVER_PROJECT),
                false,
                List.of());
    }
}
