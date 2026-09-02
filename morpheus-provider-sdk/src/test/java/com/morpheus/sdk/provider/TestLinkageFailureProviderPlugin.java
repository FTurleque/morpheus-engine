package com.morpheus.sdk.provider;

import com.morpheus.application.provider.SpecificationProvider;
import com.morpheus.application.read.SpecificationContentReader;
import com.morpheus.domain.provider.ProviderId;

import java.util.Optional;

/**
 * Test fixture that loads and reports metadata, then fails linkage inside a factory call.
 *
 * <p>ServiceLoader wraps whatever a provider throws while being instantiated, so a class-initialization failure
 * reaches the activator as a {@code ServiceConfigurationError}. The three factory calls the activator makes
 * afterwards have no such wrapper: an {@link Error} raised there propagates with its own type, which is the case
 * the activator's cleanup used to miss.</p>
 */
public final class TestLinkageFailureProviderPlugin implements MorpheusProviderPlugin {
    @Override
    public ProviderPluginMetadata metadata() {
        return new ProviderPluginMetadata(
                "manifest-plugin",
                new ProviderId("manifest-provider"),
                "1.0.0",
                ProviderSdk.API_VERSION,
                "1.0.0",
                Optional.empty());
    }

    @Override
    public SpecificationProvider createProvider() {
        throw new NoClassDefFoundError("com/example/ProviderDependencyMissingAtRuntime");
    }

    @Override
    public SpecificationContentReader createContentReader() {
        throw new UnsupportedOperationException("unreachable: createProvider fails first");
    }
}
