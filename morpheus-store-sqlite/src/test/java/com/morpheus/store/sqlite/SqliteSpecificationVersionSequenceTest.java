package com.morpheus.store.sqlite;

import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.source.SourceLocator;
import com.morpheus.domain.version.SpecificationVersion;
import com.morpheus.domain.version.SpecificationVersionId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SqliteSpecificationVersionSequenceTest {
    private static final Instant NOW = Instant.parse("2026-08-19T18:00:00Z");

    @TempDir
    Path tempDir;

    @Test
    void durableSequenceAllocationUsesMaximumStoredAttempt() {
        Path database = tempDir.resolve("sequence.db");
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        try (var projects = new SqliteSpecificationKnowledgeStore(database)) {
            projects.putProject(new ProjectStoreEntry(projectId, new SourceLocator("file", "workspace")));
        }

        try (var versions = new SqliteVersionedRequirementStore(database)) {
            assertEquals(1L, versions.nextSpecificationVersionSequence(projectId));

            SpecificationVersion first = version(projectId, 1L, Optional.empty());
            versions.putSpecificationVersion(first);
            assertEquals(2L, versions.nextSpecificationVersionSequence(projectId));

            SpecificationVersion third = version(projectId, 3L, Optional.of(first.id()));
            versions.putSpecificationVersion(third);
            assertEquals(4L, versions.nextSpecificationVersionSequence(projectId));

            assertThrows(
                    KnowledgeStoreException.class,
                    () -> versions.putSpecificationVersion(version(projectId, 3L, Optional.of(first.id()))));
        }
    }

    @Test
    void sequenceAllocationRejectsUnknownProject() {
        Path database = tempDir.resolve("unknown-project.db");
        try (var versions = new SqliteVersionedRequirementStore(database)) {
            assertThrows(
                    KnowledgeStoreException.class,
                    () -> versions.nextSpecificationVersionSequence(ProjectSpecificationId.generate()));
        }
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
