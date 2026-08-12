package com.morpheus.sdk.provider;

import com.morpheus.application.provider.SpecificationProvider;
import com.morpheus.application.read.SpecificationContentReader;

import java.io.IOException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.Optional;

/** Explicitly activated plugin/provider pair. Closing the handle releases its classloader and trusted staging copy. */
public final class ProviderPluginActivation implements AutoCloseable {
    private final ProviderPluginCandidate candidate;
    private final MorpheusProviderPlugin plugin;
    private final SpecificationProvider provider;
    private final SpecificationContentReader contentReader;
    private final URLClassLoader classLoader;
    private final Optional<Path> stagedJar;

    ProviderPluginActivation(
            ProviderPluginCandidate candidate,
            MorpheusProviderPlugin plugin,
            SpecificationProvider provider,
            SpecificationContentReader contentReader,
            URLClassLoader classLoader) {
        this(candidate, plugin, provider, contentReader, classLoader, Optional.empty());
    }

    ProviderPluginActivation(
            ProviderPluginCandidate candidate,
            MorpheusProviderPlugin plugin,
            SpecificationProvider provider,
            SpecificationContentReader contentReader,
            URLClassLoader classLoader,
            Optional<Path> stagedJar) {
        this.candidate = Objects.requireNonNull(candidate, "candidate");
        this.plugin = Objects.requireNonNull(plugin, "plugin");
        this.provider = Objects.requireNonNull(provider, "provider");
        this.contentReader = Objects.requireNonNull(contentReader, "contentReader");
        this.classLoader = Objects.requireNonNull(classLoader, "classLoader");
        this.stagedJar = Objects.requireNonNull(stagedJar, "stagedJar")
                .map(path -> path.toAbsolutePath().normalize());
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
        RuntimeException failure = null;
        try {
            classLoader.close();
        } catch (IOException closeFailure) {
            failure = new IllegalStateException("cannot close provider plugin classloader", closeFailure);
        }
        if (stagedJar.isPresent()) {
            try {
                Files.deleteIfExists(stagedJar.orElseThrow());
            } catch (IOException deleteFailure) {
                IllegalStateException wrapped = new IllegalStateException(
                        "cannot delete trusted provider plugin staging copy", deleteFailure);
                if (failure == null) failure = wrapped;
                else failure.addSuppressed(wrapped);
            }
        }
        if (failure != null) throw failure;
    }
}
