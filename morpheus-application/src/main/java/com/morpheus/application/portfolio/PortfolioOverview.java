package com.morpheus.application.portfolio;

import com.morpheus.domain.portfolio.PortfolioDefinition;
import com.morpheus.domain.portfolio.PortfolioFreshness;
import com.morpheus.domain.portfolio.PortfolioMembership;

import java.util.List;
import java.util.Objects;

public record PortfolioOverview(
        PortfolioDefinition portfolio,
        List<PortfolioMembership> memberships,
        List<PortfolioFreshness> freshness,
        List<PortfolioReferenceConflict> conflicts,
        int referenceCount) {
    public PortfolioOverview {
        Objects.requireNonNull(portfolio, "portfolio");
        memberships = Objects.requireNonNull(memberships, "memberships").stream().sorted().toList();
        freshness = Objects.requireNonNull(freshness, "freshness").stream().sorted().toList();
        conflicts = Objects.requireNonNull(conflicts, "conflicts").stream().sorted().toList();
        if (referenceCount < 0) {
            throw new IllegalArgumentException("referenceCount must be non-negative");
        }
    }
}
