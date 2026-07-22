package com.morpheus.application.provider;

import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.provider.ProviderProbeResult;

import java.nio.file.Path;

/** Port implemented by specification-format adapters. */
public interface SpecificationProvider {
    ProviderId id();

    String version();

    boolean remote();

    ProviderProbeResult probe(Path workspaceRoot);
}
