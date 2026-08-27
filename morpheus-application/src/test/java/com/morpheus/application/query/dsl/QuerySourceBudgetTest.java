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
import com.morpheus.domain.portfolio.PortfolioMembershipStatus;
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
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuerySourceBudgetTest {
    private static final Instant NOW = Instant.parse("2026-08-19T18:00:00Z");

    @Test
    void rejectsPortfolioFanOutAboveProjectBudgetBeforeMaterialization() {
        PortfolioId portfolioId = PortfolioId.generate();
        PortfolioMembership membership = membership(portfolioId, ProjectSpecificationId.generate());
        PortfolioStore portfolios = new StubPortfolioStore(
                portfolioId,
                Collections.nCopies(QueryBudgets.MAX_PORTFOLIO_PROJECTS + 1, membership));
        QueryExecutionService service = service(portfolios);

        QueryValidationException failure = assertThrows(
                QueryValidationException.class,
                () -> service.execute(QueryDefinition.all(
                        new PortfolioQueryScope(portfolioId),
                        QueryEntityType.PORTFOLIO_MEMBERSHIP,
                        QueryPage.first(1))));

        assertTrue(failure.getMessage().contains("portfolio query exceeds"));
    }

    @Test
    void materializesPortfolioMembershipSourcesWithinBudget() {
        PortfolioId portfolioId = PortfolioId.generate();
        PortfolioMembership membership = membership(portfolioId, ProjectSpecificationId.generate());
        QueryExecutionService service = service(new StubPortfolioStore(portfolioId, List.of(membership)));

        QueryResult result = service.execute(QueryDefinition.all(
                new PortfolioQueryScope(portfolioId),
                QueryEntityType.PORTFOLIO_MEMBERSHIP,
                QueryPage.first(10)));

        assertEquals(1, result.totalMatches());
        assertEquals(membership.projectId().toString(), result.items().getFirst().projectId());
    }

    @Test
    void materializesPortfolioReferenceSourcesWithinBudget() {
        PortfolioId portfolioId = PortfolioId.generate();
        QueryExecutionService service = service(new StubPortfolioStore(portfolioId, List.of()));

        QueryResult result = service.execute(QueryDefinition.all(
                new PortfolioQueryScope(portfolioId),
                QueryEntityType.PORTFOLIO_REFERENCE,
                QueryPage.first(10)));

        assertEquals(0, result.totalMatches());
        assertTrue(result.items().isEmpty());
    }

    @Test
    void projectRequirementSourceWithinBudgetCanBeEmpty() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        KnowledgeSnapshotId snapshotId = KnowledgeSnapshotId.generate();
        QueryExecutionService service = service(
                new StubPortfolioStore(PortfolioId.generate(), List.of()),
                new EmptySnapshotStore(activeSnapshot(projectId, snapshotId)),
                new EmptyVersionStore(),
                new EmptyContentStore());

        QueryResult result = service.execute(QueryDefinition.all(
                new ProjectQueryScope(projectId),
                QueryEntityType.REQUIREMENT,
                QueryPage.first(10)));

        assertEquals(0, result.totalMatches());
        assertTrue(result.items().isEmpty());
    }

    @Test
    void allBusinessContentSourcesExerciseBoundedProjectionBranches() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        KnowledgeSnapshotId snapshotId = KnowledgeSnapshotId.generate();
        SpecificationVersionId versionId = SpecificationVersionId.generate();
        SnapshotBusinessContent content = new SnapshotBusinessContent(
                snapshotId,
                versionId,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());
        QueryExecutionService service = service(
                new StubPortfolioStore(PortfolioId.generate(), List.of()),
                new EmptySnapshotStore(activeSnapshot(projectId, snapshotId)),
                new EmptyVersionStore(),
                new EmptyContentStore(content));

        for (QueryEntityType type : List.of(
                QueryEntityType.SPECIFICATION,
                QueryEntityType.SCENARIO,
                QueryEntityType.CHANGE,
                QueryEntityType.CONSTRAINT,
                QueryEntityType.DESIGN_DECISION,
                QueryEntityType.TASK,
                QueryEntityType.ACCEPTANCE_CRITERION,
                QueryEntityType.EVIDENCE)) {
            QueryResult result = service.execute(QueryDefinition.all(
                    new ProjectQueryScope(projectId), type, QueryPage.first(10)));
            assertEquals(0, result.totalMatches(), type.name());
        }
    }

    @Test
    void portfolioAggregationStaysBoundedWhenMembersHaveNoActiveSnapshot() {
        PortfolioId portfolioId = PortfolioId.generate();
        PortfolioMembership first = membership(portfolioId, ProjectSpecificationId.generate());
        PortfolioMembership second = membership(portfolioId, ProjectSpecificationId.generate());
        QueryExecutionService service = service(new StubPortfolioStore(portfolioId, List.of(first, second)));

        QueryResult result = service.execute(QueryDefinition.all(
                new PortfolioQueryScope(portfolioId),
                QueryEntityType.REQUIREMENT,
                QueryPage.first(10)));

        assertEquals(0, result.totalMatches());
        assertTrue(result.items().isEmpty());
    }

    private QueryExecutionService service(PortfolioStore portfolios) {
        return service(portfolios, new EmptySnapshotStore(), new EmptyVersionStore(), new EmptyContentStore());
    }

    private QueryExecutionService service(
            PortfolioStore portfolios,
            SpecificationKnowledgeStore snapshots,
            VersionedRequirementStore versions,
            SnapshotBusinessContentStore content) {
        return new QueryExecutionService(snapshots, versions, content, portfolios);
    }

    private KnowledgeSnapshotMetadata activeSnapshot(ProjectSpecificationId projectId, KnowledgeSnapshotId snapshotId) {
        return new KnowledgeSnapshotMetadata(
                snapshotId,
                projectId,
                Optional.empty(),
                KnowledgeSnapshotState.ACTIVE,
                Optional.of("rev-test"),
                NOW);
    }

    private PortfolioMembership membership(PortfolioId portfolioId, ProjectSpecificationId projectId) {
        return new PortfolioMembership(
                portfolioId,
                projectId,
                "project",
                Optional.empty(),
                Optional.empty(),
                Set.of(),
                PortfolioMembershipStatus.ACTIVE,
                NOW,
                NOW);
    }

    private static final class StubPortfolioStore implements PortfolioStore {
        private final PortfolioId portfolioId;
        private final List<PortfolioMembership> memberships;

        private StubPortfolioStore(PortfolioId portfolioId, List<PortfolioMembership> memberships) {
            this.portfolioId = portfolioId;
            this.memberships = memberships;
        }

        @Override public void putPortfolio(PortfolioDefinition portfolio) { throw new UnsupportedOperationException(); }
        @Override public Optional<PortfolioDefinition> findPortfolio(PortfolioId id) {
            return id.equals(portfolioId)
                    ? Optional.of(new PortfolioDefinition(portfolioId, "portfolio", NOW, NOW))
                    : Optional.empty();
        }
        @Override public List<PortfolioDefinition> listPortfolios() { return List.of(); }
        @Override public void putMembership(PortfolioMembership membership) { throw new UnsupportedOperationException(); }
        @Override public Optional<PortfolioMembership> findMembership(PortfolioId id, ProjectSpecificationId projectId) { return Optional.empty(); }
        @Override public List<PortfolioMembership> listMemberships(PortfolioId id) { return memberships; }
        @Override public void putReference(CrossProjectReference reference) { throw new UnsupportedOperationException(); }
        @Override public Optional<CrossProjectReference> findReference(CrossProjectReferenceId referenceId) { return Optional.empty(); }
        @Override public List<CrossProjectReference> listReferences(PortfolioId id) { return List.of(); }
        @Override public List<CrossProjectReference> outgoing(PortfolioId id, PortfolioEntityRef source) { return List.of(); }
        @Override public List<CrossProjectReference> incoming(PortfolioId id, PortfolioEntityRef target) { return List.of(); }
        @Override public void putFreshness(PortfolioFreshness freshness) { throw new UnsupportedOperationException(); }
        @Override public Optional<PortfolioFreshness> findFreshness(PortfolioId id, ProjectSpecificationId projectId) { return Optional.empty(); }
        @Override public List<PortfolioFreshness> listFreshness(PortfolioId id) { return List.of(); }
    }

    private static final class EmptySnapshotStore implements SpecificationKnowledgeStore {
        private final Optional<KnowledgeSnapshotMetadata> active;

        private EmptySnapshotStore() { this.active = Optional.empty(); }
        private EmptySnapshotStore(KnowledgeSnapshotMetadata active) { this.active = Optional.of(active); }

        @Override public void putProject(ProjectStoreEntry project) { }
        @Override public Optional<ProjectStoreEntry> findProject(ProjectSpecificationId projectId) { return Optional.empty(); }
        @Override public Optional<ProjectStoreEntry> findProjectByRoot(SourceLocator rootLocator) { return Optional.empty(); }
        @Override public List<ProjectStoreEntry> listProjects() { return List.of(); }
        @Override public void putSnapshot(KnowledgeSnapshotMetadata snapshot) { throw new UnsupportedOperationException(); }
        @Override public Optional<KnowledgeSnapshotMetadata> findSnapshot(KnowledgeSnapshotId snapshotId) { return Optional.empty(); }
        @Override public Optional<KnowledgeSnapshotMetadata> activeSnapshot(ProjectSpecificationId projectId) {
            return active.filter(snapshot -> snapshot.projectId().equals(projectId));
        }
        @Override public KnowledgeSnapshotMetadata transitionSnapshotState(KnowledgeSnapshotId id, KnowledgeSnapshotState from, KnowledgeSnapshotState to) { throw new UnsupportedOperationException(); }
        @Override public KnowledgeSnapshotMetadata activateSnapshot(KnowledgeSnapshotId id, Optional<KnowledgeSnapshotId> expected) { throw new UnsupportedOperationException(); }
    }

    private static final class EmptyVersionStore implements VersionedRequirementStore {
        @Override public void putSpecificationVersion(SpecificationVersion version) { throw new UnsupportedOperationException(); }
        @Override public Optional<SpecificationVersion> findSpecificationVersion(SpecificationVersionId versionId) { return Optional.empty(); }
        @Override public void bindSnapshotVersion(SnapshotSpecificationVersionBinding binding) { throw new UnsupportedOperationException(); }
        @Override public Optional<SnapshotSpecificationVersionBinding> findSnapshotVersion(KnowledgeSnapshotId snapshotId) { return Optional.empty(); }
        @Override public void putRequirementVersion(RequirementVersionRecord record) { throw new UnsupportedOperationException(); }
        @Override public Optional<RequirementVersionRecord> findRequirementVersion(EntityVersionId entityVersionId) { return Optional.empty(); }
        @Override public List<RequirementVersionRecord> listRequirementVersions(KnowledgeSnapshotId snapshotId) { return List.of(); }
        @Override public Optional<RequirementVersionRecord> currentRequirement(KnowledgeSnapshotId snapshotId, DomainIdentity entityIdentity) { return Optional.empty(); }
    }

    private static final class EmptyContentStore implements SnapshotBusinessContentStore {
        private final Optional<SnapshotBusinessContent> content;

        private EmptyContentStore() { this.content = Optional.empty(); }
        private EmptyContentStore(SnapshotBusinessContent content) { this.content = Optional.of(content); }

        @Override public void putSnapshotContent(SnapshotBusinessContent content) { throw new UnsupportedOperationException(); }
        @Override public Optional<SnapshotBusinessContent> findSnapshotContent(KnowledgeSnapshotId snapshotId) {
            return content.filter(item -> item.snapshotId().equals(snapshotId));
        }
    }
}
