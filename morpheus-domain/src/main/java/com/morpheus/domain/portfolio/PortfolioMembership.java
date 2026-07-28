package com.morpheus.domain.portfolio;

import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.source.SourceLocator;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Stable project membership. Technical locations and providers are observations, not identity keys. */
public record PortfolioMembership(
        PortfolioId portfolioId,
        ProjectSpecificationId projectId,
        String displayName,
        Optional<SourceLocator> workspace,
        Optional<SourceLocator> repository,
        Set<ProviderId> providers,
        PortfolioMembershipStatus status,
        Instant firstRegisteredAt,
        Instant lastObservedAt) implements Comparable<PortfolioMembership> {

    public PortfolioMembership {
        Objects.requireNonNull(portfolioId, "portfolioId");
        Objects.requireNonNull(projectId, "projectId");
        displayName = requireText(displayName, "displayName", 256);
        workspace = Objects.requireNonNull(workspace, "workspace");
        repository = Objects.requireNonNull(repository, "repository");
        providers = Set.copyOf(Objects.requireNonNull(providers, "providers"));
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(firstRegisteredAt, "firstRegisteredAt");
        Objects.requireNonNull(lastObservedAt, "lastObservedAt");
        if (lastObservedAt.isBefore(firstRegisteredAt)) {
            throw new IllegalArgumentException("lastObservedAt must not be before firstRegisteredAt");
        }
    }

    public PortfolioMembership observe(
            String name,
            Optional<SourceLocator> observedWorkspace,
            Optional<SourceLocator> observedRepository,
            Set<ProviderId> observedProviders,
            Instant observedAt) {
        Objects.requireNonNull(observedAt, "observedAt");
        if (observedAt.isBefore(lastObservedAt)) {
            throw new IllegalArgumentException("observedAt must not move backwards");
        }
        return new PortfolioMembership(
                portfolioId,
                projectId,
                name,
                observedWorkspace,
                observedRepository,
                observedProviders,
                PortfolioMembershipStatus.ACTIVE,
                firstRegisteredAt,
                observedAt);
    }

    public PortfolioMembership markMissing(Instant observedAt) {
        Objects.requireNonNull(observedAt, "observedAt");
        if (observedAt.isBefore(lastObservedAt)) {
            throw new IllegalArgumentException("observedAt must not move backwards");
        }
        return new PortfolioMembership(
                portfolioId, projectId, displayName, workspace, repository, providers,
                PortfolioMembershipStatus.MISSING, firstRegisteredAt, observedAt);
    }

    @Override
    public int compareTo(PortfolioMembership other) {
        int portfolio = portfolioId.compareTo(other.portfolioId);
        return portfolio != 0 ? portfolio : projectId.toString().compareTo(other.projectId.toString());
    }

    private static String requireText(String value, String name, int maximumLength) {
        Objects.requireNonNull(value, name);
        String trimmed = value.trim();
        if (trimmed.isEmpty() || trimmed.length() > maximumLength) {
            throw new IllegalArgumentException(name + " must contain 1.." + maximumLength + " characters");
        }
        return trimmed;
    }
}
