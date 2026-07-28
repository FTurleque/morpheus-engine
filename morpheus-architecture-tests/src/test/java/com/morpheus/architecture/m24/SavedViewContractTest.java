package com.morpheus.architecture.m24;

import com.morpheus.application.query.dsl.ProjectQueryScope;
import com.morpheus.application.query.dsl.QueryDefinition;
import com.morpheus.application.query.dsl.QueryEntityType;
import com.morpheus.application.query.dsl.QueryExecutionService;
import com.morpheus.application.query.dsl.QueryOperator;
import com.morpheus.application.query.dsl.QueryPage;
import com.morpheus.application.query.dsl.QueryPredicate;
import com.morpheus.application.query.dsl.QueryProjection;
import com.morpheus.application.query.dsl.QueryValidationException;
import com.morpheus.application.query.saved.SavedViewConflictException;
import com.morpheus.application.query.saved.SavedViewService;
import com.morpheus.application.query.saved.SavedViewStatus;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.store.memory.MemoryPortfolioStore;
import com.morpheus.store.memory.MemorySavedViewStore;
import com.morpheus.store.memory.MemorySnapshotBusinessContentStore;
import com.morpheus.store.memory.MemorySpecificationKnowledgeStore;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SavedViewContractTest {
    private static final ProjectSpecificationId PROJECT =
            ProjectSpecificationId.parse("01890f7a-36d4-7c1e-8000-000000000041");
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-28T19:30:00Z"), ZoneOffset.UTC);

    @Test
    void sameNameDoesNotDefineIdentityAndUpdatesPreserveHistory() {
        Fixture fixture = fixture();
        QueryDefinition initial = QueryDefinition.all(
                new ProjectQueryScope(PROJECT), QueryEntityType.CHANGE, QueryPage.first(25));

        var first = fixture.views().create("My view", initial);
        var second = fixture.views().create("My view", initial);

        assertNotEquals(first.id(), second.id());
        assertEquals(1L, first.revision());

        QueryDefinition updatedQuery = new QueryDefinition(
                initial.scope(), initial.entityType(),
                Optional.of(QueryPredicate.unary("title", QueryOperator.CONTAINS, "security")),
                List.of(), new QueryProjection(List.of("id", "title")), QueryPage.first(10));
        var updated = fixture.views().update(first.id(), 1L, "Security changes", updatedQuery);

        assertEquals(first.id(), updated.id());
        assertEquals(2L, updated.revision());
        assertEquals(2, fixture.views().versions(first.id()).size());
        assertEquals(List.of(1L, 2L), fixture.views().versions(first.id()).stream().map(item -> item.revision()).toList());
    }

    @Test
    void staleRevisionNeverSilentlyOverwrites() {
        Fixture fixture = fixture();
        QueryDefinition query = QueryDefinition.all(
                new ProjectQueryScope(PROJECT), QueryEntityType.CHANGE, QueryPage.first(10));
        var view = fixture.views().create("CAS", query);
        fixture.views().update(view.id(), 1L, "CAS v2", query);

        assertThrows(SavedViewConflictException.class,
                () -> fixture.views().update(view.id(), 1L, "stale", query));
        assertEquals("CAS v2", fixture.views().get(view.id()).name());
        assertEquals(2L, fixture.views().get(view.id()).revision());
    }

    @Test
    void invalidQueryIsRejectedBeforePersistence() {
        Fixture fixture = fixture();
        QueryDefinition invalid = new QueryDefinition(
                new ProjectQueryScope(PROJECT), QueryEntityType.CHANGE,
                Optional.of(QueryPredicate.unary("sqlite_column", QueryOperator.EQ, "x")),
                List.of(), QueryProjection.defaults(), QueryPage.first(10));

        assertThrows(QueryValidationException.class, () -> fixture.views().create("Invalid", invalid));
        assertTrue(fixture.views().list(new ProjectQueryScope(PROJECT)).isEmpty());
    }

    @Test
    void archiveIsVersionedAndExcludedFromActiveList() {
        Fixture fixture = fixture();
        QueryDefinition query = QueryDefinition.all(
                new ProjectQueryScope(PROJECT), QueryEntityType.CHANGE, QueryPage.first(10));
        var view = fixture.views().create("Archive me", query);

        var archived = fixture.views().archive(view.id(), 1L);

        assertEquals(SavedViewStatus.ARCHIVED, archived.status());
        assertEquals(2L, archived.revision());
        assertTrue(fixture.views().list(new ProjectQueryScope(PROJECT)).isEmpty());
        assertEquals(1, fixture.views().listIncludingArchived(new ProjectQueryScope(PROJECT)).size());
        assertEquals(2, fixture.views().versions(view.id()).size());
    }

    @Test
    void savedViewExecutesStoredDefinitionNotMaterializedResults() {
        Fixture fixture = fixture();
        QueryDefinition query = QueryDefinition.all(
                new ProjectQueryScope(PROJECT), QueryEntityType.CHANGE, QueryPage.first(10));
        var view = fixture.views().create("Live definition", query);

        var result = fixture.views().execute(view.id());

        assertEquals(query, result.query());
        assertEquals(0, result.totalMatches());
    }

    private Fixture fixture() {
        MemorySpecificationKnowledgeStore core = new MemorySpecificationKnowledgeStore();
        MemorySnapshotBusinessContentStore content = new MemorySnapshotBusinessContentStore(core, core);
        QueryExecutionService execution = new QueryExecutionService(core, core, content, new MemoryPortfolioStore());
        SavedViewService views = new SavedViewService(
                new MemorySavedViewStore(), execution, new com.morpheus.application.query.dsl.QueryValidator(), CLOCK);
        return new Fixture(views);
    }

    private record Fixture(SavedViewService views) {
    }
}
