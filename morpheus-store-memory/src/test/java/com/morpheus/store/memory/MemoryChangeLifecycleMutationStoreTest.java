package com.morpheus.store.memory;

import com.morpheus.application.lifecycle.mutation.ChangeLifecycleMutationAttempt;
import com.morpheus.application.lifecycle.mutation.ChangeLifecycleMutationPersistenceResult;
import com.morpheus.application.lifecycle.mutation.ChangeLifecycleMutationPersistenceState;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleIdempotencyKey;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleMutationId;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleRevision;
import com.morpheus.domain.change.lifecycle.ChangeLifecycleState;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.source.SourceLocator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MemoryChangeLifecycleMutationStoreTest {
    private static final Instant T0 = Instant.parse("2026-08-31T12:00:00Z");
    private static final ProviderId PROVIDER = new ProviderId("store-memory-fixture");

    @Test
    void rejectsMutationForProjectThatWasNeverRegistered() {
        MemorySpecificationKnowledgeStore projects = new MemorySpecificationKnowledgeStore();
        MemoryChangeLifecycleMutationStore mutations = new MemoryChangeLifecycleMutationStore(projects);

        assertThrows(KnowledgeStoreException.class, () -> mutations.apply(attempt(
                ProjectSpecificationId.generate(), ChangeId.generate(), "unregistered")));
    }

    @Test
    void appliesMutationForRegisteredProject() {
        MemorySpecificationKnowledgeStore projects = new MemorySpecificationKnowledgeStore();
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        projects.putProject(new ProjectStoreEntry(projectId, SourceLocator.file("workspace")));
        MemoryChangeLifecycleMutationStore mutations = new MemoryChangeLifecycleMutationStore(projects);
        ChangeId changeId = ChangeId.generate();

        ChangeLifecycleMutationPersistenceResult result = mutations.apply(attempt(projectId, changeId, "registered"));

        assertEquals(ChangeLifecycleMutationPersistenceState.APPLIED, result.state());
        assertTrue(mutations.findState(projectId, changeId).isPresent());
        assertEquals(1, mutations.listAudit(projectId, changeId).size());
    }

    @Test
    void constructorRejectsNullProjectStore() {
        assertThrows(NullPointerException.class, () -> new MemoryChangeLifecycleMutationStore(null));
    }

    private ChangeLifecycleMutationAttempt attempt(ProjectSpecificationId projectId, ChangeId changeId, String key) {
        return new ChangeLifecycleMutationAttempt(
                ChangeLifecycleMutationId.generate(),
                new ChangeLifecycleIdempotencyKey(key),
                "fingerprint",
                projectId,
                changeId,
                ChangeLifecycleState.DRAFT,
                ChangeLifecycleState.VERIFYING,
                Optional.empty(),
                ChangeLifecycleRevision.initial(),
                "test-actor",
                PROVIDER,
                "test reason",
                T0);
    }
}
