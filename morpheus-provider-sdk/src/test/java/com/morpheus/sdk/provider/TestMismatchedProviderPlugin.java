package com.morpheus.sdk.provider;

import com.morpheus.application.provider.SpecificationProvider;
import com.morpheus.domain.provider.ProviderCapabilitySet;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.provider.ProviderProbeResult;
import com.morpheus.domain.provider.ProviderProbeStatus;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/** Test fixture intentionally disagreeing with the declarative JAR metadata. */
public final class TestMismatchedProviderPlugin implements MorpheusProviderPlugin {
    @Override
    public ProviderPluginMetadata metadata() {
        return new ProviderPluginMetadata(
                "runtime-different-plugin",
                new ProviderId("runtime-provider"),
                "1.0.0",
                ProviderSdk.API_VERSION,
                "1.0.0",
                Optional.empty());
    }

    @Override
    public SpecificationProvider createProvider() {
        return new SpecificationProvider() {
            @Override
            public ProviderId id() {
                return new ProviderId("runtime-provider");
            }

            @Override
            public String version() {
                return "1.0.0";
            }

            @Override
            public boolean remote() {
                return false;
            }

            @Override
            public ProviderProbeResult probe(Path workspaceRoot) {
                return new ProviderProbeResult(
                        id(), version(), ProviderProbeStatus.UNSUPPORTED,
                        Optional.empty(), Optional.empty(), ProviderCapabilitySet.of(), false, List.of());
            }
        };
    }
}
