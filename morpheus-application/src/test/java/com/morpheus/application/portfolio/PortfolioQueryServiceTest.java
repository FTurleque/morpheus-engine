package com.morpheus.application.portfolio;

import com.morpheus.application.store.EntityNotFoundException;
import com.morpheus.application.store.EntityStateException;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.portfolio.CrossProjectReference;
import com.morpheus.domain.portfolio.CrossProjectReferenceId;
import com.morpheus.domain.portfolio.PortfolioDefinition;
import com.morpheus.domain.portfolio.PortfolioEntityRef;
import com.morpheus.domain.portfolio.PortfolioFreshnessState;
import com.morpheus.domain.portfolio.PortfolioId;
import com.morpheus.domain.portfolio.PortfolioMembership;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provider.ProviderId;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Three projects: one source requirement observed pointing at two different targets, and one agreeing link. */
class PortfolioQueryServiceTest {
    private static final Instant T0 = Instant.parse("2026-10-30T08:00:00Z");
    private static final ProviderId OPENSPEC = new ProviderId("openspec");

    private final HandWrittenPortfolioStore store = new HandWrittenPortfolioStore();
    private final PortfolioRegistryService registry =
            new PortfolioRegistryService(store, Clock.fixed(T0, ZoneOffset.UTC));
    private final PortfolioQueryService query = new PortfolioQueryService(store);
    private final PortfolioId portfolio = registry.create("Platform").id();
    private final ProjectSpecificationId billing = register("Billing");
    private final ProjectSpecificationId ledger = register("Ledger");
    private final ProjectSpecificationId audit = register("Audit");
    private final PortfolioEntityRef source = ref(billing);
    private final CrossProjectReference toLedger = link(source, ref(ledger), "depends-on");
    private final CrossProjectReference toAudit = link(source, ref(audit), "depends-on");
    private final CrossProjectReference agreeing = link(ref(ledger), ref(audit), "implements");

    @Test
    void theOverviewSortsWhatItShowsAndCountsEveryReference() {
        registry.observeFreshness(portfolio, ledger, PortfolioFreshnessState.STALE, Optional.empty(), Optional.empty());

        PortfolioOverview overview = query.overview(portfolio);

        assertEquals("Platform", overview.portfolio().name());
        assertEquals(3, overview.referenceCount());
        assertEquals(store.listMemberships(portfolio).stream().sorted().toList(), overview.memberships());
        assertEquals(1, overview.freshness().size());
        assertEquals(1, overview.conflicts().size());
    }

    @Test
    void aSourceObservedPointingAtTwoTargetsIsAConflictThatKeepsBothObservations() {
        List<PortfolioReferenceConflict> conflicts = query.conflicts(portfolio);

        assertEquals(1, conflicts.size());
        PortfolioReferenceConflict conflict = conflicts.getFirst();
        assertEquals(source, conflict.source());
        assertEquals("depends-on", conflict.relation());
        assertEquals(List.of(toLedger, toAudit).stream().sorted().toList(), conflict.observations());
    }

    @Test
    void aProjectSeesTheReferencesItIsAnEndOfAndOnlyThose() {
        List<CrossProjectReference> forBilling = query.projectReferences(portfolio, billing, 0, 10);
        assertEquals(List.of(toLedger, toAudit).stream().sorted().toList(), forBilling);

        List<CrossProjectReference> forAudit = query.projectReferences(portfolio, audit, 0, 10);
        assertEquals(List.of(toAudit, agreeing).stream().sorted().toList(), forAudit);

        ProjectSpecificationId stranger = ProjectSpecificationId.generate();
        EntityStateException failure = assertThrows(EntityStateException.class,
                () -> query.projectReferences(portfolio, stranger, 0, 10));
        assertEquals("project is not a portfolio member: " + stranger, failure.getMessage());
    }

    @Test
    void pagesAreCutFromTheStoreOrderWithinTheirBounds() {
        PortfolioDefinition second = registry.create("Second");
        registry.create("Third");

        assertEquals(List.of(second), query.listPortfolios(1, 1));
        assertTrue(query.listPortfolios(3, 5).isEmpty());
        assertEquals(3, query.references(portfolio, 0, PortfolioQueryService.MAX_PAGE_SIZE).size());
        assertEquals(1, query.references(portfolio, 2, 5).size());
        List<PortfolioMembership> memberships = query.memberships(portfolio, 0, 2);
        assertEquals(2, memberships.size());
        assertEquals(billing, memberships.getFirst().projectId());
    }

    @Test
    void anOffsetOrLimitOutsideTheBoundsIsRefusedByName() {
        assertEquals("offset must be non-negative", assertThrows(IllegalArgumentException.class,
                () -> query.listPortfolios(-1, 1)).getMessage());
        assertEquals("limit must be between 1 and 500", assertThrows(IllegalArgumentException.class,
                () -> query.listPortfolios(0, 0)).getMessage());
        assertEquals("limit must be between 1 and 500", assertThrows(IllegalArgumentException.class,
                () -> query.memberships(portfolio, 0, 501)).getMessage());
    }

    @Test
    void anUnknownPortfolioIsRefusedByEveryQuery() {
        PortfolioId unknown = PortfolioId.generate();
        String expected = "unknown portfolio: " + unknown;

        assertEquals(expected, assertThrows(EntityNotFoundException.class,
                () -> query.overview(unknown)).getMessage());
        assertEquals(expected, assertThrows(EntityNotFoundException.class,
                () -> query.memberships(unknown, 0, 1)).getMessage());
        assertEquals(expected, assertThrows(EntityNotFoundException.class,
                () -> query.references(unknown, 0, 1)).getMessage());
        assertEquals(expected, assertThrows(EntityNotFoundException.class,
                () -> query.conflicts(unknown)).getMessage());
    }

    @Test
    void anOverviewAndAConflictRefuseWhatTheyCannotBe() {
        PortfolioDefinition definition = store.findPortfolio(portfolio).orElseThrow();
        assertEquals("referenceCount must be non-negative", assertThrows(IllegalArgumentException.class,
                () -> new PortfolioOverview(definition, List.of(), List.of(), List.of(), -1)).getMessage());
        assertEquals("a conflict requires a relation and at least two observations",
                assertThrows(IllegalArgumentException.class,
                        () -> new PortfolioReferenceConflict(source, "depends-on", List.of(toLedger))).getMessage());
        CrossProjectReference duplicate = new CrossProjectReference(CrossProjectReferenceId.generate(), portfolio,
                source, toLedger.target(), "depends-on", new ProviderId("markdown"), Optional.empty(),
                Optional.empty(), T0);
        assertEquals("a conflict requires at least two distinct targets", assertThrows(IllegalArgumentException.class,
                () -> new PortfolioReferenceConflict(source, "depends-on", List.of(toLedger, duplicate))).getMessage());
    }

    private ProjectSpecificationId register(String name) {
        ProjectSpecificationId project = ProjectSpecificationId.generate();
        registry.registerProject(portfolio, project, name, Optional.empty(), Optional.empty(), Set.of(OPENSPEC));
        return project;
    }

    private static PortfolioEntityRef ref(ProjectSpecificationId project) {
        return new PortfolioEntityRef(project, "requirement", DomainIdentity.generate());
    }

    private CrossProjectReference link(PortfolioEntityRef from, PortfolioEntityRef to, String relation) {
        return registry.addReference(portfolio, from, to, relation, OPENSPEC, Optional.empty(), Optional.empty());
    }
}
