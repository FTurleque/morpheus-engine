package com.morpheus.application.lifecycle.mutation;

/** Stable outcome taxonomy for one controlled lifecycle mutation request. */
public enum ChangeLifecycleMutationResultState {
    APPLIED,
    ALREADY_APPLIED,
    CONFLICT,
    NOT_AUTHORIZED,
    REQUIRES_CONFIRMATION,
    REJECTED;

    /**
     * Whether the requested lifecycle state holds after the call: the mutation was applied now
     * ({@link #APPLIED}) or by an earlier call carrying the same idempotency key ({@link #ALREADY_APPLIED}).
     * Every other state is a refusal that left the change where it was.
     *
     * <p>The service <em>returns</em> its refusals instead of throwing them, so an adapter that only maps
     * exceptions cannot see one. This is the one place the partition is written: the CLI turns it into an exit
     * code, MCP into {@code isError}, and {@link ChangeLifecycleMutationResult} into the rule that only a success
     * carries an audit record. The {@code switch} names every constant and has no {@code default}: a new state
     * does not compile until someone decides which side of the line it is on.</p>
     */
    public boolean successful() {
        return switch (this) {
            case APPLIED, ALREADY_APPLIED -> true;
            case CONFLICT, NOT_AUTHORIZED, REQUIRES_CONFIRMATION, REJECTED -> false;
        };
    }
}
