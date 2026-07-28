package com.morpheus.application.store;

import com.morpheus.domain.portfolio.CrossProjectReference;
import com.morpheus.domain.portfolio.PortfolioDefinition;
import com.morpheus.domain.portfolio.PortfolioEntityRef;
import com.morpheus.domain.portfolio.PortfolioFreshness;
import com.morpheus.domain.portfolio.PortfolioId;
import com.morpheus.domain.portfolio.PortfolioMembership;
import com.morpheus.domain.project.ProjectSpecificationId;

import java.util.List;
import java.util.Optional;

/** Technology-neutral persistence boundary for M23 portfolio intelligence. */
public interface PortfolioStore {
    void putPortfolio(PortfolioDefinition portfolio);

    Optional<PortfolioDefinition> findPortfolio(PortfolioId portfolioId);

    List<PortfolioDefinition> listPortfolios();

    void putMembership(PortfolioMembership membership);

    Optional<PortfolioMembership> findMembership(PortfolioId portfolioId, ProjectSpecificationId projectId);

    List<PortfolioMembership> listMemberships(PortfolioId portfolioId);

    void putReference(CrossProjectReference reference);

    Optional<CrossProjectReference> findReference(com.morpheus.domain.portfolio.CrossProjectReferenceId referenceId);

    List<CrossProjectReference> listReferences(PortfolioId portfolioId);

    List<CrossProjectReference> outgoing(PortfolioId portfolioId, PortfolioEntityRef source);

    List<CrossProjectReference> incoming(PortfolioId portfolioId, PortfolioEntityRef target);

    void putFreshness(PortfolioFreshness freshness);

    Optional<PortfolioFreshness> findFreshness(PortfolioId portfolioId, ProjectSpecificationId projectId);

    List<PortfolioFreshness> listFreshness(PortfolioId portfolioId);
}
