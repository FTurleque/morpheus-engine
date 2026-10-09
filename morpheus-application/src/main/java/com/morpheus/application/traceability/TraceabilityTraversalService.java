package com.morpheus.application.traceability;

import com.morpheus.application.store.TraceabilityStore;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.traceability.TraceabilityEntityRef;
import com.morpheus.domain.traceability.TraceabilityLink;
import com.morpheus.domain.traceability.TraceabilityLinkId;
import com.morpheus.domain.traceability.TraceabilityRelationType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/**
 * Bounded deterministic traversal over the snapshot-scoped traceability persistence port.
 *
 * <p>Depth is the caller's; nodes and links are bounded here, with the budgets and the truncation vocabulary of
 * {@code PortfolioTraversalService}. A traversal that stops before observing everything within its depth says so in
 * {@link TraceabilitySubgraph#truncationReason()}; a path search that exhausts its node budget fails with
 * {@link TraceabilityTraversalBudgetException} instead of answering that no path exists.</p>
 */
public final class TraceabilityTraversalService {
    public static final int MAX_NODES = 1_000;
    public static final int MAX_LINKS = 5_000;

    private static final Comparator<Neighbor> NEIGHBOR_ORDER = Comparator
            .comparing(Neighbor::next)
            .thenComparing(neighbor -> neighbor.link().relationType())
            .thenComparing(neighbor -> neighbor.link().id());

    private final TraceabilityStore store;

    public TraceabilityTraversalService(TraceabilityStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    public List<TraceabilityLink> direct(
            KnowledgeSnapshotId snapshotId,
            TraceabilityEntityRef endpoint,
            TraceabilityTraversalDirection direction,
            Set<TraceabilityRelationType> relationTypes) {
        Objects.requireNonNull(snapshotId, "snapshotId");
        Objects.requireNonNull(endpoint, "endpoint");
        Objects.requireNonNull(direction, "direction");
        Set<TraceabilityRelationType> filter = immutableFilter(relationTypes);

        if (direction == TraceabilityTraversalDirection.OUTGOING) {
            return sortedLinks(store.outgoing(snapshotId, endpoint, filter));
        }
        if (direction == TraceabilityTraversalDirection.INCOMING) {
            return sortedLinks(store.incoming(snapshotId, endpoint, filter));
        }

        Map<TraceabilityLinkId, TraceabilityLink> links = new TreeMap<>();
        store.outgoing(snapshotId, endpoint, filter).forEach(link -> links.put(link.id(), link));
        store.incoming(snapshotId, endpoint, filter).forEach(link -> links.put(link.id(), link));
        return List.copyOf(links.values());
    }

    public TraceabilitySubgraph traverse(
            KnowledgeSnapshotId snapshotId,
            TraceabilityEntityRef start,
            int maxDepth,
            TraceabilityTraversalDirection direction,
            Set<TraceabilityRelationType> relationTypes) {
        Objects.requireNonNull(snapshotId, "snapshotId");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(direction, "direction");
        requirePositiveDepth(maxDepth);
        Set<TraceabilityRelationType> filter = immutableFilter(relationTypes);

        Map<TraceabilityEntityRef, Integer> depthByNode = new HashMap<>();
        Map<TraceabilityLinkId, TraceabilityLink> discoveredLinks = new TreeMap<>();
        ArrayDeque<TraceabilityEntityRef> queue = new ArrayDeque<>();
        depthByNode.put(start, 0);
        queue.add(start);
        Optional<String> truncation = Optional.empty();

        outer:
        while (!queue.isEmpty()) {
            TraceabilityEntityRef current = queue.removeFirst();
            int currentDepth = depthByNode.get(current);
            if (currentDepth >= maxDepth) {
                continue;
            }

            for (Neighbor neighbor : neighbors(snapshotId, current, direction, filter)) {
                if (!discoveredLinks.containsKey(neighbor.link().id()) && discoveredLinks.size() >= MAX_LINKS) {
                    truncation = Optional.of("LINK_BUDGET_REACHED:" + MAX_LINKS);
                    break outer;
                }
                if (!depthByNode.containsKey(neighbor.next())) {
                    if (depthByNode.size() >= MAX_NODES) {
                        truncation = Optional.of("NODE_BUDGET_REACHED:" + MAX_NODES);
                        break outer;
                    }
                    depthByNode.put(neighbor.next(), currentDepth + 1);
                    queue.addLast(neighbor.next());
                }
                discoveredLinks.put(neighbor.link().id(), neighbor.link());
            }
        }

        if (truncation.isEmpty() && linkBeyondDepth(snapshotId, depthByNode, discoveredLinks, maxDepth, direction, filter)) {
            truncation = Optional.of("DEPTH_BUDGET_REACHED:" + maxDepth);
        }

        List<TraceabilityEntityRef> nodes = depthByNode.keySet().stream().sorted().toList();
        return new TraceabilitySubgraph(start, nodes, List.copyOf(discoveredLinks.values()), truncation);
    }

    public Optional<TraceabilityPath> findPath(
            KnowledgeSnapshotId snapshotId,
            TraceabilityEntityRef start,
            TraceabilityEntityRef target,
            int maxDepth,
            TraceabilityTraversalDirection direction,
            Set<TraceabilityRelationType> relationTypes) {
        Objects.requireNonNull(snapshotId, "snapshotId");
        Objects.requireNonNull(start, "start");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(direction, "direction");
        requirePositiveDepth(maxDepth);
        Set<TraceabilityRelationType> filter = immutableFilter(relationTypes);

        if (start.equals(target)) {
            direct(snapshotId, start, direction, filter); // validate snapshot ownership through the store
            return Optional.of(new TraceabilityPath(start, target, List.of()));
        }

        Map<TraceabilityEntityRef, Integer> depthByNode = new HashMap<>();
        Map<TraceabilityEntityRef, TraceabilityPathStep> predecessor = new HashMap<>();
        ArrayDeque<TraceabilityEntityRef> queue = new ArrayDeque<>();
        depthByNode.put(start, 0);
        queue.add(start);

        while (!queue.isEmpty()) {
            TraceabilityEntityRef current = queue.removeFirst();
            int currentDepth = depthByNode.get(current);
            if (currentDepth >= maxDepth) {
                continue;
            }

            for (Neighbor neighbor : neighbors(snapshotId, current, direction, filter)) {
                if (depthByNode.containsKey(neighbor.next())) {
                    continue;
                }
                if (depthByNode.size() >= MAX_NODES) {
                    throw new TraceabilityTraversalBudgetException("NODE_BUDGET_REACHED:" + MAX_NODES);
                }
                depthByNode.put(neighbor.next(), currentDepth + 1);
                predecessor.put(
                        neighbor.next(),
                        new TraceabilityPathStep(neighbor.link(), current, neighbor.next()));

                if (neighbor.next().equals(target)) {
                    return Optional.of(reconstruct(start, target, predecessor));
                }
                queue.addLast(neighbor.next());
            }
        }

        return Optional.empty();
    }

    /**
     * True when a node at the requested depth has a link the traversal did not record: a node beyond the depth, or a
     * link between two nodes at the depth. A link back towards the start was recorded when its other end was expanded
     * and does not count, so a graph that ends exactly at the requested depth is not reported as truncated.
     */
    private boolean linkBeyondDepth(
            KnowledgeSnapshotId snapshotId,
            Map<TraceabilityEntityRef, Integer> depthByNode,
            Map<TraceabilityLinkId, TraceabilityLink> discoveredLinks,
            int maxDepth,
            TraceabilityTraversalDirection direction,
            Set<TraceabilityRelationType> relationTypes) {
        return depthByNode.entrySet().stream()
                .filter(entry -> entry.getValue() == maxDepth)
                .map(Map.Entry::getKey)
                .sorted()
                .anyMatch(frontier -> neighbors(snapshotId, frontier, direction, relationTypes).stream()
                        .anyMatch(neighbor -> !discoveredLinks.containsKey(neighbor.link().id())));
    }

    private List<Neighbor> neighbors(
            KnowledgeSnapshotId snapshotId,
            TraceabilityEntityRef current,
            TraceabilityTraversalDirection direction,
            Set<TraceabilityRelationType> relationTypes) {
        List<Neighbor> neighbors = new ArrayList<>();
        if (direction != TraceabilityTraversalDirection.INCOMING) {
            for (TraceabilityLink link : store.outgoing(snapshotId, current, relationTypes)) {
                neighbors.add(new Neighbor(link.target(), link));
            }
        }
        if (direction != TraceabilityTraversalDirection.OUTGOING) {
            for (TraceabilityLink link : store.incoming(snapshotId, current, relationTypes)) {
                neighbors.add(new Neighbor(link.source(), link));
            }
        }
        return neighbors.stream().distinct().sorted(NEIGHBOR_ORDER).toList();
    }

    private TraceabilityPath reconstruct(
            TraceabilityEntityRef start,
            TraceabilityEntityRef target,
            Map<TraceabilityEntityRef, TraceabilityPathStep> predecessor) {
        List<TraceabilityPathStep> reversed = new ArrayList<>();
        TraceabilityEntityRef current = target;
        while (!current.equals(start)) {
            TraceabilityPathStep step = predecessor.get(current);
            if (step == null) {
                throw new IllegalStateException("missing predecessor while reconstructing traceability path");
            }
            reversed.add(step);
            current = step.from();
        }
        java.util.Collections.reverse(reversed);
        return new TraceabilityPath(start, target, reversed);
    }

    private List<TraceabilityLink> sortedLinks(List<TraceabilityLink> links) {
        return links.stream().sorted(Comparator.comparing(TraceabilityLink::id)).toList();
    }

    private Set<TraceabilityRelationType> immutableFilter(Set<TraceabilityRelationType> relationTypes) {
        return Set.copyOf(Objects.requireNonNull(relationTypes, "relationTypes"));
    }

    private void requirePositiveDepth(int maxDepth) {
        if (maxDepth <= 0) {
            throw new IllegalArgumentException("maxDepth must be greater than zero");
        }
    }

    private record Neighbor(TraceabilityEntityRef next, TraceabilityLink link) {
        private Neighbor {
            Objects.requireNonNull(next, "next");
            Objects.requireNonNull(link, "link");
        }
    }
}
