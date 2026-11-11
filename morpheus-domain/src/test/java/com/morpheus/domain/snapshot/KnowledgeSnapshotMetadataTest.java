package com.morpheus.domain.snapshot;

import com.morpheus.domain.project.ProjectSpecificationId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KnowledgeSnapshotMetadataTest {
    private static final Instant CREATED_AT = Instant.parse("2026-10-30T08:00:00Z");

    private final KnowledgeSnapshotId id = KnowledgeSnapshotId.generate();
    private final ProjectSpecificationId projectId = ProjectSpecificationId.generate();
    private final Optional<KnowledgeSnapshotId> predecessor = Optional.of(KnowledgeSnapshotId.generate());

    @Test
    void metadataKeepsItsComponentsAndATrimmedRevision() {
        KnowledgeSnapshotMetadata metadata = metadata(KnowledgeSnapshotState.BUILDING, Optional.of(" abc123 "));

        assertEquals(id, metadata.id());
        assertEquals(projectId, metadata.projectId());
        assertEquals(predecessor, metadata.predecessorId());
        assertEquals(KnowledgeSnapshotState.BUILDING, metadata.state());
        assertEquals(Optional.of("abc123"), metadata.sourceRevision());
        assertEquals(CREATED_AT, metadata.createdAt());
    }

    @Test
    void aBlankRevisionIsReadAsNoRevision() {
        assertEquals(Optional.empty(), metadata(KnowledgeSnapshotState.READY, Optional.of("  ")).sourceRevision());
    }

    @Test
    void aNewStateKeepsTheDefinitionAndLeavesTheOriginalUnchanged() {
        KnowledgeSnapshotMetadata building = metadata(KnowledgeSnapshotState.BUILDING, Optional.of("r"));

        KnowledgeSnapshotMetadata active = building.withState(KnowledgeSnapshotState.ACTIVE);

        assertEquals(KnowledgeSnapshotState.ACTIVE, active.state());
        assertEquals(KnowledgeSnapshotState.BUILDING, building.state());
        assertTrue(active.sameDefinitionAs(building));
        assertTrue(building.sameDefinitionAs(active));
    }

    @Test
    void eachDefinitionComponentDistinguishesTwoSnapshots() {
        KnowledgeSnapshotMetadata base = metadata(KnowledgeSnapshotState.READY, Optional.of("r"));

        assertFalse(base.sameDefinitionAs(new KnowledgeSnapshotMetadata(KnowledgeSnapshotId.generate(), projectId,
                predecessor, KnowledgeSnapshotState.READY, Optional.of("r"), CREATED_AT)));
        assertFalse(base.sameDefinitionAs(new KnowledgeSnapshotMetadata(id, ProjectSpecificationId.generate(),
                predecessor, KnowledgeSnapshotState.READY, Optional.of("r"), CREATED_AT)));
        assertFalse(base.sameDefinitionAs(new KnowledgeSnapshotMetadata(id, projectId,
                Optional.empty(), KnowledgeSnapshotState.READY, Optional.of("r"), CREATED_AT)));
        assertFalse(base.sameDefinitionAs(new KnowledgeSnapshotMetadata(id, projectId,
                predecessor, KnowledgeSnapshotState.READY, Optional.of("other"), CREATED_AT)));
        assertFalse(base.sameDefinitionAs(new KnowledgeSnapshotMetadata(id, projectId,
                predecessor, KnowledgeSnapshotState.READY, Optional.of("r"), CREATED_AT.plusSeconds(1))));
    }

    @Test
    void aStateChangeOrAComparisonRefusesAnAbsentArgumentByName() {
        KnowledgeSnapshotMetadata metadata = metadata(KnowledgeSnapshotState.VALIDATING, Optional.empty());

        assertEquals("newState", assertThrows(NullPointerException.class, () -> metadata.withState(null))
                .getMessage());
        assertEquals("other", assertThrows(NullPointerException.class, () -> metadata.sameDefinitionAs(null))
                .getMessage());
    }

    @Test
    void anAbsentComponentIsRefusedByName() {
        assertEquals("id", assertThrows(NullPointerException.class, () -> new KnowledgeSnapshotMetadata(null,
                projectId, predecessor, KnowledgeSnapshotState.FAILED, Optional.empty(), CREATED_AT)).getMessage());
        assertEquals("projectId", assertThrows(NullPointerException.class, () -> new KnowledgeSnapshotMetadata(id,
                null, predecessor, KnowledgeSnapshotState.FAILED, Optional.empty(), CREATED_AT)).getMessage());
        assertEquals("predecessorId", assertThrows(NullPointerException.class, () -> new KnowledgeSnapshotMetadata(
                id, projectId, null, KnowledgeSnapshotState.FAILED, Optional.empty(), CREATED_AT)).getMessage());
        assertEquals("state", assertThrows(NullPointerException.class, () -> new KnowledgeSnapshotMetadata(id,
                projectId, predecessor, null, Optional.empty(), CREATED_AT)).getMessage());
        assertEquals("sourceRevision", assertThrows(NullPointerException.class, () -> new KnowledgeSnapshotMetadata(
                id, projectId, predecessor, KnowledgeSnapshotState.RETIRED, null, CREATED_AT)).getMessage());
        assertEquals("createdAt", assertThrows(NullPointerException.class, () -> new KnowledgeSnapshotMetadata(id,
                projectId, predecessor, KnowledgeSnapshotState.RETIRED, Optional.empty(), null)).getMessage());
    }

    private KnowledgeSnapshotMetadata metadata(KnowledgeSnapshotState state, Optional<String> revision) {
        return new KnowledgeSnapshotMetadata(id, projectId, predecessor, state, revision, CREATED_AT);
    }
}
