package com.morpheus.sdk.provider.testkit;

import com.morpheus.application.identity.EntityIdentityResolver;
import com.morpheus.application.provider.SpecificationProvider;
import com.morpheus.application.read.ProviderReadRequest;
import com.morpheus.application.read.ProviderReadResult;
import com.morpheus.application.read.ReadCategory;
import com.morpheus.application.read.ReadCategoryReport;
import com.morpheus.application.read.ReadCategoryStatus;
import com.morpheus.application.read.SpecificationContentReader;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provider.ProviderCapabilitySet;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.provider.ProviderProbeResult;
import com.morpheus.domain.provider.ProviderProbeStatus;
import com.morpheus.sdk.provider.MorpheusProviderPlugin;
import com.morpheus.sdk.provider.ProviderPluginMetadata;
import com.morpheus.sdk.provider.ProviderSdk;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProviderPluginContractAssertionsTest {
    private static final ProviderId PROVIDER_ID = new ProviderId("testkit-fixture");
    private static final Path WORKSPACE = Path.of(".");

    @Test
    void conformingPluginPassesVerifyAndVerifyRead() {
        FakePlugin plugin = new FakePlugin(PROVIDER_ID, false);

        var snapshot = ProviderPluginContractAssertions.verify(plugin, WORKSPACE);
        assertEquals(PROVIDER_ID, snapshot.metadata().providerId());
        assertTrue(snapshot.supportedProbe().supported());

        var result = ProviderPluginContractAssertions.verifyRead(
                snapshot, WORKSPACE, ProjectSpecificationId.generate());
        assertEquals(PROVIDER_ID, result.providerId());
        assertEquals(EnumSet.allOf(ReadCategory.class).size(), result.categoryReports().size());
    }

    @Test
    void rejectsPluginWhoseMetadataProviderIdDisagreesWithItsProvider() {
        FakePlugin plugin = new FakePlugin(PROVIDER_ID, true);

        assertThrows(AssertionError.class, () -> ProviderPluginContractAssertions.verify(plugin, WORKSPACE));
    }

    @Test
    void rejectsPluginWithNonDeterministicRead() {
        FakePlugin plugin = new FakePlugin(PROVIDER_ID, false);
        var snapshot = ProviderPluginContractAssertions.verify(plugin, WORKSPACE);
        plugin.nonDeterministicReadFrom(2);
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();

        assertThrows(AssertionError.class, () -> ProviderPluginContractAssertions.verifyRead(
                snapshot, WORKSPACE, projectId));
    }

    @Test
    void rejectsPluginThatOmitsARequestedCategoryReport() {
        FakePlugin plugin = new FakePlugin(PROVIDER_ID, false);
        var snapshot = ProviderPluginContractAssertions.verify(plugin, WORKSPACE);
        plugin.dropCategoryReport(ReadCategory.ARCHIVES);
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();

        assertThrows(AssertionError.class, () -> ProviderPluginContractAssertions.verifyRead(
                snapshot, WORKSPACE, projectId));
    }

    private static final class FakePlugin implements MorpheusProviderPlugin {
        private final ProviderId providerId;
        private final boolean metadataMismatch;
        private int nonDeterministicAfterCall = Integer.MAX_VALUE;
        private ReadCategory droppedCategory;
        private final AtomicInteger readCalls = new AtomicInteger();

        FakePlugin(ProviderId providerId, boolean metadataMismatch) {
            this.providerId = providerId;
            this.metadataMismatch = metadataMismatch;
        }

        void nonDeterministicReadFrom(int call) {
            this.nonDeterministicAfterCall = call;
        }

        void dropCategoryReport(ReadCategory category) {
            this.droppedCategory = category;
        }

        @Override
        public ProviderPluginMetadata metadata() {
            ProviderId metadataProviderId = metadataMismatch ? new ProviderId("mismatched") : providerId;
            return new ProviderPluginMetadata(
                    "testkit-fixture-plugin", metadataProviderId, "1.0.0", ProviderSdk.API_VERSION, "1.0.0",
                    Optional.empty());
        }

        @Override
        public SpecificationProvider createProvider() {
            return new FakeProvider();
        }

        @Override
        public SpecificationContentReader createContentReader() {
            return new FakeContentReader();
        }

        private final class FakeProvider implements SpecificationProvider {
            @Override
            public ProviderId id() {
                return providerId;
            }

            @Override
            public String version() {
                return "1.0.0";
            }

            @Override
            public boolean remote() {
                return false;
            }

            @Override
            public ProviderProbeResult probe(Path workspaceRoot) {
                return new ProviderProbeResult(
                        providerId, version(), ProviderProbeStatus.SUPPORTED, Optional.empty(), Optional.empty(),
                        ProviderCapabilitySet.of(), false, List.of());
            }
        }

        private final class FakeContentReader implements SpecificationContentReader {
            @Override
            public ProviderId providerId() {
                return providerId;
            }

            @Override
            public ProviderReadResult read(ProviderReadRequest request, EntityIdentityResolver identityResolver) {
                int call = readCalls.incrementAndGet();
                List<ReadCategoryReport> reports = request.requestedCategories().stream()
                        .filter(category -> category != droppedCategory)
                        .map(category -> ReadCategoryReport.of(
                                category,
                                category == ReadCategory.CURRENT_SPECIFICATIONS
                                        ? ReadCategoryStatus.READ
                                        : ReadCategoryStatus.UNSUPPORTED,
                                call >= nonDeterministicAfterCall && category == ReadCategory.CURRENT_SPECIFICATIONS
                                        ? call
                                        : 0))
                        .toList();
                return new ProviderReadResult(providerId, Optional.empty(), reports, List.of());
            }
        }
    }
}
