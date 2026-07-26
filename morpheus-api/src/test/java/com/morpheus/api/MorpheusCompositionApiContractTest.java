package com.morpheus.api;

import com.morpheus.application.composition.CompositionCandidate;
import com.morpheus.application.composition.CompositionConflict;
import com.morpheus.application.composition.CompositionEntityType;
import com.morpheus.application.composition.CompositionProviderState;
import com.morpheus.application.composition.CompositionResolution;
import com.morpheus.application.composition.CompositionSnapshotState;
import com.morpheus.application.identity.PersistentEntityIdentityResolver;
import com.morpheus.application.ingestion.ProjectSnapshotImportService;
import com.morpheus.application.read.ProviderReadRequest;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.provider.synthetic.SyntheticSpecificationContentReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MorpheusCompositionApiContractTest {
    @TempDir
    Path tempDirectory;

    private final ApiTestSupport http = new ApiTestSupport();

    @Test
    void exposesPersistedCompositionStatusAndConflicts() {
        Path database = tempDirectory.resolve("m18-composition-api.db");
        String projectId = seed(database);

        try (MorpheusHttpServer server = MorpheusHttpServer.start(database, "127.0.0.1", 0)) {
            ApiTestSupport.Response status = http.get(server, "/projects/" + projectId + "/composition");
            assertEquals(200, status.status(), status.body());
            assertTrue(status.body().contains("\"primaryProviderId\":\"openspec\""), status.body());
            assertTrue(status.body().contains("\"providerId\":\"structured-markdown\""), status.body());
            assertTrue(status.body().contains("\"logicalKey\":\"auth-session/session-expiration\""), status.body());

            ApiTestSupport.Response conflicts = http.get(
                    server,
                    "/projects/" + projectId + "/composition/conflicts?offset=0&limit=1");
            assertEquals(200, conflicts.status(), conflicts.body());
            assertTrue(conflicts.body().contains("\"totalMatches\":1"), conflicts.body());
            assertTrue(conflicts.body().contains("\"selectedProviderId\":\"openspec\""), conflicts.body());
            assertTrue(conflicts.body().contains("\"evidenceId\":\"evidence-openspec\""), conflicts.body());
            assertTrue(conflicts.body().contains("\"evidenceId\":\"evidence-markdown\""), conflicts.body());
        }
    }

    private String seed(Path database) {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        Path fixture = http.fixture("synthetic-basic");
        try (ApiRuntime runtime = new ApiRuntime(database)) {
            var normalized = new SyntheticSpecificationContentReader()
                    .read(
                            ProviderReadRequest.all(fixture, projectId),
                            new PersistentEntityIdentityResolver(runtime.identities))
                    .content()
                    .orElseThrow();
            var imported = new ProjectSnapshotImportService(
                    runtime.snapshots,
                    runtime.requirements,
                    runtime.content,
                    runtime.traceability)
                    .publishFull(
                            normalized,
                            Optional.of("m18-api-test"),
                            Instant.parse("2026-07-26T17:30:00Z"));

            ProviderId openspec = new ProviderId("openspec");
            ProviderId markdown = new ProviderId("structured-markdown");
            CompositionConflict conflict = new CompositionConflict(
                    CompositionEntityType.REQUIREMENT,
                    "auth-session/session-expiration",
                    "statement",
                    List.of(
                            new CompositionCandidate(openspec, 100, "30 minutes", "file:openspec/spec.md", "evidence-openspec"),
                            new CompositionCandidate(markdown, 50, "45 minutes", "file:morpheus/specification.md", "evidence-markdown")),
                    CompositionResolution.SELECTED_BY_PRECEDENCE,
                    Optional.of(openspec),
                    "OpenSpec has higher configured precedence");
            runtime.compositions.put(new CompositionSnapshotState(
                    imported.snapshot().id(),
                    openspec,
                    List.of(
                            new CompositionProviderState(openspec, 100, true, true, 0),
                            new CompositionProviderState(markdown, 50, false, true, 0)),
                    List.of(conflict)));
            return projectId.toString();
        }
    }
}
