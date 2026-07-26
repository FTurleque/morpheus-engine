package com.morpheus.architecture;

import com.morpheus.application.composition.CompositionResolution;
import com.morpheus.application.composition.CompositionSnapshotState;
import com.morpheus.application.composition.MultiProviderCompositionService;
import com.morpheus.application.composition.MultiProviderReadService;
import com.morpheus.application.composition.ProviderCompositionSource;
import com.morpheus.application.identity.EntityIdentityResolver;
import com.morpheus.application.read.ProviderReadRequest;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.provider.markdown.StructuredMarkdownSpecificationContentReader;
import com.morpheus.provider.markdown.StructuredMarkdownSpecificationProvider;
import com.morpheus.provider.openspec.OpenSpecSpecificationContentReader;
import com.morpheus.provider.openspec.OpenSpecSpecificationProvider;
import com.morpheus.store.memory.MemoryCompositionStateStore;
import com.morpheus.store.sqlite.SqliteCompositionStateStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MultiProviderCompositionContractTest {

    @TempDir
    Path temporaryDirectory;

    @Test
    void twoRealProvidersComposeDeterministicallyAndPersistAcrossSqliteReopen() {
        Path workspace = fixture("openspec-basic");
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        ProviderReadRequest request = ProviderReadRequest.all(workspace, projectId);
        EntityIdentityResolver identities = new StableIdentityResolver();
        MultiProviderReadService service = new MultiProviderReadService(
                List.of(
                        new OpenSpecSpecificationContentReader(),
                        new StructuredMarkdownSpecificationContentReader()),
                new MultiProviderCompositionService());

        var result = service.read(
                request,
                identities,
                List.of(
                        new ProviderCompositionSource(OpenSpecSpecificationProvider.ID, 100, true),
                        new ProviderCompositionSource(StructuredMarkdownSpecificationProvider.ID, 50, true)));

        assertEquals(OpenSpecSpecificationProvider.ID, result.primaryProviderId());
        assertEquals(2, result.contributions().size());
        assertTrue(result.contributions().stream().allMatch(item -> item.available()));
        assertTrue(result.content().requirements().size() >= 4);
        assertTrue(result.conflicts().stream().anyMatch(conflict ->
                conflict.logicalKey().equals("auth-session/session-expiration")
                        && conflict.field().equals("statement")
                        && conflict.resolution() == CompositionResolution.SELECTED_BY_PRECEDENCE
                        && conflict.selectedProviderId().orElseThrow().equals(OpenSpecSpecificationProvider.ID)));
        assertFalse(result.conflicts().stream().anyMatch(conflict -> conflict.candidates().size() < 2));

        KnowledgeSnapshotId snapshotId = KnowledgeSnapshotId.generate();
        CompositionSnapshotState expected = CompositionSnapshotState.from(snapshotId, result);

        var memory = new MemoryCompositionStateStore();
        memory.save(expected);
        assertEquals(expected, memory.find(snapshotId).orElseThrow());

        Path database = temporaryDirectory.resolve("composition.db");
        try (var sqlite = new SqliteCompositionStateStore(database)) {
            sqlite.save(expected);
            assertEquals(expected, sqlite.find(snapshotId).orElseThrow());
        }
        try (var reopened = new SqliteCompositionStateStore(database)) {
            assertEquals(expected, reopened.find(snapshotId).orElseThrow());
        }
    }

    @Test
    void unavailableOptionalProviderDoesNotFailAValidRequiredContribution() {
        Path workspace = fixture("openspec-basic");
        MultiProviderReadService service = new MultiProviderReadService(
                List.of(new OpenSpecSpecificationContentReader()),
                new MultiProviderCompositionService());

        var result = service.read(
                ProviderReadRequest.all(workspace, ProjectSpecificationId.generate()),
                new StableIdentityResolver(),
                List.of(
                        new ProviderCompositionSource(OpenSpecSpecificationProvider.ID, 100, true),
                        new ProviderCompositionSource(new ProviderId("optional-missing"), 10, false)));

        assertEquals(2, result.contributions().size());
        assertTrue(result.contributions().stream()
                .filter(item -> item.providerId().value().equals("optional-missing"))
                .noneMatch(item -> item.available()));
        assertFalse(result.content().requirements().isEmpty());
    }

    private Path fixture(String name) {
        Path current = Path.of("").toAbsolutePath().normalize();
        Path fromRoot = current.resolve("experiments/m0/fixtures").resolve(name);
        if (Files.isDirectory(fromRoot)) {
            return fromRoot;
        }
        Path fromModule = current.resolve("../experiments/m0/fixtures").normalize().resolve(name);
        if (Files.isDirectory(fromModule)) {
            return fromModule;
        }
        throw new IllegalStateException("fixture not found: " + name + " from " + current);
    }

    private static final class StableIdentityResolver implements EntityIdentityResolver {
        private final Map<String, DomainIdentity> identities = new HashMap<>();

        @Override
        public DomainIdentity resolve(ProviderId providerId, String entityType, String externalId) {
            return identities.computeIfAbsent(
                    providerId.value() + "|" + entityType + "|" + externalId,
                    ignored -> DomainIdentity.generate());
        }
    }
}
