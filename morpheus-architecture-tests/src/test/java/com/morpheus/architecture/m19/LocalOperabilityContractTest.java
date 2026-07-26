package com.morpheus.architecture.m19;

import com.morpheus.application.operability.LocalOperabilityService;
import com.morpheus.application.operability.OperationalEventSink;
import com.morpheus.application.operability.OperationalMetrics;
import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.source.SourceLocator;
import com.morpheus.store.sqlite.SqliteSpecificationKnowledgeStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalOperabilityContractTest {

    @TempDir
    Path tempDir;

    @Test
    void livenessAndReadinessAreDistinctAndMetricsRemainProcessLocal() {
        Path database = tempDir.resolve("operability.db");
        OperationalMetrics metrics = new OperationalMetrics();
        metrics.increment("sync.success");

        SqliteSpecificationKnowledgeStore store = new SqliteSpecificationKnowledgeStore(database);
        store.putProject(new ProjectStoreEntry(
                ProjectSpecificationId.generate(),
                SourceLocator.file("m19/operability")));
        LocalOperabilityService service = new LocalOperabilityService(
                store,
                metrics,
                OperationalEventSink.noop());

        assertEquals("UP", service.health().status());
        assertEquals("READY", service.readiness().status());
        assertTrue(service.readiness().diagnosticCode().isEmpty());
        assertEquals(1L, service.snapshot().metrics().counters().get("sync.success"));

        store.close();

        assertEquals("UP", service.health().status(),
                "liveness must not claim the database is ready");
        assertEquals("NOT_READY", service.readiness().status());
        assertEquals("DATABASE_NOT_READY", service.readiness().diagnosticCode().orElseThrow());
    }
}
