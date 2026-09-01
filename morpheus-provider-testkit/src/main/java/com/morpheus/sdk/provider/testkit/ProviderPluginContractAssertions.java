package com.morpheus.sdk.provider.testkit;

import com.morpheus.application.identity.EntityIdentityResolver;
import com.morpheus.application.provider.SpecificationProvider;
import com.morpheus.application.read.ProviderReadRequest;
import com.morpheus.application.read.ProviderReadResult;
import com.morpheus.application.read.ReadCategory;
import com.morpheus.application.read.ReadCategoryReport;
import com.morpheus.application.read.SpecificationContentReader;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.provider.ProviderProbeResult;
import com.morpheus.sdk.provider.MorpheusProviderPlugin;
import com.morpheus.sdk.provider.ProviderPluginMetadata;
import com.morpheus.sdk.provider.ProviderSdk;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

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

        SpecificationContentReader contentReader = require(
                plugin.createContentReader(), "plugin.createContentReader() returned null");
        if (!metadata.providerId().equals(contentReader.providerId())) {
            fail("plugin metadata provider id does not match contentReader.providerId()");
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

        return new ContractSnapshot(metadata, first, contentReader);
    }

    /**
     * Exercises {@link SpecificationContentReader#read} against a workspace this plugin claims to
     * support, asserting the same fail-closed and determinism contract every built-in provider is
     * held to: exactly one report per requested category, and an identical result on a repeated
     * read against the same workspace and identity-resolver state.
     */
    public static ProviderReadResult verifyRead(
            ContractSnapshot snapshot, Path supportedWorkspace, ProjectSpecificationId projectId) {
        Objects.requireNonNull(snapshot, "snapshot");
        Path workspace = Objects.requireNonNull(supportedWorkspace, "supportedWorkspace").toAbsolutePath().normalize();
        Objects.requireNonNull(projectId, "projectId");

        ProviderReadRequest request = ProviderReadRequest.all(workspace, projectId);
        InMemoryIdentityResolver resolver = new InMemoryIdentityResolver();

        ProviderReadResult first = require(
                snapshot.contentReader().read(request, resolver), "contentReader.read() returned null");
        ProviderReadResult second = require(
                snapshot.contentReader().read(request, resolver),
                "contentReader.read() returned null on repeated read");

        if (!first.providerId().equals(snapshot.metadata().providerId())) {
            fail("read result provider id does not match plugin metadata provider id");
        }
        if (!first.equals(second)) {
            fail("contentReader.read() must be deterministic for an unchanged workspace and identity resolver state");
        }

        Set<ReadCategory> reported = EnumSet.noneOf(ReadCategory.class);
        for (ReadCategoryReport report : first.categoryReports()) {
            reported.add(report.category());
        }
        if (!reported.equals(request.requestedCategories())) {
            fail("contentReader.read() must return exactly one report per requested category, requested="
                    + request.requestedCategories() + " reported=" + reported);
        }

        return first;
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

    private static final class InMemoryIdentityResolver implements EntityIdentityResolver {
        private final Map<String, DomainIdentity> identities = new HashMap<>();

        @Override
        public DomainIdentity resolve(ProviderId providerId, String entityType, String externalId) {
            String key = providerId + "|" + entityType + "|" + externalId;
            return identities.computeIfAbsent(key, ignored -> DomainIdentity.generate());
        }
    }

    public record ContractSnapshot(
            ProviderPluginMetadata metadata,
            ProviderProbeResult supportedProbe,
            SpecificationContentReader contentReader) {
        public ContractSnapshot {
            Objects.requireNonNull(metadata, "metadata");
            Objects.requireNonNull(supportedProbe, "supportedProbe");
            Objects.requireNonNull(contentReader, "contentReader");
        }
    }
}
