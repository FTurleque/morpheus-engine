package com.morpheus.domain.portfolio;

import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.source.SourceLocator;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** One immutable cross-project observation. Conflicting observations coexist instead of overwriting each other. */
public record CrossProjectReference(
        CrossProjectReferenceId id,
        PortfolioId portfolioId,
        PortfolioEntityRef source,
        PortfolioEntityRef target,
        String relation,
        ProviderId providerId,
        Optional<SourceLocator> sourceLocator,
        Optional<EvidenceId> evidenceId,
        Instant observedAt) implements Comparable<CrossProjectReference> {

    public CrossProjectReference {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(portfolioId, "portfolioId");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        relation = requireText(relation, "relation", 128);
        Objects.requireNonNull(providerId, "providerId");
        sourceLocator = Objects.requireNonNull(sourceLocator, "sourceLocator");
        evidenceId = Objects.requireNonNull(evidenceId, "evidenceId");
        Objects.requireNonNull(observedAt, "observedAt");
        if (source.projectId().equals(target.projectId())) {
            throw new IllegalArgumentException("cross-project reference endpoints must belong to different projects");
        }
    }

    public String semanticKey() {
        return source.projectId() + "|" + source.entityType() + "|" + source.entityId()
                + "|" + relation + "|"
                + target.projectId() + "|" + target.entityType() + "|" + target.entityId();
    }

    @Override
    public int compareTo(CrossProjectReference other) {
        int semantic = semanticKey().compareTo(other.semanticKey());
        if (semantic != 0) {
            return semantic;
        }
        int provider = providerId.compareTo(other.providerId);
        return provider != 0 ? provider : id.compareTo(other.id);
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
