package com.morpheus.sdk.provider;

import com.morpheus.application.provider.SpecificationProvider;
import com.morpheus.application.read.SpecificationContentReader;

/**
 * Service-provider interface implemented by external MORPHEUS provider plugins.
 *
 * <p>The plugin object is instantiated only during explicit activation. Discovery reads the declarative JAR metadata
 * without loading this service.</p>
 *
 * <p>Probe/capability negotiation and normalized content reads remain separate contracts, preserving ADR-0028:</p>
 *
 * <pre>SpecificationProvider.probe() != SpecificationContentReader.read()</pre>
 */
public interface MorpheusProviderPlugin {
    ProviderPluginMetadata metadata();

    SpecificationProvider createProvider();

    SpecificationContentReader createContentReader();
}
