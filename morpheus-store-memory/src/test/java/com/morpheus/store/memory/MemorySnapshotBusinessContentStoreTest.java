package com.morpheus.store.memory;

import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.application.store.SnapshotBusinessContent;
import com.morpheus.application.store.SnapshotSpecificationVersionBinding;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.ChangeProposal;
import com.morpheus.domain.evidence.Evidence;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provenance.Provenance;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.source.SourceLocator;
import com.morpheus.domain.specification.Specification;
import com.morpheus.domain.specification.SpecificationId;
import com.morpheus.domain.version.SpecificationVersion;
import com.morpheus.domain.version.SpecificationVersionId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MemorySnapshotBusinessContentStoreTest {
    private static final Instant T0 = Instant.parse("2026-10-30T09:00:00Z");
    private static final ProjectSpecificationId PROJECT = new ProjectSpecificationId(identity(1));
    private static final ProjectSpecificationId OTHER_PROJECT = new ProjectSpecificationId(identity(2));
    private static final KnowledgeSnapshotId SNAPSHOT = new KnowledgeSnapshotId(identity(3));
    private static final KnowledgeSnapshotId UNBOUND_SNAPSHOT = new KnowledgeSnapshotId(identity(4));
    private static final KnowledgeSnapshotId UNKNOWN_SNAPSHOT = new KnowledgeSnapshotId(identity(5));
    private static final SpecificationVersionId VERSION = new SpecificationVersionId(identity(6));
    private static final SpecificationVersionId OTHER_VERSION = new SpecificationVersionId(identity(7));
    private static final EvidenceId EVIDENCE = new EvidenceId(identity(8));

    @Test
    void contentPutForABoundSnapshotIsReadBackAndAnIdenticalRepeatIsAccepted() {
        MemorySnapshotBusinessContentStore store = store();
        SnapshotBusinessContent content = content(SNAPSHOT, VERSION, PROJECT, PROJECT, "Sessions");

        store.putSnapshotContent(content);
        store.putSnapshotContent(content(SNAPSHOT, VERSION, PROJECT, PROJECT, "Sessions"));

        assertEquals(Optional.of(content), store.findSnapshotContent(SNAPSHOT));
        assertEquals(Optional.empty(), store.findSnapshotContent(UNBOUND_SNAPSHOT));
    }

    @Test
    void aDifferentContentForTheSameSnapshotIsAConflictAndTheFirstOneStays() {
        MemorySnapshotBusinessContentStore store = store();
        SnapshotBusinessContent first = content(SNAPSHOT, VERSION, PROJECT, PROJECT, "Sessions");
        store.putSnapshotContent(first);

        KnowledgeStoreException collision = assertThrows(KnowledgeStoreException.class,
                () -> store.putSnapshotContent(content(SNAPSHOT, VERSION, PROJECT, PROJECT, "Renamed")));

        assertEquals("snapshot business content collision: " + SNAPSHOT, collision.getMessage());
        assertEquals(Optional.of(first), store.findSnapshotContent(SNAPSHOT));
    }

    @Test
    void contentForAnUnknownOrUnboundSnapshotOrAnotherVersionIsRefusedByName() {
        MemorySnapshotBusinessContentStore store = store();

        KnowledgeStoreException unknown = assertThrows(KnowledgeStoreException.class,
                () -> store.putSnapshotContent(content(UNKNOWN_SNAPSHOT, VERSION, PROJECT, PROJECT, "Sessions")));
        KnowledgeStoreException unbound = assertThrows(KnowledgeStoreException.class,
                () -> store.putSnapshotContent(content(UNBOUND_SNAPSHOT, VERSION, PROJECT, PROJECT, "Sessions")));
        KnowledgeStoreException otherVersion = assertThrows(KnowledgeStoreException.class,
                () -> store.putSnapshotContent(content(SNAPSHOT, OTHER_VERSION, PROJECT, PROJECT, "Sessions")));

        assertEquals("snapshot not found: " + UNKNOWN_SNAPSHOT, unknown.getMessage());
        assertEquals("snapshot has no specification version binding: " + UNBOUND_SNAPSHOT, unbound.getMessage());
        assertEquals("business content does not match snapshot specification version", otherVersion.getMessage());
        assertEquals(Optional.empty(), store.findSnapshotContent(SNAPSHOT));
    }

    @Test
    void aSpecificationOrAChangeOfAnotherProjectIsRefusedByName() {
        MemorySnapshotBusinessContentStore store = store();
        SnapshotBusinessContent foreignSpecification =
                content(SNAPSHOT, VERSION, OTHER_PROJECT, PROJECT, "Sessions");
        SnapshotBusinessContent foreignChange = content(SNAPSHOT, VERSION, PROJECT, OTHER_PROJECT, "Sessions");

        KnowledgeStoreException specification = assertThrows(KnowledgeStoreException.class,
                () -> store.putSnapshotContent(foreignSpecification));
        KnowledgeStoreException change = assertThrows(KnowledgeStoreException.class,
                () -> store.putSnapshotContent(foreignChange));

        assertEquals("specification belongs to another project: "
                + foreignSpecification.specifications().getFirst().id(), specification.getMessage());
        assertEquals("change belongs to another project: " + foreignChange.changes().getFirst().id(),
                change.getMessage());
        assertEquals(Optional.empty(), store.findSnapshotContent(SNAPSHOT));
    }

    private static MemorySnapshotBusinessContentStore store() {
        MemorySpecificationKnowledgeStore knowledge = new MemorySpecificationKnowledgeStore();
        knowledge.putProject(new ProjectStoreEntry(PROJECT, SourceLocator.file("workspace-business-content")));
        knowledge.putSpecificationVersion(new SpecificationVersion(VERSION, PROJECT, Optional.of(1L),
                Optional.of("provider-v1"), Optional.of("revision-1"), T0, Optional.empty()));
        for (KnowledgeSnapshotId snapshotId : List.of(SNAPSHOT, UNBOUND_SNAPSHOT)) {
            knowledge.putSnapshot(new KnowledgeSnapshotMetadata(snapshotId, PROJECT, Optional.empty(),
                    KnowledgeSnapshotState.READY, Optional.of("revision-1"), T0));
        }
        knowledge.bindSnapshotVersion(new SnapshotSpecificationVersionBinding(SNAPSHOT, VERSION));
        return new MemorySnapshotBusinessContentStore(knowledge, knowledge);
    }

    private static SnapshotBusinessContent content(
            KnowledgeSnapshotId snapshotId,
            SpecificationVersionId versionId,
            ProjectSpecificationId specificationProject,
            ProjectSpecificationId changeProject,
            String specificationTitle) {
        Specification specification = new Specification(new SpecificationId(identity(20)), specificationProject,
                "sessions", specificationTitle, Optional.empty(), provenance());
        ChangeProposal change = new ChangeProposal(new ChangeId(identity(21)), changeProject,
                Optional.of("CHG-1"), "Expire sessions", "Sessions expire after inactivity",
                List.of("sessions"), List.of(), List.of(), provenance());
        return new SnapshotBusinessContent(snapshotId, versionId, List.of(specification), List.of(),
                List.of(change), List.of(), List.of(), List.of(), List.of(),
                List.of(new Evidence(EVIDENCE, SourceLocator.file("specs/sessions.md"), Optional.empty(),
                        Optional.empty())));
    }

    private static Provenance provenance() {
        return new Provenance(new ProviderId("memory-business-content"), Optional.empty(),
                SourceLocator.file("specs/sessions.md"), Optional.empty(), Optional.empty(), EVIDENCE);
    }

    private static DomainIdentity identity(int number) {
        return DomainIdentity.parse("00000000-0000-7000-8000-%012d".formatted(number));
    }
}
