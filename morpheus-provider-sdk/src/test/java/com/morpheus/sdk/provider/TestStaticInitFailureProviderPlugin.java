package com.morpheus.sdk.provider;

import com.morpheus.application.provider.SpecificationProvider;
import com.morpheus.application.read.SpecificationContentReader;

/**
 * Test fixture whose class initialization fails.
 *
 * <p>A plugin that throws from a static initializer reaches the activator as {@link ExceptionInInitializerError},
 * which is neither a {@code ServiceConfigurationError} nor a {@code RuntimeException}. That is the shape the
 * activator's cleanup used to miss.</p>
 */
public final class TestStaticInitFailureProviderPlugin implements MorpheusProviderPlugin {
    static {
        if (Boolean.TRUE) {
            throw new IllegalStateException("plugin static initializer refused to run");
        }
    }

    @Override
    public ProviderPluginMetadata metadata() {
        throw new UnsupportedOperationException("unreachable: class initialization always fails");
    }

    @Override
    public SpecificationProvider createProvider() {
        throw new UnsupportedOperationException("unreachable: class initialization always fails");
    }

    @Override
    public SpecificationContentReader createContentReader() {
        throw new UnsupportedOperationException("unreachable: class initialization always fails");
    }
}
