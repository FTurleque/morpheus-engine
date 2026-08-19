package com.morpheus.architecture.m24;

import com.morpheus.application.portfolio.PortfolioRegistryService;
import com.morpheus.application.query.dsl.PortfolioQueryScope;
import com.morpheus.application.query.dsl.ProjectQueryScope;
import com.morpheus.application.query.dsl.QueryBudgets;
import com.morpheus.application.query.dsl.QueryDefinition;
import com.morpheus.application.query.dsl.QueryEntityType;
import com.morpheus.application.query.dsl.QueryExecutionService;
import com.morpheus.application.query.dsl.QueryOperator;
import com.morpheus.application.query.dsl.QueryPage;
import com.morpheus.application.query.dsl.QueryPredicate;
import com.morpheus.application.query.dsl.QueryProjection;
import com.morpheus.application.query.dsl.QuerySort;
import com.morpheus.application.query.dsl.QuerySortDirection;
import com.morpheus.application.query.dsl.QueryValidationException;
import com.morpheus.application.store.ProjectStoreEntry;
import com.morpheus.application.store.SnapshotBusinessContent;
import com.morpheus.application.store.SnapshotSpecificationVersionBinding;
import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.change.ChangeProposal;
import com.morpheus.domain.evidence.Evidence;
import com.morpheus.domain.evidence.EvidenceId;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.provenance.Provenance;
import com.morpheus.domain.provider.ProviderId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotId;
import com.morpheus.domain.snapshot.KnowledgeSnapshotMetadata;
import com.morpheus.domain.snapshot.KnowledgeSnapshotState;
import com.morpheus.domain.source.SourceLocator;
import com.morpheus.domain.version.SpecificationVersion;
import com.morpheus.domain.version.SpecificationVersionId;
import com.morpheus.store.memory.MemoryPortfolioStore;
import com.morpheus.store.memory.MemorySnapshotBusinessContentStore;
import com.morpheus.store.memory.MemorySpecificationKnowledgeStore;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QueryExecutionContractTest {
    private static final Instant NOW = Instant.parse("2026-07-28T19:15:00Z");
    private static final ProjectSpecificationId PROJECT_A =
            ProjectSpecificationId.parse("01890f7a-36d4-7c1e-8000-000000000002");
    private static final ProjectSpecificationId PROJECT_B =
            ProjectSpecificationId.parse("01890f7a-36d4-7c1e-8000-000000000001");

    @Test
    void filtersSortsProjectsAndPaginatesDeterministically() {
        Fixture fixture = fixture();
        seedProject(fixture, PROJECT_A, "Alpha Project", List.of(
                change(PROJECT_A, "01890f7a-36d4-7c1e-8000-000000000012", "CHG-A2", "Shared", "Deliver shared login"),
                change(PROJECT_A, "01890f7a-36d4-7c1e-8000-000000000011", "CHG-A1", "Ignored", "Unrelated")));
        seedProject(fixture, PROJECT_B, "Beta Project", List.of(
                change(PROJECT_B, "01890f7a-36d4-7c1e-8000-000000000021", "CHG-B1", "Shared", "Deliver shared login")));

        QueryDefinition query = new QueryDefinition(
                new PortfolioQueryScope(fixture.portfolioId()),
                QueryEntityType.CHANGE,
                Optional.of(QueryPredicate.unary("intent", QueryOperator.CONTAINS, "LOGIN")),
                List.of(new QuerySort("title", QuerySortDirection.ASC)),
                new QueryProjection(List.of("title", "intent")),
                new QueryPage(0, 1));

        var first = fixture.queries().execute(query);
        var second = fixture.queries().execute(new QueryDefinition(
                query.scope(), query.entityType(), query.filter(), query.sort(), query.projection(), new QueryPage(1, 1)));

        assertEquals(2, first.totalMatches());
        assertTrue(first.hasMore());
        assertEquals(PROJECT_B.toString(), first.items().getFirst().projectId());
        assertEquals(PROJECT_A.toString(), second.items().getFirst().projectId());
        assertFalse(second.hasMore());
        assertEquals(List.of("id", "projectId", "title", "intent"), first.columns());
        assertTrue(first.items().getFirst().cell("projectId").isPresent());
    }

    @Test
    void projectScopeNeverLeaksAnotherProject() {
        Fixture fixture = fixture();
        seedProject(fixture, PROJECT_A, "Alpha Project", List.of(
                change(PROJECT_A, "01890f7a-36d4-7c1e-8000-000000000031", "CHG-A", "Alpha", "A")));
        seedProject(fixture, PROJECT_B, "Beta Project", List.of(
                change(PROJECT_B, "01890f7a-36d4-7c1e-8000-000000000032", "CHG-B", "Beta", "B")));

        var result = fixture.queries().execute(QueryDefinition.all(
                new ProjectQueryScope(PROJECT_A), QueryEntityType.CHANGE, QueryPage.first(100)));

        assertEquals(1, result.totalMatches());
        assertEquals(PROJECT_A.toString(), result.items().getFirst().projectId());
        assertEquals("Alpha", result.items().getFirst().cell("title").orElseThrow().render());
    }

    @Test
    void portfolioProjectBudgetIsEnforcedBeforePaginationOrSorting() {
        Fixture fixture = fixture();
        for (int index = 0; index <= QueryBudgets.MAX_PORTFOLIO_PROJECTS; index++) {
            ProjectSpecificationId projectId = ProjectSpecificationId.generate();
            fixture.registry().registerProject(
                    fixture.portfolioId(),
                    projectId,
                    "Project " + index,
                    Optional.empty(),
                    Optional.empty(),
                    Set.of(new ProviderId("test-provider")));
        }

        QueryDefinition query = QueryDefinition.all(
                new PortfolioQueryScope(fixture.portfolioId()),
                QueryEntityType.PORTFOLIO_MEMBERSHIP,
                QueryPage.first(1));

        QueryValidationException failure = assertThrows(QueryValidationException.class, () -> fixture.queries().execute(query));
        assertEquals("QUERY_SOURCE_BUDGET_EXCEEDED", failure.diagnostics().getFirst().code());
        assertEquals("$.scope.portfolio", failure.diagnostics().getFirst().path());
    }

    private Fixture fixture() {
        MemorySpecificationKnowledgeStore core = new MemorySpecificationKnowledgeStore();
        MemorySnapshotBusinessContentStore content = new MemorySnapshotBusinessContentStore(core, core);
        MemoryPortfolioStore portfolios = new MemoryPortfolioStore();
        PortfolioRegistryService registry = new PortfolioRegistryService(portfolios);
        var portfolio = registry.create("M24 Query Portfolio");
        QueryExecutionService queries = new QueryExecutionService(core, core, content, portfolios);
        return new Fixture(core, content, portfolios, registry, portfolio.id(), queries);
    }

    private void seedProject(Fixture fixture, ProjectSpecificationId projectId, String name, List<ChangeProposal> changes) {
        fixture.registry().registerProject(
                fixture.portfolioId(), projectId, name,
                Optional.of(SourceLocator.file("workspace/" + name.toLowerCase().replace(' ', '-'))),
                Optional.empty(), Set.of(new ProviderId("test-provider")));

        KnowledgeSnapshotId snapshotId = KnowledgeSnapshotId.generate();
        SpecificationVersionId versionId = SpecificationVersionId.generate();
        Evidence evidence = new Evidence(
                EvidenceId.generate(), SourceLocator.file("specs/changes.md"), Optional.empty(), Optional.of("sha256:test"));
        Provenance provenance = provenance(evidence);
        List<ChangeProposal> normalized = changes.stream()
                .map(item -> new ChangeProposal(
                        item.id(), item.projectId(), item.key(), item.title(), item.intent(), item.scope(), item.outOfScope(), item.risks(), provenance))
                .toList();

        fixture.core().putProject(new ProjectStoreEntry(projectId, SourceLocator.file("workspace/" + projectId)));
        fixture.core().putSnapshot(new KnowledgeSnapshotMetadata(
                snapshotId, projectId, Optional.empty(), KnowledgeSnapshotState.READY, Optional.of("rev-1"), NOW));
        fixture.core().putSpecificationVersion(new SpecificationVersion(
                versionId, projectId, Optional.of(1L), Optional.of("provider-v1"), Optional.of("rev-1"), NOW, Optional.empty()));
        fixture.core().bindSnapshotVersion(new SnapshotSpecificationVersionBinding(snapshotId, versionId));
        fixture.content().putSnapshotContent(new SnapshotBusinessContent(
                snapshotId, versionId, List.of(), List.of(), normalized, List.of(), List.of(), List.of(), List.of(evidence)));
        fixture.core().activateSnapshot(snapshotId, Optional.empty());
    }

    private ChangeProposal change(ProjectSpecificationId projectId, String id, String key, String title, String intent) {
        Evidence evidence = new Evidence(
                EvidenceId.generate(), SourceLocator.file("fixture"), Optional.empty(), Optional.empty());
        return new ChangeProposal(
                ChangeId.parse(id), projectId, Optional.of(key), title, intent,
                List.of("scope"), List.of(), List.of(), provenance(evidence));
    }

    private Provenance provenance(Evidence evidence) {
        return new Provenance(
                new ProviderId("test-provider"), Optional.of("1"), evidence.source(),
                Optional.empty(), Optional.of("rev-1"), evidence.id());
    }

    private record Fixture(
            MemorySpecificationKnowledgeStore core,
            MemorySnapshotBusinessContentStore content,
            MemoryPortfolioStore portfolios,
            PortfolioRegistryService registry,
            com.morpheus.domain.portfolio.PortfolioId portfolioId,
            QueryExecutionService queries) {
    }
}
