package com.morpheus.store.memory;

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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The reference portfolio adapter: deterministic order, and the same refusals the durable one makes.
 *
 * <p>This adapter is what most of MORPHEUS is tested against, so a divergence here does not surface as a bug in
 * the adapter -- it surfaces as every other test agreeing with a behaviour SQLite does not have. Ordering and the
 * membership guards are therefore asserted as its own contract rather than assumed from the interface.</p>
 */
class MemoryPortfolioStoreTest {
    private static final Instant CREATED = Instant.parse("2026-09-01T08:00:00Z");
    private static final Instant OBSERVED = Instant.parse("2026-09-02T08:00:00Z");

    @Test
    void listingsAreOrderedByIdentityRatherThanByInsertion() {
        MemoryPortfolioStore store = new MemoryPortfolioStore();
        List<PortfolioId> ids = new ArrayList<>(List.of(
                PortfolioId.generate(), PortfolioId.generate(), PortfolioId.generate()));

        for (PortfolioId id : List.of(ids.get(2), ids.get(0), ids.get(1))) {
            store.putPortfolio(portfolio(id, "portfolio"));
        }

        List<PortfolioId> listed = store.listPortfolios().stream().map(PortfolioDefinition::id).toList();
        assertEquals(ids.stream().sorted().toList(), listed);
    }

    @Test
    void aPortfolioIsUpdatedInPlaceRatherThanDuplicated() {
        MemoryPortfolioStore store = new MemoryPortfolioStore();
        PortfolioId portfolioId = PortfolioId.generate();
        store.putPortfolio(portfolio(portfolioId, "platform"));

        store.putPortfolio(portfolio(portfolioId, "platform").rename("platform-core", OBSERVED));

        assertEquals(1, store.listPortfolios().size());
        assertEquals("platform-core", store.findPortfolio(portfolioId).orElseThrow().name());
        assertEquals(CREATED, store.findPortfolio(portfolioId).orElseThrow().createdAt());
    }

    @Test
    void membershipAndReferenceWritesRefuseAnUnknownPortfolio() {
        MemoryPortfolioStore store = new MemoryPortfolioStore();
        PortfolioId unknown = PortfolioId.generate();
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();

        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> store.putMembership(membership(unknown, projectId, "orphan")))
                .getMessage().contains("unknown portfolio"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> store.listMemberships(unknown))
                .getMessage().contains("unknown portfolio"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> store.listReferences(unknown))
                .getMessage().contains("unknown portfolio"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> store.listFreshness(unknown))
                .getMessage().contains("unknown portfolio"));
        assertEquals(Optional.empty(), store.findPortfolio(unknown));
        assertEquals(Optional.empty(), store.findMembership(unknown, projectId));
    }

    @Test
    void aReferenceIsRefusedUnlessBothEndsAreMembers() {
        MemoryPortfolioStore store = new MemoryPortfolioStore();
        PortfolioId portfolioId = PortfolioId.generate();
        ProjectSpecificationId member = ProjectSpecificationId.generate();
        ProjectSpecificationId stranger = ProjectSpecificationId.generate();
        store.putPortfolio(portfolio(portfolioId, "platform"));
        store.putMembership(membership(portfolioId, member, "member"));

        CrossProjectReference dangling = reference(
                portfolioId,
                new PortfolioEntityRef(member, "requirement", DomainIdentity.generate()),
                new PortfolioEntityRef(stranger, "requirement", DomainIdentity.generate()));

        assertTrue(assertThrows(IllegalArgumentException.class, () -> store.putReference(dangling))
                .getMessage().contains("project is not a portfolio member"));
        assertEquals(List.of(), store.listReferences(portfolioId));
        assertEquals(Optional.empty(), store.findReference(dangling.id()));
    }

    @Test
    void referencesAreReadableFromBothEndsAndOnlyFromTheirOwn() {
        MemoryPortfolioStore store = new MemoryPortfolioStore();
        PortfolioId portfolioId = PortfolioId.generate();
        ProjectSpecificationId sourceProject = ProjectSpecificationId.generate();
        ProjectSpecificationId targetProject = ProjectSpecificationId.generate();
        store.putPortfolio(portfolio(portfolioId, "platform"));
        store.putMembership(membership(portfolioId, sourceProject, "source"));
        store.putMembership(membership(portfolioId, targetProject, "target"));

        PortfolioEntityRef source = new PortfolioEntityRef(sourceProject, "requirement", DomainIdentity.generate());
        PortfolioEntityRef target = new PortfolioEntityRef(targetProject, "requirement", DomainIdentity.generate());
        CrossProjectReference stored = reference(portfolioId, source, target);
        store.putReference(stored);

        assertEquals(List.of(stored), store.listReferences(portfolioId));
        assertEquals(List.of(stored), store.outgoing(portfolioId, source));
        assertEquals(List.of(stored), store.incoming(portfolioId, target));
        assertEquals(List.of(), store.outgoing(portfolioId, target));
        assertEquals(List.of(), store.incoming(portfolioId, source));
    }

    @Test
    void freshnessRequiresMembershipAndNeverMovesBackwards() {
        MemoryPortfolioStore store = new MemoryPortfolioStore();
        PortfolioId portfolioId = PortfolioId.generate();
        ProjectSpecificationId member = ProjectSpecificationId.generate();
        ProjectSpecificationId stranger = ProjectSpecificationId.generate();
        store.putPortfolio(portfolio(portfolioId, "platform"));
        store.putMembership(membership(portfolioId, member, "member"));

        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> store.putFreshness(freshness(portfolioId, stranger, PortfolioFreshnessState.FRESH, OBSERVED)))
                .getMessage().contains("project is not a portfolio member"));

        store.putFreshness(freshness(portfolioId, member, PortfolioFreshnessState.FRESH, OBSERVED));
        store.putFreshness(freshness(
                portfolioId, member, PortfolioFreshnessState.STALE, OBSERVED.plusSeconds(60)));

        assertEquals(PortfolioFreshnessState.STALE,
                store.findFreshness(portfolioId, member).orElseThrow().state());
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> store.putFreshness(freshness(portfolioId, member, PortfolioFreshnessState.UNKNOWN, OBSERVED)))
                .getMessage().contains("must not move backwards"));
        assertEquals(PortfolioFreshnessState.STALE,
                store.findFreshness(portfolioId, member).orElseThrow().state());
        assertEquals(1, store.listFreshness(portfolioId).size());
    }

    /** Two portfolios are two answers; neither listing may include the other's rows. */
    @Test
    void listingsAreScopedToTheirOwnPortfolio() {
        MemoryPortfolioStore store = new MemoryPortfolioStore();
        PortfolioId first = PortfolioId.generate();
        PortfolioId second = PortfolioId.generate();
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        store.putPortfolio(portfolio(first, "first"));
        store.putPortfolio(portfolio(second, "second"));
        store.putMembership(membership(first, projectId, "shared"));
        store.putFreshness(freshness(first, projectId, PortfolioFreshnessState.FRESH, OBSERVED));

        assertEquals(1, store.listMemberships(first).size());
        assertEquals(List.of(), store.listMemberships(second));
        assertEquals(1, store.listFreshness(first).size());
        assertEquals(List.of(), store.listFreshness(second));
        assertEquals(Optional.empty(), store.findMembership(second, projectId));
    }

    private static PortfolioDefinition portfolio(PortfolioId id, String name) {
        return new PortfolioDefinition(id, name, CREATED, CREATED);
    }

    private static PortfolioMembership membership(
            PortfolioId portfolioId, ProjectSpecificationId projectId, String displayName) {
        return new PortfolioMembership(
                portfolioId,
                projectId,
                displayName,
                Optional.of(SourceLocator.file("workspace/" + displayName)),
                Optional.empty(),
                Set.of(new ProviderId("openspec")),
                PortfolioMembershipStatus.ACTIVE,
                CREATED,
                OBSERVED);
    }

    private static CrossProjectReference reference(
            PortfolioId portfolioId, PortfolioEntityRef source, PortfolioEntityRef target) {
        return new CrossProjectReference(
                CrossProjectReferenceId.generate(),
                portfolioId,
                source,
                target,
                "depends-on",
                new ProviderId("openspec"),
                Optional.empty(),
                Optional.of(EvidenceId.generate()),
                OBSERVED);
    }

    private static PortfolioFreshness freshness(
            PortfolioId portfolioId,
            ProjectSpecificationId projectId,
            PortfolioFreshnessState state,
            Instant observedAt) {
        return new PortfolioFreshness(
                portfolioId, projectId, state, observedAt, Optional.of("revision"), Optional.empty());
    }
}
