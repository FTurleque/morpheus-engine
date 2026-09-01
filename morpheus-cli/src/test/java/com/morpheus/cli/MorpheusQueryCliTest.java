package com.morpheus.cli;

import com.morpheus.domain.project.ProjectSpecificationId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MorpheusQueryCliTest {
    @TempDir
    Path tempDirectory;

    @Test
    void executesProviderNeutralProjectQueryWithStructuredJson() {
        ProjectSpecificationId project = ProjectSpecificationId.generate();

        Result result = run(
                "--json", "query", "execute",
                "--project", project.toString(),
                "--entity", "change",
                "--filter", "title contains security",
                "--sort", "title:asc",
                "--fields", "id,title",
                "--limit", "25");

        assertEquals(CliExitCode.SUCCESS.code(), result.exitCode(), result.err());
        assertTrue(result.out().contains("\"scope\""), result.out());
        assertTrue(result.out().contains("\"entityType\":\"CHANGE\""), result.out());
        assertTrue(result.out().contains("\"totalMatches\":0"), result.out());
        assertFalse(result.out().toLowerCase().contains("select "));
    }

    @Test
    void explicitDataAndConfigDirectoriesAreBothConsumedBeforeCommandDispatch() {
        ProjectSpecificationId project = ProjectSpecificationId.generate();

        Result result = run(
                "--data-dir", tempDirectory.resolve("data").toString(),
                "--config-dir", tempDirectory.resolve("config").toString(),
                "--db", tempDirectory.resolve("morpheus.db").toString(),
                "--json", "query", "execute",
                "--project", project.toString(),
                "--entity", "change",
                "--filter", "title contains security",
                "--fields", "id,title",
                "--limit", "25");

        assertEquals(CliExitCode.SUCCESS.code(), result.exitCode(), result.err());
        assertTrue(result.out().contains("\"totalMatches\":0"), result.out());
    }

    @Test
    void savedViewIdentityVersioningAndStaleCasAreVisibleOnCli() {
        ProjectSpecificationId project = ProjectSpecificationId.generate();
        Result created = run(
                "--json", "views", "create",
                "--name", "Security",
                "--project", project.toString(),
                "--entity", "change",
                "--filter", "title starts-with sec");
        assertEquals(CliExitCode.SUCCESS.code(), created.exitCode(), created.err());
        String id = firstUuid(created.out());

        Result updated = run(
                "--json", "views", "update",
                "--id", id,
                "--expected-revision", "1",
                "--name", "Security current",
                "--entity", "change",
                "--filter", "title ends-with ity");
        Result stale = run(
                "--json", "views", "update",
                "--id", id,
                "--expected-revision", "1",
                "--name", "Stale",
                "--entity", "change");
        Result versions = run("--json", "views", "versions", "--id", id);

        assertEquals(CliExitCode.SUCCESS.code(), updated.exitCode(), updated.err());
        assertTrue(updated.out().contains("\"revision\":2"), updated.out());
        assertEquals(CliExitCode.STATE_ERROR.code(), stale.exitCode(), stale.err());
        assertTrue(stale.err().contains("stale saved view revision"), stale.err());
        assertEquals(CliExitCode.SUCCESS.code(), versions.exitCode(), versions.err());
        assertTrue(versions.out().contains("\"revision\":1"), versions.out());
        assertTrue(versions.out().contains("\"revision\":2"), versions.out());
    }

    @Test
    void exportViewIsReadOnlyAndProducesRequestedFormat() {
        ProjectSpecificationId project = ProjectSpecificationId.generate();
        Result created = run(
                "--json", "views", "create",
                "--name", "Report",
                "--project", project.toString(),
                "--entity", "change",
                "--fields", "id,title");
        String id = firstUuid(created.out());

        Result exported = run("export", "view", "--format", "csv", "--id", id);
        Result after = run("--json", "views", "get", "--id", id);

        assertEquals(CliExitCode.SUCCESS.code(), exported.exitCode(), exported.err());
        assertEquals("\"id\",\"projectId\",\"title\"\n", exported.out());
        assertEquals(CliExitCode.SUCCESS.code(), after.exitCode(), after.err());
        assertTrue(after.out().contains("\"revision\":1"), after.out());
    }

    @Test
    void invalidBusinessFieldReturnsUsageErrorWithoutStacktrace() {
        ProjectSpecificationId project = ProjectSpecificationId.generate();
        Result result = run(
                "query", "execute",
                "--project", project.toString(),
                "--entity", "change",
                "--filter", "sqlite_column eq value");

        assertEquals(CliExitCode.USAGE.code(), result.exitCode());
        assertTrue(result.err().contains("QUERY_FIELD_UNKNOWN"), result.err());
        assertFalse(result.err().contains("Exception"), result.err());
        assertFalse(result.err().contains("\tat "), result.err());
    }

    private Result run(String... args) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        int exit;
        try (PrintStream out = new PrintStream(output, true, StandardCharsets.UTF_8);
             PrintStream err = new PrintStream(errors, true, StandardCharsets.UTF_8)) {
            Properties properties = new Properties();
            properties.setProperty("user.home", tempDirectory.resolve("home").toString());
            properties.setProperty("os.name", "Linux");
            exit = MorpheusMain.run(args, out, err, Map.of(), properties);
        }
        return new Result(exit, output.toString(StandardCharsets.UTF_8), errors.toString(StandardCharsets.UTF_8));
    }

    private String firstUuid(String json) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern
                .compile("[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}")
                .matcher(json);
        if (!matcher.find()) {
            throw new AssertionError("UUIDv7 not found in " + json);
        }
        return matcher.group();
    }

    private record Result(int exitCode, String out, String err) {
    }
}
