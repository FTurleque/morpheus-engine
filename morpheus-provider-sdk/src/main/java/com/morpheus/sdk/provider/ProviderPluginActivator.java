package com.morpheus.sdk.provider;

import com.morpheus.application.provider.SpecificationProvider;
import com.morpheus.application.read.SpecificationContentReader;
import com.morpheus.application.security.ExternalJarIntegrity;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;

/**
 * Explicit activation of one compatible candidate in a dedicated classloader.
 *
 * <p>Not public: plugin code runs only in the probe worker, the child process of SEC-0129. A host reaches it through
 * {@link ProviderPluginService#probe}, never by activating a plugin in its own process.</p>
 */
final class ProviderPluginActivator {

    /**
     * Activates a compatible candidate after mandatory SHA-256 pin verification. An owner-hardened verified staging
     * copy is created and that immutable copy is the only JAR exposed to URLClassLoader/ServiceLoader, closing the
     * verification-to-load TOCTOU window on the original path. A missing pin is refused before the JAR is read.
     */
    ProviderPluginActivation activate(ProviderPluginCandidate candidate, String expectedSha256) {
        Objects.requireNonNull(candidate, "candidate");
        if (expectedSha256 == null || expectedSha256.isBlank()) {
            throw new IllegalArgumentException("provider plugin activation requires a trusted SHA-256 pin");
        }
        return activatePinned(candidate, ExternalJarIntegrity.normalizeSha256(expectedSha256));
    }

    private ProviderPluginActivation activatePinned(ProviderPluginCandidate candidate, String trustedSha256) {
        if (!candidate.compatible()) {
            throw new IllegalArgumentException("provider plugin candidate is not compatible: " + candidate.jarPath());
        }
        ProviderPluginMetadata manifestMetadata = candidate.metadata()
                .orElseThrow(() -> new IllegalArgumentException("compatible candidate has no metadata"));

        Path stagedJar = ExternalJarIntegrity.stageVerifiedCopy(candidate.jarPath(), trustedSha256);

        URLClassLoader loader;
        try {
            loader = new URLClassLoader(
                    new java.net.URL[] {stagedJar.toUri().toURL()},
                    MorpheusProviderPlugin.class.getClassLoader());
        } catch (MalformedURLException failure) {
            deleteStagedSuppressing(stagedJar, failure);
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
            return new ProviderPluginActivation(candidate, plugin, provider, contentReader, loader, stagedJar);
        } catch (Throwable failure) {
            // Everything inside the try runs third-party code: service lookup, class initialization, then three
            // plugin factory calls. That code fails in ways no exception taxonomy enumerates -- a static
            // initializer that throws surfaces as ExceptionInInitializerError, a JAR built against another API as
            // NoClassDefFoundError, neither of which is a ServiceConfigurationError or a RuntimeException. Those
            // used to escape before the classloader was closed, and on Windows an open classloader holds the JAR
            // handle, so the staged copy could not be deleted at all. Cleanup is therefore driven by leaving the
            // block, not by the kind of failure that caused it.
            releaseLoaderAndStaging(loader, stagedJar, failure);
            if (failure instanceof ServiceConfigurationError || failure instanceof RuntimeException) {
                throw new IllegalStateException("provider plugin activation failed for " + candidate.jarPath(), failure);
            }
            // A LinkageError or a VM error keeps its own type: ProviderPluginService reports the first as a
            // diagnostic, and the second must not be disguised as an ordinary activation failure.
            if (failure instanceof Error error) {
                throw error;
            }
            throw new IllegalStateException("provider plugin activation failed for " + candidate.jarPath(), failure);
        }
    }

    private static void releaseLoaderAndStaging(URLClassLoader loader, Path stagedJar, Throwable primary) {
        try {
            loader.close();
        } catch (IOException | RuntimeException closeFailure) {
            primary.addSuppressed(closeFailure);
        }
        deleteStagedSuppressing(stagedJar, primary);
    }

    private static void deleteStagedSuppressing(Path stagedJar, Throwable primary) {
        try {
            Files.deleteIfExists(stagedJar);
        } catch (IOException deleteFailure) {
            primary.addSuppressed(deleteFailure);
        }
    }
}
