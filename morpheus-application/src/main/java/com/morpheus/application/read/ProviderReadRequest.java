package com.morpheus.application.read;

import com.morpheus.domain.project.ProjectSpecificationId;

import java.nio.file.Path;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** Provider-neutral request for normalized specification content. */
public record ProviderReadRequest(
        Path workspaceRoot,
        ProjectSpecificationId projectId,
        Set<ReadCategory> requestedCategories) {

    public ProviderReadRequest {
        workspaceRoot = Objects.requireNonNull(workspaceRoot, "workspaceRoot").toAbsolutePath().normalize();
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(requestedCategories, "requestedCategories");
        if (requestedCategories.isEmpty()) {
            throw new IllegalArgumentException("requestedCategories must not be empty");
        }
        requestedCategories = Set.copyOf(requestedCategories);
    }

    public static ProviderReadRequest all(Path workspaceRoot, ProjectSpecificationId projectId) {
        return new ProviderReadRequest(workspaceRoot, projectId, EnumSet.allOf(ReadCategory.class));
    }
}
