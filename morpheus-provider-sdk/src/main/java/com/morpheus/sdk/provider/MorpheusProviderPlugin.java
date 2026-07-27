package com.morpheus.sdk.provider;

import com.morpheus.application.provider.SpecificationProvider;

/**
 * Service-provider interface implemented by external MORPHEUS provider plugins.
 *
 * <p>The plugin object is instantiated only during explicit activation. Discovery reads the declarative JAR metadata
 * without loading this service.</p>
 */
public interface MorpheusProviderPlugin {
    ProviderPluginMetadata metadata();

    SpecificationProvider createProvider();
}
