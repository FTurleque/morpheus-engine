package com.morpheus.application.lifecycle.mutation;

import com.morpheus.domain.provider.ProviderId;

import java.util.Objects;
import java.util.Optional;

/** Provider-neutral observation proving whether WRITE_CHANGE is explicitly available. */
public record ChangeWriteCapabilityObservation(
        boolean writeAllowed,
        Optional<ProviderId> providerId,
        String reason) {

    public ChangeWriteCapabilityObservation {
        providerId = Objects.requireNonNull(providerId, "providerId");
        Objects.requireNonNull(reason, "reason");
        reason = reason.trim();
        if (reason.isEmpty()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        if (writeAllowed && providerId.isEmpty()) {
            throw new IllegalArgumentException("writeAllowed requires an explicit providerId");
        }
    }

    public static ChangeWriteCapabilityObservation denied(String reason) {
        return new ChangeWriteCapabilityObservation(false, Optional.empty(), reason);
    }

    public static ChangeWriteCapabilityObservation allowed(ProviderId providerId, String reason) {
        return new ChangeWriteCapabilityObservation(true, Optional.of(providerId), reason);
    }
}
