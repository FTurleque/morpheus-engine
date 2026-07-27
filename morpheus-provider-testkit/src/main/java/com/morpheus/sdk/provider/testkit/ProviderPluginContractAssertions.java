package com.morpheus.sdk.provider.testkit;

import com.morpheus.application.provider.SpecificationProvider;
import com.morpheus.domain.provider.ProviderProbeResult;
import com.morpheus.sdk.provider.MorpheusProviderPlugin;
import com.morpheus.sdk.provider.ProviderPluginMetadata;
import com.morpheus.sdk.provider.ProviderSdk;

import java.nio.file.Path;
import java.util.Objects;

/** Framework-neutral contract assertions usable from JUnit, TestNG or another test runner. */
public final class ProviderPluginContractAssertions {
    private ProviderPluginContractAssertions() {
    }

    public static ContractSnapshot verify(MorpheusProviderPlugin plugin, Path supportedWorkspace) {
        Objects.requireNonNull(plugin, "plugin");
        Path workspace = Objects.requireNonNull(supportedWorkspace, "supportedWorkspace").toAbsolutePath().normalize();
        ProviderPluginMetadata metadata = require(plugin.metadata(), "plugin.metadata() returned null");
        if (metadata.sdkApiVersion() != ProviderSdk.API_VERSION) {
            fail("plugin SDK API version does not match current ProviderSdk.API_VERSION");
        }

        SpecificationProvider provider = require(plugin.createProvider(), "plugin.createProvider() returned null");
        if (!metadata.providerId().equals(provider.id())) {
            fail("plugin metadata provider id does not match provider.id()");
        }
        if (provider.version() == null || provider.version().isBlank()) {
            fail("provider.version() must not be blank");
        }

        ProviderProbeResult first = require(provider.probe(workspace), "provider.probe() returned null");
        ProviderProbeResult second = require(provider.probe(workspace), "provider.probe() returned null on repeated probe");
        if (!first.equals(second)) {
            fail("provider probe must be deterministic for an unchanged workspace");
        }
        if (!first.providerId().equals(provider.id())) {
            fail("probe provider id does not match provider.id()");
        }
        if (!first.providerVersion().equals(provider.version())) {
            fail("probe provider version does not match provider.version()");
        }
        if (first.remote() != provider.remote()) {
            fail("probe remote flag does not match provider.remote()");
        }

        return new ContractSnapshot(metadata, first);
    }

    private static <T> T require(T value, String message) {
        if (value == null) {
            fail(message);
        }
        return value;
    }

    private static void fail(String message) {
        throw new AssertionError(message);
    }

    public record ContractSnapshot(ProviderPluginMetadata metadata, ProviderProbeResult supportedProbe) {
        public ContractSnapshot {
            Objects.requireNonNull(metadata, "metadata");
            Objects.requireNonNull(supportedProbe, "supportedProbe");
        }
    }
}
