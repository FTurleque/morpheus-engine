package com.morpheus.sdk.provider;

import com.morpheus.application.identity.EntityIdentityResolver;
import com.morpheus.application.provider.SpecificationProvider;
import com.morpheus.application.read.ProviderReadRequest;
import com.morpheus.application.read.ProviderReadResult;
import com.morpheus.application.read.SpecificationContentReader;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.provider.ProviderProbeResult;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.locks.LockSupport;

/** Test-only plugin whose probe never returns and ignores interruption. */
public final class TestBlockingProviderPlugin implements MorpheusProviderPlugin {
    private static final ProviderId PROVIDER_ID = new ProviderId("blocking-provider");

    @Override
    public ProviderPluginMetadata metadata() {
        return new ProviderPluginMetadata(
                "blocking-plugin",
                PROVIDER_ID,
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
                return PROVIDER_ID;
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
                while (true) {
                    LockSupport.parkNanos(100_000_000L);
                    Thread.interrupted();
                }
            }
        };
    }

    @Override
    public SpecificationContentReader createContentReader() {
        return new SpecificationContentReader() {
            @Override
            public ProviderId providerId() {
                return PROVIDER_ID;
            }

            @Override
            public ProviderReadResult read(ProviderReadRequest request, EntityIdentityResolver identityResolver) {
                throw new UnsupportedOperationException("blocking probe fixture is never read");
            }
        };
    }
}
