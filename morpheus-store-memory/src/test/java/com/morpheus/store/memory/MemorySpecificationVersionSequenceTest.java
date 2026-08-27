package com.morpheus.store.memory;

import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.source.SourceLocator;
import com.morpheus.domain.version.SpecificationVersion;
import com.morpheus.domain.version.SpecificationVersionId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MemorySpecificationVersionSequenceTest {
    private static final Instant NOW = Instant.parse("2026-08-19T18:00:00Z");

    @Test
    void durableSequencesAdvanceAcrossStoredAttemptsAndRejectDuplicates() {
        MemorySpecificationKnowledgeStore store = new MemorySpecificationKnowledgeStore();
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        store.putProject(new ProjectStoreEntry(projectId, new SourceLocator("file", "workspace")));

        assertEquals(1L, store.nextSpecificationVersionSequence(projectId));

        SpecificationVersion first = version(projectId, 1L, Optional.empty());
        store.putSpecificationVersion(first);
        assertEquals(2L, store.nextSpecificationVersionSequence(projectId));

        KnowledgeStoreException duplicate = assertThrows(
                KnowledgeStoreException.class,
                () -> store.putSpecificationVersion(version(projectId, 1L, Optional.of(first.id()))));
        assertEquals(
                "specification version sequence collision for project " + projectId + ": 1",
                duplicate.getMessage());

        SpecificationVersion third = version(projectId, 3L, Optional.of(first.id()));
        store.putSpecificationVersion(third);
        assertEquals(4L, store.nextSpecificationVersionSequence(projectId));
    }

    @Test
    void sequenceAllocationRejectsUnknownProject() {
        MemorySpecificationKnowledgeStore store = new MemorySpecificationKnowledgeStore();
        ProjectSpecificationId unknown = ProjectSpecificationId.generate();

        assertThrows(KnowledgeStoreException.class, () -> store.nextSpecificationVersionSequence(unknown));
    }

    private SpecificationVersion version(
            ProjectSpecificationId projectId,
            long sequence,
            Optional<SpecificationVersionId> predecessor) {
        return new SpecificationVersion(
                SpecificationVersionId.generate(),
                projectId,
                Optional.of(sequence),
                Optional.empty(),
                Optional.of("rev-" + sequence),
                NOW.plusSeconds(sequence),
                predecessor);
    }
}
