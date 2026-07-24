package com.morpheus.api;

import com.morpheus.application.reference.ExternalIntegrationStatus;
import com.morpheus.application.reference.ExternalReferenceResolver;
import com.morpheus.application.reference.ExternalReferenceResolverRegistry;
import com.morpheus.application.reference.ExternalReferenceResolverResult;
import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.reference.ExternalReference;
import com.morpheus.domain.reference.ExternalReferenceId;
import com.morpheus.domain.reference.ExternalReferenceResolutionState;
import com.morpheus.domain.reference.ExternalReferenceTarget;
import com.morpheus.domain.reference.ResolvedExternalTarget;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.source.SourceLocator;
import com.morpheus.store.sqlite.SqliteExternalReferenceStore;
import com.morpheus.store.sqlite.SqliteSpecificationKnowledgeStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MorpheusExternalReferenceApiContractTest {
    @TempDir
    Path tempDirectory;

    private final ApiTestSupport http = new ApiTestSupport();

    @Test
    void exposesMinosStatusListsAndResolvesLiveWithoutPersistingObservation() {
        Path database = tempDirectory.resolve("m12-api.db");
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        KnowledgeSnapshotId snapshotId = KnowledgeSnapshotId.generate();
        DomainIdentity ownerId = DomainIdentity.generate();
        ExternalReference reference = reference(ownerId);
        seed(database, projectId, snapshotId, reference);

        ExternalReferenceResolver resolver = new ExternalReferenceResolver() {
            @Override
            public String system() {
                return "MINOS";
            }

            @Override
            public ExternalReferenceResolverResult resolve(ExternalReferenceTarget target) {
                return ExternalReferenceResolverResult.found(new ResolvedExternalTarget(
                        target,
                        Map.of("minos.symbolKey", target.externalId(), "minos.activeSnapshotId", "snapshot-42")));
            }
        };

        try (MorpheusHttpServer server = MorpheusHttpServer.start(
                database,
                "127.0.0.1",
                0,
                new ExternalReferenceResolverRegistry(List.of(resolver)),
                () -> new ExternalIntegrationStatus(
                        "MINOS", "AVAILABLE", true, "fixture available", Map.of("timeoutSeconds", "20")))) {
            ApiTestSupport.Response status = http.get(server, "/integrations/minos/status");
            assertEquals(200, status.status(), status.body());
            assertTrue(status.body().contains("\"state\":\"AVAILABLE\""), status.body());

            ApiTestSupport.Response list = http.get(
                    server, "/projects/" + projectId + "/external-references?ownerId=" + ownerId);
            assertEquals(200, list.status(), list.body());
            assertTrue(list.body().contains(reference.id().toString()), list.body());

            ApiTestSupport.Response resolution = http.get(
                    server, "/projects/" + projectId + "/external-references/" + reference.id() + "/resolution");
            assertEquals(200, resolution.status(), resolution.body());
            assertTrue(resolution.body().contains("\"persisted\":false"), resolution.body());
            assertTrue(resolution.body().contains("\"resolutionState\":\"UNVALIDATED\""), resolution.body());
            assertTrue(resolution.body().contains("\"resolutionState\":\"RESOLVED\""), resolution.body());
            assertTrue(resolution.body().contains("minos.symbolKey"), resolution.body());
        }

        try (var references = new SqliteExternalReferenceStore(database)) {
            assertEquals(reference, references.findReference(snapshotId, reference.id()).orElseThrow());
            assertEquals(ExternalReferenceResolutionState.UNVALIDATED,
                    references.findReference(snapshotId, reference.id()).orElseThrow().resolutionState());
        }
    }

    private void seed(
            Path database,
            ProjectSpecificationId projectId,
            KnowledgeSnapshotId snapshotId,
            ExternalReference reference) {
        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var references = new SqliteExternalReferenceStore(database)) {
            snapshots.putProject(new ProjectStoreEntry(projectId, SourceLocator.file("workspace-" + projectId)));
            snapshots.putSnapshot(new KnowledgeSnapshotMetadata(
                    snapshotId,
                    projectId,
                    Optional.empty(),
                    KnowledgeSnapshotState.READY,
                    Optional.of("rev"),
                    Instant.parse("2026-07-24T12:00:00Z")));
            snapshots.activateSnapshot(snapshotId, Optional.empty());
            references.putReference(snapshotId, reference);
        }
    }

    private ExternalReference reference(DomainIdentity ownerId) {
        return ExternalReference.unvalidated(
                ExternalReferenceId.generate(),
                ownerId,
                new ExternalReferenceTarget(
                        "MINOS",
                        Optional.of("morpheus-engine"),
                        "SYMBOL",
                        "symbol:RequirementService",
                        Optional.of("snapshot-42")),
                Optional.empty());
    }
}
