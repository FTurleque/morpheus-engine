package com.morpheus.application.traceability;

import com.morpheus.domain.traceability.TraceabilityEntityRef;
import com.morpheus.domain.traceability.TraceabilityLink;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Canonical persisted links and entities discovered by a bounded traversal. A present {@code truncationReason} says
 * which budget stopped the traversal before it observed everything within its requested depth.
 */
public record TraceabilitySubgraph(
        TraceabilityEntityRef start,
        List<TraceabilityEntityRef> nodes,
        List<TraceabilityLink> links,
        Optional<String> truncationReason) {

    public TraceabilitySubgraph {
        Objects.requireNonNull(start, "start");
        nodes = List.copyOf(Objects.requireNonNull(nodes, "nodes"));
        links = List.copyOf(Objects.requireNonNull(links, "links"));
        truncationReason = Objects.requireNonNull(truncationReason, "truncationReason");
        if (truncationReason.filter(String::isBlank).isPresent()) {
            throw new IllegalArgumentException("truncationReason must not be blank");
        }
        if (!nodes.contains(start)) {
            throw new IllegalArgumentException("subgraph nodes must contain the start entity");
        }
        if (new HashSet<>(nodes).size() != nodes.size()) {
            throw new IllegalArgumentException("subgraph nodes must not contain duplicates");
        }
        if (links.stream().map(TraceabilityLink::id).distinct().count() != links.size()) {
            throw new IllegalArgumentException("subgraph links must not contain duplicate link identities");
        }
    }

    public boolean truncated() {
        return truncationReason.isPresent();
    }
}
