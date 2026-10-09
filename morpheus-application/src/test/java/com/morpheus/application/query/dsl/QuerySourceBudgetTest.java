package com.morpheus.application.query.dsl;

import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.application.store.PortfolioStore;
import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.application.store.RequirementVersionRecord;
import com.morpheus.application.store.SnapshotBusinessContent;
import com.morpheus.application.store.SnapshotBusinessContentStore;
import com.morpheus.application.store.SnapshotSpecificationVersionBinding;
import com.morpheus.application.store.SpecificationKnowledgeStore;
import com.morpheus.application.store.VersionedRequirementStore;
import com.morpheus.domain.acceptance.AcceptanceCriterion;
import com.morpheus.domain.acceptance.AcceptanceCriterionId;
import com.morpheus.domain.acceptance.VerificationStatus;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.ChangeProposal;
import com.morpheus.domain.constraint.Constraint;
import com.morpheus.domain.constraint.ConstraintId;
import com.morpheus.domain.decision.DesignDecision;
import com.morpheus.domain.decision.DesignDecisionId;
import com.morpheus.domain.evidence.Evidence;
import com.morpheus.domain.evidence.EvidenceId;
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
import com.morpheus.domain.provenance.Provenance;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.requirement.RequirementId;
import com.morpheus.domain.scenario.Scenario;
import com.morpheus.domain.scenario.ScenarioId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.source.SourceLocator;
import com.morpheus.domain.specification.Specification;
import com.morpheus.domain.specification.SpecificationId;
import com.morpheus.domain.task.ImplementationTask;
import com.morpheus.domain.task.TaskId;
import com.morpheus.domain.version.EntityVersionId;
import com.morpheus.domain.version.SpecificationVersion;
import com.morpheus.domain.version.SpecificationVersionId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

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
        QueryDefinition definition = QueryDefinition.all(
                new PortfolioQueryScope(portfolioId),
                QueryEntityType.PORTFOLIO_MEMBERSHIP,
                QueryPage.first(1));

        QueryValidationException failure = assertThrows(
                QueryValidationException.class,
                () -> service.execute(definition));

        assertTrue(failure.getMessage().contains("portfolio query exceeds"));
    }

    @Test
    void aPortfolioOfExactlyTheProjectBudgetIsQueried() {
        PortfolioId portfolioId = PortfolioId.generate();
        PortfolioMembership membership = membership(portfolioId, ProjectSpecificationId.generate());
        QueryExecutionService service = service(new StubPortfolioStore(
                portfolioId, Collections.nCopies(QueryBudgets.MAX_PORTFOLIO_PROJECTS, membership)));

        QueryResult result = service.execute(QueryDefinition.all(
                new PortfolioQueryScope(portfolioId), QueryEntityType.PORTFOLIO_MEMBERSHIP, QueryPage.first(1)));

        assertEquals(QueryBudgets.MAX_PORTFOLIO_PROJECTS, result.totalMatches());
    }

    @Test
    void materializesPortfolioMembershipSourcesWithinBudget() {
        PortfolioId portfolioId = PortfolioId.generate();
        PortfolioMembership first = membership(portfolioId, ProjectSpecificationId.generate());
        PortfolioMembership second = membership(portfolioId, ProjectSpecificationId.generate());
        QueryExecutionService service = service(new StubPortfolioStore(portfolioId, List.of(first, second)));
        QueryDefinition sorted = new QueryDefinition(
                new PortfolioQueryScope(portfolioId),
                QueryEntityType.PORTFOLIO_MEMBERSHIP,
                Optional.empty(),
                List.of(new QuerySort("projectId", QuerySortDirection.ASC)),
                QueryProjection.defaults(),
                QueryPage.first(10));

        QueryResult result = service.execute(sorted);

        String expectedFirst = first.projectId().toString().compareTo(second.projectId().toString()) <= 0
                ? first.projectId().toString()
                : second.projectId().toString();
        assertEquals(2, result.totalMatches());
        assertEquals(expectedFirst, result.items().getFirst().projectId());
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
    void everyPortfolioReferenceWithinTheBudgetIsReturned() {
        PortfolioId portfolioId = PortfolioId.generate();
        CrossProjectReference first = reference(portfolioId);
        CrossProjectReference second = reference(portfolioId);
        QueryExecutionService service = service(new StubPortfolioStore(portfolioId, List.of(), List.of(second, first)));

        QueryResult result = service.execute(QueryDefinition.all(
                new PortfolioQueryScope(portfolioId), QueryEntityType.PORTFOLIO_REFERENCE, QueryPage.first(10)));

        assertEquals(2, result.totalMatches());
        assertEquals(Set.of(first.id().toString(), second.id().toString()),
                result.items().stream().map(QueryRow::entityId).collect(Collectors.toSet()));
    }

    /** References skip the scope-wide bound, so this check is the only one they meet. */
    @Test
    void portfolioReferencesAboveTheSourceBudgetAreRefusedAtTheirOwnPath() {
        PortfolioId portfolioId = PortfolioId.generate();
        QueryExecutionService service = service(new StubPortfolioStore(
                portfolioId, List.of(), Collections.nCopies(QueryBudgets.MAX_SOURCE_ROWS + 1, reference(portfolioId))));

        assertBudgetExceededAt("$.source.portfolioReferences", () -> service.execute(QueryDefinition.all(
                new PortfolioQueryScope(portfolioId), QueryEntityType.PORTFOLIO_REFERENCE, QueryPage.first(10))));
    }

    @Test
    void projectRequirementSourceWithinBudgetCanBeEmpty() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        KnowledgeSnapshotId snapshotId = KnowledgeSnapshotId.generate();
        QueryExecutionService service = service(
                new StubPortfolioStore(PortfolioId.generate(), List.of()),
                new StubSnapshotStore(activeSnapshot(projectId, snapshotId)),
                new EmptyVersionStore(),
                new StubContentStore());

        QueryResult result = service.execute(QueryDefinition.all(
                new ProjectQueryScope(projectId),
                QueryEntityType.REQUIREMENT,
                QueryPage.first(10)));

        assertEquals(0, result.totalMatches());
        assertTrue(result.items().isEmpty());
    }

    @Test
    void anActiveSnapshotWithoutABusinessContentProjectionIsANamedStoreFailure() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        KnowledgeSnapshotId snapshotId = KnowledgeSnapshotId.generate();
        QueryExecutionService service = service(
                new StubPortfolioStore(PortfolioId.generate(), List.of()),
                new StubSnapshotStore(activeSnapshot(projectId, snapshotId)),
                new EmptyVersionStore(),
                new StubContentStore());

        KnowledgeStoreException failure = assertThrows(KnowledgeStoreException.class, () -> service.execute(
                QueryDefinition.all(new ProjectQueryScope(projectId), QueryEntityType.SPECIFICATION, QueryPage.first(10))));

        assertEquals("published snapshot has no business-content projection: " + snapshotId, failure.getMessage());
    }

    @Test
    void everyBusinessContentSourceMapsItsItem() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        KnowledgeSnapshotId snapshotId = KnowledgeSnapshotId.generate();
        SnapshotBusinessContent content = oneOfEach(projectId, snapshotId);
        QueryExecutionService service = service(
                new StubPortfolioStore(PortfolioId.generate(), List.of()),
                new StubSnapshotStore(activeSnapshot(projectId, snapshotId)),
                new EmptyVersionStore(),
                new StubContentStore(content));
        Map<QueryEntityType, String> expected = new LinkedHashMap<>();
        expected.put(QueryEntityType.SPECIFICATION, content.specifications().getFirst().id().toString());
        expected.put(QueryEntityType.SCENARIO, content.scenarios().getFirst().id().toString());
        expected.put(QueryEntityType.CHANGE, content.changes().getFirst().id().toString());
        expected.put(QueryEntityType.CONSTRAINT, content.constraints().getFirst().id().toString());
        expected.put(QueryEntityType.DESIGN_DECISION, content.designDecisions().getFirst().id().toString());
        expected.put(QueryEntityType.TASK, content.tasks().getFirst().id().toString());
        expected.put(QueryEntityType.ACCEPTANCE_CRITERION, content.acceptanceCriteria().getFirst().id().toString());
        expected.put(QueryEntityType.EVIDENCE, content.evidence().getFirst().id().toString());

        expected.forEach((type, entityId) -> {
            QueryResult result = service.execute(QueryDefinition.all(
                    new ProjectQueryScope(projectId), type, QueryPage.first(10)));
            assertEquals(1, result.totalMatches(), type.name());
            assertEquals(type, result.items().getFirst().entityType());
            assertEquals(entityId, result.items().getFirst().entityId(), type.name());
        });
    }

    /** The scope-wide bound would refuse it too, but later, after mapping, and at {@code $.scope.project}. */
    @Test
    void projectContentAboveTheSourceBudgetIsRefusedAtItsOwnPath() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        KnowledgeSnapshotId snapshotId = KnowledgeSnapshotId.generate();
        QueryExecutionService service = service(
                new StubPortfolioStore(PortfolioId.generate(), List.of()),
                new StubSnapshotStore(activeSnapshot(projectId, snapshotId)),
                new EmptyVersionStore(),
                new StubContentStore(evidenceOnly(snapshotId, QueryBudgets.MAX_SOURCE_ROWS + 1)));

        assertBudgetExceededAt("$.source.evidence", () -> service.execute(QueryDefinition.all(
                new ProjectQueryScope(projectId), QueryEntityType.EVIDENCE, QueryPage.first(10))));
    }

    @Test
    void portfolioProjectsWithinTheBudgetEachButOverItTogetherAreRefused() {
        PortfolioId portfolioId = PortfolioId.generate();
        ProjectSpecificationId firstProject = ProjectSpecificationId.generate();
        ProjectSpecificationId secondProject = ProjectSpecificationId.generate();
        KnowledgeSnapshotId firstSnapshot = KnowledgeSnapshotId.generate();
        KnowledgeSnapshotId secondSnapshot = KnowledgeSnapshotId.generate();
        int half = QueryBudgets.MAX_SOURCE_ROWS / 2 + 1;
        QueryExecutionService service = service(
                new StubPortfolioStore(portfolioId,
                        List.of(membership(portfolioId, firstProject), membership(portfolioId, secondProject))),
                new StubSnapshotStore(
                        activeSnapshot(firstProject, firstSnapshot), activeSnapshot(secondProject, secondSnapshot)),
                new EmptyVersionStore(),
                new StubContentStore(evidenceOnly(firstSnapshot, half), evidenceOnly(secondSnapshot, half)));

        assertBudgetExceededAt("$.source.portfolioProjects", () -> service.execute(QueryDefinition.all(
                new PortfolioQueryScope(portfolioId), QueryEntityType.EVIDENCE, QueryPage.first(10))));
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

    private static void assertBudgetExceededAt(String path, Executable query) {
        QueryValidationException failure = assertThrows(QueryValidationException.class, query);
        QueryDiagnostic diagnostic = failure.diagnostics().getFirst();
        assertEquals("QUERY_SOURCE_BUDGET_EXCEEDED", diagnostic.code());
        assertEquals(path, diagnostic.path());
    }

    private QueryExecutionService service(PortfolioStore portfolios) {
        return service(portfolios, new StubSnapshotStore(), new EmptyVersionStore(), new StubContentStore());
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

    private static CrossProjectReference reference(PortfolioId portfolioId) {
        return new CrossProjectReference(
                CrossProjectReferenceId.generate(),
                portfolioId,
                new PortfolioEntityRef(ProjectSpecificationId.generate(), "requirement", DomainIdentity.generate()),
                new PortfolioEntityRef(ProjectSpecificationId.generate(), "requirement", DomainIdentity.generate()),
                "depends-on",
                new ProviderId("openspec"),
                Optional.empty(),
                Optional.empty(),
                NOW);
    }

    private static SnapshotBusinessContent oneOfEach(ProjectSpecificationId projectId, KnowledgeSnapshotId snapshotId) {
        Evidence evidence = evidence();
        Provenance provenance = new Provenance(
                new ProviderId("openspec"), Optional.of("1"), SourceLocator.file("specs/a.md"),
                Optional.of("A-1"), Optional.of("rev"), evidence.id());
        ChangeProposal change = new ChangeProposal(
                ChangeId.generate(), projectId, Optional.of("C-1"), "Change", "Intent",
                List.of(), List.of(), List.of(), provenance);
        return new SnapshotBusinessContent(
                snapshotId,
                SpecificationVersionId.generate(),
                List.of(new Specification(
                        SpecificationId.generate(), projectId, "spec-1", "Specification", Optional.empty(), provenance)),
                List.of(new Scenario(
                        ScenarioId.generate(), Optional.of(RequirementId.generate()), "Scenario", List.of(),
                        "act", "outcome", provenance)),
                List.of(change),
                List.of(new Constraint(ConstraintId.generate(), change.id(), "Constraint", provenance)),
                List.of(new DesignDecision(DesignDecisionId.generate(), change.id(), "Decision", "Because", provenance)),
                List.of(new ImplementationTask(
                        TaskId.generate(), change.id(), Optional.of("T-1"), "Task", false, provenance)),
                List.of(new AcceptanceCriterion(
                        AcceptanceCriterionId.generate(), Optional.empty(), Optional.of(change.id()), "Criterion",
                        "Condition", VerificationStatus.NOT_VERIFIED, List.of(), provenance)),
                List.of(evidence));
    }

    private static SnapshotBusinessContent evidenceOnly(KnowledgeSnapshotId snapshotId, int count) {
        return new SnapshotBusinessContent(
                snapshotId, SpecificationVersionId.generate(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), IntStream.range(0, count).mapToObj(index -> evidence()).toList());
    }

    private static Evidence evidence() {
        return new Evidence(EvidenceId.generate(), SourceLocator.file("specs/a.md"), Optional.empty(), Optional.empty());
    }

    private static final class StubPortfolioStore implements PortfolioStore {
        private final PortfolioId portfolioId;
        private final List<PortfolioMembership> memberships;
        private final List<CrossProjectReference> references;

        private StubPortfolioStore(PortfolioId portfolioId, List<PortfolioMembership> memberships) {
            this(portfolioId, memberships, List.of());
        }

        private StubPortfolioStore(
                PortfolioId portfolioId,
                List<PortfolioMembership> memberships,
                List<CrossProjectReference> references) {
            this.portfolioId = portfolioId;
            this.memberships = memberships;
            this.references = references;
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
        @Override public List<CrossProjectReference> listReferences(PortfolioId id) { return references; }
        @Override public List<CrossProjectReference> outgoing(PortfolioId id, PortfolioEntityRef source) { return List.of(); }
        @Override public List<CrossProjectReference> incoming(PortfolioId id, PortfolioEntityRef target) { return List.of(); }
        @Override public void putFreshness(PortfolioFreshness freshness) { throw new UnsupportedOperationException(); }
        @Override public Optional<PortfolioFreshness> findFreshness(PortfolioId id, ProjectSpecificationId projectId) { return Optional.empty(); }
        @Override public List<PortfolioFreshness> listFreshness(PortfolioId id) { return List.of(); }
    }

    private static final class StubSnapshotStore implements SpecificationKnowledgeStore {
        private final Map<ProjectSpecificationId, KnowledgeSnapshotMetadata> active;

        private StubSnapshotStore(KnowledgeSnapshotMetadata... active) {
            this.active = Arrays.stream(active)
                    .collect(Collectors.toMap(KnowledgeSnapshotMetadata::projectId, Function.identity()));
        }

        @Override public void putProject(ProjectStoreEntry project) { }
        @Override public Optional<ProjectStoreEntry> findProject(ProjectSpecificationId projectId) { return Optional.empty(); }
        @Override public Optional<ProjectStoreEntry> findProjectByRoot(SourceLocator rootLocator) { return Optional.empty(); }
        @Override public List<ProjectStoreEntry> listProjects() { return List.of(); }
        @Override public void putSnapshot(KnowledgeSnapshotMetadata snapshot) { throw new UnsupportedOperationException(); }
        @Override public Optional<KnowledgeSnapshotMetadata> findSnapshot(KnowledgeSnapshotId snapshotId) { return Optional.empty(); }
        @Override public Optional<KnowledgeSnapshotMetadata> activeSnapshot(ProjectSpecificationId projectId) {
            return Optional.ofNullable(active.get(projectId));
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

    private static final class StubContentStore implements SnapshotBusinessContentStore {
        private final List<SnapshotBusinessContent> contents;

        private StubContentStore(SnapshotBusinessContent... contents) {
            this.contents = List.of(contents);
        }

        @Override public void putSnapshotContent(SnapshotBusinessContent content) { throw new UnsupportedOperationException(); }
        @Override public Optional<SnapshotBusinessContent> findSnapshotContent(KnowledgeSnapshotId snapshotId) {
            return contents.stream().filter(item -> item.snapshotId().equals(snapshotId)).findFirst();
        }
    }
}
