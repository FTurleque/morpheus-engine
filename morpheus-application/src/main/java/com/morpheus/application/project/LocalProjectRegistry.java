package com.morpheus.application.project;

import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.source.SourceLocator;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/** Local registry of project roots backed by the technology-neutral knowledge-store port. */
public final class LocalProjectRegistry {
    private final SpecificationKnowledgeStore store;

    public LocalProjectRegistry(SpecificationKnowledgeStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    /**
     * Registers the normalized local root once and returns the stable MORPHEUS project identity.
     * Re-registering the same lexical root is idempotent, including when another registration wins
     * the insertion race between the initial lookup and the store write.
     */
    public ProjectStoreEntry register(Path workspaceRoot) {
        SourceLocator rootLocator = rootLocator(workspaceRoot);
        Optional<ProjectStoreEntry> existing = store.findProjectByRoot(rootLocator);
        if (existing.isPresent()) {
            return existing.orElseThrow();
        }

        ProjectStoreEntry created = new ProjectStoreEntry(ProjectSpecificationId.generate(), rootLocator);
        try {
            store.putProject(created);
            return created;
        } catch (KnowledgeStoreException exception) {
            return store.findProjectByRoot(rootLocator).orElseThrow(() -> exception);
        }
    }

    public Optional<ProjectStoreEntry> find(Path workspaceRoot) {
        return store.findProjectByRoot(rootLocator(workspaceRoot));
    }

    public List<ProjectStoreEntry> list() {
        return store.listProjects();
    }

    private SourceLocator rootLocator(Path workspaceRoot) {
        Path normalized = Objects.requireNonNull(workspaceRoot, "workspaceRoot")
                .toAbsolutePath()
                .normalize();
        return SourceLocator.file(normalized.toString());
    }
}
