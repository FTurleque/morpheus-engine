package com.morpheus.application.portfolio;

import com.morpheus.application.store.PortfolioStore;
import com.morpheus.domain.portfolio.CrossProjectReference;
import com.morpheus.domain.portfolio.CrossProjectReferenceId;
import com.morpheus.domain.portfolio.PortfolioEntityRef;
import com.morpheus.domain.portfolio.PortfolioId;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/** Bounded deterministic BFS over explicit cross-project references. */
public final class PortfolioTraversalService {
    public static final int MAX_DEPTH = 8;
    public static final int MAX_NODES = 1_000;
    public static final int MAX_LINKS = 5_000;

    private static final Comparator<Neighbor> ORDER = Comparator
            .comparing(Neighbor::next)
            .thenComparing(neighbor -> neighbor.reference().relation())
            .thenComparing(neighbor -> neighbor.reference().id());

    private final PortfolioStore store;

    public PortfolioTraversalService(PortfolioStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    public PortfolioTraversalResult traverse(
            PortfolioId portfolioId,
            PortfolioEntityRef start,
            int maxDepth,
            int maxNodes,
            int maxLinks,
            PortfolioTraversalDirection direction) {
        Objects.requireNonNull(portfolioId, "portfolioId");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(direction, "direction");
        requireBudget(maxDepth, maxNodes, maxLinks);
        if (store.findPortfolio(portfolioId).isEmpty()) {
            throw new IllegalArgumentException("unknown portfolio: " + portfolioId);
        }
        if (store.findMembership(portfolioId, start.projectId()).isEmpty()) {
            throw new IllegalArgumentException("start project is not a portfolio member: " + start.projectId());
        }

        Map<PortfolioEntityRef, Integer> depthByNode = new LinkedHashMap<>();
        Map<CrossProjectReferenceId, CrossProjectReference> links = new TreeMap<>();
        ArrayDeque<PortfolioEntityRef> queue = new ArrayDeque<>();
        depthByNode.put(start, 0);
        queue.add(start);
        Optional<String> truncation = Optional.empty();

        outer:
        while (!queue.isEmpty()) {
            PortfolioEntityRef current = queue.removeFirst();
            int depth = depthByNode.get(current);
            if (depth >= maxDepth) {
                continue;
            }
            for (Neighbor neighbor : neighbors(portfolioId, current, direction)) {
                if (!links.containsKey(neighbor.reference().id()) && links.size() >= maxLinks) {
                    truncation = Optional.of("LINK_BUDGET_REACHED:" + maxLinks);
                    break outer;
                }
                links.put(neighbor.reference().id(), neighbor.reference());
                if (!depthByNode.containsKey(neighbor.next())) {
                    if (depthByNode.size() >= maxNodes) {
                        truncation = Optional.of("NODE_BUDGET_REACHED:" + maxNodes);
                        break outer;
                    }
                    depthByNode.put(neighbor.next(), depth + 1);
                    queue.addLast(neighbor.next());
                }
            }
        }

        if (truncation.isEmpty() && depthByNode.values().stream().anyMatch(depth -> depth >= maxDepth)) {
            boolean more = depthByNode.entrySet().stream()
                    .filter(entry -> entry.getValue() == maxDepth)
                    .anyMatch(entry -> !neighbors(portfolioId, entry.getKey(), direction).isEmpty());
            if (more) {
                truncation = Optional.of("DEPTH_BUDGET_REACHED:" + maxDepth);
            }
        }

        return new PortfolioTraversalResult(start, depthByNode, List.copyOf(links.values()), truncation);
    }

    private List<Neighbor> neighbors(
            PortfolioId portfolioId,
            PortfolioEntityRef current,
            PortfolioTraversalDirection direction) {
        List<Neighbor> neighbors = new ArrayList<>();
        if (direction != PortfolioTraversalDirection.INCOMING) {
            for (CrossProjectReference reference : store.outgoing(portfolioId, current)) {
                neighbors.add(new Neighbor(reference.target(), reference));
            }
        }
        if (direction != PortfolioTraversalDirection.OUTGOING) {
            for (CrossProjectReference reference : store.incoming(portfolioId, current)) {
                neighbors.add(new Neighbor(reference.source(), reference));
            }
        }
        return neighbors.stream().distinct().sorted(ORDER).toList();
    }

    private void requireBudget(int depth, int nodes, int links) {
        if (depth <= 0 || depth > MAX_DEPTH) {
            throw new IllegalArgumentException("maxDepth must be between 1 and " + MAX_DEPTH);
        }
        if (nodes <= 0 || nodes > MAX_NODES) {
            throw new IllegalArgumentException("maxNodes must be between 1 and " + MAX_NODES);
        }
        if (links <= 0 || links > MAX_LINKS) {
            throw new IllegalArgumentException("maxLinks must be between 1 and " + MAX_LINKS);
        }
    }

    private record Neighbor(PortfolioEntityRef next, CrossProjectReference reference) {
        private Neighbor {
            Objects.requireNonNull(next, "next");
            Objects.requireNonNull(reference, "reference");
        }
    }
}
