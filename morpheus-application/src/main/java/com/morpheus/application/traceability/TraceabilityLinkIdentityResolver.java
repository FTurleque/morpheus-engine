package com.morpheus.application.traceability;

import com.morpheus.domain.traceability.TraceabilityLinkId;

import java.util.Optional;

/** Explicit identity source for derived traceability observations; derivation never allocates hidden IDs. */
@FunctionalInterface
public interface TraceabilityLinkIdentityResolver {
    Optional<TraceabilityLinkId> resolve(TraceabilityDerivationKey key);
}
