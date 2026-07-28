package com.morpheus.store.memory;

import com.morpheus.application.store.PortfolioStore;
import com.morpheus.domain.portfolio.CrossProjectReference;
import com.morpheus.domain.portfolio.CrossProjectReferenceId;
import com.morpheus.domain.portfolio.PortfolioDefinition;
import com.morpheus.domain.portfolio.PortfolioEntityRef;
import com.morpheus.domain.portfolio.PortfolioFreshness;
import com.morpheus.domain.portfolio.PortfolioId;
import com.morpheus.domain.portfolio.PortfolioMembership;
import com.morpheus.domain.project.ProjectSpecificationId;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

/** Thread-safe deterministic memory adapter for M23 portfolio state. */
public final class MemoryPortfolioStore implements PortfolioStore {
    private final Map<PortfolioId, PortfolioDefinition> portfolios = new TreeMap<>();
    private final Map<MembershipKey, PortfolioMembership> memberships = new TreeMap<>();
    private final Map<CrossProjectReferenceId, CrossProjectReference> references = new TreeMap<>();
    private final Map<MembershipKey, PortfolioFreshness> freshness = new TreeMap<>();

    @Override
    public synchronized void putPortfolio(PortfolioDefinition portfolio) {
        portfolios.put(portfolio.id(), portfolio);
    }

    @Override
    public synchronized Optional<PortfolioDefinition> findPortfolio(PortfolioId portfolioId) {
        return Optional.ofNullable(portfolios.get(portfolioId));
    }

    @Override
    public synchronized List<PortfolioDefinition> listPortfolios() {
        return List.copyOf(portfolios.values());
    }

    @Override
    public synchronized void putMembership(PortfolioMembership membership) {
        requirePortfolio(membership.portfolioId());
        memberships.put(new MembershipKey(membership.portfolioId(), membership.projectId()), membership);
    }

    @Override
    public synchronized Optional<PortfolioMembership> findMembership(
            PortfolioId portfolioId,
            ProjectSpecificationId projectId) {
        return Optional.ofNullable(memberships.get(new MembershipKey(portfolioId, projectId)));
    }

    @Override
    public synchronized List<PortfolioMembership> listMemberships(PortfolioId portfolioId) {
        requirePortfolio(portfolioId);
        return memberships.values().stream()
                .filter(item -> item.portfolioId().equals(portfolioId))
                .sorted()
                .toList();
    }

    @Override
    public synchronized void putReference(CrossProjectReference reference) {
        requirePortfolio(reference.portfolioId());
        requireMembership(reference.portfolioId(), reference.source().projectId());
        requireMembership(reference.portfolioId(), reference.target().projectId());
        references.put(reference.id(), reference);
    }

    @Override
    public synchronized Optional<CrossProjectReference> findReference(CrossProjectReferenceId referenceId) {
        return Optional.ofNullable(references.get(referenceId));
    }

    @Override
    public synchronized List<CrossProjectReference> listReferences(PortfolioId portfolioId) {
        requirePortfolio(portfolioId);
        return references.values().stream()
                .filter(item -> item.portfolioId().equals(portfolioId))
                .sorted()
                .toList();
    }

    @Override
    public synchronized List<CrossProjectReference> outgoing(PortfolioId portfolioId, PortfolioEntityRef source) {
        return listReferences(portfolioId).stream().filter(item -> item.source().equals(source)).toList();
    }

    @Override
    public synchronized List<CrossProjectReference> incoming(PortfolioId portfolioId, PortfolioEntityRef target) {
        return listReferences(portfolioId).stream().filter(item -> item.target().equals(target)).toList();
    }

    @Override
    public synchronized void putFreshness(PortfolioFreshness observation) {
        requireMembership(observation.portfolioId(), observation.projectId());
        MembershipKey key = new MembershipKey(observation.portfolioId(), observation.projectId());
        PortfolioFreshness previous = freshness.get(key);
        if (previous != null && observation.observedAt().isBefore(previous.observedAt())) {
            throw new IllegalArgumentException("freshness observation must not move backwards");
        }
        freshness.put(key, observation);
    }

    @Override
    public synchronized Optional<PortfolioFreshness> findFreshness(
            PortfolioId portfolioId,
            ProjectSpecificationId projectId) {
        return Optional.ofNullable(freshness.get(new MembershipKey(portfolioId, projectId)));
    }

    @Override
    public synchronized List<PortfolioFreshness> listFreshness(PortfolioId portfolioId) {
        requirePortfolio(portfolioId);
        return freshness.values().stream()
                .filter(item -> item.portfolioId().equals(portfolioId))
                .sorted()
                .toList();
    }

    private void requirePortfolio(PortfolioId portfolioId) {
        if (!portfolios.containsKey(portfolioId)) {
            throw new IllegalArgumentException("unknown portfolio: " + portfolioId);
        }
    }

    private void requireMembership(PortfolioId portfolioId, ProjectSpecificationId projectId) {
        if (!memberships.containsKey(new MembershipKey(portfolioId, projectId))) {
            throw new IllegalArgumentException("project is not a portfolio member: " + projectId);
        }
    }

    private record MembershipKey(PortfolioId portfolioId, ProjectSpecificationId projectId)
            implements Comparable<MembershipKey> {
        @Override
        public int compareTo(MembershipKey other) {
            int portfolio = portfolioId.compareTo(other.portfolioId);
            return portfolio != 0 ? portfolio : projectId.toString().compareTo(other.projectId.toString());
        }
    }
}
