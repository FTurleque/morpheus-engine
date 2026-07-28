package com.morpheus.architecture.m24;

import com.morpheus.application.query.dsl.ProjectQueryScope;
import com.morpheus.application.query.dsl.QueryAnd;
import com.morpheus.application.query.dsl.QueryDefinition;
import com.morpheus.application.query.dsl.QueryDefinitionCodec;
import com.morpheus.application.query.dsl.QueryEntityType;
import com.morpheus.application.query.dsl.QueryOperator;
import com.morpheus.application.query.dsl.QueryPage;
import com.morpheus.application.query.dsl.QueryPredicate;
import com.morpheus.application.query.dsl.QueryProjection;
import com.morpheus.application.query.dsl.QuerySort;
import com.morpheus.application.query.dsl.QuerySortDirection;
import com.morpheus.application.query.saved.SavedViewDefinition;
import com.morpheus.application.query.saved.SavedViewId;
import com.morpheus.application.query.saved.SavedViewStatus;
import com.morpheus.application.query.saved.SavedViewVersion;
import com.morpheus.application.store.SavedViewStore;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.store.memory.MemorySavedViewStore;
import com.morpheus.store.sqlite.SqliteSavedViewStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SavedViewPersistenceParityTest {
    private static final ProjectSpecificationId PROJECT =
            ProjectSpecificationId.parse("01890f7a-36d4-7c1e-8000-000000000051");
    private static final SavedViewId ID =
            SavedViewId.parse("01890f7a-36d4-7c1e-8000-000000000052");
    private static final Instant T1 = Instant.parse("2026-07-28T19:40:00Z");
    private static final Instant T2 = Instant.parse("2026-07-28T19:41:00Z");

    @TempDir
    Path tempDir;

    @Test
    void queryDefinitionCodecRoundTripsNestedAstDeterministically() {
        QueryDefinition query = query();
        QueryDefinitionCodec codec = new QueryDefinitionCodec();

        String first = codec.encode(query);
        String second = codec.encode(query);

        assertEquals(first, second);
        assertEquals(query, codec.decode(first));
    }

    @Test
    void memoryAndSqlitePreserveSameCurrentAndVersionHistory() {
        SavedViewDefinition initial = initial();
        SavedViewDefinition updated = updated(initial);
        SavedViewVersion v1 = version(initial);
        SavedViewVersion v2 = version(updated);

        SavedViewStore memory = new MemorySavedViewStore();
        memory.create(initial, v1);
        memory.compareAndSet(ID, 1L, updated, v2);

        Path database = tempDir.resolve("saved-views.db");
        try (SqliteSavedViewStore sqlite = new SqliteSavedViewStore(database)) {
            sqlite.create(initial, v1);
            sqlite.compareAndSet(ID, 1L, updated, v2);
            assertEquals(memory.find(ID), sqlite.find(ID));
            assertEquals(memory.list(initial.query().scope()), sqlite.list(initial.query().scope()));
            assertEquals(memory.listVersions(ID), sqlite.listVersions(ID));
            assertEquals(memory.count(initial.query().scope()), sqlite.count(initial.query().scope()));
        }

        try (SqliteSavedViewStore reopened = new SqliteSavedViewStore(database)) {
            assertEquals(Optional.of(updated), reopened.find(ID));
            assertEquals(List.of(v1, v2), reopened.listVersions(ID));
            assertEquals(1L, reopened.count(initial.query().scope()));
        }
    }

    private QueryDefinition query() {
        return new QueryDefinition(
                new ProjectQueryScope(PROJECT),
                QueryEntityType.CHANGE,
                Optional.of(new QueryAnd(List.of(
                        QueryPredicate.unary("title", QueryOperator.CONTAINS, "security"),
                        new QueryPredicate("providerId", QueryOperator.IN, List.of("openspec", "markdown"))))),
                List.of(
                        new QuerySort("title", QuerySortDirection.ASC),
                        new QuerySort("id", QuerySortDirection.DESC)),
                new QueryProjection(List.of("id", "title", "intent")),
                new QueryPage(10, 25));
    }

    private SavedViewDefinition initial() {
        return new SavedViewDefinition(ID, "Security", query(), 1L, SavedViewStatus.ACTIVE, T1, T1);
    }

    private SavedViewDefinition updated(SavedViewDefinition initial) {
        QueryDefinition updatedQuery = new QueryDefinition(
                initial.query().scope(), initial.query().entityType(), initial.query().filter(),
                List.of(new QuerySort("title", QuerySortDirection.DESC)),
                initial.query().projection(), QueryPage.first(50));
        return new SavedViewDefinition(ID, "Security latest", updatedQuery, 2L, SavedViewStatus.ACTIVE, T1, T2);
    }

    private SavedViewVersion version(SavedViewDefinition definition) {
        return new SavedViewVersion(
                definition.id(), definition.revision(), definition.name(), definition.query(),
                definition.status(), definition.updatedAt());
    }
}
