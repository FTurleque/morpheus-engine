package com.morpheus.architecture.m24;

import com.morpheus.application.query.dsl.ProjectQueryScope;
import com.morpheus.application.query.dsl.QueryAnd;
import com.morpheus.application.query.dsl.QueryDslParser;
import com.morpheus.application.query.dsl.QueryOperator;
import com.morpheus.application.query.dsl.QueryPredicate;
import com.morpheus.application.query.dsl.QuerySortDirection;
import com.morpheus.domain.project.ProjectSpecificationId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class QueryDslParserContractTest {
    private static final ProjectQueryScope SCOPE = new ProjectQueryScope(
            ProjectSpecificationId.parse("01890f7a-36d4-7c1e-8000-000000000061"));

    @Test
    void parsesNestedFilterSortProjectionAndPageIntoExplicitAst() {
        var query = new QueryDslParser().parse(
                SCOPE,
                "change",
                "and(title contains \"security login\",providerId in [openspec,\"markdown\"])",
                "title:desc,id:asc",
                "id,title,intent",
                5,
                25);

        QueryAnd root = (QueryAnd) query.filter().orElseThrow();
        assertEquals(QueryOperator.CONTAINS, ((QueryPredicate) root.children().getFirst()).operator());
        assertEquals(List.of("openspec", "markdown"), ((QueryPredicate) root.children().get(1)).values());
        assertEquals(QuerySortDirection.DESC, query.sort().getFirst().direction());
        assertEquals(List.of("id", "title", "intent"), query.projection().fields());
        assertEquals(5, query.page().offset());
        assertEquals(25, query.page().limit());
    }

    @Test
    void rejectsSqlAndUnknownBusinessFieldsBeforeExecution() {
        QueryDslParser parser = new QueryDslParser();

        assertThrows(IllegalArgumentException.class, () -> parser.parse(
                SCOPE, "change", "sql eq \"SELECT * FROM snapshot_changes\"", null, null, 0, 10));
        assertThrows(IllegalArgumentException.class, () -> parser.parse(
                SCOPE, "change", "sqlite_column eq value", null, null, 0, 10));
    }

    @Test
    void rejectsMalformedOrOverNestedTextInsteadOfPartiallyExecuting() {
        QueryDslParser parser = new QueryDslParser();
        assertThrows(IllegalArgumentException.class, () -> parser.parse(
                SCOPE, "change", "and(title eq x)", null, null, 0, 10));

        String filter = "title eq x";
        for (int index = 0; index < 9; index++) {
            filter = "not(" + filter + ")";
        }
        String excessive = filter;
        assertThrows(IllegalArgumentException.class, () -> parser.parse(
                SCOPE, "change", excessive, null, null, 0, 10));
    }
}
