package com.morpheus.application.portfolio;

import com.morpheus.application.store.PortfolioStore;
import com.morpheus.domain.portfolio.CrossProjectReference;
import com.morpheus.domain.portfolio.CrossProjectReferenceId;
import com.morpheus.domain.portfolio.PortfolioDefinition;
import com.morpheus.domain.portfolio.PortfolioEntityRef;
import com.morpheus.domain.portfolio.PortfolioFreshness;
import com.morpheus.domain.portfolio.PortfolioId;
import com.morpheus.domain.portfolio.PortfolioMembership;
import com.morpheus.domain.project.ProjectSpecificationId;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A portfolio port written by hand, because the application module cannot depend on the memory store. Every list is
 * returned in insertion order so that the services, not the store, are the ones that order what they answer.
 */
final class HandWrittenPortfolioStore implements PortfolioStore {
    private final List<PortfolioDefinition> portfolios = new ArrayList<>();
    private final List<PortfolioMembership> memberships = new ArrayList<>();
    private final List<CrossProjectReference> references = new ArrayList<>();
    private final List<PortfolioFreshness> freshness = new ArrayList<>();

    @Override
    public void putPortfolio(PortfolioDefinition portfolio) {
        portfolios.removeIf(item -> item.id().equals(portfolio.id()));
        portfolios.add(portfolio);
    }

    @Override
    public Optional<PortfolioDefinition> findPortfolio(PortfolioId portfolioId) {
        return portfolios.stream().filter(item -> item.id().equals(portfolioId)).findFirst();
    }

    @Override
    public List<PortfolioDefinition> listPortfolios() {
        return List.copyOf(portfolios);
    }

    @Override
    public void putMembership(PortfolioMembership membership) {
        memberships.removeIf(item -> item.portfolioId().equals(membership.portfolioId())
                && item.projectId().equals(membership.projectId()));
        memberships.add(membership);
    }

    @Override
    public Optional<PortfolioMembership> findMembership(PortfolioId portfolioId, ProjectSpecificationId projectId) {
        return memberships.stream()
                .filter(item -> item.portfolioId().equals(portfolioId) && item.projectId().equals(projectId))
                .findFirst();
    }

    @Override
    public List<PortfolioMembership> listMemberships(PortfolioId portfolioId) {
        return memberships.stream().filter(item -> item.portfolioId().equals(portfolioId)).toList();
    }

    @Override
    public void putReference(CrossProjectReference reference) {
        references.add(reference);
    }

    @Override
    public Optional<CrossProjectReference> findReference(CrossProjectReferenceId referenceId) {
        return references.stream().filter(item -> item.id().equals(referenceId)).findFirst();
    }

    @Override
    public List<CrossProjectReference> listReferences(PortfolioId portfolioId) {
        return references.stream().filter(item -> item.portfolioId().equals(portfolioId)).toList();
    }

    @Override
    public List<CrossProjectReference> outgoing(PortfolioId portfolioId, PortfolioEntityRef source) {
        return listReferences(portfolioId).stream().filter(item -> item.source().equals(source)).toList();
    }

    @Override
    public List<CrossProjectReference> incoming(PortfolioId portfolioId, PortfolioEntityRef target) {
        return listReferences(portfolioId).stream().filter(item -> item.target().equals(target)).toList();
    }

    @Override
    public void putFreshness(PortfolioFreshness observation) {
        freshness.removeIf(item -> item.portfolioId().equals(observation.portfolioId())
                && item.projectId().equals(observation.projectId()));
        freshness.add(observation);
    }

    @Override
    public Optional<PortfolioFreshness> findFreshness(PortfolioId portfolioId, ProjectSpecificationId projectId) {
        return freshness.stream()
                .filter(item -> item.portfolioId().equals(portfolioId) && item.projectId().equals(projectId))
                .findFirst();
    }

    @Override
    public List<PortfolioFreshness> listFreshness(PortfolioId portfolioId) {
        return freshness.stream().filter(item -> item.portfolioId().equals(portfolioId)).toList();
    }
}
