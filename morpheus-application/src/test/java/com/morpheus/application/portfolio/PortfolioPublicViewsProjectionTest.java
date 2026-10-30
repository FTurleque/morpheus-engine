package com.morpheus.application.portfolio;

import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.portfolio.CrossProjectReference;
import com.morpheus.domain.portfolio.CrossProjectReferenceId;
import com.morpheus.domain.portfolio.PortfolioDefinition;
import com.morpheus.domain.portfolio.PortfolioEntityRef;
import com.morpheus.domain.portfolio.PortfolioFreshness;
import com.morpheus.domain.portfolio.PortfolioFreshnessState;
import com.morpheus.domain.portfolio.PortfolioId;
import com.morpheus.domain.portfolio.PortfolioMembership;
import com.morpheus.domain.portfolio.PortfolioMembershipStatus;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.source.SourceLocator;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** {@link PortfolioPublicViews#project} dispatches every portfolio type to its view and refuses any other. */
class PortfolioPublicViewsProjectionTest {
    private static final Instant NOW = Instant.parse("2026-10-30T08:00:00Z");

    private final PortfolioId portfolioId = PortfolioId.generate();
    private final ProjectSpecificationId source = ProjectSpecificationId.generate();
    private final ProjectSpecificationId target = ProjectSpecificationId.generate();
    private final PortfolioEntityRef from = new PortfolioEntityRef(source, "requirement", DomainIdentity.generate());
    private final PortfolioDefinition portfolio = new PortfolioDefinition(portfolioId, "Platform", NOW, NOW);
    private final PortfolioMembership membership = new PortfolioMembership(portfolioId, source, "Billing",
            Optional.of(SourceLocator.file("workspace/billing")), Optional.empty(),
            Set.of(new ProviderId("openspec"), new ProviderId("markdown")), PortfolioMembershipStatus.ACTIVE, NOW, NOW);
    private final PortfolioFreshness freshness = new PortfolioFreshness(portfolioId, source,
            PortfolioFreshnessState.STALE, NOW, Optional.of("r1"), Optional.empty());

    @Test
    void aMembershipNamesItsLocatorsAndOrdersItsProviders() {
        PortfolioPublicViews.MembershipView view = assertInstanceOf(PortfolioPublicViews.MembershipView.class,
                PortfolioPublicViews.project(membership));

        assertEquals(source.toString(), view.projectId());
        assertEquals(Optional.of(SourceLocator.file("workspace/billing").toString()), view.workspace());
        assertEquals(Optional.empty(), view.repository());
        assertEquals(List.of("markdown", "openspec"), view.providers());
        assertEquals("ACTIVE", view.status());
        assertEquals(NOW.toString(), view.lastObservedAt());
    }

    @Test
    void aFreshnessKeepsItsStateRevisionAndAbsentExplanation() {
        PortfolioPublicViews.FreshnessView view = assertInstanceOf(PortfolioPublicViews.FreshnessView.class,
                PortfolioPublicViews.project(freshness));

        assertEquals(new PortfolioPublicViews.FreshnessView(portfolioId.toString(), source.toString(), "STALE",
                NOW.toString(), Optional.of("r1"), Optional.empty()), view);
    }

    @Test
    void aReferenceProjectsBothEndpointsAndItsOptionalProvenance() {
        EvidenceId evidenceId = EvidenceId.generate();
        CrossProjectReference reference = reference(Optional.of(evidenceId));

        PortfolioPublicViews.ReferenceView view = assertInstanceOf(PortfolioPublicViews.ReferenceView.class,
                PortfolioPublicViews.project(reference));

        assertEquals(reference.id().toString(), view.id());
        assertEquals(PortfolioPublicViews.entity(from), view.source());
        assertEquals(target.toString(), view.target().projectId());
        assertEquals("depends-on", view.relation());
        assertEquals("openspec", view.providerId());
        assertEquals(Optional.of(evidenceId.toString()), view.evidenceId());
        assertEquals(Optional.empty(), view.sourceLocator());
    }

    @Test
    void anOverviewProjectsItsMembersFreshnessConflictsAndCount() {
        PortfolioReferenceConflict conflict = new PortfolioReferenceConflict(from, "depends-on",
                List.of(reference(Optional.empty()), reference(Optional.empty())));

        PortfolioPublicViews.OverviewView view = assertInstanceOf(PortfolioPublicViews.OverviewView.class,
                PortfolioPublicViews.project(new PortfolioOverview(portfolio, List.of(membership), List.of(freshness),
                        List.of(conflict), 2)));

        assertEquals("Platform", view.portfolio().name());
        assertEquals(1, view.memberships().size());
        assertEquals(1, view.freshness().size());
        assertEquals(2, view.conflicts().getFirst().observations().size());
        assertEquals(2, view.referenceCount());
        assertInstanceOf(PortfolioPublicViews.ConflictView.class, PortfolioPublicViews.project(conflict));
    }

    @Test
    void aListAndATraversalAreProjectedItemByItem() {
        Object listed = PortfolioPublicViews.project(List.of(portfolio, membership));
        Object traversal = PortfolioPublicViews.project(new PortfolioTraversalResult(
                from, Map.of(from, 0), List.of(), Optional.of("MAX_DEPTH")));

        assertEquals(List.of(PortfolioPublicViews.portfolio(portfolio), PortfolioPublicViews.membership(membership)),
                listed);
        PortfolioPublicViews.TraversalView view = assertInstanceOf(PortfolioPublicViews.TraversalView.class, traversal);
        assertEquals(Optional.of("MAX_DEPTH"), view.truncationReason());
        assertEquals(true, view.truncated());
    }

    @Test
    void anyOtherTypeIsRefusedByName() {
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> PortfolioPublicViews.project("text"));

        assertEquals("unsupported M23 public view type: java.lang.String", refused.getMessage());
    }

    private CrossProjectReference reference(Optional<EvidenceId> evidenceId) {
        return new CrossProjectReference(CrossProjectReferenceId.generate(), portfolioId, from,
                new PortfolioEntityRef(target, "requirement", DomainIdentity.generate()), "depends-on",
                new ProviderId("openspec"), Optional.empty(), evidenceId, NOW);
    }
}
