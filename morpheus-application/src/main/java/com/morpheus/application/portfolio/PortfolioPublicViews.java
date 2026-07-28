package com.morpheus.application.portfolio;

import com.morpheus.domain.portfolio.CrossProjectReference;
import com.morpheus.domain.portfolio.PortfolioDefinition;
import com.morpheus.domain.portfolio.PortfolioEntityRef;
import com.morpheus.domain.portfolio.PortfolioFreshness;
import com.morpheus.domain.portfolio.PortfolioMembership;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Transport-safe M23 projections for CLI, MCP and HTTP.
 *
 * <p>Domain identities and temporal values are deliberately converted to strings before canonical JSON
 * serialization, in accordance with ADR-0047. Domain objects are never serialized directly.</p>
 */
public final class PortfolioPublicViews {
    private PortfolioPublicViews() {
    }

    public static Object project(Object value) {
        Objects.requireNonNull(value, "value");
        if (value instanceof PortfolioDefinition definition) {
            return portfolio(definition);
        }
        if (value instanceof PortfolioMembership membership) {
            return membership(membership);
        }
        if (value instanceof PortfolioFreshness freshness) {
            return freshness(freshness);
        }
        if (value instanceof CrossProjectReference reference) {
            return reference(reference);
        }
        if (value instanceof PortfolioOverview overview) {
            return overview(overview);
        }
        if (value instanceof PortfolioReferenceConflict conflict) {
            return conflict(conflict);
        }
        if (value instanceof PortfolioTraversalResult traversal) {
            return traversal(traversal);
        }
        if (value instanceof List<?> list) {
            return list.stream().map(PortfolioPublicViews::project).toList();
        }
        throw new IllegalArgumentException("unsupported M23 public view type: " + value.getClass().getName());
    }

    public static PortfolioView portfolio(PortfolioDefinition value) {
        return new PortfolioView(
                value.id().toString(),
                value.name(),
                value.createdAt().toString(),
                value.updatedAt().toString());
    }

    public static MembershipView membership(PortfolioMembership value) {
        return new MembershipView(
                value.portfolioId().toString(),
                value.projectId().toString(),
                value.displayName(),
                value.workspace().map(Object::toString),
                value.repository().map(Object::toString),
                value.providers().stream().map(provider -> provider.value()).sorted().toList(),
                value.status().name(),
                value.firstRegisteredAt().toString(),
                value.lastObservedAt().toString());
    }

    public static FreshnessView freshness(PortfolioFreshness value) {
        return new FreshnessView(
                value.portfolioId().toString(),
                value.projectId().toString(),
                value.state().name(),
                value.observedAt().toString(),
                value.revision(),
                value.explanation());
    }

    public static ReferenceView reference(CrossProjectReference value) {
        return new ReferenceView(
                value.id().toString(),
                value.portfolioId().toString(),
                entity(value.source()),
                entity(value.target()),
                value.relation(),
                value.providerId().value(),
                value.sourceLocator().map(Object::toString),
                value.evidenceId().map(Object::toString),
                value.observedAt().toString());
    }

    public static ConflictView conflict(PortfolioReferenceConflict value) {
        return new ConflictView(
                entity(value.source()),
                value.relation(),
                value.observations().stream().map(PortfolioPublicViews::reference).toList());
    }

    public static OverviewView overview(PortfolioOverview value) {
        return new OverviewView(
                portfolio(value.portfolio()),
                value.memberships().stream().map(PortfolioPublicViews::membership).toList(),
                value.freshness().stream().map(PortfolioPublicViews::freshness).toList(),
                value.conflicts().stream().map(PortfolioPublicViews::conflict).toList(),
                value.referenceCount());
    }

    public static TraversalView traversal(PortfolioTraversalResult value) {
        List<TraversalNodeView> nodes = value.depthByNode().entrySet().stream()
                .map(entry -> new TraversalNodeView(entity(entry.getKey()), entry.getValue()))
                .toList();
        return new TraversalView(
                entity(value.start()),
                nodes,
                value.links().stream().map(PortfolioPublicViews::reference).toList(),
                value.truncationReason(),
                value.truncated());
    }

    public static EntityRefView entity(PortfolioEntityRef value) {
        return new EntityRefView(
                value.projectId().toString(),
                value.entityType(),
                value.entityId().toString());
    }

    public record PortfolioView(String id, String name, String createdAt, String updatedAt) {
    }

    public record MembershipView(
            String portfolioId,
            String projectId,
            String displayName,
            Optional<String> workspace,
            Optional<String> repository,
            List<String> providers,
            String status,
            String firstRegisteredAt,
            String lastObservedAt) {
    }

    public record FreshnessView(
            String portfolioId,
            String projectId,
            String state,
            String observedAt,
            Optional<String> revision,
            Optional<String> explanation) {
    }

    public record EntityRefView(String projectId, String entityType, String entityId) {
    }

    public record ReferenceView(
            String id,
            String portfolioId,
            EntityRefView source,
            EntityRefView target,
            String relation,
            String providerId,
            Optional<String> sourceLocator,
            Optional<String> evidenceId,
            String observedAt) {
    }

    public record ConflictView(EntityRefView source, String relation, List<ReferenceView> observations) {
    }

    public record OverviewView(
            PortfolioView portfolio,
            List<MembershipView> memberships,
            List<FreshnessView> freshness,
            List<ConflictView> conflicts,
            int referenceCount) {
    }

    public record TraversalNodeView(EntityRefView node, int depth) {
    }

    public record TraversalView(
            EntityRefView start,
            List<TraversalNodeView> nodes,
            List<ReferenceView> links,
            Optional<String> truncationReason,
            boolean truncated) {
    }
}
