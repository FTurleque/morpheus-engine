package com.morpheus.store.sqlite;

import com.morpheus.application.store.EntityStateException;
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
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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

    /**
     * A provider set holding an empty segment can only come from an identifier written with a doubled line break,
     * which {@link ProviderId} refuses; such a row is refused by name rather than read back as other providers.
     */
    @Test
    void aStoredProviderSetWithAnEmptySegmentIsRefusedRatherThanRecomposed() throws SQLException {
        Path database = tempDir.resolve("legacy-providers.db");
        PortfolioId portfolioId = PortfolioId.generate();
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        try (SqlitePortfolioStore store = new SqlitePortfolioStore(database)) {
            store.putPortfolio(portfolio(portfolioId, "platform"));
            store.putMembership(membership(portfolioId, projectId, "checkout"));
        }
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath());
                PreparedStatement update = connection.prepareStatement(
                        "UPDATE portfolio_memberships SET provider_ids = ?")) {
            update.setString(1, "openspec\n\nmarkdown");
            assertEquals(1, update.executeUpdate());
        }

        try (SqlitePortfolioStore reopened = new SqlitePortfolioStore(database)) {
            IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class,
                    () -> reopened.findMembership(portfolioId, projectId));
            assertEquals("provider id must not be blank", refusal.getMessage());
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
            CrossProjectReference written = new CrossProjectReference(
                    referenceId,
                    portfolioId,
                    source,
                    target,
                    "depends-on",
                    new ProviderId("openspec"),
                    Optional.of(SourceLocator.file("workspace/source/spec.md")),
                    Optional.of(evidenceId),
                    OBSERVED);
            store.putReference(written);

            CrossProjectReference stored = store.findReference(referenceId).orElseThrow();
            assertEquals(written, stored);
            assertEquals(List.of(written), store.listReferences(portfolioId));
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

            assertEquals("project is not a portfolio member: " + stranger,
                    assertThrows(EntityStateException.class, () -> store.putReference(dangling)).getMessage());

            CrossProjectReference fromAStranger = new CrossProjectReference(
                    CrossProjectReferenceId.generate(),
                    portfolioId,
                    new PortfolioEntityRef(stranger, "requirement", DomainIdentity.generate()),
                    new PortfolioEntityRef(member, "requirement", DomainIdentity.generate()),
                    "depends-on",
                    new ProviderId("openspec"),
                    Optional.empty(),
                    Optional.empty(),
                    OBSERVED);
            assertEquals("project is not a portfolio member: " + stranger,
                    assertThrows(EntityStateException.class, () -> store.putReference(fromAStranger)).getMessage());
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
            assertEquals(new PortfolioFreshness(
                    portfolioId, projectId, PortfolioFreshnessState.STALE, OBSERVED.plusSeconds(60),
                    Optional.of("revision-2"), Optional.of("workspace moved")), latest);
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

        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        PortfolioEntityRef entity = new PortfolioEntityRef(projectId, "requirement", DomainIdentity.generate());
        PortfolioEntityRef other = new PortfolioEntityRef(
                ProjectSpecificationId.generate(), "requirement", DomainIdentity.generate());
        List<Executable> operations = List.of(
                () -> store.putPortfolio(portfolio(portfolioId, "renamed")),
                () -> store.findPortfolio(portfolioId),
                store::listPortfolios,
                () -> store.putMembership(membership(portfolioId, projectId, "checkout")),
                () -> store.findMembership(portfolioId, projectId),
                () -> store.listMemberships(portfolioId),
                () -> store.putReference(new CrossProjectReference(
                        CrossProjectReferenceId.generate(), portfolioId, entity, other, "depends-on",
                        new ProviderId("openspec"), Optional.empty(), Optional.empty(), OBSERVED)),
                () -> store.findReference(CrossProjectReferenceId.generate()),
                () -> store.listReferences(portfolioId),
                () -> store.outgoing(portfolioId, entity),
                () -> store.incoming(portfolioId, entity),
                () -> store.putFreshness(new PortfolioFreshness(
                        portfolioId, projectId, PortfolioFreshnessState.FRESH, OBSERVED, Optional.empty(), Optional.empty())),
                () -> store.findFreshness(portfolioId, projectId),
                () -> store.listFreshness(portfolioId));
        // The JDBC path of a closed connection throws the same type: only the message and the missing cause tell
        // the guard apart from a failure further down.
        for (Executable operation : operations) {
            KnowledgeStoreException refusal = assertThrows(KnowledgeStoreException.class, operation);
            assertEquals("SQLite portfolio store is closed", refusal.getMessage());
            assertNull(refusal.getCause());
        }
    }

    @Test
    void aMembershipWithEveryObservationRoundTripsUnchanged() {
        Path database = tempDir.resolve("full-membership.db");
        PortfolioId portfolioId = PortfolioId.generate();
        PortfolioMembership written = new PortfolioMembership(
                portfolioId,
                ProjectSpecificationId.generate(),
                "checkout",
                Optional.of(SourceLocator.file("workspace/checkout")),
                Optional.of(new SourceLocator("git", "https://example.test/checkout.git")),
                Set.of(new ProviderId("openspec"), new ProviderId("structured-markdown")),
                PortfolioMembershipStatus.ACTIVE,
                CREATED,
                OBSERVED);

        try (SqlitePortfolioStore store = new SqlitePortfolioStore(database)) {
            store.putPortfolio(portfolio(portfolioId, "platform"));
            store.putMembership(written);
        }

        try (SqlitePortfolioStore reopened = new SqlitePortfolioStore(database)) {
            assertEquals(Optional.of(written), reopened.findMembership(portfolioId, written.projectId()));
        }
    }

    @Test
    void freshnessIsRefusedForAProjectThatIsNotAMember() {
        Path database = tempDir.resolve("freshness-guard.db");
        PortfolioId portfolioId = PortfolioId.generate();
        ProjectSpecificationId stranger = ProjectSpecificationId.generate();

        try (SqlitePortfolioStore store = new SqlitePortfolioStore(database)) {
            store.putPortfolio(portfolio(portfolioId, "platform"));

            EntityStateException refusal = assertThrows(EntityStateException.class, () -> store.putFreshness(
                    new PortfolioFreshness(portfolioId, stranger, PortfolioFreshnessState.FRESH, OBSERVED,
                            Optional.empty(), Optional.empty())));
            assertEquals("project is not a portfolio member: " + stranger, refusal.getMessage());
            assertEquals(Optional.empty(), store.findFreshness(portfolioId, stranger));
        }
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
