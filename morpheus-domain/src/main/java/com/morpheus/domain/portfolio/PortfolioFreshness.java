package com.morpheus.domain.portfolio;

import com.morpheus.domain.project.ProjectSpecificationId;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Incremental freshness observation for one project in a portfolio. */
public record PortfolioFreshness(
        PortfolioId portfolioId,
        ProjectSpecificationId projectId,
        PortfolioFreshnessState state,
        Instant observedAt,
        Optional<String> revision,
        Optional<String> explanation) implements Comparable<PortfolioFreshness> {

    public PortfolioFreshness {
        Objects.requireNonNull(portfolioId, "portfolioId");
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(observedAt, "observedAt");
        revision = normalize(revision, "revision", 512);
        explanation = normalize(explanation, "explanation", 1024);
    }

    @Override
    public int compareTo(PortfolioFreshness other) {
        int portfolio = portfolioId.compareTo(other.portfolioId);
        return portfolio != 0 ? portfolio : projectId.toString().compareTo(other.projectId.toString());
    }

    private static Optional<String> normalize(Optional<String> value, String name, int max) {
        Objects.requireNonNull(value, name);
        return value.map(item -> {
            String trimmed = item.trim();
            if (trimmed.isEmpty() || trimmed.length() > max) {
                throw new IllegalArgumentException(name + " must contain 1.." + max + " characters when present");
            }
            return trimmed;
        });
    }
}
