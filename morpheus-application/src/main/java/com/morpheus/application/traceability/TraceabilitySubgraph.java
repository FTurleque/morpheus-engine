package com.morpheus.application.traceability;

import com.morpheus.domain.traceability.TraceabilityEntityRef;
import com.morpheus.domain.traceability.TraceabilityLink;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/** Canonical persisted links and entities discovered by a bounded traversal. */
public record TraceabilitySubgraph(
        TraceabilityEntityRef start,
        List<TraceabilityEntityRef> nodes,
        List<TraceabilityLink> links) {

    public TraceabilitySubgraph {
        Objects.requireNonNull(start, "start");
        nodes = List.copyOf(Objects.requireNonNull(nodes, "nodes"));
        links = List.copyOf(Objects.requireNonNull(links, "links"));
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
}
