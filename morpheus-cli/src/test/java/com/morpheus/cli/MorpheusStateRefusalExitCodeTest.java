package com.morpheus.cli;

import com.morpheus.domain.identity.DomainIdentity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A refusal about state exits with the code the help publishes for it, never with the usage code.
 *
 * <p>{@code morpheus help} publishes 2 for usage, 3 for a requested entity that is not found and 4 for a persisted
 * state error. The policy, query, portfolio and server adapters answered 2 to every refusal their services raised,
 * so a script could not tell a typo from an identifier that designates nothing. The rule the codes follow: 3 when an
 * identifier given on the command line designates nothing; 4 when what is designated exists but the relation or the
 * resulting state the operation needs is refused; 2 only when the invocation itself is malformed.</p>
 */
class MorpheusStateRefusalExitCodeTest {
    private static final Pattern UUID_V7 =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-7[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");
    private static final String RULES = "new|No findings|QUALITY_THRESHOLD|BLOCKER|FINDINGS|LTE|0";
    private static final String NOT_FOUND = "MORPHEUS error [3]: ";
    private static final String STATE = "MORPHEUS error [4]: ";
    private static final String SERVER = "MORPHEUS server error: ";

    @TempDir
    Path temp;

    @Test
    void anUnknownPolicyPackOrVersionIsNotFound() {
        String pack = id();
        String version = id();
        String project = id();

        assertRefused(CliExitCode.NOT_FOUND, NOT_FOUND + "unknown policy pack: " + pack,
                run("policy", "pack", "get", "--id", pack));
        assertRefused(CliExitCode.NOT_FOUND, NOT_FOUND + "unknown policy pack: " + pack,
                run("policy", "pack", "versions", "--id", pack));
        assertRefused(CliExitCode.NOT_FOUND, NOT_FOUND + "unknown policy pack: " + pack,
                run("policy", "audit", "--id", pack));
        assertRefused(CliExitCode.NOT_FOUND, NOT_FOUND + "unknown policy pack: " + pack,
                run("policy", "pack", "update", "--id", pack, "--expected-revision", "1", "--name", "n",
                        "--rules", RULES, "--actor", "alice", "--reason", "r"));
        assertRefused(CliExitCode.NOT_FOUND, NOT_FOUND + "unknown policy pack version: " + pack + "/" + version,
                run("policy", "activate", "--id", pack, "--version", version, "--project", project,
                        "--expected-revision", "0", "--actor", "alice", "--reason", "r"));
        assertRefused(CliExitCode.NOT_FOUND, NOT_FOUND + "unknown policy pack version: " + pack + "/" + version,
                run("policy", "dry-run", "--id", pack, "--version", version, "--project", project));
        String rule = id();
        assertRefused(CliExitCode.NOT_FOUND, NOT_FOUND + "policy override does not exist: " + rule,
                run("policy", "override", "remove", "--id", pack, "--rule", rule, "--project", project,
                        "--expected-revision", "1", "--actor", "alice", "--reason", "r"));
    }

    @Test
    void aPolicyPackThatIsNotActiveInTheScopeIsAStateRefusal() {
        String pack = id();
        String project = id();

        assertRefused(CliExitCode.STATE_ERROR, STATE + "policy pack is not active in scope: " + pack,
                run("policy", "evaluate", "--id", pack, "--project", project));
        assertRefused(CliExitCode.STATE_ERROR, STATE + "policy pack is not active in scope: " + pack,
                run("policy", "deactivate", "--id", pack, "--project", project, "--expected-revision", "1",
                        "--actor", "alice", "--reason", "r"));
        assertRefused(CliExitCode.STATE_ERROR,
                STATE + "policy pack must be active before adding an override: " + pack,
                run("policy", "override", "put", "--id", pack, "--rule", id(), "--mode", "DISABLE",
                        "--project", project, "--expected-revision", "0", "--actor", "alice", "--reason", "r"));
    }

    @Test
    void aRuleAbsentFromTheActiveVersionIsNotFound() {
        String pack = uuids(run("--json", "policy", "pack", "create", "--name", "Governance", "--rules", RULES,
                "--actor", "alice", "--reason", "baseline").out()).getFirst();
        String version = uuids(run("--json", "policy", "pack", "versions", "--id", pack).out()).get(1);
        String project = id();
        assertEquals(CliExitCode.SUCCESS.code(), run("policy", "activate", "--id", pack, "--version", version,
                "--project", project, "--expected-revision", "0", "--actor", "alice", "--reason", "r").exitCode());
        String rule = id();

        assertRefused(CliExitCode.NOT_FOUND, NOT_FOUND + "rule is not present in active policy pack version: " + rule,
                run("policy", "override", "put", "--id", pack, "--rule", rule, "--mode", "DISABLE",
                        "--project", project, "--expected-revision", "0", "--actor", "alice", "--reason", "r"));
    }

    @Test
    void anUnknownSavedViewIsNotFoundOnEveryCommandThatNamesIt() {
        String view = id();
        String expected = NOT_FOUND + "unknown saved view: " + view;

        assertRefused(CliExitCode.NOT_FOUND, expected, run("views", "get", "--id", view));
        assertRefused(CliExitCode.NOT_FOUND, expected, run("views", "versions", "--id", view));
        assertRefused(CliExitCode.NOT_FOUND, expected, run("views", "execute", "--id", view));
        assertRefused(CliExitCode.NOT_FOUND, expected,
                run("views", "update", "--id", view, "--expected-revision", "1", "--name", "n", "--entity", "change"));
        assertRefused(CliExitCode.NOT_FOUND, expected, run("views", "archive", "--id", view, "--expected-revision", "1"));
        assertRefused(CliExitCode.NOT_FOUND, expected, run("export", "view", "--format", "json", "--id", view));
    }

    @Test
    void anUnknownPortfolioIsNotFoundAndANonMemberProjectIsAStateRefusal() {
        String unknown = id();
        String project = id();

        assertRefused(CliExitCode.NOT_FOUND, NOT_FOUND + "unknown portfolio: " + unknown,
                run("portfolio", "overview", "--portfolio", unknown));
        assertRefused(CliExitCode.NOT_FOUND, NOT_FOUND + "unknown portfolio: " + unknown,
                run("portfolio", "missing", "--portfolio", unknown, "--project", project));
        assertRefused(CliExitCode.NOT_FOUND, NOT_FOUND + "unknown portfolio: " + unknown,
                run("query", "execute", "--portfolio", unknown, "--entity", "change"));

        String portfolio = uuids(run("--json", "portfolio", "create", "--name", "Platform").out()).getFirst();
        assertRefused(CliExitCode.STATE_ERROR, STATE + "project is not a portfolio member: " + project,
                run("portfolio", "missing", "--portfolio", portfolio, "--project", project));
        assertRefused(CliExitCode.STATE_ERROR, STATE + "start project is not a portfolio member: " + project,
                run("portfolio", "traverse", "--portfolio", portfolio, "--start-project", project,
                        "--start-type", "PROJECT", "--start-id", id()));
    }

    @Test
    void aMissingAuthFileOrPrincipalIsNotFoundAndALockoutIsAStateRefusal() {
        String missingFile = SERVER + "remote auth file does not exist";
        assertRefused(CliExitCode.NOT_FOUND, missingFile, run("server", "identity", "list"));
        assertRefused(CliExitCode.NOT_FOUND, missingFile, run("server", "identity", "revoke", "--principal", "ghost"));
        assertRefused(CliExitCode.NOT_FOUND, missingFile,
                run("server", "identity", "migrate-legacy", "--expires-at", "2099-01-01T00:00:00Z"));

        assertEquals(CliExitCode.SUCCESS.code(),
                run("server", "identity", "create", "--principal", "admin", "--role", "ADMIN").exitCode());

        assertRefused(CliExitCode.NOT_FOUND, SERVER + "remote principal does not exist: ghost",
                run("server", "identity", "rotate", "--principal", "ghost"));
        assertRefused(CliExitCode.STATE_ERROR, SERVER + "remote principal already exists: admin",
                run("server", "identity", "create", "--principal", "admin", "--role", "READ"));
        assertRefused(CliExitCode.STATE_ERROR, SERVER + "cannot revoke the last active ADMIN identity",
                run("server", "identity", "revoke", "--principal", "admin"));
        assertRefused(CliExitCode.STATE_ERROR, SERVER + "cannot change the role of the last active ADMIN identity",
                run("server", "identity", "role", "--principal", "admin", "--role", "READ"));
        assertRefused(CliExitCode.STATE_ERROR,
                SERVER + "migration would leave no ADMIN identity active after 2099-01-01T00:00:00Z;"
                        + " give one administrator a later expiry or exclude it from the migration",
                run("server", "identity", "migrate-legacy", "--expires-at", "2099-01-01T00:00:00Z"));
    }

    @Test
    void aMalformedInvocationStaysAUsageError() throws IOException {
        Path directory = Files.createDirectories(temp.resolve("not-a-file"));

        assertRefused(CliExitCode.USAGE, "MORPHEUS error [2]: Invalid UUID string: not-an-id",
                run("policy", "pack", "get", "--id", "not-an-id"));
        assertRefused(CliExitCode.USAGE, "MORPHEUS error [2]: exactly one of --project or --portfolio is required",
                run("policy", "evaluate", "--id", id()));
        assertRefused(CliExitCode.USAGE, "MORPHEUS server usage error: remote auth file must be a regular non-symbolic file",
                run("server", "identity", "list", "--auth-file", directory.toString()));
    }

    private static void assertRefused(CliExitCode expectedCode, String expectedError, Result result) {
        assertEquals(expectedCode.code(), result.exitCode(), result.err());
        assertEquals(expectedError, result.err().strip());
    }

    private static String id() {
        return DomainIdentity.generate().toString();
    }

    private static List<String> uuids(String text) {
        Matcher matcher = UUID_V7.matcher(text);
        List<String> values = new ArrayList<>();
        while (matcher.find()) {
            values.add(matcher.group());
        }
        if (values.isEmpty()) {
            throw new AssertionError("UUIDv7 not found in " + text);
        }
        return List.copyOf(values);
    }

    private Result run(String... rawArgs) {
        List<String> args = new ArrayList<>(List.of(
                "--data-dir", temp.resolve("data").toString(),
                "--config-dir", temp.resolve("config").toString(),
                "--db", temp.resolve("data/morpheus.db").toString()));
        args.addAll(List.of(rawArgs));
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ByteArrayOutputStream errors = new ByteArrayOutputStream();
        int exit;
        try (PrintStream out = new PrintStream(output, true, StandardCharsets.UTF_8);
             PrintStream err = new PrintStream(errors, true, StandardCharsets.UTF_8)) {
            Properties properties = new Properties();
            properties.setProperty("user.home", temp.resolve("home").toString());
            properties.setProperty("os.name", "Linux");
            exit = MorpheusMain.run(args.toArray(String[]::new), out, err, Map.of(), properties);
        }
        return new Result(exit, output.toString(StandardCharsets.UTF_8), errors.toString(StandardCharsets.UTF_8));
    }

    private record Result(int exitCode, String out, String err) {
    }
}
