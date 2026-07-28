package com.morpheus.sdk.provider;

import com.morpheus.application.provider.SpecificationProvider;
import com.morpheus.application.read.SpecificationContentReader;

import java.io.IOException;
import java.net.URLClassLoader;
import java.util.Objects;

/** Explicitly activated plugin/provider pair. Closing the handle releases its dedicated classloader. */
public final class ProviderPluginActivation implements AutoCloseable {
    private final ProviderPluginCandidate candidate;
    private final MorpheusProviderPlugin plugin;
    private final SpecificationProvider provider;
    private final SpecificationContentReader contentReader;
    private final URLClassLoader classLoader;

    ProviderPluginActivation(
            ProviderPluginCandidate candidate,
            MorpheusProviderPlugin plugin,
            SpecificationProvider provider,
            SpecificationContentReader contentReader,
            URLClassLoader classLoader) {
        this.candidate = Objects.requireNonNull(candidate, "candidate");
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.provider = Objects.requireNonNull(provider, "provider");
        this.contentReader = Objects.requireNonNull(contentReader, "contentReader");
        this.classLoader = Objects.requireNonNull(classLoader, "classLoader");
    }

    public ProviderPluginCandidate candidate() {
        return candidate;
    }

    public MorpheusProviderPlugin plugin() {
        return plugin;
    }

    public SpecificationProvider provider() {
        return provider;
    }

    public SpecificationContentReader contentReader() {
        return contentReader;
    }

    @Override
    public void close() {
        try {
            classLoader.close();
        } catch (IOException failure) {
            throw new IllegalStateException("cannot close provider plugin classloader", failure);
        }
    }
}
