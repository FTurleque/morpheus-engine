package com.morpheus.application.query.dsl;

/** Explicit provider-neutral scope for an M24 query. */
public sealed interface QueryScope permits ProjectQueryScope, PortfolioQueryScope {
}
