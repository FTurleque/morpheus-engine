package com.morpheus.application.query.dsl;

import com.morpheus.application.query.saved.SavedViewId;
import com.morpheus.application.query.saved.SavedViewStatus;
import com.morpheus.application.query.saved.SavedViewVersion;
import com.morpheus.domain.portfolio.PortfolioId;
import com.morpheus.domain.project.ProjectSpecificationId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The query projections every transport shares: a result, its rows and cells, and the saved-view history. */
class QueryPublicViewsTest {
    private static final Instant NOW = Instant.parse("2026-10-30T08:00:00Z");

    @Test
    void aResultProjectsItsQueryColumnsRowsAndPagingFacts() {
        ProjectSpecificationId projectId = ProjectSpecificationId.generate();
        QueryDefinition query = new QueryDefinition(
                new ProjectQueryScope(projectId), QueryEntityType.REQUIREMENT,
                Optional.of(new QueryOr(List.of(
                        QueryPredicate.unary("key", QueryOperator.EQ, "A"),
                        new QueryNot(QueryPredicate.exists("title"))))),
                List.of(new QuerySort("key", QuerySortDirection.DESC)),
                new QueryProjection(List.of("key")), new QueryPage(2, 5));
        QueryRow row = new QueryRow(QueryEntityType.REQUIREMENT, projectId.toString(), "requirement-1",
                List.of(QueryCell.scalar("key", "A")));

        QueryPublicViews.QueryResultView view = QueryPublicViews.result(
                new QueryResult(query, List.of("key"), List.of(row), 7, true));

        assertEquals(new QueryPublicViews.ScopeView("PROJECT", projectId.toString()), view.query().scope());
        assertEquals("REQUIREMENT", view.query().entityType());
        QueryPublicViews.FilterView or = view.query().filter().orElseThrow();
        assertEquals("OR", or.kind());
        assertEquals(new QueryPublicViews.FilterView("PREDICATE", Optional.of("key"), Optional.of("EQ"), List.of("A"),
                List.of()), or.children().getFirst());
        assertEquals("NOT", or.children().get(1).kind());
        assertEquals(List.of(new QueryPublicViews.SortView("key", "DESC")), view.query().sort());
        assertEquals(new QueryPublicViews.PageView(2, 5), view.query().page());
        assertEquals(List.of("key"), view.columns());
        assertEquals(new QueryPublicViews.RowView("REQUIREMENT", projectId.toString(), "requirement-1",
                List.of(new QueryPublicViews.CellView("key", List.of("A")))), view.items().getFirst());
        assertEquals(7, view.totalMatches());
        assertEquals(true, view.hasMore());
    }

    @Test
    void aSavedViewVersionProjectsItsRevisionAndAPortfolioScope() {
        PortfolioId portfolioId = PortfolioId.generate();
        SavedViewId id = SavedViewId.generate();
        SavedViewVersion version = new SavedViewVersion(id, 2, "members",
                QueryDefinition.all(new PortfolioQueryScope(portfolioId), QueryEntityType.PORTFOLIO_MEMBERSHIP,
                        QueryPage.first(10)),
                SavedViewStatus.ACTIVE, NOW);

        QueryPublicViews.SavedViewVersionView view = QueryPublicViews.savedVersions(List.of(version)).getFirst();

        assertEquals(id.toString(), view.id());
        assertEquals(2, view.revision());
        assertEquals("members", view.name());
        assertEquals(new QueryPublicViews.ScopeView("PORTFOLIO", portfolioId.toString()), view.query().scope());
        assertEquals(Optional.empty(), view.query().filter());
        assertEquals("ACTIVE", view.status());
        assertEquals(NOW.toString(), view.recordedAt());
    }
}
