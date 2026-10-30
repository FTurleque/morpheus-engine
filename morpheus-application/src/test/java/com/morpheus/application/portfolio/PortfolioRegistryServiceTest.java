package com.morpheus.application.portfolio;

import com.morpheus.application.store.EntityNotFoundException;
import com.morpheus.application.store.EntityStateException;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.portfolio.CrossProjectReference;
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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PortfolioRegistryServiceTest {
    private static final Instant T0 = Instant.parse("2026-10-30T08:00:00Z");
    private static final ProviderId OPENSPEC = new ProviderId("openspec");

    private final HandWrittenPortfolioStore store = new HandWrittenPortfolioStore();
    private final SteppingClock clock = new SteppingClock(T0);
    private final PortfolioRegistryService registry = new PortfolioRegistryService(store, clock);
    private final ProjectSpecificationId first = ProjectSpecificationId.generate();
    private final ProjectSpecificationId second = ProjectSpecificationId.generate();

    @Test
    void aNewPortfolioIsStoredWithTheClockInstantAsCreationAndUpdate() {
        PortfolioDefinition portfolio = registry.create(" Platform ");

        assertEquals("Platform", portfolio.name());
        assertEquals(T0, portfolio.createdAt());
        assertEquals(T0, portfolio.updatedAt());
        assertEquals(Optional.of(portfolio), store.findPortfolio(portfolio.id()));
        PortfolioDefinition defaulted = new PortfolioRegistryService(store).create("System clock");
        assertEquals(defaulted.createdAt(), defaulted.updatedAt());
    }

    @Test
    void registeringAProjectAgainObservesItWithoutForgettingWhenItWasFirstRegistered() {
        PortfolioId portfolio = registry.create("Platform").id();
        PortfolioMembership registered = registry.registerProject(portfolio, first, "Billing",
                Optional.of(SourceLocator.file("billing")), Optional.empty(), Set.of(OPENSPEC));
        clock.advance(Duration.ofMinutes(5));

        PortfolioMembership observed = registry.registerProject(portfolio, first, "Billing service",
                Optional.of(SourceLocator.file("billing")), Optional.of(SourceLocator.file("repo/billing")),
                Set.of(OPENSPEC));

        assertEquals(PortfolioMembershipStatus.ACTIVE, observed.status());
        assertEquals(registered.firstRegisteredAt(), observed.firstRegisteredAt());
        assertEquals(T0.plus(Duration.ofMinutes(5)), observed.lastObservedAt());
        assertEquals("Billing service", observed.displayName());
        assertEquals(List.of(observed), store.listMemberships(portfolio));
    }

    @Test
    void anUnknownPortfolioAndANonMemberAreRefusedByName() {
        PortfolioId unknown = PortfolioId.generate();
        EntityNotFoundException missing = assertThrows(EntityNotFoundException.class,
                () -> registry.registerProject(unknown, first, "Billing", Optional.empty(), Optional.empty(), Set.of()));
        assertEquals("unknown portfolio: " + unknown, missing.getMessage());

        PortfolioId portfolio = registry.create("Platform").id();
        EntityStateException notMember = assertThrows(EntityStateException.class,
                () -> registry.observeFreshness(portfolio, first, PortfolioFreshnessState.FRESH,
                        Optional.empty(), Optional.empty()));
        assertEquals("project is not a portfolio member: " + first, notMember.getMessage());
        EntityStateException missingNotMember = assertThrows(EntityStateException.class,
                () -> registry.markMissing(portfolio, first));
        assertEquals("project is not a portfolio member: " + first, missingNotMember.getMessage());
    }

    @Test
    void aMissingProjectKeepsItsIdentityAndItsLastKnownRevision() {
        PortfolioId portfolio = registry.create("Platform").id();
        registry.registerProject(portfolio, first, "Billing", Optional.empty(), Optional.empty(), Set.of(OPENSPEC));
        PortfolioFreshness fresh = registry.observeFreshness(portfolio, first, PortfolioFreshnessState.FRESH,
                Optional.of("rev-7"), Optional.of("Observed by the nightly scan"));
        assertEquals(T0, fresh.observedAt());
        clock.advance(Duration.ofHours(1));

        PortfolioMembership missing = registry.markMissing(portfolio, first);

        assertEquals(PortfolioMembershipStatus.MISSING, missing.status());
        assertEquals(first, missing.projectId());
        PortfolioFreshness recorded = store.findFreshness(portfolio, first).orElseThrow();
        assertEquals(PortfolioFreshnessState.MISSING, recorded.state());
        assertEquals(Optional.of("rev-7"), recorded.revision());
        assertEquals(T0.plus(Duration.ofHours(1)), recorded.observedAt());
        assertEquals(Optional.of("Project was not observed; identity and historical references were retained"),
                recorded.explanation());
    }

    @Test
    void theSameObservationOfAReferenceIsRecordedOnceAndADifferentEvidenceIsKeptBeside() {
        PortfolioId portfolio = registry.create("Platform").id();
        registry.registerProject(portfolio, first, "Billing", Optional.empty(), Optional.empty(), Set.of(OPENSPEC));
        registry.registerProject(portfolio, second, "Ledger", Optional.empty(), Optional.empty(), Set.of(OPENSPEC));
        PortfolioEntityRef source = new PortfolioEntityRef(first, "requirement", DomainIdentity.generate());
        PortfolioEntityRef target = new PortfolioEntityRef(second, "requirement", DomainIdentity.generate());
        Optional<SourceLocator> locator = Optional.of(SourceLocator.file("specs/billing.md"));
        EvidenceId evidence = EvidenceId.generate();

        CrossProjectReference recorded = registry.addReference(portfolio, source, target, "depends-on", OPENSPEC,
                locator, Optional.of(evidence));
        CrossProjectReference again = registry.addReference(portfolio, source, target, "depends-on", OPENSPEC,
                locator, Optional.of(evidence));
        CrossProjectReference otherEvidence = registry.addReference(portfolio, source, target, "depends-on",
                OPENSPEC, locator, Optional.of(EvidenceId.generate()));

        assertEquals(recorded, again);
        assertNotEquals(recorded.id(), otherEvidence.id());
        assertEquals(2, store.listReferences(portfolio).size());
        assertEquals(T0, recorded.observedAt());
    }

    @Test
    void aReferenceToAProjectOutsideThePortfolioIsRefused() {
        PortfolioId portfolio = registry.create("Platform").id();
        registry.registerProject(portfolio, first, "Billing", Optional.empty(), Optional.empty(), Set.of(OPENSPEC));
        PortfolioEntityRef source = new PortfolioEntityRef(first, "requirement", DomainIdentity.generate());
        PortfolioEntityRef outsider = new PortfolioEntityRef(second, "requirement", DomainIdentity.generate());

        EntityStateException failure = assertThrows(EntityStateException.class,
                () -> registry.addReference(portfolio, source, outsider, "depends-on", OPENSPEC,
                        Optional.empty(), Optional.empty()));

        assertEquals("project is not a portfolio member: " + second, failure.getMessage());
        assertTrue(store.listReferences(portfolio).isEmpty());
    }

    private static final class SteppingClock extends Clock {
        private Instant now;

        private SteppingClock(Instant start) {
            this.now = start;
        }

        private void advance(Duration step) {
            now = now.plus(step);
        }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { throw new UnsupportedOperationException(); }
        @Override public Instant instant() { return now; }
    }
}
