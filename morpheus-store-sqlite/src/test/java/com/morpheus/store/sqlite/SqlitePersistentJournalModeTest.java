package com.morpheus.store.sqlite;

import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SqlitePersistentJournalModeTest {

    @Test
    void alreadyPersistentModeDoesNotRequestAWriteTransition() {
        JournalModeStatement fixture = statement(result(true, "PERSIST"));

        assertDoesNotThrow(() -> ensurePersistentJournalMode(fixture.statement()));
        assertEquals(List.of("PRAGMA journal_mode"), fixture.queries());
    }

    @Test
    void nonPersistentModeTransitionsToPersist() {
        JournalModeStatement fixture = statement(result(true, "delete"), result(true, "persist"));

        assertDoesNotThrow(() -> ensurePersistentJournalMode(fixture.statement()));
        assertEquals(List.of("PRAGMA journal_mode", "PRAGMA journal_mode = PERSIST"), fixture.queries());
    }

    @Test
    void missingCurrentModeRowStillAttemptsTheRequiredTransition() {
        JournalModeStatement fixture = statement(result(false, null), result(true, "persist"));

        assertDoesNotThrow(() -> ensurePersistentJournalMode(fixture.statement()));
        assertEquals(List.of("PRAGMA journal_mode", "PRAGMA journal_mode = PERSIST"), fixture.queries());
    }

    @Test
    void transitionWithoutAResultRowFailsClosed() {
        JournalModeStatement fixture = statement(result(true, "delete"), result(false, null));

        assertThrows(SQLException.class, () -> ensurePersistentJournalMode(fixture.statement()));
    }

    @Test
    void transitionThatDoesNotReachPersistFailsClosed() {
        JournalModeStatement fixture = statement(result(true, "wal"), result(true, "delete"));

        assertThrows(SQLException.class, () -> ensurePersistentJournalMode(fixture.statement()));
    }

    private static void ensurePersistentJournalMode(Statement statement) throws Exception {
        Method method = SqliteDatabaseSecurity.class.getDeclaredMethod("ensurePersistentJournalMode", Statement.class);
        method.setAccessible(true);
        try {
            method.invoke(null, statement);
        } catch (InvocationTargetException failure) {
            if (failure.getCause() instanceof SQLException sqlFailure) {
                throw sqlFailure;
            }
            if (failure.getCause() instanceof RuntimeException runtimeFailure) {
                throw runtimeFailure;
            }
            throw failure;
        }
    }

    private static JournalModeStatement statement(ResultSet... results) {
        ArrayDeque<ResultSet> remaining = new ArrayDeque<>(List.of(results));
        List<String> queries = new ArrayList<>();
        Statement statement = (Statement) Proxy.newProxyInstance(
                Statement.class.getClassLoader(),
                new Class<?>[]{Statement.class},
                (proxy, method, args) -> {
                    if ("executeQuery".equals(method.getName())) {
                        queries.add((String) args[0]);
                        if (remaining.isEmpty()) {
                            throw new AssertionError("Unexpected journal-mode query: " + args[0]);
                        }
                        return remaining.removeFirst();
                    }
                    if ("toString".equals(method.getName())) {
                        return "JournalModeStatement";
                    }
                    throw new UnsupportedOperationException("Unexpected Statement method: " + method.getName());
                });
        return new JournalModeStatement(statement, queries);
    }

    private static ResultSet result(boolean hasRow, String mode) {
        boolean[] first = {true};
        return (ResultSet) Proxy.newProxyInstance(
                ResultSet.class.getClassLoader(),
                new Class<?>[]{ResultSet.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "next" -> {
                        boolean available = first[0] && hasRow;
                        first[0] = false;
                        yield available;
                    }
                    case "getString" -> mode;
                    case "close" -> null;
                    case "toString" -> "JournalModeResult[" + mode + "]";
                    default -> throw new UnsupportedOperationException("Unexpected ResultSet method: " + method.getName());
                });
    }

    private record JournalModeStatement(Statement statement, List<String> queries) {
    }
}
