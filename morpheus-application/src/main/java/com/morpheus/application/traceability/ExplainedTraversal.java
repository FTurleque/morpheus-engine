package com.morpheus.application.traceability;

import com.morpheus.domain.traceability.TraceabilityEntityRef;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/**
 * A bounded traversal and, for every node it reached other than its start, the shortest path that reached it. The
 * paths are ordered by target and are those the traversal itself followed: a node has one if and only if the subgraph
 * lists it.
 */
public record ExplainedTraversal(
        TraceabilitySubgraph subgraph,
        Map<TraceabilityEntityRef, TraceabilityPath> paths) {

    public ExplainedTraversal {
        Objects.requireNonNull(subgraph, "subgraph");
        paths = Collections.unmodifiableMap(new TreeMap<>(Objects.requireNonNull(paths, "paths")));
        Set<TraceabilityEntityRef> reached = new HashSet<>(subgraph.nodes());
        reached.remove(subgraph.start());
        if (!reached.equals(paths.keySet())) {
            throw new IllegalArgumentException("a traversal explains exactly the nodes it reached besides its start");
        }
    }
}
