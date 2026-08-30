package com.morpheus.store.memory;

import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.source.SourceLocator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MemorySpecificationVersionSequenceReservationTest {

    @Test
    void repeatedAllocationsReserveDistinctSequencesBeforeAnyVersionIsStored() {
        MemorySpecificationKnowledgeStore store = new MemorySpecificationKnowledgeStore();
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        store.putProject(new ProjectStoreEntry(projectId, new SourceLocator("file", "workspace")));

        assertEquals(1L, store.nextSpecificationVersionSequence(projectId));
        assertEquals(2L, store.nextSpecificationVersionSequence(projectId));
        assertEquals(3L, store.nextSpecificationVersionSequence(projectId));
    }
}
