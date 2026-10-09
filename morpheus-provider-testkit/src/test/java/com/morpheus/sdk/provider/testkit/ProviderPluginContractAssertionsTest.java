package com.morpheus.sdk.provider.testkit;

import com.morpheus.application.identity.EntityIdentityResolver;
import com.morpheus.application.ingestion.NormalizedProjectContent;
import com.morpheus.application.provider.SpecificationProvider;
import com.morpheus.application.read.ProviderReadRequest;
import com.morpheus.application.read.ProviderReadResult;
import com.morpheus.application.read.ReadCategory;
import com.morpheus.application.read.ReadCategoryReport;
import com.morpheus.application.read.ReadCategoryStatus;
import com.morpheus.application.read.SpecificationContentReader;
import com.morpheus.domain.evidence.Evidence;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.project.ProjectSpecification;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provider.ProviderCapabilitySet;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.provider.ProviderProbeResult;
import com.morpheus.domain.provider.ProviderProbeStatus;
import com.morpheus.domain.source.SourceLocator;
import com.morpheus.sdk.provider.MorpheusProviderPlugin;
import com.morpheus.sdk.provider.ProviderPluginMetadata;
import com.morpheus.sdk.provider.ProviderSdk;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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

    /**
     * A reader publishes the workspace it received as the project root, never a file inside it.
     *
     * <p>Publication compares that root with the one the project was registered under; a reader that publishes
     * anything else makes its own publication impossible, and the testkit is where a plugin author learns it.</p>
     */
    @Test
    void rejectsAReaderThatPublishesAFileInsideTheWorkspaceAsProjectRoot() {
        FakePlugin plugin = new FakePlugin(PROVIDER_ID, false);
        var snapshot = ProviderPluginContractAssertions.verify(plugin, WORKSPACE);
        plugin.publish((request, identities) -> content(
                request, SourceLocator.file("morpheus/specification.md"), List.of()));
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();

        AssertionError failure = assertThrows(AssertionError.class, () -> ProviderPluginContractAssertions.verifyRead(
                snapshot, WORKSPACE, projectId));
        assertTrue(failure.getMessage().contains("project root"), failure.getMessage());
    }

    @Test
    void acceptsAReaderThatPublishesTheWorkspaceRootItReceived() {
        FakePlugin plugin = new FakePlugin(PROVIDER_ID, false);
        var snapshot = ProviderPluginContractAssertions.verify(plugin, WORKSPACE);
        plugin.publish((request, identities) -> content(
                request, SourceLocator.file(request.workspaceRoot().toString()), List.of()));

        var result = ProviderPluginContractAssertions.verifyRead(snapshot, WORKSPACE, ProjectSpecificationId.generate());

        assertTrue(result.content().isPresent());
    }

    /**
     * Identity keys are the triple the port declares, not a string joined on a separator the key may contain.
     *
     * <p>{@code ProviderId} and external ids may both contain a vertical bar. Joined on it, these two distinct
     * keys became the same string, the testkit handed both entities one identity, and the normalized content
     * refused it -- for a reader the production resolver accepts.</p>
     */
    @Test
    void givesTwoDistinctKeysThatJoinToTheSameStringTwoIdentities() {
        FakePlugin plugin = new FakePlugin(PROVIDER_ID, false);
        var snapshot = ProviderPluginContractAssertions.verify(plugin, WORKSPACE);
        plugin.publish((request, identities) -> content(
                request,
                SourceLocator.file(request.workspaceRoot().toString()),
                List.of(
                        evidence(identities.resolve(PROVIDER_ID, "evidence", "a|b")),
                        evidence(identities.resolve(PROVIDER_ID, "evidence|a", "b")))));

        var result = ProviderPluginContractAssertions.verifyRead(snapshot, WORKSPACE, ProjectSpecificationId.generate());

        List<Evidence> evidence = result.content().orElseThrow().evidence();
        assertEquals(2, evidence.size());
        assertNotEquals(evidence.get(0).id(), evidence.get(1).id());
    }

    /**
     * The testkit resolves identities exactly as the production resolver does, including its trimming.
     *
     * <p>A key that differs only by surrounding blanks is one key in production. A testkit that told the two
     * apart would accept a reader that production refuses as publishing a duplicate identity.</p>
     */
    @Test
    void resolvesAKeyThatDiffersOnlyBySurroundingBlanksToTheSameIdentity() {
        FakePlugin plugin = new FakePlugin(PROVIDER_ID, false);
        var snapshot = ProviderPluginContractAssertions.verify(plugin, WORKSPACE);
        List<DomainIdentity> resolved = new ArrayList<>();
        plugin.publish((request, identities) -> {
            resolved.add(identities.resolve(PROVIDER_ID, "evidence", "key"));
            resolved.add(identities.resolve(PROVIDER_ID, " evidence ", " key "));
            return Optional.empty();
        });

        ProviderPluginContractAssertions.verifyRead(snapshot, WORKSPACE, ProjectSpecificationId.generate());

        assertEquals(4, resolved.size());
        assertEquals(1, resolved.stream().distinct().count(), resolved::toString);
    }

    private static Optional<NormalizedProjectContent> content(
            ProviderReadRequest request, SourceLocator root, List<Evidence> evidence) {
        return Optional.of(new NormalizedProjectContent(
                new ProjectSpecification(request.projectId(), "fixture", root),
                List.of(),
                List.of(),
                List.of(),
                evidence,
                List.of()));
    }

    private static Evidence evidence(DomainIdentity identity) {
        return new Evidence(
                new EvidenceId(identity), SourceLocator.file("fixture.md"), Optional.empty(), Optional.empty());
    }

    private static final class FakePlugin implements MorpheusProviderPlugin {
        private final ProviderId providerId;
        private final boolean metadataMismatch;
        private int nonDeterministicAfterCall = Integer.MAX_VALUE;
        private ReadCategory droppedCategory;
        private BiFunction<ProviderReadRequest, EntityIdentityResolver, Optional<NormalizedProjectContent>> published =
                (request, identities) -> Optional.empty();
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

        void publish(BiFunction<ProviderReadRequest, EntityIdentityResolver, Optional<NormalizedProjectContent>> content) {
            this.published = content;
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
                return new ProviderReadResult(
                        providerId, published.apply(request, identityResolver), reports, List.of());
            }
        }
    }
}
