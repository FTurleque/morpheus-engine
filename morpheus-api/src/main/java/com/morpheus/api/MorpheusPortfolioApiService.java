package com.morpheus.api;

import com.morpheus.application.portfolio.PortfolioPublicViews;
import com.morpheus.application.portfolio.PortfolioQueryService;
import com.morpheus.application.portfolio.PortfolioRegistryService;
import com.morpheus.application.portfolio.PortfolioTraversalDirection;
import com.morpheus.application.portfolio.PortfolioTraversalService;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.portfolio.PortfolioEntityRef;
import com.morpheus.domain.portfolio.PortfolioFreshnessState;
import com.morpheus.domain.portfolio.PortfolioId;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.source.SourceLocator;
import com.morpheus.store.sqlite.SqlitePortfolioStore;

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/** HTTP-facing M23 service; transport parsing stays separate from portfolio business semantics. */
public final class MorpheusPortfolioApiService {
    private final Path databasePath;

    public MorpheusPortfolioApiService(Path databasePath) {
        this.databasePath = Objects.requireNonNull(databasePath, "databasePath").toAbsolutePath().normalize();
    }

    public Object create(CreatePortfolioRequest request) {
        Objects.requireNonNull(request, "request");
        try (SqlitePortfolioStore store = store()) {
            return PortfolioPublicViews.project(new PortfolioRegistryService(store).create(request.name()));
        }
    }

    public Object registerProject(String portfolioId, RegisterProjectRequest request) {
        Objects.requireNonNull(request, "request");
        try (SqlitePortfolioStore store = store()) {
            return PortfolioPublicViews.project(new PortfolioRegistryService(store).registerProject(
                    PortfolioId.parse(portfolioId),
                    ProjectSpecificationId.parse(request.projectId()),
                    request.name(),
                    optional(request.workspace()).map(SourceLocator::file),
                    optional(request.repository()).map(MorpheusPortfolioApiService::locator),
                    providers(request.providers())));
        }
    }

    public Object markMissing(String portfolioId, String projectId) {
        try (SqlitePortfolioStore store = store()) {
            return PortfolioPublicViews.project(new PortfolioRegistryService(store).markMissing(
                    PortfolioId.parse(portfolioId), ProjectSpecificationId.parse(projectId)));
        }
    }

    public Object observeFreshness(String portfolioId, String projectId, FreshnessRequest request) {
        Objects.requireNonNull(request, "request");
        try (SqlitePortfolioStore store = store()) {
            return PortfolioPublicViews.project(new PortfolioRegistryService(store).observeFreshness(
                    PortfolioId.parse(portfolioId),
                    ProjectSpecificationId.parse(projectId),
                    PortfolioFreshnessState.valueOf(request.state().trim().toUpperCase()),
                    optional(request.revision()),
                    optional(request.explanation())));
        }
    }

    public Object addReference(String portfolioId, CrossProjectReferenceRequest request) {
        Objects.requireNonNull(request, "request");
        try (SqlitePortfolioStore store = store()) {
            return PortfolioPublicViews.project(new PortfolioRegistryService(store).addReference(
                    PortfolioId.parse(portfolioId),
                    entity(request.sourceProjectId(), request.sourceType(), request.sourceId()),
                    entity(request.targetProjectId(), request.targetType(), request.targetId()),
                    request.relation(),
                    new ProviderId(request.providerId()),
                    optional(request.sourceLocator()).map(MorpheusPortfolioApiService::locator),
                    optional(request.evidenceId()).map(EvidenceId::parse)));
        }
    }

    public Object list(int offset, int limit) {
        try (SqlitePortfolioStore store = store()) {
            return PortfolioPublicViews.project(new PortfolioQueryService(store).listPortfolios(offset, limit));
        }
    }

    public Object overview(String portfolioId) {
        try (SqlitePortfolioStore store = store()) {
            return PortfolioPublicViews.project(new PortfolioQueryService(store).overview(PortfolioId.parse(portfolioId)));
        }
    }

    public Object members(String portfolioId, int offset, int limit) {
        try (SqlitePortfolioStore store = store()) {
            return PortfolioPublicViews.project(
                    new PortfolioQueryService(store).memberships(PortfolioId.parse(portfolioId), offset, limit));
        }
    }

    public Object references(String portfolioId, Optional<String> projectId, int offset, int limit) {
        try (SqlitePortfolioStore store = store()) {
            PortfolioQueryService query = new PortfolioQueryService(store);
            PortfolioId id = PortfolioId.parse(portfolioId);
            Object references = projectId
                    .map(ProjectSpecificationId::parse)
                    .map(project -> query.projectReferences(id, project, offset, limit))
                    .orElseGet(() -> query.references(id, offset, limit));
            return PortfolioPublicViews.project(references);
        }
    }

    public Object conflicts(String portfolioId) {
        try (SqlitePortfolioStore store = store()) {
            return PortfolioPublicViews.project(new PortfolioQueryService(store).conflicts(PortfolioId.parse(portfolioId)));
        }
    }

    public Object traverse(String portfolioId, TraversalRequest request) {
        Objects.requireNonNull(request, "request");
        try (SqlitePortfolioStore store = store()) {
            return PortfolioPublicViews.project(new PortfolioTraversalService(store).traverse(
                    PortfolioId.parse(portfolioId),
                    entity(request.startProjectId(), request.startType(), request.startId()),
                    request.maxDepth() == null ? 4 : request.maxDepth(),
                    request.maxNodes() == null ? 250 : request.maxNodes(),
                    request.maxLinks() == null ? 1000 : request.maxLinks(),
                    PortfolioTraversalDirection.valueOf(optional(request.direction()).orElse("BOTH").toUpperCase())));
        }
    }

    private SqlitePortfolioStore store() {
        return new SqlitePortfolioStore(databasePath);
    }

    private static PortfolioEntityRef entity(String projectId, String type, String entityId) {
        return new PortfolioEntityRef(
                ProjectSpecificationId.parse(projectId), type, DomainIdentity.parse(entityId));
    }

    private static SourceLocator locator(String encoded) {
        int separator = encoded.indexOf(':');
        if (separator <= 0 || separator == encoded.length() - 1) {
            throw new IllegalArgumentException("locator must use scheme:value syntax");
        }
        return new SourceLocator(encoded.substring(0, separator), encoded.substring(separator + 1));
    }

    private static Set<ProviderId> providers(String encoded) {
        if (encoded == null || encoded.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(encoded.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(ProviderId::new)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static Optional<String> optional(String value) {
        return Optional.ofNullable(value).map(String::trim).filter(item -> !item.isEmpty());
    }

    public record CreatePortfolioRequest(String name) {
    }

    public record RegisterProjectRequest(
            String projectId,
            String name,
            String workspace,
            String repository,
            String providers) {
    }

    public record FreshnessRequest(String state, String revision, String explanation) {
    }

    public record CrossProjectReferenceRequest(
            String sourceProjectId,
            String sourceType,
            String sourceId,
            String targetProjectId,
            String targetType,
            String targetId,
            String relation,
            String providerId,
            String sourceLocator,
            String evidenceId) {
    }

    public record TraversalRequest(
            String startProjectId,
            String startType,
            String startId,
            String direction,
            Integer maxDepth,
            Integer maxNodes,
            Integer maxLinks) {
    }
}
