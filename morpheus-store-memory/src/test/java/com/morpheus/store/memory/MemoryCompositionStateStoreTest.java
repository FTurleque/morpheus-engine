package com.morpheus.store.memory;

import com.morpheus.application.composition.CompositionProviderState;
import com.morpheus.application.composition.CompositionSnapshotState;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MemoryCompositionStateStoreTest {
    private static final KnowledgeSnapshotId FIRST = new KnowledgeSnapshotId(identity(1));
    private static final KnowledgeSnapshotId SECOND = new KnowledgeSnapshotId(identity(2));
    private static final ProviderId OPENSPEC = new ProviderId("openspec");
    private static final ProviderId MARKDOWN = new ProviderId("markdown");

    @Test
    void aSavedStateIsReadBackForItsSnapshotWithItsProvidersInPriorityOrder() {
        MemoryCompositionStateStore store = new MemoryCompositionStateStore();
        CompositionSnapshotState state = new CompositionSnapshotState(FIRST, OPENSPEC, List.of(
                new CompositionProviderState(MARKDOWN, 10, false, true, 2),
                new CompositionProviderState(OPENSPEC, 100, true, true, 0)), List.of());

        store.save(state);

        CompositionSnapshotState read = store.find(FIRST).orElseThrow();
        assertEquals(state, read);
        assertEquals(List.of(OPENSPEC, MARKDOWN),
                read.providers().stream().map(CompositionProviderState::providerId).toList());
        assertEquals(Optional.empty(), store.find(SECOND));
    }

    @Test
    void aSecondSaveForTheSameSnapshotReplacesItsStateAndLeavesOtherSnapshotsAlone() {
        MemoryCompositionStateStore store = new MemoryCompositionStateStore();
        CompositionSnapshotState other = singleProvider(SECOND, MARKDOWN);
        store.save(singleProvider(FIRST, OPENSPEC));
        store.save(other);

        CompositionSnapshotState replacement = singleProvider(FIRST, MARKDOWN);
        store.save(replacement);

        assertEquals(replacement, store.find(FIRST).orElseThrow());
        assertEquals(other, store.find(SECOND).orElseThrow());
    }

    @Test
    void anAbsentStateOrSnapshotIsRefusedByName() {
        MemoryCompositionStateStore store = new MemoryCompositionStateStore();

        NullPointerException missingState = assertThrows(NullPointerException.class, () -> store.save(null));
        NullPointerException missingSnapshot = assertThrows(NullPointerException.class, () -> store.find(null));

        assertEquals("state", missingState.getMessage());
        assertEquals("snapshotId", missingSnapshot.getMessage());
    }

    private static CompositionSnapshotState singleProvider(KnowledgeSnapshotId snapshotId, ProviderId providerId) {
        return new CompositionSnapshotState(snapshotId, providerId,
                List.of(new CompositionProviderState(providerId, 1, true, true, 0)), List.of());
    }

    private static DomainIdentity identity(int number) {
        return DomainIdentity.parse("00000000-0000-7000-8000-%012d".formatted(number));
    }
}
