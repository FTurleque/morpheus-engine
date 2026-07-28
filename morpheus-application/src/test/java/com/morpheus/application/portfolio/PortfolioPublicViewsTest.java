package com.morpheus.application.portfolio;

import com.morpheus.application.query.compact.CanonicalJsonSerializer;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.portfolio.PortfolioDefinition;
import com.morpheus.domain.portfolio.PortfolioEntityRef;
import com.morpheus.domain.portfolio.PortfolioId;
import com.morpheus.domain.project.ProjectSpecificationId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
                id("01890f7a-36d4-7c1e-8000-000000000002"));
        PortfolioEntityRef start = new PortfolioEntityRef(
                project,
                "requirement",
                id("01890f7a-36d4-7c1e-8000-000000000003"));
        PortfolioTraversalResult traversal = new PortfolioTraversalResult(
                start,
                Map.of(start, 0),
                List.of(),
                Optional.empty());

        String encoded = json.toJson(PortfolioPublicViews.traversal(traversal));

        assertTrue(encoded.contains("\"nodes\":[{\"node\":"), encoded);
        assertTrue(encoded.contains("\"depth\":0"), encoded);
        assertFalse(encoded.contains("depthByNode"), encoded);
    }

    private static DomainIdentity id(String value) {
        return DomainIdentity.parse(value);
    }
}
