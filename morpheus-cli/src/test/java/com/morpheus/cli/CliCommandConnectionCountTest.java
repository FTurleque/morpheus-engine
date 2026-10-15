package com.morpheus.cli;

import com.morpheus.application.operability.OperationalLogMode;
import com.morpheus.application.operability.OperationalRuntime;
import com.morpheus.store.sqlite.SqliteConnectionScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * One CLI command opens one physical SQLite connection, as the same operation does through HTTP and MCP.
 *
 * <p>Counted at the JDBC driver on a whole command, from its arguments to its exit code: the query and policy commands
 * read through store sets that live in the SQLite adapter, which opened five and eight connections for one command
 * while every rule looked at the transport adapters. The two operations are the ones
 * {@code HttpRequestConnectionCountTest} and {@code McpToolCallConnectionCountTest} measure on their transport. The
 * database is initialised by a first command, so that the count is the one of an ordinary command.</p>
 */
class CliCommandConnectionCountTest {
    @TempDir
    Path temporaryDirectory;

    private final OperationalRuntime observed = OperationalRuntime.of(OperationalLogMode.OFF);

    @Test
    void listingSavedViewsOpensOnePhysicalConnection() throws Exception {
        assertOneConnection("views", "list", "--project", "01920000-0000-7000-8000-0000000000ff");
    }

    @Test
    void listingPolicyPacksOpensOnePhysicalConnection() throws Exception {
        assertOneConnection("policy", "pack", "list");
    }

    private void assertOneConnection(String... command) throws Exception {
        String label = String.join(" ", command);
        assertEquals(0, run(command), label);
        Map<String, Long> before = observed.metrics().snapshot().counters();

        int opened = PhysicalSqliteConnections.openedDuring(() -> assertEquals(0, run(command), label));

        assertEquals(1, opened, label + " opens one physical SQLite connection");
        Map<String, Long> after = observed.metrics().snapshot().counters();
        assertEquals(1L, delta(before, after, SqliteConnectionScope.CONNECTIONS_OPENED),
                "and that connection is the one of a scope, counted on the runtime the launcher was handed");
        assertEquals(1L, delta(before, after, SqliteConnectionScope.SCHEMA_CHECKS), "whose schema check runs once");
    }

    private static long delta(Map<String, Long> before, Map<String, Long> after, String counter) {
        return after.getOrDefault(counter, 0L) - before.getOrDefault(counter, 0L);
    }

    private int run(String... command) {
        List<String> args = new ArrayList<>(
                List.of("--json", "--data-dir", temporaryDirectory.resolve("data").toString()));
        args.addAll(List.of(command));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream(output, true, StandardCharsets.UTF_8);
             PrintStream err = new PrintStream(errors, true, StandardCharsets.UTF_8)) {
            Properties properties = new Properties();
            properties.setProperty("user.home", temporaryDirectory.resolve("home").toString());
            properties.setProperty("os.name", "Linux");
            return MorpheusMain.run(observed, args.toArray(String[]::new), out, err, Map.of(), properties);
        }
    }
}
