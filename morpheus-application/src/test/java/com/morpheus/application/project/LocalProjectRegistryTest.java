package com.morpheus.application.project;

import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.source.SourceLocator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalProjectRegistryTest {

    @TempDir
    Path tempDir;

    @Test
    void registeringTheSameNormalizedRootIsIdempotent() {
        var store = new ProjectOnlyStore();
        var registry = new LocalProjectRegistry(store);
        Path root = tempDir.resolve("project");

        ProjectStoreEntry first = registry.register(root);
        ProjectStoreEntry replay = registry.register(root.resolve("child").resolve(".."));

        assertEquals(first, replay);
        assertEquals(SourceLocator.file(root.toAbsolutePath().normalize().toString()), first.rootLocator());
        assertEquals(1, registry.list().size());
        assertEquals(first, registry.find(root).orElseThrow());
    }

    @Test
    void distinctRootsReceiveDistinctMorpheusIdentitiesAndListDeterministically() {
        var store = new ProjectOnlyStore();
        var registry = new LocalProjectRegistry(store);

        ProjectStoreEntry first = registry.register(tempDir.resolve("alpha"));
        ProjectStoreEntry second = registry.register(tempDir.resolve("beta"));

        assertTrue(!first.id().equals(second.id()));
        assertEquals(
                registry.list().stream().sorted(Comparator.comparing(ProjectStoreEntry::id)).toList(),
                registry.list());
    }

    private static final class ProjectOnlyStore implements SpecificationKnowledgeStore {
        private final List<ProjectStoreEntry> projects = new ArrayList<>();

        @Override
        public void putProject(ProjectStoreEntry project) {
            findProject(project.id()).ifPresentOrElse(
                    existing -> {
                        if (!existing.equals(project)) {
                            throw new IllegalStateException("identity collision");
                        }
                    },
                    () -> projects.add(project));
        }

        @Override
        public Optional<ProjectStoreEntry> findProject(ProjectSpecificationId projectId) {
            return projects.stream().filter(project -> project.id().equals(projectId)).findFirst();
        }

        @Override
        public Optional<ProjectStoreEntry> findProjectByRoot(SourceLocator rootLocator) {
            return projects.stream().filter(project -> project.rootLocator().equals(rootLocator)).findFirst();
        }

        @Override
        public List<ProjectStoreEntry> listProjects() {
            return projects.stream().sorted(Comparator.comparing(ProjectStoreEntry::id)).toList();
        }

        @Override
        public void putSnapshot(KnowledgeSnapshotMetadata snapshot) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<KnowledgeSnapshotMetadata> findSnapshot(KnowledgeSnapshotId snapshotId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<KnowledgeSnapshotMetadata> activeSnapshot(ProjectSpecificationId projectId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public KnowledgeSnapshotMetadata activateSnapshot(
                KnowledgeSnapshotId snapshotId,
                Optional<KnowledgeSnapshotId> expectedActiveSnapshotId) {
            throw new UnsupportedOperationException();
        }
    }
}
