package com.morpheus.sdk.provider;

import com.morpheus.application.provider.SpecificationProvider;
import com.morpheus.application.read.SpecificationContentReader;
import com.morpheus.application.security.ExternalJarIntegrity;

import java.net.MalformedURLException;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

/** Explicit activation of one compatible candidate in a dedicated classloader. */
public final class ProviderPluginActivator {

    public ProviderPluginActivation activate(ProviderPluginCandidate candidate) {
        return activate(candidate, Optional.empty());
    }

    /**
     * Activates a compatible candidate after optional SHA-256 pin verification. When a pin is supplied,
     * verification is completed before URLClassLoader creation or ServiceLoader execution.
     */
    public ProviderPluginActivation activate(ProviderPluginCandidate candidate, String expectedSha256) {
        return activate(candidate, Optional.of(ExternalJarIntegrity.normalizeSha256(expectedSha256)));
    }

    private ProviderPluginActivation activate(ProviderPluginCandidate candidate, Optional<String> expectedSha256) {
        Objects.requireNonNull(candidate, "candidate");
        if (!candidate.compatible()) {
            throw new IllegalArgumentException("provider plugin candidate is not compatible: " + candidate.jarPath());
        }
        ProviderPluginMetadata manifestMetadata = candidate.metadata()
                .orElseThrow(() -> new IllegalArgumentException("compatible candidate has no metadata"));
        expectedSha256.ifPresent(expected -> ExternalJarIntegrity.verifySha256(candidate.jarPath(), expected));

        URLClassLoader loader;
        try {
            loader = new URLClassLoader(
                    new java.net.URL[] {candidate.jarPath().toUri().toURL()},
                    MorpheusProviderPlugin.class.getClassLoader());
        } catch (MalformedURLException failure) {
            throw new IllegalStateException("cannot create provider plugin classloader", failure);
        }

        try {
            List<MorpheusProviderPlugin> plugins = new ArrayList<>();
            ServiceLoader.load(MorpheusProviderPlugin.class, loader).forEach(plugins::add);
            if (plugins.size() != 1) {
                throw new IllegalStateException(
                        "provider plugin JAR must expose exactly one MorpheusProviderPlugin service; found " + plugins.size());
            }

            MorpheusProviderPlugin plugin = plugins.getFirst();
            ProviderPluginMetadata runtimeMetadata = Objects.requireNonNull(plugin.metadata(), "plugin metadata");
            if (!manifestMetadata.equals(runtimeMetadata)) {
                throw new IllegalStateException("provider plugin runtime metadata does not match its declarative manifest");
            }

            SpecificationProvider provider = Objects.requireNonNull(plugin.createProvider(), "plugin provider");
            if (!manifestMetadata.providerId().equals(provider.id())) {
                throw new IllegalStateException(
                        "provider id mismatch: manifest=" + manifestMetadata.providerId() + " runtime=" + provider.id());
            }

            SpecificationContentReader contentReader = Objects.requireNonNull(
                    plugin.createContentReader(), "plugin content reader");
            if (!manifestMetadata.providerId().equals(contentReader.providerId())) {
                throw new IllegalStateException(
                        "content reader provider id mismatch: manifest=" + manifestMetadata.providerId()
                                + " runtime=" + contentReader.providerId());
            }
            return new ProviderPluginActivation(candidate, plugin, provider, contentReader, loader);
        } catch (ServiceConfigurationError | RuntimeException failure) {
            try {
                loader.close();
            } catch (java.io.IOException closeFailure) {
                failure.addSuppressed(closeFailure);
            }
            throw new IllegalStateException("provider plugin activation failed for " + candidate.jarPath(), failure);
        }
    }
}
