package com.morpheus.application.store;

import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.source.SourceLocator;

import java.util.Objects;

/** Minimal persisted project metadata for the M1 storage foundation. */
public record ProjectStoreEntry(ProjectSpecificationId id, SourceLocator rootLocator) {
    public ProjectStoreEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(rootLocator, "rootLocator");
    }
}
