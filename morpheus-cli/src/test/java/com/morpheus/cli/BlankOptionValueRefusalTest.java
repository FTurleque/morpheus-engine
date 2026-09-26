package com.morpheus.cli;

import com.morpheus.domain.change.ChangeId;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.requirement.RequirementId;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every option of every CLI parser, given an empty or blank value, is refused with exit code 2 and named.
 *
 * <p>The table is the inventory of the adapter's value-taking options outside {@link SimpleOptions} (covered by
 * {@link SimpleOptionsTest}). Each invocation runs in its own case directory, which holds its data directory and any
 * file an option points to; a refused invocation must leave that directory absent, i.e. write nothing at all.</p>
 */
class BlankOptionValueRefusalTest {
    private static final List<String> BLANKS = List.of("", " \t ");
    private static final String B = "{blank}";
    private static final String DATA = "{data}";
    private static final String ROOT = "{root}";
    private static final String P = ProjectSpecificationId.generate().toString();
    private static final String C = ChangeId.generate().toString();
    private static final String R = RequirementId.generate().toString();

    @TempDir
    Path tempDirectory;

    private final AtomicInteger cases = new AtomicInteger();

    @TestFactory
    Stream<DynamicTest> aBlankValueIsRefusedNamingItsOptionBeforeAnythingIsWritten() {
        return invocations().stream().flatMap(invocation -> BLANKS.stream().map(blank -> DynamicTest.dynamicTest(
                String.join(" ", invocation.template()).replace(B, "'" + blank + "'"),
                () -> {
                    Path root = tempDirectory.resolve("case-" + cases.incrementAndGet());
                    Result result = run(expand(invocation.template(), root, blank));

                    assertEquals(CliExitCode.USAGE.code(), result.exitCode(), result.err());
                    assertTrue(result.err().contains(invocation.option() + " requires a non-blank value"),
                            result.err());
                    assertFalse(Files.exists(root), "a refused invocation must write nothing: " + root);
                })));
    }

    /** {@code projects add} opens the store before it checks its options; the refusal must still register nothing. */
    @Test
    void aRefusedProjectRegistrationRegistersNothingWhileTheOmittedWorkspaceIsStillRequired() {
        Path data = tempDirectory.resolve("projects-add");

        Result refused = run("--data-dir", data.toString(), "projects", "add", "--workspace", "");
        Result omitted = run("--data-dir", data.toString(), "projects", "add");
        Result listed = run("--data-dir", data.toString(), "projects", "list");

        assertEquals(CliExitCode.USAGE.code(), refused.exitCode(), refused.err());
        assertTrue(refused.err().contains("--workspace requires a non-blank value"), refused.err());
        assertEquals(CliExitCode.USAGE.code(), omitted.exitCode(), omitted.err());
        assertTrue(omitted.err().contains("--workspace is required"), omitted.err());
        assertEquals(CliExitCode.SUCCESS.code(), listed.exitCode(), listed.err());
        assertTrue(listed.out().contains("No projects registered."), listed.out());
    }

    /**
     * A non-blank layout is still accepted by every copy of the layout parsing: each command gets past it and stops at
     * its own first usage check, without writing anything.
     */
    @TestFactory
    Stream<DynamicTest> aNonBlankLayoutIsStillAcceptedByEveryParser() {
        Map<List<String>, String> commands = new java.util.LinkedHashMap<>();
        commands.put(List.of("acceptance-criteria"), "acceptance-criteria requires action: list");
        commands.put(List.of("constraints", "evaluate"), "--project is required");
        commands.put(List.of("composition"), "composition requires action");
        commands.put(List.of("lifecycle"), "lifecycle requires action: apply");
        commands.put(List.of("change-orchestration"), "change-orchestration requires action");
        commands.put(List.of("augmented-context"), "augmented-context requires subject");
        commands.put(List.of("external-references"), "external-references requires subcommand");
        return commands.entrySet().stream().map(command -> DynamicTest.dynamicTest(
                String.join(" ", command.getKey()),
                () -> {
                    Path root = tempDirectory.resolve("case-" + cases.incrementAndGet());
                    List<String> args = new ArrayList<>(List.of(
                            "--data-dir", root.resolve("data").toString(),
                            "--config-dir", root.resolve("config").toString(),
                            "--db", root.resolve("data/morpheus.db").toString()));
                    args.addAll(command.getKey());

                    Result result = run(args.toArray(String[]::new));

                    assertEquals(CliExitCode.USAGE.code(), result.exitCode(), result.err());
                    assertTrue(result.err().contains(command.getValue()), result.err());
                    assertFalse(Files.exists(root), "a usage error must write nothing: " + root);
                }));
    }

    /** Non-blank reasoning options still reach the analysis. */
    @Test
    void nonBlankReasoningOptionsAreStillAccepted() {
        Result result = run("reason", "analyze", "--question", "q", "--param", "k=v", "--max-claims", "3");

        assertEquals(CliExitCode.SUCCESS.code(), result.exitCode(), result.err());
    }

    /** An omitted required option is still reported as required, not as blank. */
    @Test
    void anOmittedExternalReferencesProjectIsStillRequired() {
        Result result = run("--data-dir", tempDirectory.resolve("external").toString(), "external-references", "list");

        assertEquals(CliExitCode.USAGE.code(), result.exitCode(), result.err());
        assertTrue(result.err().contains("--project is required"), result.err());
    }

    /** The {@code --x=v} spelling still accepts a non-blank value in the launchers that take it. */
    @Test
    void theLaunchersStillAcceptANonBlankValueInTheEqualsSpelling() {
        Path data = tempDirectory.resolve("launcher-data");

        McpLaunchOptions mcp = McpLaunchOptions.parse(
                new String[]{"mcp", "--stdio", "--data-dir=" + data}, Map.of(), properties());
        IllegalArgumentException remote = assertThrows(IllegalArgumentException.class,
                () -> RemoteApiLaunchOptions.parse(
                        new String[]{"api", "--remote", "--host=127.0.0.1"}, Map.of(), properties()));

        assertEquals(data.toAbsolutePath().normalize(), mcp.layout().dataDirectory());
        assertTrue(remote.getMessage().contains("--tls-keystore"),
                "the remote launcher must get past --host= to its keystore check: " + remote.getMessage());
    }

    /**
     * A launcher is parsed before any server starts: a blank value is refused there, not resolved as the working
     * directory.
     */
    @TestFactory
    Stream<DynamicTest> aLauncherRefusesABlankValueNamingItsOption() {
        List<Launcher> launchers = new ArrayList<>();
        for (String option : List.of("--host", "--port", "--data-dir", "--config-dir", "--db")) {
            launchers.add(new Launcher(option, args -> ApiLaunchOptions.parse(args, Map.of(), properties()), "api"));
        }
        for (String option : List.of("--data-dir", "--config-dir", "--db")) {
            launchers.add(new Launcher(option, args -> McpLaunchOptions.parse(args, Map.of(), properties()),
                    "mcp", "--stdio"));
        }
        for (String option : List.of("--host", "--port", "--data-dir", "--config-dir", "--db", "--auth-file",
                "--tls-keystore", "--provider-plugin-dir", "--workspace-root", "--max-concurrent")) {
            launchers.add(new Launcher(option, args -> RemoteApiLaunchOptions.parse(args, Map.of(), properties()),
                    "api", "--remote"));
        }
        return launchers.stream().flatMap(launcher -> BLANKS.stream().flatMap(blank -> Stream.of(
                launcherTest(launcher, launcher.option() + " '" + blank + "'", launcher.option(), blank),
                launcherTest(launcher, launcher.option() + "='" + blank + "'", launcher.option() + "=" + blank))));
    }

    private DynamicTest launcherTest(Launcher launcher, String spelling, String... option) {
        List<String> args = new ArrayList<>(List.of(launcher.command()));
        args.addAll(List.of(option));
        return DynamicTest.dynamicTest(String.join(" ", launcher.command()) + " " + spelling, () -> {
            IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                    () -> launcher.parser().accept(args.toArray(String[]::new)));
            assertTrue(refused.getMessage().startsWith(launcher.option() + " requires a non-blank value"),
                    refused.getMessage());
        });
    }

    private static List<Invocation> invocations() {
        List<Invocation> invocations = new ArrayList<>();
        // MorpheusCli.CommandOptions, and GlobalArgs for the three layout options.
        add(invocations, "--data-dir", "--data-dir", B, "paths");
        add(invocations, "--config-dir", "--data-dir", DATA, "--config-dir", B, "paths");
        add(invocations, "--db", "--data-dir", DATA, "--db", B, "paths");
        add(invocations, "--project", "--data-dir", DATA, "sync", "--project", B);
        add(invocations, "--revision", "--data-dir", DATA, "sync", "--project", P, "--revision", B);
        add(invocations, "--max-age-minutes", "--data-dir", DATA, "sync-status", "--project", P,
                "--max-age-minutes", B);
        add(invocations, "--query", "--data-dir", DATA, "requirements", "find", "--project", P, "--query", B);
        add(invocations, "--offset", "--data-dir", DATA, "requirements", "find", "--project", P, "--offset", B);
        add(invocations, "--project", "--data-dir", DATA, "changes", "list", "--project", B);
        add(invocations, "--change", "--data-dir", DATA, "constraints", "list", "--project", P, "--change", B);
        add(invocations, "--limit", "--data-dir", DATA, "decisions", "list", "--project", P, "--change", C,
                "--limit", B);
        add(invocations, "--requirement", "--data-dir", DATA, "trace-requirement", "--project", P,
                "--requirement", B);
        add(invocations, "--depth", "--data-dir", DATA, "change-context", "--project", P, "--change", C,
                "--depth", B);
        add(invocations, "--project", "--data-dir", DATA, "quality", "--project", B);
        // MorpheusCompositionCli.Options.
        add(invocations, "--project", "--data-dir", DATA, "composition", "status", "--project", B);
        add(invocations, "--revision", "--data-dir", DATA, "composition", "sync", "--project", P, "--revision", B);
        layout(invocations, "composition", "status", "--project", P);
        // MorpheusExternalIntegrationCli: its options are read once the store is open, only the layout comes first.
        layout(invocations, "external-references", "list", "--project", P);
        // MorpheusAcceptanceCriteriaCli.Options.
        for (String option : List.of("--project", "--change", "--requirement", "--offset", "--limit")) {
            add(invocations, option, "--data-dir", DATA, "acceptance-criteria", "list", option, B);
        }
        layout(invocations, "acceptance-criteria", "list", "--project", P);
        // MorpheusConstraintSemanticsCli.Options.
        for (String option : List.of("--project", "--change", "--target", "--offset", "--limit")) {
            add(invocations, option, "--data-dir", DATA, "constraints", "evaluate", option, B);
        }
        layout(invocations, "constraints", "evaluate", "--project", P, "--change", C, "--target", "PROPOSED");
        // MorpheusControlledLifecycleCli.Options: a write, refused before the store is opened.
        for (String option : List.of("--project", "--change", "--expected-revision", "--to", "--idempotency-key",
                "--actor", "--abandonment-reason")) {
            add(invocations, option, "--data-dir", DATA, "lifecycle", "apply", "--confirm", option, B);
        }
        layout(invocations, "lifecycle", "apply", "--project", P, "--change", C);
        // MorpheusJarvisOrchestrationCli.Options.
        for (String option : List.of("--project", "--change", "--lifecycle", "--abandonment-reason")) {
            add(invocations, option, "--data-dir", DATA, "change-orchestration", "state", option, B);
        }
        for (String option : List.of("--from", "--to", "--from-abandonment-reason")) {
            add(invocations, option, "--data-dir", DATA, "change-orchestration", "transition-check", option, B);
        }
        layout(invocations, "change-orchestration", "state", "--project", P, "--change", C);
        // MorpheusAugmentedContextCli.ContextOptions.
        for (String option : List.of("--project", "--requirement", "--nexus-project", "--budget", "--source",
                "--constraint")) {
            add(invocations, option, "--data-dir", DATA, "augmented-context", "requirement", option, B);
        }
        add(invocations, "--change", "--data-dir", DATA, "augmented-context", "change", "--change", B);
        layout(invocations, "augmented-context", "requirement", "--project", P, "--requirement", R);
        // MorpheusReasoningCli.
        for (String option : List.of("--question", "--evidence", "--adapter", "--param", "--max-claims")) {
            add(invocations, option, "--data-dir", DATA, "reason", "analyze", option, B);
        }
        layout(invocations, "reason", "adapters");
        // MorpheusProductCli.
        add(invocations, "--manifest", "--data-dir", DATA, "update-check", "--manifest", B);
        layout(invocations, "version");
        // MorpheusProviderPluginCli.
        add(invocations, "--directory", "--data-dir", DATA, "provider-plugins", "discover", "--directory", B);
        for (String option : List.of("--plugin", "--workspace", "--sha256")) {
            add(invocations, option, "--data-dir", DATA, "provider-plugins", "probe",
                    "--directory", ROOT + "/plugins", option, B);
        }
        layout(invocations, "provider-plugins", "discover", "--directory", ROOT + "/plugins");
        // MorpheusServerCli.options, and its own layout parsing in both spellings.
        String authFile = ROOT + "/remote-auth.txt";
        for (String option : List.of("--principal", "--role", "--auth-file", "--expires-at")) {
            add(invocations, option, "--data-dir", DATA, "server", "identity", "create", option, B);
        }
        add(invocations, "--auth-file", "--data-dir", DATA, "server", "identity", "list", "--auth-file", B);
        add(invocations, "--principal", "--data-dir", DATA, "server", "identity", "revoke", "--principal", B);
        add(invocations, "--expires-at", "--data-dir", DATA, "server", "identity", "rotate",
                "--principal", "alice", "--auth-file", authFile, "--expires-at", B);
        add(invocations, "--role", "--data-dir", DATA, "server", "identity", "role", "--principal", "alice",
                "--auth-file", authFile, "--role", B);
        add(invocations, "--principal", "--data-dir", DATA, "server", "identity", "migrate-legacy",
                "--expires-at", "2099-01-01T00:00:00Z", "--auth-file", authFile, "--principal", B);
        add(invocations, "--output-dir", "--data-dir", DATA, "server", "backup", "create", "--output-dir", B);
        add(invocations, "--file", "--data-dir", DATA, "server", "backup", "verify", "--file", B);
        add(invocations, "--file", "--data-dir", DATA, "server", "restore", "--confirm", "--file", B);
        layout(invocations, "server", "identity", "list", "--auth-file", authFile);
        add(invocations, "--data-dir", "--data-dir=" + B, "server", "identity", "list", "--auth-file", authFile);
        add(invocations, "--config-dir", "--data-dir", DATA, "--config-dir=" + B, "server", "identity", "list");
        add(invocations, "--db", "--data-dir", DATA, "--db=" + B, "server", "backup", "create");
        return invocations;
    }

    private static void add(List<Invocation> invocations, String option, String... template) {
        invocations.add(new Invocation(option, List.of(template)));
    }

    private static void layout(List<Invocation> invocations, String... command) {
        List<String> withData = new ArrayList<>(List.of("--data-dir", B));
        withData.addAll(List.of(command));
        invocations.add(new Invocation("--data-dir", withData));
        for (String option : List.of("--config-dir", "--db")) {
            List<String> template = new ArrayList<>(List.of("--data-dir", DATA, option, B));
            template.addAll(List.of(command));
            invocations.add(new Invocation(option, template));
        }
    }

    private static String[] expand(List<String> template, Path root, String blank) {
        return template.stream()
                .map(token -> token.replace(B, blank)
                        .replace(DATA, root.resolve("data").toString())
                        .replace(ROOT, root.toString()))
                .toArray(String[]::new);
    }

    private Result run(String... args) {
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
             PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8)) {
            int exitCode = MorpheusMain.run(args, out, err, Map.of(), properties());
            return new Result(exitCode, outBytes.toString(StandardCharsets.UTF_8),
                    errBytes.toString(StandardCharsets.UTF_8));
        }
    }

    private Properties properties() {
        Properties properties = new Properties();
        properties.setProperty("os.name", System.getProperty("os.name", "Windows"));
        properties.setProperty("user.home", tempDirectory.resolve("home").toString());
        return properties;
    }

    private record Invocation(String option, List<String> template) {
    }

    private record Launcher(String option, Consumer<String[]> parser, String... command) {
    }

    private record Result(int exitCode, String out, String err) {
    }
}
