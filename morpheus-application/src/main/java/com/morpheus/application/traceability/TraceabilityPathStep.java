package com.morpheus.application.traceability;

import com.morpheus.domain.traceability.TraceabilityEntityRef;
import com.morpheus.domain.traceability.TraceabilityLink;

import java.util.Objects;

/** One traversed step that preserves the canonical persisted link and the query-time traversal direction. */
public record TraceabilityPathStep(
        TraceabilityLink link,
        TraceabilityEntityRef from,
        TraceabilityEntityRef into) {

    public TraceabilityPathStep {
        Objects.requireNonNull(link, "link");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(into, "into");
        boolean canonical = link.source().equals(from) && link.target().equals(into);
        boolean inverse = link.target().equals(from) && link.source().equals(into);
        if (!canonical && !inverse) {
            throw new IllegalArgumentException("path step endpoints must match the persisted traceability link");
        }
    }

    public boolean reversed() {
        return !(link.source().equals(from) && link.target().equals(into));
    }
}
