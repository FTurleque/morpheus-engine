package com.morpheus.domain.portfolio;

/** Presence is observational. MISSING never deletes the stable project identity. */
public enum PortfolioMembershipStatus {
    ACTIVE,
    MISSING,
    DISABLED
}
