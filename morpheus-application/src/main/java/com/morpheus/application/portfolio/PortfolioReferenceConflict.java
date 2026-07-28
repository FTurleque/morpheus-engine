package com.morpheus.application.portfolio;

import com.morpheus.domain.portfolio.CrossProjectReference;
import com.morpheus.domain.portfolio.PortfolioEntityRef;

import java.util.List;
import java.util.Objects;

/** Multiple preserved observations for one source/relation that disagree on target. */
public record PortfolioReferenceConflict(
        PortfolioEntityRef source,
        String relation,
        List<CrossProjectReference> observations) implements Comparable<PortfolioReferenceConflict> {
    public PortfolioReferenceConflict {
        Objects.requireNonNull(source, "source");
        relation = Objects.requireNonNull(relation, "relation").trim();
        observations = Objects.requireNonNull(observations, "observations").stream().sorted().toList();
        if (relation.isEmpty() || observations.size() < 2) {
            throw new IllegalArgumentException("a conflict requires a relation and at least two observations");
        }
        long targets = observations.stream().map(CrossProjectReference::target).distinct().count();
        if (targets < 2) {
            throw new IllegalArgumentException("a conflict requires at least two distinct targets");
        }
    }

    @Override
    public int compareTo(PortfolioReferenceConflict other) {
        int sourceOrder = source.compareTo(other.source);
        return sourceOrder != 0 ? sourceOrder : relation.compareTo(other.relation);
    }
}
