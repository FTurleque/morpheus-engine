package com.morpheus.application.analysis;

import com.morpheus.application.traceability.TraceabilityPath;
import com.morpheus.domain.requirement.RequirementId;
import com.morpheus.domain.traceability.TraceabilityEntityRef;

import java.util.Objects;

/** One explicit dependency/dependent discovered from persisted DEPENDS_ON links, with its shortest explanation path. */
public record ChangeDependencyImpact(
        RequirementId originRequirementId,
        DependencyImpactDirection direction,
        TraceabilityEntityRef impactedEntity,
        TraceabilityPath path) {

    public ChangeDependencyImpact {
        Objects.requireNonNull(originRequirementId, "originRequirementId");
        Objects.requireNonNull(direction, "direction");
        Objects.requireNonNull(impactedEntity, "impactedEntity");
        Objects.requireNonNull(path, "path");
        if (!path.target().equals(impactedEntity)) {
            throw new IllegalArgumentException("dependency impact target must equal path target");
        }
        if (path.steps().isEmpty()) {
            throw new IllegalArgumentException("dependency impact path must contain at least one persisted link");
        }
    }

    public int depth() {
        return path.steps().size();
    }
}
