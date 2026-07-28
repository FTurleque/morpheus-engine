package com.morpheus.application.query.dsl;

import com.morpheus.domain.portfolio.PortfolioId;

import java.util.Objects;

/** Query scope bound to one MORPHEUS portfolio identity. */
public record PortfolioQueryScope(PortfolioId portfolioId) implements QueryScope {
    public PortfolioQueryScope {
        Objects.requireNonNull(portfolioId, "portfolioId");
    }
}
