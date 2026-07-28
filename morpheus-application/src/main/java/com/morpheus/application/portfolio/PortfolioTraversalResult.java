package com.morpheus.application.portfolio;

import com.morpheus.domain.portfolio.CrossProjectReference;
import com.morpheus.domain.portfolio.PortfolioEntityRef;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record PortfolioTraversalResult(
        PortfolioEntityRef start,
        Map<PortfolioEntityRef, Integer> depthByNode,
        List<CrossProjectReference> links,
        Optional<String> truncationReason) {
    public PortfolioTraversalResult {
        Objects.requireNonNull(start, "start");
        depthByNode = Map.copyOf(Objects.requireNonNull(depthByNode, "depthByNode"));
        links = Objects.requireNonNull(links, "links").stream().sorted().toList();
        truncationReason = Objects.requireNonNull(truncationReason, "truncationReason");
        if (!depthByNode.containsKey(start) || depthByNode.get(start) != 0) {
            throw new IllegalArgumentException("start node must be present at depth zero");
        }
    }

    public boolean truncated() {
        return truncationReason.isPresent();
    }
}
