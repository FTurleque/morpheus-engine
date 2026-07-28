package com.morpheus.domain.portfolio;

import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.project.ProjectSpecificationId;

import java.util.Objects;

/** An entity identity explicitly qualified by its MORPHEUS project identity. */
public record PortfolioEntityRef(
        ProjectSpecificationId projectId,
        String entityType,
        DomainIdentity entityId) implements Comparable<PortfolioEntityRef> {

    public PortfolioEntityRef {
        Objects.requireNonNull(projectId, "projectId");
        entityType = requireText(entityType, "entityType", 128);
        Objects.requireNonNull(entityId, "entityId");
    }

    @Override
    public int compareTo(PortfolioEntityRef other) {
        int project = projectId.toString().compareTo(other.projectId.toString());
        if (project != 0) {
            return project;
        }
        int type = entityType.compareTo(other.entityType);
        return type != 0 ? type : entityId.compareTo(other.entityId);
    }

    private static String requireText(String value, String name, int maximumLength) {
        Objects.requireNonNull(value, name);
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.length() > maximumLength) {
            throw new IllegalArgumentException(name + " must contain 1.." + maximumLength + " characters");
        }
        return trimmed;
    }
}
