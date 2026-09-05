package com.morpheus.cli;

import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.reference.ExternalReference;
import com.morpheus.domain.reference.ExternalReferenceId;
import com.morpheus.domain.reference.ExternalReferenceTarget;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.source.SourceLocator;
import com.morpheus.store.sqlite.SqliteExternalReferenceStore;
import com.morpheus.store.sqlite.SqliteSpecificationKnowledgeStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;

class MorpheusM12McpStdioIntegrationTest {
    @TempDir
    Path tempDirectory;

    @Test
    void listsAndResolvesExternalReferencesOverRealStdioWithoutMinosInstalled() throws Exception {
        Path database = tempDirectory.resolve("m12-mcp.db");
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        KnowledgeSnapshotId snapshotId = KnowledgeSnapshotId.generate();
        DomainIdentity ownerId = DomainIdentity.generate();
        ExternalReference reference = seed(database, projectId, snapshotId, ownerId);

        try (McpStdioSession session = McpStdioSession.start(database)) {
            session.send("{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"initialize\",\"params\":{\"protocolVersion\":\"2025-11-25\",\"capabilities\":{},\"clientInfo\":{\"name\":\"m12-test\",\"version\":\"1\"}}}");
            session.readLine(Duration.ofSeconds(10));
            session.send("{\"jsonrpc\":\"2.0\",\"method\":\"notifications/initialized\",\"params\":{}}");
            session.send("{\"jsonrpc\":\"2.0\",\"id\":2,\"method\":\"tools/list\",\"params\":{}}");
            String tools = session.readLine(Duration.ofSeconds(10));
            assertTrue(tools.contains("list_external_references"), tools);
            assertTrue(tools.contains("resolve_external_reference"), tools);

            session.send("{\"jsonrpc\":\"2.0\",\"id\":3,\"method\":\"tools/call\",\"params\":{\"name\":\"list_external_references\",\"arguments\":{\"projectId\":\"" + projectId + "\",\"ownerId\":\"" + ownerId + "\"}}}");
            String list = session.readLine(Duration.ofSeconds(10));
            assertTrue(list.contains(reference.id().toString()), list);

            session.send("{\"jsonrpc\":\"2.0\",\"id\":4,\"method\":\"tools/call\",\"params\":{\"name\":\"resolve_external_reference\",\"arguments\":{\"projectId\":\"" + projectId + "\",\"referenceId\":\"" + reference.id() + "\"}}}");
            String resolution = session.readLine(Duration.ofSeconds(10));
            assertTrue(resolution.contains("NO_RESOLVER"), resolution);
            assertTrue(resolution.contains("persisted"), resolution);
        }
    }

    private ExternalReference seed(
            Path database,
            ProjectSpecificationId projectId,
            KnowledgeSnapshotId snapshotId,
            DomainIdentity ownerId) {
        ExternalReference reference = ExternalReference.unvalidated(
                ExternalReferenceId.generate(), ownerId,
                new ExternalReferenceTarget("MINOS", Optional.of("morpheus-engine"), "SYMBOL",
                        "symbol:RequirementService", Optional.empty()), Optional.empty());
        try (var snapshots = new SqliteSpecificationKnowledgeStore(database);
             var references = new SqliteExternalReferenceStore(database)) {
            snapshots.putProject(new ProjectStoreEntry(projectId, SourceLocator.file("workspace-" + projectId)));
            snapshots.putSnapshot(new KnowledgeSnapshotMetadata(
                    snapshotId, projectId, Optional.empty(), KnowledgeSnapshotState.READY,
                    Optional.of("rev"), Instant.parse("2026-07-24T12:00:00Z")));
            snapshots.activateSnapshot(snapshotId, Optional.empty());
            references.putReference(snapshotId, reference);
        }
        return reference;
    }
}
