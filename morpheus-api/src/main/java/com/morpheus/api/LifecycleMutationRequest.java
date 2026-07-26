package com.morpheus.api;

/** JSON request for the explicit M17 lifecycle mutation endpoint. */
public record LifecycleMutationRequest(
        String mutationId,
        String idempotencyKey,
        Long expectedRevision,
        String targetState,
        String abandonmentReason,
        String actor,
        Boolean confirmed) {
}
