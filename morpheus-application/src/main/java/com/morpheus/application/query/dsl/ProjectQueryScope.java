package com.morpheus.application.query.dsl;

import com.morpheus.domain.project.ProjectSpecificationId;

import java.util.Objects;

/** Query scope bound to one MORPHEUS project identity. */
public record ProjectQueryScope(ProjectSpecificationId projectId) implements QueryScope {
    public ProjectQueryScope {
        Objects.requireNonNull(projectId, "projectId");
    }
}
