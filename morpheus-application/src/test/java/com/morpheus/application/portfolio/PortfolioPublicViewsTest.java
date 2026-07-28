package com.morpheus.application.portfolio;

import com.morpheus.application.query.compact.CanonicalJsonSerializer;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.portfolio.PortfolioDefinition;
import com.morpheus.domain.portfolio.PortfolioEntityRef;
import com.morpheus.domain.portfolio.PortfolioId;
import com.morpheus.domain.project.ProjectSpecificationId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortfolioPublicViewsTest {
    private final CanonicalJsonSerializer json = new CanonicalJsonSerializer();

    @Test
    void portfolioIdentityAndInstantsAreProjectedToCanonicalStrings() {
        PortfolioDefinition portfolio = new PortfolioDefinition(
                new PortfolioId(id("01890f7a-36d4-7c1e-8000-000000000001")),
                "Platform",
                Instant.parse("2026-07-28T12:00:00Z"),
                Instant.parse("2026-07-28T12:01:00Z"));

        String encoded = json.toJson(PortfolioPublicViews.portfolio(portfolio));

        assertTrue(encoded.contains("\"id\":\"01890f7a-36d4-7c1e-8000-000000000001\""), encoded);
        assertTrue(encoded.contains("\"createdAt\":\"2026-07-28T12:00:00Z\""), encoded);
        assertFalse(encoded.contains("java.util.UUID"), encoded);
    }

    @Test
    void traversalDepthMapIsProjectedToOrderedNodeList() {
        ProjectSpecificationId project = new ProjectSpecificationId(
                id("01890f7a-36d4-7c1e-8000-000000000010"));
        PortfolioEntityRef start = new PortfolioEntityRef(
                project,
                "requirement",
                id("01890f7a-36d4-7c1e-8000-000000000003"));
        PortfolioEntityRef second = new PortfolioEntityRef(
                project,
                "specification",
                id("01890f7a-36d4-7c1e-8000-000000000001"));
        PortfolioEntityRef third = new PortfolioEntityRef(
                project,
                "specification",
                id("01890f7a-36d4-7c1e-8000-000000000002"));
        Map<PortfolioEntityRef, Integer> depths = new LinkedHashMap<>();
        depths.put(start, 0);
        depths.put(second, 1);
        depths.put(third, 2);
        PortfolioTraversalResult traversal = new PortfolioTraversalResult(
                start,
                depths,
                List.of(),
                Optional.empty());

        PortfolioPublicViews.TraversalView view = PortfolioPublicViews.traversal(traversal);
        assertEquals(
                List.of(
                        "01890f7a-36d4-7c1e-8000-000000000003",
                        "01890f7a-36d4-7c1e-8000-000000000001",
                        "01890f7a-36d4-7c1e-8000-000000000002"),
                view.nodes().stream().map(item -> item.node().entityId()).toList());

        String encoded = json.toJson(view);
        assertTrue(encoded.contains("\"nodes\":[{\"node\":"), encoded);
        assertTrue(encoded.contains("\"depth\":0"), encoded);
        assertFalse(encoded.contains("depthByNode"), encoded);
    }

    private static DomainIdentity id(String value) {
        return DomainIdentity.parse(value);
    }
}
