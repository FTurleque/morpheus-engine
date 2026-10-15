package com.morpheus.cli;

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
        SqliteConnectionScope.Diagnostics before = SqliteConnectionScope.diagnostics();

        int opened = PhysicalSqliteConnections.openedDuring(() -> assertEquals(0, run(command), label));

        assertEquals(1, opened, label + " opens one physical SQLite connection");
        assertEquals(1, SqliteConnectionScope.diagnostics().opened() - before.opened(),
                "and that connection is the one of a scope, whose schema check runs once");
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
            return MorpheusMain.run(args.toArray(String[]::new), out, err, Map.of(), properties);
        }
    }
}
