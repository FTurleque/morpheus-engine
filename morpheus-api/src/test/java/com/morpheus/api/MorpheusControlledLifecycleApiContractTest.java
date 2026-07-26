package com.morpheus.api;

import com.morpheus.application.context.DisabledTechnicalContextProvider;
import com.morpheus.application.identity.PersistentEntityIdentityResolver;
import com.morpheus.application.ingestion.ProjectSnapshotImportService;
import com.morpheus.application.read.ProviderReadRequest;
import com.morpheus.application.reference.ExternalIntegrationStatus;
import com.morpheus.application.reference.ExternalReferenceResolverRegistry;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.provider.synthetic.SyntheticSpecificationContentReader;
import com.morpheus.provider.synthetic.SyntheticSpecificationProvider;
import com.morpheus.store.sqlite.SqliteChangeLifecycleMutationStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MorpheusControlledLifecycleApiContractTest {
    @TempDir
    Path tempDirectory;

    private final ApiTestSupport http = new ApiTestSupport();

    @Test
    void explicitWriteEndpointAppliesAndRetriesIdempotentlyWhileDefaultServerDeniesWrites() {
        Path database = tempDirectory.resolve("m17-controlled-api.db");
        Seed seed = seed(database);
        String route = "/projects/" + seed.projectId() + "/changes/" + seed.changeId() + "/lifecycle-transitions";
        String body = "{\"idempotencyKey\":\"m17-http-1\",\"expectedRevision\":0,"
                + "\"targetState\":\"PROPOSED\",\"actor\":\"m17-api-test\",\"confirmed\":true}";

        var resolvers = new ExternalReferenceResolverRegistry(List.of());
        var minos = (com.morpheus.application.reference.ExternalIntegrationStatusProvider) () -> new ExternalIntegrationStatus(
                "MINOS", "DISABLED", false, "test", Map.of());
        var nexus = new DisabledTechnicalContextProvider("NEXUS", "test");

        try (MorpheusHttpServer server = MorpheusHttpServer.start(
                database,
                "127.0.0.1",
                0,
                resolvers,
                minos,
                nexus,
                projectId -> com.morpheus.application.lifecycle.mutation.ChangeWriteCapabilityObservation.allowed(
                        SyntheticSpecificationProvider.ID, "explicit test WRITE_CHANGE"))) {
            ApiTestSupport.Response applied = http.postJson(server, route, body);
            assertEquals(200, applied.status(), applied.body());
            assertTrue(applied.body().contains("\"state\":\"APPLIED\""), applied.body());
            assertTrue(applied.body().contains("\"revision\":1"), applied.body());
            assertTrue(applied.body().contains("\"providerId\":\"synthetic-json\""), applied.body());

            ApiTestSupport.Response retry = http.postJson(server, route, body);
            assertEquals(200, retry.status(), retry.body());
            assertTrue(retry.body().contains("\"state\":\"ALREADY_APPLIED\""), retry.body());
        }

        try (var mutations = new SqliteChangeLifecycleMutationStore(database)) {
            assertEquals(1, mutations.listAudit(
                    ProjectSpecificationId.parse(seed.projectId()), ChangeId.parse(seed.changeId())).size());
            assertEquals(1, mutations.findState(
                    ProjectSpecificationId.parse(seed.projectId()), ChangeId.parse(seed.changeId())).orElseThrow()
                    .revision().value());
        }

        Path deniedDatabase = tempDirectory.resolve("m17-denied-api.db");
        Seed deniedSeed = seed(deniedDatabase);
        String deniedRoute = "/projects/" + deniedSeed.projectId() + "/changes/" + deniedSeed.changeId()
                + "/lifecycle-transitions";
        try (MorpheusHttpServer server = MorpheusHttpServer.start(deniedDatabase, "127.0.0.1", 0)) {
            ApiTestSupport.Response denied = http.postJson(server, deniedRoute, body.replace("m17-http-1", "m17-http-denied"));
            assertEquals(200, denied.status(), denied.body());
            assertTrue(denied.body().contains("\"state\":\"NOT_AUTHORIZED\""), denied.body());
        }
        try (var mutations = new SqliteChangeLifecycleMutationStore(deniedDatabase)) {
            assertTrue(mutations.listAudit(
                    ProjectSpecificationId.parse(deniedSeed.projectId()), ChangeId.parse(deniedSeed.changeId())).isEmpty());
        }
    }

    private Seed seed(Path database) {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        Path fixture = http.fixture("synthetic-basic");
        try (ApiRuntime runtime = new ApiRuntime(database)) {
            var normalized = new SyntheticSpecificationContentReader()
                    .read(
                            ProviderReadRequest.all(fixture, projectId),
                            new PersistentEntityIdentityResolver(runtime.identities))
                    .content()
                    .orElseThrow();
            new ProjectSnapshotImportService(
                    runtime.snapshots,
                    runtime.requirements,
                    runtime.content,
                    runtime.traceability)
                    .publishFull(
                            normalized,
                            Optional.of("m17-api-test"),
                            Instant.parse("2026-07-26T14:30:00Z"));
            return new Seed(projectId.toString(), normalized.changes().getFirst().id().toString());
        }
    }

    private record Seed(String projectId, String changeId) {
    }
}
