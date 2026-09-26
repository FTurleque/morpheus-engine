package com.morpheus.application.query.dsl;

import com.morpheus.application.store.PortfolioStore;
import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.application.store.RequirementVersionRecord;
import com.morpheus.application.store.SnapshotBusinessContent;
import com.morpheus.application.store.SnapshotBusinessContentStore;
import com.morpheus.application.store.SnapshotSpecificationVersionBinding;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.application.store.VersionedRequirementStore;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.portfolio.CrossProjectReference;
import com.morpheus.domain.portfolio.CrossProjectReferenceId;
import com.morpheus.domain.portfolio.PortfolioDefinition;
import com.morpheus.domain.portfolio.PortfolioEntityRef;
import com.morpheus.domain.portfolio.PortfolioFreshness;
import com.morpheus.domain.portfolio.PortfolioId;
import com.morpheus.domain.portfolio.PortfolioMembership;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.source.SourceLocator;
import com.morpheus.domain.version.EntityVersionId;
import com.morpheus.domain.version.SpecificationVersion;
import com.morpheus.domain.version.SpecificationVersionId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryCurrentRequirementBudgetTest {
    private static final Instant NOW = Instant.parse("2026-09-07T20:00:00Z");

    @Test
    void requirementQueryUsesBoundedCurrentProjectionInsteadOfLoadingAllVersions() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        KnowledgeSnapshotId snapshotId = KnowledgeSnapshotId.generate();
        TrackingVersionStore versions = new TrackingVersionStore();
        QueryExecutionService service = new QueryExecutionService(
                new ActiveSnapshotStore(activeSnapshot(projectId, snapshotId)),
                versions,
                new EmptyContentStore(),
                new EmptyPortfolioStore());

        QueryResult result = service.execute(QueryDefinition.all(
                new ProjectQueryScope(projectId),
                QueryEntityType.REQUIREMENT,
                QueryPage.first(10)));

        assertEquals(0, result.totalMatches());
        assertTrue(result.items().isEmpty());
        assertEquals(1, versions.currentReads);
        assertEquals(QueryBudgets.MAX_SOURCE_ROWS + 1, versions.lastLimit);
    }

    private static KnowledgeSnapshotMetadata activeSnapshot(
            ProjectSpecificationId projectId,
            KnowledgeSnapshotId snapshotId) {
        return new KnowledgeSnapshotMetadata(
                snapshotId,
                projectId,
                Optional.empty(),
                KnowledgeSnapshotState.ACTIVE,
                Optional.of("audit-remediation"),
                NOW);
    }

    private static final class TrackingVersionStore implements VersionedRequirementStore {
        private int currentReads;
        private int lastLimit;

        @Override
        public List<RequirementVersionRecord> listCurrentRequirementVersions(
                KnowledgeSnapshotId snapshotId,
                int limit) {
            currentReads++;
            lastLimit = limit;
            return List.of();
        }

        @Override
        public List<RequirementVersionRecord> listRequirementVersions(KnowledgeSnapshotId snapshotId) {
            throw new AssertionError("query engine must not load historical/proposed requirement versions");
        }

        @Override public void putSpecificationVersion(SpecificationVersion version) { throw new UnsupportedOperationException(); }
        @Override public Optional<SpecificationVersion> findSpecificationVersion(SpecificationVersionId versionId) { return Optional.empty(); }
        @Override public void bindSnapshotVersion(SnapshotSpecificationVersionBinding binding) { throw new UnsupportedOperationException(); }
        @Override public Optional<SnapshotSpecificationVersionBinding> findSnapshotVersion(KnowledgeSnapshotId snapshotId) { return Optional.empty(); }
        @Override public void putRequirementVersion(RequirementVersionRecord record) { throw new UnsupportedOperationException(); }
        @Override public Optional<RequirementVersionRecord> findRequirementVersion(EntityVersionId entityVersionId) { return Optional.empty(); }
        @Override public Optional<RequirementVersionRecord> currentRequirement(
                KnowledgeSnapshotId snapshotId, DomainIdentity entityIdentity) { return Optional.empty(); }
    }

    private static final class ActiveSnapshotStore implements SpecificationKnowledgeStore {
        private final KnowledgeSnapshotMetadata active;

        private ActiveSnapshotStore(KnowledgeSnapshotMetadata active) {
            this.active = active;
        }

        @Override public void putProject(ProjectStoreEntry project) { throw new UnsupportedOperationException(); }
        @Override public Optional<ProjectStoreEntry> findProject(ProjectSpecificationId projectId) { return Optional.empty(); }
        @Override public Optional<ProjectStoreEntry> findProjectByRoot(SourceLocator rootLocator) { return Optional.empty(); }
        @Override public List<ProjectStoreEntry> listProjects() { return List.of(); }
        @Override public void putSnapshot(KnowledgeSnapshotMetadata snapshot) { throw new UnsupportedOperationException(); }
        @Override public Optional<KnowledgeSnapshotMetadata> findSnapshot(KnowledgeSnapshotId snapshotId) { return Optional.empty(); }
        @Override public Optional<KnowledgeSnapshotMetadata> activeSnapshot(ProjectSpecificationId projectId) {
            return active.projectId().equals(projectId) ? Optional.of(active) : Optional.empty();
        }
        @Override public KnowledgeSnapshotMetadata transitionSnapshotState(
                KnowledgeSnapshotId id, KnowledgeSnapshotState from, KnowledgeSnapshotState to) {
            throw new UnsupportedOperationException();
        }
        @Override public KnowledgeSnapshotMetadata activateSnapshot(
                KnowledgeSnapshotId id, Optional<KnowledgeSnapshotId> expected) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class EmptyContentStore implements SnapshotBusinessContentStore {
        @Override public void putSnapshotContent(SnapshotBusinessContent content) { throw new UnsupportedOperationException(); }
        @Override public Optional<SnapshotBusinessContent> findSnapshotContent(KnowledgeSnapshotId snapshotId) {
            return Optional.empty();
        }
    }

    private static final class EmptyPortfolioStore implements PortfolioStore {
        @Override public void putPortfolio(PortfolioDefinition portfolio) { throw new UnsupportedOperationException(); }
        @Override public Optional<PortfolioDefinition> findPortfolio(PortfolioId id) { return Optional.empty(); }
        @Override public List<PortfolioDefinition> listPortfolios() { return List.of(); }
        @Override public void putMembership(PortfolioMembership membership) { throw new UnsupportedOperationException(); }
        @Override public Optional<PortfolioMembership> findMembership(
                PortfolioId id, ProjectSpecificationId projectId) { return Optional.empty(); }
        @Override public List<PortfolioMembership> listMemberships(PortfolioId id) { return List.of(); }
        @Override public void putReference(CrossProjectReference reference) { throw new UnsupportedOperationException(); }
        @Override public Optional<CrossProjectReference> findReference(CrossProjectReferenceId referenceId) { return Optional.empty(); }
        @Override public List<CrossProjectReference> listReferences(PortfolioId id) { return List.of(); }
        @Override public List<CrossProjectReference> outgoing(PortfolioId id, PortfolioEntityRef source) { return List.of(); }
        @Override public List<CrossProjectReference> incoming(PortfolioId id, PortfolioEntityRef target) { return List.of(); }
        @Override public void putFreshness(PortfolioFreshness freshness) { throw new UnsupportedOperationException(); }
        @Override public Optional<PortfolioFreshness> findFreshness(
                PortfolioId id, ProjectSpecificationId projectId) { return Optional.empty(); }
        @Override public List<PortfolioFreshness> listFreshness(PortfolioId id) { return List.of(); }
    }
}
