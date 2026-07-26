package com.morpheus.application.lifecycle.mutation;

import com.morpheus.domain.project.ProjectSpecificationId;

/** Resolves explicit provider WRITE_CHANGE capability for a registered project. */
@FunctionalInterface
public interface ChangeWriteCapabilityResolver {
    ChangeWriteCapabilityObservation resolve(ProjectSpecificationId projectId);
}
