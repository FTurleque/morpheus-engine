package com.morpheus.application.query.saved;

import com.morpheus.application.query.PublishedSnapshotWorld;
import com.morpheus.application.query.dsl.PortfolioQueryScope;
import com.morpheus.application.query.dsl.ProjectQueryScope;
import com.morpheus.application.query.dsl.QueryBudgets;
import com.morpheus.application.query.dsl.QueryDefinition;
import com.morpheus.application.query.dsl.QueryEntityType;
import com.morpheus.application.query.dsl.QueryExecutionService;
import com.morpheus.application.query.dsl.QueryPage;
import com.morpheus.application.query.dsl.QueryProjection;
import com.morpheus.application.query.dsl.QueryResult;
import com.morpheus.application.query.dsl.QueryScope;
import com.morpheus.application.query.dsl.QuerySort;
import com.morpheus.application.query.dsl.QuerySortDirection;
import com.morpheus.application.query.dsl.QueryValidationException;
import com.morpheus.application.query.dsl.QueryValidator;
import com.morpheus.application.store.EntityNotFoundException;
import com.morpheus.application.store.PortfolioStore;
import com.morpheus.domain.portfolio.PortfolioId;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SavedViewServiceTest {
    private static final Instant T0 = Instant.parse("2026-10-30T09:00:00Z");

    private final PublishedSnapshotWorld world = new PublishedSnapshotWorld();
    private final MutableClock clock = new MutableClock(T0);
    private final RecordingStore store = new RecordingStore();
    private final SavedViewService service = new SavedViewService(store, execution(), new QueryValidator(), clock);
    private final QueryScope scope = new ProjectQueryScope(world.projectId());

    @Test
    void aCreatedViewStartsActiveAtRevisionOneAndRecordsThatRevision() {
        SavedViewDefinition created = service.create("  Open changes ", changes(scope));

        assertEquals("Open changes", created.name());
        assertEquals(1L, created.revision());
        assertEquals(SavedViewStatus.ACTIVE, created.status());
        assertEquals(T0, created.createdAt());
        assertEquals(T0, created.updatedAt());
        assertEquals(created, service.get(created.id()));
        assertEquals(List.of(new SavedViewVersion(created.id(), 1L, "Open changes", changes(scope),
                SavedViewStatus.ACTIVE, T0)), service.versions(created.id()));
    }

    @Test
    void anInvalidQueryAndAFullScopeAreRefusedBeforeAnythingIsStored() {
        QueryDefinition unknownSort = new QueryDefinition(scope, QueryEntityType.CHANGE, Optional.empty(),
                List.of(new QuerySort("nope", QuerySortDirection.ASC)), QueryProjection.defaults(),
                QueryPage.first(10));

        QueryValidationException invalid = assertThrows(QueryValidationException.class,
                () -> service.create("invalid", unknownSort));
        assertEquals("QUERY_FIELD_UNKNOWN", invalid.diagnostics().getFirst().code());

        store.countOverride = Optional.of((long) QueryBudgets.MAX_SAVED_VIEWS_PER_SCOPE);
        IllegalStateException full = assertThrows(IllegalStateException.class,
                () -> service.create("one too many", changes(scope)));
        assertEquals("saved view budget exceeded for scope: " + QueryBudgets.MAX_SAVED_VIEWS_PER_SCOPE,
                full.getMessage());
        assertTrue(store.definitions.isEmpty());
    }

    @Test
    void anUpdateAtTheExpectedRevisionReplacesTheViewAndAStaleWriterFailsExplicitly() {
        SavedViewDefinition created = service.create("view", changes(scope));
        clock.instant = T0.plusSeconds(60);

        SavedViewDefinition updated = service.update(created.id(), 1L, "renamed", requirements(scope));

        assertEquals(2L, updated.revision());
        assertEquals("renamed", updated.name());
        assertEquals(QueryEntityType.REQUIREMENT, updated.query().entityType());
        assertEquals(T0, updated.createdAt());
        assertEquals(T0.plusSeconds(60), updated.updatedAt());
        assertEquals(List.of(1L, 2L), service.versions(created.id()).stream().map(SavedViewVersion::revision).toList());

        SavedViewConflictException stale = assertThrows(SavedViewConflictException.class,
                () -> service.update(created.id(), 1L, "lost update", changes(scope)));
        assertEquals("stale saved view revision for " + created.id() + ": expected 1, current 2", stale.getMessage());
        assertEquals("renamed", service.get(created.id()).name());
    }

    @Test
    void aRevisionIsNeverStampedBeforeTheRevisionItReplacesWhenTheClockStepsBack() {
        SavedViewDefinition created = service.create("view", changes(scope));
        clock.instant = T0.minusSeconds(3600);

        SavedViewDefinition updated = service.update(created.id(), 1L, "view", changes(scope));

        assertEquals(T0, updated.updatedAt());
    }

    @Test
    void theScopeOfAViewIsImmutableAndAnArchivedViewIsNeitherUpdatedNorExecuted() {
        SavedViewDefinition created = service.create("view", changes(scope));
        QueryScope portfolio = new PortfolioQueryScope(new PortfolioId(world.next()));

        IllegalArgumentException moved = assertThrows(IllegalArgumentException.class,
                () -> service.update(created.id(), 1L, "view", changes(portfolio)));
        assertEquals("saved view scope is immutable", moved.getMessage());

        clock.instant = T0.plusSeconds(5);
        SavedViewEntry archived = service.archive(created.id(), 1L);
        assertEquals(SavedViewStatus.ARCHIVED, archived.status());
        assertEquals(T0.plusSeconds(5), archived.updatedAt());
        IllegalStateException update = assertThrows(IllegalStateException.class,
                () -> service.update(created.id(), 2L, "view", changes(scope)));
        assertEquals("saved view is archived: " + created.id(), update.getMessage());
        IllegalStateException execute = assertThrows(IllegalStateException.class,
                () -> service.execute(created.id()));
        assertEquals("saved view is archived: " + created.id(), execute.getMessage());
        SavedViewConflictException twice = assertThrows(SavedViewConflictException.class,
                () -> service.archive(created.id(), 2L));
        assertEquals("saved view is already archived: " + created.id(), twice.getMessage());
    }

    @Test
    void aListingHidesArchivedViewsUnlessAskedToIncludeThem() {
        SavedViewDefinition kept = service.create("kept", changes(scope));
        SavedViewDefinition retired = service.create("retired", changes(scope));
        service.archive(retired.id(), 1L);

        assertEquals(List.of(kept.id()), service.list(scope).stream().map(SavedViewEntry::id).toList());
        assertEquals(List.of(kept.id(), retired.id()),
                service.listIncludingArchived(scope).stream().map(SavedViewEntry::id).toList());
    }

    @Test
    void anActiveViewExecutesItsQueryAgainstThePublishedFacts() {
        world.change("only");
        SavedViewDefinition created = service.create("changes", changes(scope));

        QueryResult result = service.execute(created.id());

        assertEquals(changes(scope), result.query());
        assertEquals(1, result.totalMatches());
        assertEquals(1, result.items().size());
    }

    @Test
    void anUnknownViewIsNamedOnEveryRead() {
        SavedViewId unknown = new SavedViewId(world.next());

        EntityNotFoundException get = assertThrows(EntityNotFoundException.class, () -> service.get(unknown));
        assertEquals("unknown saved view: " + unknown, get.getMessage());
        EntityNotFoundException versions = assertThrows(EntityNotFoundException.class,
                () -> service.versions(unknown));
        assertEquals("unknown saved view: " + unknown, versions.getMessage());
        EntityNotFoundException execute = assertThrows(EntityNotFoundException.class,
                () -> service.execute(unknown));
        assertEquals("unknown saved view: " + unknown, execute.getMessage());
        EntityNotFoundException withDefaults = assertThrows(EntityNotFoundException.class,
                () -> new SavedViewService(store, execution()).get(unknown));
        assertEquals("unknown saved view: " + unknown, withDefaults.getMessage());
    }

    @Test
    void aVersionRefusesANonPositiveRevisionAndABlankNameAndOrdersByIdThenRevision() {
        SavedViewId first = new SavedViewId(world.next());
        SavedViewId second = new SavedViewId(world.next());
        QueryDefinition query = changes(scope);

        IllegalArgumentException revision = assertThrows(IllegalArgumentException.class,
                () -> new SavedViewVersion(first, 0L, "name", query, SavedViewStatus.ACTIVE, T0));
        assertEquals("revision must be greater than zero", revision.getMessage());
        IllegalArgumentException blank = assertThrows(IllegalArgumentException.class,
                () -> new SavedViewVersion(first, 1L, "  ", query, SavedViewStatus.ACTIVE, T0));
        assertEquals("name must not be blank", blank.getMessage());
        SavedViewVersion a1 = new SavedViewVersion(first, 1L, " a ", query, SavedViewStatus.ACTIVE, T0);
        SavedViewVersion a2 = new SavedViewVersion(first, 2L, "a", query, SavedViewStatus.ARCHIVED, T0);
        SavedViewVersion b1 = new SavedViewVersion(second, 1L, "b", query, SavedViewStatus.ACTIVE, T0);

        assertEquals("a", a1.name());
        assertEquals(List.of(a1, a2, b1), new ArrayList<>(List.of(b1, a2, a1)).stream().sorted().toList());
    }

    private QueryDefinition changes(QueryScope target) {
        return QueryDefinition.all(target, QueryEntityType.CHANGE, QueryPage.first(10));
    }

    private QueryDefinition requirements(QueryScope target) {
        return QueryDefinition.all(target, QueryEntityType.REQUIREMENT, QueryPage.first(10));
    }

    private QueryExecutionService execution() {
        return new QueryExecutionService(world.snapshots(), world.requirementStore(), world.contentStore(),
                unreadPortfolios());
    }

    private static PortfolioStore unreadPortfolios() {
        return (PortfolioStore) Proxy.newProxyInstance(SavedViewServiceTest.class.getClassLoader(),
                new Class<?>[]{PortfolioStore.class},
                (proxy, method, args) -> {
                    throw new AssertionError("a project-scoped view never reads portfolios: " + method.getName());
                });
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    /** A store that holds the current definition and the history of each view, and refuses a stale revision. */
    private static final class RecordingStore implements SavedViewStore {
        private final Map<SavedViewId, SavedViewDefinition> definitions = new LinkedHashMap<>();
        private final List<SavedViewVersion> versions = new ArrayList<>();
        private Optional<Long> countOverride = Optional.empty();

        @Override
        public void create(SavedViewDefinition definition, SavedViewVersion version) {
            definitions.put(definition.id(), definition);
            versions.add(version);
        }

        @Override
        public Optional<SavedViewDefinition> find(SavedViewId id) {
            return Optional.ofNullable(definitions.get(id));
        }

        @Override
        public List<SavedViewEntry> list(QueryScope scope) {
            return definitions.values().stream()
                    .filter(definition -> definition.query().scope().equals(scope))
                    .sorted()
                    .<SavedViewEntry>map(SavedViewEntry.Readable::new)
                    .toList();
        }

        @Override
        public List<SavedViewVersion> listVersions(SavedViewId id) {
            return versions.stream().filter(version -> version.id().equals(id)).sorted().toList();
        }

        @Override
        public long count(QueryScope scope) {
            return countOverride.orElseGet(() -> (long) list(scope).size());
        }

        @Override
        public SavedViewEntry archive(SavedViewId id, long expectedRevision, Instant at) {
            SavedViewDefinition current = find(id)
                    .orElseThrow(() -> new EntityNotFoundException("unknown saved view: " + id));
            if (current.status() == SavedViewStatus.ARCHIVED) {
                throw new SavedViewConflictException("saved view is already archived: " + id);
            }
            requireRevision(current, expectedRevision);
            SavedViewDefinition archived = new SavedViewDefinition(id, current.name(), current.query(),
                    expectedRevision + 1, SavedViewStatus.ARCHIVED, current.createdAt(), at);
            definitions.put(id, archived);
            return new SavedViewEntry.Readable(archived);
        }

        @Override
        public SavedViewDefinition compareAndSet(
                SavedViewId id, long expectedRevision, SavedViewDefinition replacement, SavedViewVersion version) {
            requireRevision(definitions.get(id), expectedRevision);
            definitions.put(id, replacement);
            versions.add(version);
            return replacement;
        }

        private static void requireRevision(SavedViewDefinition current, long expectedRevision) {
            if (current.revision() != expectedRevision) {
                throw new SavedViewConflictException("stale saved view revision for " + current.id()
                        + ": expected " + expectedRevision + ", current " + current.revision());
            }
        }
    }
}
