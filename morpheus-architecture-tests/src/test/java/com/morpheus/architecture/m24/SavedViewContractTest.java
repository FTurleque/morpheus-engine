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
import com.morpheus.application.query.saved.SavedViewDefinition;
import com.morpheus.application.query.saved.SavedViewId;
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
import java.time.ZoneId;
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

    @Test
    void writesAfterBackwardWallClockStepAreAcceptedWithoutRewindingRevisionTime() {
        Instant createdAt = Instant.parse("2026-07-28T19:30:00Z");
        SteppableClock clock = new SteppableClock(createdAt);
        Fixture fixture = fixture(clock);
        QueryDefinition query = QueryDefinition.all(
                new ProjectQueryScope(PROJECT), QueryEntityType.CHANGE, QueryPage.first(10));
        var view = fixture.views().create("Clock step", query);

        clock.set(createdAt.minusMillis(1_500));
        var updated = fixture.views().update(view.id(), 1L, "Clock step v2", query);
        clock.set(createdAt.minusSeconds(30));
        var archived = fixture.views().archive(view.id(), 2L);

        assertEquals(2L, updated.revision());
        assertEquals(createdAt, updated.createdAt());
        assertEquals(createdAt, updated.updatedAt());
        assertEquals(3L, archived.revision());
        assertEquals(createdAt, archived.updatedAt());
        assertEquals(
                List.of(createdAt, createdAt, createdAt),
                fixture.views().versions(view.id()).stream().map(item -> item.recordedAt()).toList());

        clock.set(createdAt.plusSeconds(60));
        var other = fixture.views().create("Clock recovered", query);
        var recovered = fixture.views().update(other.id(), 1L, "Clock recovered v2", query);
        assertEquals(createdAt.plusSeconds(60), recovered.updatedAt());
    }

    @Test
    void persistedDefinitionWhoseUpdateTimePrecedesCreationStaysRejected() {
        QueryDefinition query = QueryDefinition.all(
                new ProjectQueryScope(PROJECT), QueryEntityType.CHANGE, QueryPage.first(10));
        Instant createdAt = Instant.parse("2026-07-28T19:30:00Z");

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> new SavedViewDefinition(
                SavedViewId.generate(), "Corrupt", query, 2L, SavedViewStatus.ACTIVE,
                createdAt, createdAt.minusNanos(1)));

        assertEquals("updatedAt must not precede createdAt", failure.getMessage());
    }

    private Fixture fixture() {
        return fixture(CLOCK);
    }

    private Fixture fixture(Clock clock) {
        MemorySpecificationKnowledgeStore core = new MemorySpecificationKnowledgeStore();
        MemorySnapshotBusinessContentStore content = new MemorySnapshotBusinessContentStore(core, core);
        QueryExecutionService execution = new QueryExecutionService(core, core, content, new MemoryPortfolioStore());
        SavedViewService views = new SavedViewService(
                new MemorySavedViewStore(), execution, new com.morpheus.application.query.dsl.QueryValidator(), clock);
        return new Fixture(views);
    }

    private record Fixture(SavedViewService views) {
    }

    private static final class SteppableClock extends Clock {
        private Instant instant;

        private SteppableClock(Instant instant) {
            this.instant = instant;
        }

        private void set(Instant next) {
            instant = next;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return Clock.fixed(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
