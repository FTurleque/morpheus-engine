package com.morpheus.application.delta;

/** Raised when an explicit RequirementDelta application plan is inconsistent with the active baseline. */
public final class RequirementDeltaApplicationException extends RuntimeException {
    public RequirementDeltaApplicationException(String message) {
        super(message);
    }
}
