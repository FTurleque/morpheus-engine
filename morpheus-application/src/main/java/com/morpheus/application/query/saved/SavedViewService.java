package com.morpheus.application.query.saved;

import com.morpheus.application.query.dsl.QueryBudgets;
import com.morpheus.application.query.dsl.QueryDefinition;
import com.morpheus.application.query.dsl.QueryExecutionService;
import com.morpheus.application.query.dsl.QueryResult;
import com.morpheus.application.query.dsl.QueryScope;
import com.morpheus.application.query.dsl.QueryValidator;
import com.morpheus.application.store.SavedViewStore;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Application authority for saved-view identity, validation, versioning, CAS and execution. */
public final class SavedViewService {
    private final SavedViewStore store;
    private final QueryValidator validator;
    private final QueryExecutionService execution;
    private final Clock clock;

    public SavedViewService(SavedViewStore store, QueryExecutionService execution) {
        this(store, execution, new QueryValidator(), Clock.systemUTC());
    }

    public SavedViewService(
            SavedViewStore store,
            QueryExecutionService execution,
            QueryValidator validator,
            Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.execution = Objects.requireNonNull(execution, "execution");
        this.validator = Objects.requireNonNull(validator, "validator");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public SavedViewDefinition create(String name, QueryDefinition query) {
        validator.requireValid(Objects.requireNonNull(query, "query"));
        if (store.count(query.scope()) >= QueryBudgets.MAX_SAVED_VIEWS_PER_SCOPE) {
            throw new IllegalStateException(
                    "saved view budget exceeded for scope: " + QueryBudgets.MAX_SAVED_VIEWS_PER_SCOPE);
        }
        Instant now = clock.instant();
        SavedViewDefinition definition = new SavedViewDefinition(
                SavedViewId.generate(), name, query, 1L, SavedViewStatus.ACTIVE, now, now);
        store.create(definition, version(definition));
        return definition;
    }

    public SavedViewDefinition get(SavedViewId id) {
        return store.find(Objects.requireNonNull(id, "id"))
                .orElseThrow(() -> new IllegalArgumentException("unknown saved view: " + id));
    }

    public List<SavedViewDefinition> list(QueryScope scope) {
        return store.list(Objects.requireNonNull(scope, "scope")).stream()
                .filter(item -> item.status() == SavedViewStatus.ACTIVE)
                .toList();
    }

    public List<SavedViewDefinition> listIncludingArchived(QueryScope scope) {
        return store.list(Objects.requireNonNull(scope, "scope"));
    }

    public List<SavedViewVersion> versions(SavedViewId id) {
        get(id);
        return store.listVersions(id);
    }

    public SavedViewDefinition update(
            SavedViewId id,
            long expectedRevision,
            String name,
            QueryDefinition query) {
        SavedViewDefinition current = get(id);
        requireActive(current);
        validator.requireValid(Objects.requireNonNull(query, "query"));
        if (!current.query().scope().equals(query.scope())) {
            throw new IllegalArgumentException("saved view scope is immutable");
        }
        SavedViewDefinition replacement = new SavedViewDefinition(
                current.id(), name, query, expectedRevision + 1, current.status(), current.createdAt(), clock.instant());
        return store.compareAndSet(id, expectedRevision, replacement, version(replacement));
    }

    public SavedViewDefinition archive(SavedViewId id, long expectedRevision) {
        SavedViewDefinition current = get(id);
        requireActive(current);
        SavedViewDefinition replacement = new SavedViewDefinition(
                current.id(), current.name(), current.query(), expectedRevision + 1,
                SavedViewStatus.ARCHIVED, current.createdAt(), clock.instant());
        return store.compareAndSet(id, expectedRevision, replacement, version(replacement));
    }

    public QueryResult execute(SavedViewId id) {
        SavedViewDefinition definition = get(id);
        requireActive(definition);
        return execution.execute(definition.query());
    }

    private SavedViewVersion version(SavedViewDefinition definition) {
        return new SavedViewVersion(
                definition.id(), definition.revision(), definition.name(), definition.query(),
                definition.status(), definition.updatedAt());
    }

    private void requireActive(SavedViewDefinition definition) {
        if (definition.status() != SavedViewStatus.ACTIVE) {
            throw new IllegalStateException("saved view is archived: " + definition.id());
        }
    }
}
