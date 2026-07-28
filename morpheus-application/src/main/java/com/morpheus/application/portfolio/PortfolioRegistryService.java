package com.morpheus.application.portfolio;

import com.morpheus.application.store.PortfolioStore;
import com.morpheus.domain.evidence.EvidenceId;
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

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Explicit, non-destructive registry and observation service for M23 portfolios. */
public final class PortfolioRegistryService {
    private final PortfolioStore store;
    private final Clock clock;

    public PortfolioRegistryService(PortfolioStore store) {
        this(store, Clock.systemUTC());
    }

    public PortfolioRegistryService(PortfolioStore store, Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public PortfolioDefinition create(String name) {
        Instant now = clock.instant();
        PortfolioDefinition portfolio = new PortfolioDefinition(PortfolioId.generate(), name, now, now);
        store.putPortfolio(portfolio);
        return portfolio;
    }

    public PortfolioMembership registerProject(
            PortfolioId portfolioId,
            ProjectSpecificationId projectId,
            String displayName,
            Optional<SourceLocator> workspace,
            Optional<SourceLocator> repository,
            Set<ProviderId> providers) {
        requirePortfolio(portfolioId);
        Instant now = clock.instant();
        Optional<PortfolioMembership> existing = store.findMembership(portfolioId, projectId);
        PortfolioMembership membership = existing
                .map(item -> item.observe(displayName, workspace, repository, providers, now))
                .orElseGet(() -> new PortfolioMembership(
                        portfolioId,
                        projectId,
                        displayName,
                        workspace,
                        repository,
                        providers,
                        PortfolioMembershipStatus.ACTIVE,
                        now,
                        now));
        store.putMembership(membership);
        return membership;
    }

    public PortfolioMembership markMissing(PortfolioId portfolioId, ProjectSpecificationId projectId) {
        PortfolioMembership existing = store.findMembership(portfolioId, projectId)
                .orElseThrow(() -> new IllegalArgumentException("project is not a portfolio member: " + projectId));
        PortfolioMembership missing = existing.markMissing(clock.instant());
        store.putMembership(missing);
        store.putFreshness(new PortfolioFreshness(
                portfolioId,
                projectId,
                PortfolioFreshnessState.MISSING,
                missing.lastObservedAt(),
                store.findFreshness(portfolioId, projectId).flatMap(PortfolioFreshness::revision),
                Optional.of("Project was not observed; identity and historical references were retained")));
        return missing;
    }

    public PortfolioFreshness observeFreshness(
            PortfolioId portfolioId,
            ProjectSpecificationId projectId,
            PortfolioFreshnessState state,
            Optional<String> revision,
            Optional<String> explanation) {
        requireMembership(portfolioId, projectId);
        PortfolioFreshness observation = new PortfolioFreshness(
                portfolioId, projectId, state, clock.instant(), revision, explanation);
        store.putFreshness(observation);
        return observation;
    }

    public CrossProjectReference addReference(
            PortfolioId portfolioId,
            PortfolioEntityRef source,
            PortfolioEntityRef target,
            String relation,
            ProviderId providerId,
            Optional<SourceLocator> sourceLocator,
            Optional<EvidenceId> evidenceId) {
        requireMembership(portfolioId, source.projectId());
        requireMembership(portfolioId, target.projectId());
        Instant now = clock.instant();
        CrossProjectReference candidate = new CrossProjectReference(
                CrossProjectReferenceId.generate(),
                portfolioId,
                source,
                target,
                relation,
                providerId,
                sourceLocator,
                evidenceId,
                now);
        Optional<CrossProjectReference> existing = store.listReferences(portfolioId).stream()
                .filter(item -> item.semanticKey().equals(candidate.semanticKey()))
                .filter(item -> item.providerId().equals(providerId))
                .filter(item -> item.sourceLocator().equals(sourceLocator))
                .filter(item -> item.evidenceId().equals(evidenceId))
                .findFirst();
        if (existing.isPresent()) {
            return existing.orElseThrow();
        }
        store.putReference(candidate);
        return candidate;
    }

    private PortfolioDefinition requirePortfolio(PortfolioId portfolioId) {
        return store.findPortfolio(Objects.requireNonNull(portfolioId, "portfolioId"))
                .orElseThrow(() -> new IllegalArgumentException("unknown portfolio: " + portfolioId));
    }

    private PortfolioMembership requireMembership(PortfolioId portfolioId, ProjectSpecificationId projectId) {
        requirePortfolio(portfolioId);
        return store.findMembership(portfolioId, projectId)
                .orElseThrow(() -> new IllegalArgumentException("project is not a portfolio member: " + projectId));
    }
}
