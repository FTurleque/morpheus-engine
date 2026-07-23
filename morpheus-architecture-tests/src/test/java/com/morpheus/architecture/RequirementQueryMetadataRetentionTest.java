package com.morpheus.architecture;

import com.morpheus.application.query.PageRequest;
import com.morpheus.application.query.RequirementQueryService;
import com.morpheus.application.query.RequirementSearchQuery;
import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.application.store.RequirementVersionRecord;
import com.morpheus.application.store.SnapshotSpecificationVersionBinding;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provenance.Provenance;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.requirement.Requirement;
import com.morpheus.domain.requirement.RequirementId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.source.SourceLocator;
import com.morpheus.domain.specification.SpecificationId;
import com.morpheus.domain.temporal.TemporalState;
import com.morpheus.domain.version.EntityVersion;
import com.morpheus.domain.version.EntityVersionId;
import com.morpheus.domain.version.SpecificationVersion;
import com.morpheus.domain.version.SpecificationVersionId;
import com.morpheus.store.memory.MemorySpecificationKnowledgeStore;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RequirementQueryMetadataRetentionTest {

    @Test
    void requirementQueryServiceRetainsTheNormalizedQueryInItsResultPage() {
        Instant now = Instant.parse("2026-07-23T18:00:00Z");
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        KnowledgeSnapshotId snapshotId = KnowledgeSnapshotId.generate();
        SpecificationVersionId versionId = SpecificationVersionId.generate();
        RequirementId requirementId = RequirementId.generate();
        EvidenceId evidenceId = EvidenceId.generate();
        MemorySpecificationKnowledgeStore store = new MemorySpecificationKnowledgeStore();

        store.putProject(new ProjectStoreEntry(projectId, SourceLocator.file("workspace-query-metadata")));
        store.putSpecificationVersion(new SpecificationVersion(
                versionId,
                projectId,
                Optional.of(1L),
                Optional.of("provider-v1"),
                Optional.of("revision-1"),
                now,
                Optional.empty()));
        store.putSnapshot(new KnowledgeSnapshotMetadata(
                snapshotId,
                projectId,
                Optional.empty(),
                KnowledgeSnapshotState.READY,
                Optional.of("revision-1"),
                now));
        store.bindSnapshotVersion(new SnapshotSpecificationVersionBinding(snapshotId, versionId));
        Requirement requirement = new Requirement(
                requirementId,
                SpecificationId.generate(),
                Optional.of("REQ-RETENTION"),
                "Retention requirement",
                "Keep audit retention for seven years",
                new Provenance(
                        new ProviderId("metadata-test"),
                        Optional.of("1"),
                        SourceLocator.file("specs/retention.md"),
                        Optional.of("REQ-RETENTION"),
                        Optional.of("revision-1"),
                        evidenceId));
        store.putRequirementVersion(new RequirementVersionRecord(
                snapshotId,
                new EntityVersion<>(
                        EntityVersionId.generate(),
                        requirementId.value(),
                        versionId,
                        TemporalState.CURRENT,
                        requirement)));
        store.activateSnapshot(snapshotId, Optional.empty());

        RequirementSearchQuery query = new RequirementSearchQuery("  RETENTION   seven  ");
        var page = new RequirementQueryService(store, store)
                .findActive(projectId, query, PageRequest.first(10))
                .orElseThrow();

        assertEquals(query, page.query());
        assertEquals("retention   seven", page.query().text());
        assertEquals(1, page.totalMatches());
    }
}
