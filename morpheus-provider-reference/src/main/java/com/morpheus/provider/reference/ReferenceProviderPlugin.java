package com.morpheus.provider.reference;

import com.morpheus.application.provider.SpecificationProvider;
import com.morpheus.application.read.SpecificationContentReader;
import com.morpheus.sdk.provider.MorpheusProviderPlugin;
import com.morpheus.sdk.provider.ProviderPluginMetadata;
import com.morpheus.sdk.provider.ProviderSdk;

import java.util.Optional;

/** Reference implementation copied by provider authors; it is not a built-in MORPHEUS provider. */
public final class ReferenceProviderPlugin implements MorpheusProviderPlugin {
    public static final ProviderPluginMetadata METADATA = new ProviderPluginMetadata(
            "reference-provider-plugin",
            ReferenceSpecificationProvider.ID,
            "1.0.0",
            ProviderSdk.API_VERSION,
            "1.0.0",
            Optional.empty());

    @Override
    public ProviderPluginMetadata metadata() {
        return METADATA;
    }

    @Override
    public SpecificationProvider createProvider() {
        return new ReferenceSpecificationProvider();
    }

    @Override
    public SpecificationContentReader createContentReader() {
        return new ReferenceSpecificationContentReader();
    }
}
