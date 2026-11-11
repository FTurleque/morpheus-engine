package com.morpheus.application.portfolio;

import com.morpheus.application.store.EntityNotFoundException;
import com.morpheus.application.store.EntityStateException;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.portfolio.PortfolioEntityRef;
import com.morpheus.domain.portfolio.PortfolioId;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provider.ProviderId;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A chain a -> b -> c -> d of explicit references across four projects, walked under every budget. */
class PortfolioTraversalServiceTest {
    private static final ProviderId OPENSPEC = new ProviderId("openspec");

    private final HandWrittenPortfolioStore store = new HandWrittenPortfolioStore();
    private final PortfolioRegistryService registry = new PortfolioRegistryService(
            store, Clock.fixed(Instant.parse("2026-10-30T08:00:00Z"), ZoneOffset.UTC));
    private final PortfolioTraversalService traversal = new PortfolioTraversalService(store);
    private final PortfolioId portfolio = registry.create("Platform").id();
    private final PortfolioEntityRef a = member("a");
    private final PortfolioEntityRef b = member("b");
    private final PortfolioEntityRef c = member("c");
    private final PortfolioEntityRef d = member("d");

    PortfolioTraversalServiceTest() {
        link(a, b);
        link(b, c);
        link(c, d);
    }

    @Test
    void anOutgoingWalkWithinBudgetReachesTheWholeChainUntruncated() {
        PortfolioTraversalResult result = walk(a, PortfolioTraversalService.MAX_DEPTH,
                PortfolioTraversalService.MAX_NODES, PortfolioTraversalService.MAX_LINKS,
                PortfolioTraversalDirection.OUTGOING);

        assertEquals(Map.of(a, 0, b, 1, c, 2, d, 3), result.depthByNode());
        assertEquals(3, result.links().size());
        assertFalse(result.truncated());
    }

    @Test
    void aFrontierWithoutFurtherNeighboursAtTheDepthBudgetIsNotTruncated() {
        PortfolioTraversalResult result = walk(a, 3, 10, 10, PortfolioTraversalDirection.OUTGOING);

        assertEquals(3, result.depthByNode().get(d));
        assertTrue(result.truncationReason().isEmpty());
    }

    @Test
    void aFrontierThatStillHasNeighboursAtTheDepthBudgetIsTruncatedByDepth() {
        PortfolioTraversalResult result = walk(a, 1, 10, 10, PortfolioTraversalDirection.OUTGOING);

        assertEquals(Map.of(a, 0, b, 1), result.depthByNode());
        assertEquals(Optional.of("DEPTH_BUDGET_REACHED:1"), result.truncationReason());
    }

    @Test
    void incomingAndBothDirectionsFollowReferencesBackwards() {
        PortfolioTraversalResult incoming = walk(c, 8, 10, 10, PortfolioTraversalDirection.INCOMING);
        assertEquals(Map.of(c, 0, b, 1, a, 2), incoming.depthByNode());

        PortfolioTraversalResult both = walk(b, 8, 10, 10, PortfolioTraversalDirection.BOTH);
        assertEquals(Map.of(b, 0, a, 1, c, 1, d, 2), both.depthByNode());
        assertEquals(3, both.links().size());
    }

    @Test
    void theNodeBudgetStopsTheWalkAndNamesItself() {
        PortfolioTraversalResult result = walk(a, 8, 2, 10, PortfolioTraversalDirection.OUTGOING);

        assertEquals(Set.of(a, b), result.depthByNode().keySet());
        assertEquals(Optional.of("NODE_BUDGET_REACHED:2"), result.truncationReason());
    }

    @Test
    void theLinkBudgetStopsTheWalkAndNamesItself() {
        PortfolioTraversalResult result = walk(a, 8, 10, 1, PortfolioTraversalDirection.OUTGOING);

        assertEquals(1, result.links().size());
        assertEquals(Optional.of("LINK_BUDGET_REACHED:1"), result.truncationReason());
    }

    @Test
    void everyBudgetOutsideItsBoundsIsRefusedByName() {
        assertEquals("maxDepth must be between 1 and 8", assertThrows(IllegalArgumentException.class,
                () -> walk(a, 0, 10, 10, PortfolioTraversalDirection.OUTGOING)).getMessage());
        assertEquals("maxDepth must be between 1 and 8", assertThrows(IllegalArgumentException.class,
                () -> walk(a, 9, 10, 10, PortfolioTraversalDirection.OUTGOING)).getMessage());
        assertEquals("maxNodes must be between 1 and 1000", assertThrows(IllegalArgumentException.class,
                () -> walk(a, 1, 1001, 10, PortfolioTraversalDirection.OUTGOING)).getMessage());
        assertEquals("maxLinks must be between 1 and 5000", assertThrows(IllegalArgumentException.class,
                () -> walk(a, 1, 10, 0, PortfolioTraversalDirection.OUTGOING)).getMessage());
    }

    @Test
    void anUnknownPortfolioOrAStartOutsideItIsRefused() {
        PortfolioId unknown = PortfolioId.generate();
        EntityNotFoundException missing = assertThrows(EntityNotFoundException.class,
                () -> traversal.traverse(unknown, a, 1, 1, 1, PortfolioTraversalDirection.BOTH));
        assertEquals("unknown portfolio: " + unknown, missing.getMessage());

        ProjectSpecificationId stranger = ProjectSpecificationId.generate();
        EntityStateException outside = assertThrows(EntityStateException.class,
                () -> walk(new PortfolioEntityRef(stranger, "requirement", DomainIdentity.generate()), 1, 1, 1,
                        PortfolioTraversalDirection.BOTH));
        assertEquals("start project is not a portfolio member: " + stranger, outside.getMessage());
    }

    @Test
    void aResultMustStartAtDepthZero() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> new PortfolioTraversalResult(a, Map.of(a, 1), List.of(), Optional.empty()));

        assertEquals("start node must be present at depth zero", failure.getMessage());
    }

    private PortfolioTraversalResult walk(
            PortfolioEntityRef start, int depth, int nodes, int links, PortfolioTraversalDirection direction) {
        return traversal.traverse(portfolio, start, depth, nodes, links, direction);
    }

    private PortfolioEntityRef member(String name) {
        ProjectSpecificationId project = ProjectSpecificationId.generate();
        registry.registerProject(portfolio, project, name, Optional.empty(), Optional.empty(), Set.of(OPENSPEC));
        return new PortfolioEntityRef(project, "requirement", DomainIdentity.generate());
    }

    private void link(PortfolioEntityRef source, PortfolioEntityRef target) {
        registry.addReference(portfolio, source, target, "depends-on", OPENSPEC, Optional.empty(), Optional.empty());
    }
}
