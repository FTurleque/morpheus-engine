package com.morpheus.application.query.export;

import com.morpheus.application.query.dsl.PortfolioQueryScope;
import com.morpheus.application.query.dsl.QueryBudgets;
import com.morpheus.application.query.dsl.QueryDefinition;
import com.morpheus.application.query.dsl.QueryEntityType;
import com.morpheus.application.query.dsl.QueryExecutionService;
import com.morpheus.application.query.dsl.QueryMaterializationLimitException;
import com.morpheus.application.query.dsl.QueryPage;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryExportMaterializationTest {
    private static final Instant NOW = Instant.parse("2026-09-06T17:00:00Z");

    @Test
    void exportReadsAndSortsItsCompleteSourceOnlyOnce() {
        PortfolioId portfolioId = PortfolioId.generate();
        List<PortfolioMembership> firstView = memberships(portfolioId, 600, "first");
        List<PortfolioMembership> secondView = memberships(portfolioId, 600, "second");
        SwitchingPortfolioStore portfolios = new SwitchingPortfolioStore(portfolioId, firstView, secondView);
        QueryExecutionService queries = queries(portfolios);
        QueryExportService exports = new QueryExportService(queries);
        QueryDefinition query = query(portfolioId);

        QueryExport export = exports.export(query, QueryExportFormat.JSON);

        assertEquals(1, portfolios.membershipReads);
        assertTrue(export.content().contains(firstView.getFirst().projectId().toString()));
        assertFalse(export.content().contains(secondView.getFirst().projectId().toString()));
    }

    @Test
    void completeMaterializationRejectsOverBudgetResultAfterOneSourceRead() {
        PortfolioId portfolioId = PortfolioId.generate();
        List<PortfolioMembership> firstView = memberships(portfolioId, 600, "first");
        SwitchingPortfolioStore portfolios = new SwitchingPortfolioStore(
                portfolioId, firstView, memberships(portfolioId, 600, "second"));
        QueryExecutionService queries = queries(portfolios);

        QueryMaterializationLimitException failure = assertThrows(
                QueryMaterializationLimitException.class,
                () -> queries.materializeComplete(query(portfolioId), 500));

        assertEquals(500, failure.maximumRows());
        assertEquals(600, failure.actualRows());
        assertEquals(1, portfolios.membershipReads);
    }

    @Test
    void completeMaterializationRejectsInvalidCallerCeilingsBeforeReadingTheSource() {
        PortfolioId portfolioId = PortfolioId.generate();
        SwitchingPortfolioStore portfolios = new SwitchingPortfolioStore(
                portfolioId, List.of(), List.of());
        QueryExecutionService queries = queries(portfolios);
        QueryDefinition query = query(portfolioId);

        assertThrows(IllegalArgumentException.class, () -> queries.materializeComplete(query, 0));
        assertThrows(IllegalArgumentException.class,
                () -> queries.materializeComplete(query, QueryBudgets.MAX_SOURCE_ROWS + 1));
        assertEquals(0, portfolios.membershipReads);
    }

    private QueryExecutionService queries(PortfolioStore portfolios) {
        return new QueryExecutionService(
                new EmptySnapshotStore(), new EmptyVersionStore(), new EmptyContentStore(), portfolios);
    }

    private QueryDefinition query(PortfolioId portfolioId) {
        return QueryDefinition.all(
                new PortfolioQueryScope(portfolioId),
                QueryEntityType.PORTFOLIO_MEMBERSHIP,
                QueryPage.first(10));
    }

    private List<PortfolioMembership> memberships(PortfolioId portfolioId, int count, String prefix) {
        List<PortfolioMembership> values = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            values.add(new PortfolioMembership(
                    portfolioId,
                    ProjectSpecificationId.generate(),
                    prefix + "-" + index,
                    Optional.empty(),
                    Optional.empty(),
                    Set.of(),
                    PortfolioMembershipStatus.ACTIVE,
                    NOW,
                    NOW));
        }
        return List.copyOf(values);
    }

    private static final class SwitchingPortfolioStore implements PortfolioStore {
        private final PortfolioId portfolioId;
        private final List<PortfolioMembership> first;
        private final List<PortfolioMembership> second;
        private int membershipReads;

        private SwitchingPortfolioStore(
                PortfolioId portfolioId,
                List<PortfolioMembership> first,
                List<PortfolioMembership> second) {
            this.portfolioId = portfolioId;
            this.first = first;
            this.second = second;
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
        @Override public List<PortfolioMembership> listMemberships(PortfolioId id) {
            membershipReads++;
            return membershipReads == 1 ? first : second;
        }
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
        @Override public void putProject(ProjectStoreEntry project) { throw new UnsupportedOperationException(); }
        @Override public Optional<ProjectStoreEntry> findProject(ProjectSpecificationId projectId) { return Optional.empty(); }
        @Override public Optional<ProjectStoreEntry> findProjectByRoot(SourceLocator rootLocator) { return Optional.empty(); }
        @Override public List<ProjectStoreEntry> listProjects() { return List.of(); }
        @Override public void putSnapshot(KnowledgeSnapshotMetadata snapshot) { throw new UnsupportedOperationException(); }
        @Override public Optional<KnowledgeSnapshotMetadata> findSnapshot(KnowledgeSnapshotId snapshotId) { return Optional.empty(); }
        @Override public Optional<KnowledgeSnapshotMetadata> activeSnapshot(ProjectSpecificationId projectId) { return Optional.empty(); }
        @Override public KnowledgeSnapshotMetadata transitionSnapshotState(
                KnowledgeSnapshotId id, KnowledgeSnapshotState from, KnowledgeSnapshotState to) {
            throw new UnsupportedOperationException();
        }
        @Override public KnowledgeSnapshotMetadata activateSnapshot(
                KnowledgeSnapshotId id, Optional<KnowledgeSnapshotId> expected) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class EmptyVersionStore implements VersionedRequirementStore {
        @Override public void putSpecificationVersion(SpecificationVersion version) { throw new UnsupportedOperationException(); }
        @Override public Optional<SpecificationVersion> findSpecificationVersion(SpecificationVersionId versionId) { return Optional.empty(); }
        @Override public void bindSnapshotVersion(SnapshotSpecificationVersionBinding binding) { throw new UnsupportedOperationException(); }
        @Override public Optional<SnapshotSpecificationVersionBinding> findSnapshotVersion(KnowledgeSnapshotId snapshotId) { return Optional.empty(); }
        @Override public void putRequirementVersion(RequirementVersionRecord record) { throw new UnsupportedOperationException(); }
        @Override public Optional<RequirementVersionRecord> findRequirementVersion(EntityVersionId entityVersionId) { return Optional.empty(); }
        @Override public List<RequirementVersionRecord> listRequirementVersions(KnowledgeSnapshotId snapshotId) { return List.of(); }
        @Override public Optional<RequirementVersionRecord> currentRequirement(
                KnowledgeSnapshotId snapshotId, DomainIdentity entityIdentity) { return Optional.empty(); }
    }

    private static final class EmptyContentStore implements SnapshotBusinessContentStore {
        @Override public void putSnapshotContent(SnapshotBusinessContent content) { throw new UnsupportedOperationException(); }
        @Override public Optional<SnapshotBusinessContent> findSnapshotContent(KnowledgeSnapshotId snapshotId) { return Optional.empty(); }
    }
}
