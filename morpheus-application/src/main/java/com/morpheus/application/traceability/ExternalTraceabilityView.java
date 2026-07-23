package com.morpheus.application.traceability;

import com.morpheus.domain.reference.ExternalReference;
import com.morpheus.domain.traceability.TraceabilityEntityKind;
import com.morpheus.domain.traceability.TraceabilityLink;

import java.util.Objects;
import java.util.Optional;

/** Explainable view joining a canonical traceability link with its optional external reference. */
public record ExternalTraceabilityView(
        TraceabilityLink link,
        Optional<ExternalReference> reference,
        ExternalTraceabilityAvailability availability) {

    public ExternalTraceabilityView {
        Objects.requireNonNull(link, "link");
        reference = Objects.requireNonNull(reference, "reference");
        Objects.requireNonNull(availability, "availability");
        if (link.target().kind() != TraceabilityEntityKind.EXTERNAL_REFERENCE) {
            throw new IllegalArgumentException("external traceability view requires an EXTERNAL_REFERENCE target");
        }
        reference.ifPresent(value -> {
            if (!value.id().value().equals(link.target().identity())) {
                throw new IllegalArgumentException("external reference identity must match link target");
            }
        });
        if (availability == ExternalTraceabilityAvailability.BROKEN_REFERENCE && reference.isPresent()) {
            throw new IllegalArgumentException("BROKEN_REFERENCE must not contain an external reference");
        }
        if (availability != ExternalTraceabilityAvailability.BROKEN_REFERENCE && reference.isEmpty()) {
            throw new IllegalArgumentException("non-broken external traceability view requires a reference");
        }
    }
}
