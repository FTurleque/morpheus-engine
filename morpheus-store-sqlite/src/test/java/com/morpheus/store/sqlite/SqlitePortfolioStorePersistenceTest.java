package com.morpheus.store.sqlite;

import com.morpheus.application.store.KnowledgeStoreException;
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
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Durable portfolio state: what survives a reopen, and what the store refuses to record at all.
 *
 * <p>Portfolio intelligence is read across projects, so its persistence layer decides two things a reader cannot
 * recover afterwards: whether an observation was kept faithfully, and whether an ordering is stable. Both are
 * asserted here against a real database file rather than against an in-memory stand-in, because the SQL is the
 * part that can silently lose an optional column or return rows in insertion order.</p>
 */
class SqlitePortfolioStorePersistenceTest {
    private static final Instant CREATED = Instant.parse("2026-09-01T08:00:00Z");
    private static final Instant OBSERVED = Instant.parse("2026-09-02T08:00:00Z");

    @TempDir
    Path tempDir;

    @Test
    void aPortfolioAndItsMembershipsSurviveAReopen() {
        Path database = tempDir.resolve("portfolio.db");
        PortfolioId portfolioId = PortfolioId.generate();
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();

        try (SqlitePortfolioStore store = new SqlitePortfolioStore(database)) {
            store.putPortfolio(portfolio(portfolioId, "platform"));
            store.putMembership(membership(portfolioId, projectId, "checkout"));
        }

        try (SqlitePortfolioStore reopened = new SqlitePortfolioStore(database)) {
            PortfolioDefinition definition = reopened.findPortfolio(portfolioId).orElseThrow();
            assertEquals("platform", definition.name());
            assertEquals(CREATED, definition.createdAt());

            PortfolioMembership member = reopened.findMembership(portfolioId, projectId).orElseThrow();
            assertEquals("checkout", member.displayName());
            assertEquals(PortfolioMembershipStatus.ACTIVE, member.status());
            assertEquals(Optional.of(SourceLocator.file("workspace/checkout")), member.workspace());
            assertEquals(Set.of(new ProviderId("openspec")), member.providers());
        }
    }

    /** A membership with no workspace, no repository and no provider is a valid observation, not a broken row. */
    @Test
    void anObservationWithNothingObservedRoundTrips() {
        Path database = tempDir.resolve("sparse.db");
        PortfolioId portfolioId = PortfolioId.generate();
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();

        try (SqlitePortfolioStore store = new SqlitePortfolioStore(database)) {
            store.putPortfolio(portfolio(portfolioId, "platform"));
            store.putMembership(new PortfolioMembership(
                    portfolioId,
                    projectId,
                    "unseen",
                    Optional.empty(),
                    Optional.empty(),
                    Set.of(),
                    PortfolioMembershipStatus.MISSING,
                    CREATED,
                    OBSERVED));

            PortfolioMembership stored = store.findMembership(portfolioId, projectId).orElseThrow();
            assertEquals(Optional.empty(), stored.workspace());
            assertEquals(Optional.empty(), stored.repository());
            assertEquals(Set.of(), stored.providers());
            assertEquals(PortfolioMembershipStatus.MISSING, stored.status());
        }
    }

    @Test
    void replacingAMembershipKeepsItsFirstRegistrationRatherThanAddingASecondRow() {
        Path database = tempDir.resolve("replace.db");
        PortfolioId portfolioId = PortfolioId.generate();
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();

        try (SqlitePortfolioStore store = new SqlitePortfolioStore(database)) {
            store.putPortfolio(portfolio(portfolioId, "platform"));
            PortfolioMembership first = membership(portfolioId, projectId, "checkout");
            store.putMembership(first);
            store.putMembership(first.observe(
                    "checkout-service",
                    Optional.of(SourceLocator.file("workspace/checkout-service")),
                    Optional.empty(),
                    Set.of(new ProviderId("markdown")),
                    OBSERVED.plusSeconds(60)));

            List<PortfolioMembership> memberships = store.listMemberships(portfolioId);
            assertEquals(1, memberships.size());
            assertEquals("checkout-service", memberships.get(0).displayName());
            assertEquals(CREATED, memberships.get(0).firstRegisteredAt());
        }
    }

    @Test
    void listingsAreOrderedByIdentityRatherThanByInsertion() {
        Path database = tempDir.resolve("ordering.db");
        PortfolioId first = PortfolioId.generate();
        PortfolioId second = PortfolioId.generate();

        try (SqlitePortfolioStore store = new SqlitePortfolioStore(database)) {
            store.putPortfolio(portfolio(second, "second"));
            store.putPortfolio(portfolio(first, "first"));

            List<PortfolioDefinition> listed = store.listPortfolios();
            assertEquals(2, listed.size());
            assertTrue(listed.get(0).id().compareTo(listed.get(1).id()) < 0,
                    "a cross-project listing must be ordered by identity, not by write order");
        }
    }

    @Test
    void referencesAreReadableBothWaysAndKeepTheirEvidence() {
        Path database = tempDir.resolve("references.db");
        PortfolioId portfolioId = PortfolioId.generate();
        ProjectSpecificationId sourceProject = ProjectSpecificationId.generate();
        ProjectSpecificationId targetProject = ProjectSpecificationId.generate();
        PortfolioEntityRef source = new PortfolioEntityRef(sourceProject, "requirement", DomainIdentity.generate());
        PortfolioEntityRef target = new PortfolioEntityRef(targetProject, "requirement", DomainIdentity.generate());
        CrossProjectReferenceId referenceId = CrossProjectReferenceId.generate();
        EvidenceId evidenceId = EvidenceId.generate();

        try (SqlitePortfolioStore store = new SqlitePortfolioStore(database)) {
            store.putPortfolio(portfolio(portfolioId, "platform"));
            store.putMembership(membership(portfolioId, sourceProject, "source"));
            store.putMembership(membership(portfolioId, targetProject, "target"));
            store.putReference(new CrossProjectReference(
                    referenceId,
                    portfolioId,
                    source,
                    target,
                    "depends-on",
                    new ProviderId("openspec"),
                    Optional.of(SourceLocator.file("workspace/source/spec.md")),
                    Optional.of(evidenceId),
                    OBSERVED));

            CrossProjectReference stored = store.findReference(referenceId).orElseThrow();
            assertEquals("depends-on", stored.relation());
            assertEquals(Optional.of(evidenceId), stored.evidenceId());
            assertEquals(List.of(stored), store.outgoing(portfolioId, source));
            assertEquals(List.of(stored), store.incoming(portfolioId, target));
            assertEquals(List.of(), store.outgoing(portfolioId, target));
            assertEquals(List.of(), store.incoming(portfolioId, source));
        }
    }

    @Test
    void aReferenceIsRefusedUnlessBothEndsAreMembers() {
        Path database = tempDir.resolve("reference-guard.db");
        PortfolioId portfolioId = PortfolioId.generate();
        ProjectSpecificationId member = ProjectSpecificationId.generate();
        ProjectSpecificationId stranger = ProjectSpecificationId.generate();

        try (SqlitePortfolioStore store = new SqlitePortfolioStore(database)) {
            store.putPortfolio(portfolio(portfolioId, "platform"));
            store.putMembership(membership(portfolioId, member, "member"));

            CrossProjectReference dangling = new CrossProjectReference(
                    CrossProjectReferenceId.generate(),
                    portfolioId,
                    new PortfolioEntityRef(member, "requirement", DomainIdentity.generate()),
                    new PortfolioEntityRef(stranger, "requirement", DomainIdentity.generate()),
                    "depends-on",
                    new ProviderId("openspec"),
                    Optional.empty(),
                    Optional.empty(),
                    OBSERVED);

            assertTrue(assertThrows(IllegalArgumentException.class, () -> store.putReference(dangling))
                    .getMessage().contains("project is not a portfolio member"));
            assertEquals(List.of(), store.listReferences(portfolioId));
        }
    }

    @Test
    void freshnessKeepsTheLatestObservationAndRefusesToMoveBackwards() {
        Path database = tempDir.resolve("freshness.db");
        PortfolioId portfolioId = PortfolioId.generate();
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();

        try (SqlitePortfolioStore store = new SqlitePortfolioStore(database)) {
            store.putPortfolio(portfolio(portfolioId, "platform"));
            store.putMembership(membership(portfolioId, projectId, "checkout"));
            store.putFreshness(new PortfolioFreshness(
                    portfolioId, projectId, PortfolioFreshnessState.FRESH, OBSERVED,
                    Optional.of("revision-1"), Optional.empty()));
            store.putFreshness(new PortfolioFreshness(
                    portfolioId, projectId, PortfolioFreshnessState.STALE, OBSERVED.plusSeconds(60),
                    Optional.of("revision-2"), Optional.of("workspace moved")));

            PortfolioFreshness latest = store.findFreshness(portfolioId, projectId).orElseThrow();
            assertEquals(PortfolioFreshnessState.STALE, latest.state());
            assertEquals(Optional.of("workspace moved"), latest.explanation());
            assertEquals(List.of(latest), store.listFreshness(portfolioId));

            PortfolioFreshness stale = new PortfolioFreshness(
                    portfolioId, projectId, PortfolioFreshnessState.FRESH, OBSERVED,
                    Optional.empty(), Optional.empty());
            assertTrue(assertThrows(IllegalArgumentException.class, () -> store.putFreshness(stale))
                    .getMessage().contains("must not move backwards"));
            assertEquals(PortfolioFreshnessState.STALE,
                    store.findFreshness(portfolioId, projectId).orElseThrow().state());
        }
    }

    @Test
    void anUnknownPortfolioIsRefusedRatherThanCreatedImplicitly() {
        Path database = tempDir.resolve("unknown.db");
        PortfolioId unknown = PortfolioId.generate();
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();

        try (SqlitePortfolioStore store = new SqlitePortfolioStore(database)) {
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
        }
    }

    @Test
    void aClosedStoreRefusesEveryOperationInsteadOfFailingLater() {
        Path database = tempDir.resolve("closed.db");
        PortfolioId portfolioId = PortfolioId.generate();
        SqlitePortfolioStore store = new SqlitePortfolioStore(database);
        store.putPortfolio(portfolio(portfolioId, "platform"));
        store.close();

        assertThrows(KnowledgeStoreException.class, () -> store.putPortfolio(portfolio(portfolioId, "renamed")));
        assertThrows(KnowledgeStoreException.class, () -> store.findPortfolio(portfolioId));
        assertThrows(KnowledgeStoreException.class, store::listPortfolios);
    }

    /** Closing twice is how a try-with-resources nested in a shutdown path behaves; it must stay harmless. */
    @Test
    void closingTwiceIsHarmless() {
        SqlitePortfolioStore store = new SqlitePortfolioStore(tempDir.resolve("double-close.db"));
        store.close();
        store.close();
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
}
