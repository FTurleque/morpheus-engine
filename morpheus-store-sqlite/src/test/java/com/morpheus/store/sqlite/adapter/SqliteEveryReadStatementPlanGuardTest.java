package com.morpheus.store.sqlite.adapter;

import com.morpheus.store.sqlite.adapter.SqliteQueryPlanGuardTest.Guarded;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every {@code SELECT} written as a whole literal in the store sources is explained through the product's driver on a
 * migrated database, and refused when its plan scans a table or sorts in a temporary tree without an excuse that
 * says why. The text explained is the text the store executes: it is read from the source, not copied. A statement
 * assembled by concatenation is not a whole literal and is guarded where its builder is named
 * ({@link SqliteQueryPlanGuardTest}).
 */
class SqliteEveryReadStatementPlanGuardTest {
    private static final Path SOURCES = Path.of("src/main/java/com/morpheus/store/sqlite");
    private static final Pattern TEXT_BLOCK = Pattern.compile("\"\"\"\\R(.*?)\"\"\"", Pattern.DOTALL);
    private static final Pattern LINE_LITERAL = Pattern.compile("\"((?:SELECT|WITH) [^\"\\n]*)\"");

    /** The reason each statement that scans or sorts may. A fragment names exactly one statement. */
    private static final Map<String, String> EXCUSED = Map.ofEntries(
            Map.entry("FROM schema_migrations ORDER BY version",
                    "the ledger holds one row per migration, a few dozen in all, read once to verify a backup"),
            Map.entry("FROM projects ORDER BY id",
                    "the registry listing walks the primary key in order, so nothing is sorted, but it reads every "
                            + "project and no count budget bounds the registry"),
            Map.entry("FROM portfolios ORDER BY id",
                    "the listing walks the primary key in order, so nothing is sorted, but it reads every portfolio "
                            + "and no count budget bounds them; the query service pages the list in memory"),
            Map.entry("FROM policy_packs ORDER BY id",
                    "the listing walks the primary key in order, so nothing is sorted, but it reads every pack and "
                            + "no count budget bounds them"),
            Map.entry("FROM composition_provider_state WHERE snapshot_id = ? ORDER BY priority DESC, provider_id ASC",
                    "one row per provider that took part in the composition of one snapshot; no index carries the "
                            + "priority, and the sort covers that handful of rows"),
            Map.entry("FROM knowledge_snapshots WHERE project_id = ? ORDER BY created_at, id",
                    "known gap, not an accepted cost: every snapshot of a project is read and sorted on created_at, "
                            + "an Instant.toString() text whose order is not the order of time "
                            + "(filter-and-order-a-store-read-once, open question 2)"),
            Map.entry("FROM requirement_versions WHERE snapshot_id = ? ORDER BY entity_version_id",
                    "every temporal state of one snapshot, read only by the delta application and promotion "
                            + "services, which hold all the rows in memory; the CURRENT reads that serve queries are "
                            + "served in order by idx_requirement_versions_current_order"));

    @TempDir
    Path tempDir;

    @Test
    void everyWholeLiteralSelectIsServedByAnIndexOrExcusedWithAReason() throws Exception {
        Path database = tempDir.resolve("migrated.db");
        try (var ignored = new SqliteSpecificationKnowledgeStore(database)) {
            // The constructor applies every migration.
        }

        List<Statement> statements = statements();
        assertFalse(statements.isEmpty(), "no statement was read from " + SOURCES.toAbsolutePath());
        List<String> problems = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database.toAbsolutePath())) {
            for (Statement statement : statements) {
                String excuse = excuseOf(statement, problems);
                problems.addAll(SqliteQueryPlanGuardTest.problems(
                        connection, new Guarded(statement.location(), statement.sql(), excuse)));
            }
        }
        EXCUSED.keySet().forEach(fragment -> {
            long matches = statements.stream().filter(statement -> statement.sql().contains(fragment)).count();
            if (matches != 1) {
                problems.add("the excuse \"" + fragment + "\" names " + matches + " statements, it must name exactly one");
            }
        });
        assertEquals(List.of(), problems);
    }

    @Test
    void theReaderFindsTheStatementsItIsMeantToGuard() throws IOException {
        List<String> sql = statements().stream().map(Statement::sql).toList();

        assertTrue(sql.stream().anyMatch(text ->
                        text.contains("FROM requirement_versions WHERE snapshot_id = ? AND temporal_state = 'CURRENT'")),
                "the CURRENT requirement reads must be read from the sources");
        assertTrue(sql.stream().anyMatch(text ->
                        text.contains("FROM saved_views WHERE scope_kind = ? AND scope_id = ? ORDER BY name COLLATE NOCASE")),
                "the saved view listing must be read from the sources");
        assertTrue(sql.size() > 40, "the sources hold well over forty guarded statements, found " + sql.size());
    }

    private static String excuseOf(Statement statement, List<String> problems) {
        List<String> fragments = EXCUSED.keySet().stream().filter(statement.sql()::contains).toList();
        if (fragments.size() > 1) {
            problems.add(statement.location() + ": " + fragments.size() + " excuses name this statement");
        }
        return fragments.size() == 1 ? EXCUSED.get(fragments.getFirst()) : null;
    }

    private record Statement(String location, String sql) {
    }

    private static List<Statement> statements() throws IOException {
        List<Statement> statements = new ArrayList<>();
        try (Stream<Path> files = Files.walk(SOURCES)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
                String source = Files.readString(file).replace("\r\n", "\n");
                collect(file, source, TEXT_BLOCK, statements);
                collect(file, source, LINE_LITERAL, statements);
            }
        }
        return statements;
    }

    private static void collect(Path file, String source, Pattern pattern, List<Statement> statements) {
        Matcher literal = pattern.matcher(source);
        while (literal.find()) {
            if (isPartOfAConcatenation(source, literal.start(), literal.end())) {
                continue;
            }
            String sql = literal.group(1).replaceAll("\\s+", " ").trim();
            if (!sql.startsWith("SELECT ") && !sql.startsWith("WITH ")) {
                continue;
            }
            if (!sql.contains(" WHERE ") && !sql.contains(" ORDER BY ") && !sql.contains(" JOIN ")) {
                continue;
            }
            int line = (int) source.substring(0, literal.start()).chars().filter(c -> c == '\n').count() + 1;
            statements.add(new Statement(file.getFileName() + ":" + line, sql));
        }
    }

    private static boolean isPartOfAConcatenation(String source, int start, int end) {
        int before = start - 1;
        while (before >= 0 && Character.isWhitespace(source.charAt(before))) {
            before--;
        }
        int after = end;
        while (after < source.length() && Character.isWhitespace(source.charAt(after))) {
            after++;
        }
        return (before >= 0 && source.charAt(before) == '+')
                || (after < source.length() && source.charAt(after) == '+');
    }
}
