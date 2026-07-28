package com.morpheus.application.portfolio;

import com.morpheus.application.store.PortfolioStore;
import com.morpheus.domain.portfolio.CrossProjectReference;
import com.morpheus.domain.portfolio.PortfolioDefinition;
import com.morpheus.domain.portfolio.PortfolioId;
import com.morpheus.domain.portfolio.PortfolioMembership;
import com.morpheus.domain.project.ProjectSpecificationId;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Deterministic project-scoped and portfolio-scoped M23 query facade. */
public final class PortfolioQueryService {
    public static final int MAX_PAGE_SIZE = 500;

    private final PortfolioStore store;

    public PortfolioQueryService(PortfolioStore store) {
        this.store = Objects.requireNonNull(store, "store");
    }

    public List<PortfolioDefinition> listPortfolios(int offset, int limit) {
        return page(store.listPortfolios(), offset, limit);
    }

    public PortfolioOverview overview(PortfolioId portfolioId) {
        PortfolioDefinition portfolio = requirePortfolio(portfolioId);
        List<CrossProjectReference> references = store.listReferences(portfolioId);
        return new PortfolioOverview(
                portfolio,
                store.listMemberships(portfolioId),
                store.listFreshness(portfolioId),
                conflicts(references),
                references.size());
    }

    public List<PortfolioMembership> memberships(PortfolioId portfolioId, int offset, int limit) {
        requirePortfolio(portfolioId);
        return page(store.listMemberships(portfolioId), offset, limit);
    }

    public List<CrossProjectReference> references(PortfolioId portfolioId, int offset, int limit) {
        requirePortfolio(portfolioId);
        return page(store.listReferences(portfolioId), offset, limit);
    }

    public List<CrossProjectReference> projectReferences(
            PortfolioId portfolioId,
            ProjectSpecificationId projectId,
            int offset,
            int limit) {
        requirePortfolio(portfolioId);
        if (store.findMembership(portfolioId, projectId).isEmpty()) {
            throw new IllegalArgumentException("project is not a portfolio member: " + projectId);
        }
        List<CrossProjectReference> scoped = store.listReferences(portfolioId).stream()
                .filter(item -> item.source().projectId().equals(projectId)
                        || item.target().projectId().equals(projectId))
                .sorted()
                .toList();
        return page(scoped, offset, limit);
    }

    public List<PortfolioReferenceConflict> conflicts(PortfolioId portfolioId) {
        requirePortfolio(portfolioId);
        return conflicts(store.listReferences(portfolioId));
    }

    private List<PortfolioReferenceConflict> conflicts(List<CrossProjectReference> references) {
        Map<String, List<CrossProjectReference>> grouped = new LinkedHashMap<>();
        references.stream().sorted().forEach(reference -> grouped
                .computeIfAbsent(sourceRelationKey(reference), ignored -> new ArrayList<>())
                .add(reference));
        List<PortfolioReferenceConflict> conflicts = new ArrayList<>();
        for (List<CrossProjectReference> observations : grouped.values()) {
            if (observations.stream().map(CrossProjectReference::target).distinct().count() > 1) {
                CrossProjectReference first = observations.getFirst();
                conflicts.add(new PortfolioReferenceConflict(first.source(), first.relation(), observations));
            }
        }
        return conflicts.stream().sorted().toList();
    }

    private String sourceRelationKey(CrossProjectReference reference) {
        return reference.source().projectId() + "|" + reference.source().entityType() + "|"
                + reference.source().entityId() + "|" + reference.relation();
    }

    private PortfolioDefinition requirePortfolio(PortfolioId portfolioId) {
        return store.findPortfolio(Objects.requireNonNull(portfolioId, "portfolioId"))
                .orElseThrow(() -> new IllegalArgumentException("unknown portfolio: " + portfolioId));
    }

    private <T> List<T> page(List<T> source, int offset, int limit) {
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be non-negative");
        }
        if (limit <= 0 || limit > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("limit must be between 1 and " + MAX_PAGE_SIZE);
        }
        if (offset >= source.size()) {
            return List.of();
        }
        return List.copyOf(source.subList(offset, Math.min(source.size(), offset + limit)));
    }
}
