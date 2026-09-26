package com.morpheus.cli;

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
 * A value-taking option given twice is refused with exit code 2 and named, by every parser of the CLI; the second
 * value never silently replaces the first.
 *
 * <p>The families that already refused a repeat ({@link SimpleOptions}, {@code MorpheusCli.CommandOptions} and the
 * adapters' own {@code Options}) are covered by their own tests. This table covers the parsers that kept the last
 * value: {@link GlobalArgs} and its former copies, the server, product, provider-plugin and reasoning parsers, and the
 * three launchers. Where a parser recognises both spellings ({@code --x v} and {@code --x=v}), they are one option.</p>
 */
class DuplicateOptionRefusalTest {
    private static final List<String> LAYOUT = List.of("--data-dir", "--config-dir", "--db");
    private static final String ROOT = "{root}";

    @TempDir
    Path tempDirectory;

    private final AtomicInteger cases = new AtomicInteger();

    /** The command each parser family is reached by; the layout options come before it. */
    private static Map<String, List<String>> families() {
        Map<String, List<String>> families = new java.util.LinkedHashMap<>();
        families.put("GlobalArgs (MorpheusCli)", List.of("paths"));
        families.put("GlobalArgs (MorpheusPolicyCli)", List.of("policy", "pack", "list"));
        families.put("GlobalArgs (MorpheusQueryCli)", List.of("views", "list"));
        families.put("GlobalArgs (MorpheusPortfolioCli)", List.of("portfolio", "list"));
        families.put("MorpheusAcceptanceCriteriaCli", List.of("acceptance-criteria"));
        families.put("MorpheusConstraintSemanticsCli", List.of("constraints", "evaluate"));
        families.put("MorpheusCompositionCli", List.of("composition"));
        families.put("MorpheusControlledLifecycleCli", List.of("lifecycle"));
        families.put("MorpheusJarvisOrchestrationCli", List.of("change-orchestration"));
        families.put("MorpheusAugmentedContextCli", List.of("augmented-context"));
        families.put("MorpheusExternalIntegrationCli", List.of("external-references"));
        families.put("MorpheusReasoningCli", List.of("reason", "adapters"));
        families.put("MorpheusProductCli", List.of("version"));
        families.put("MorpheusProviderPluginCli", List.of("provider-plugins", "discover", "--directory", ROOT + "/plugins"));
        families.put("MorpheusServerCli", List.of("server", "identity", "list", "--auth-file", ROOT + "/remote-auth.txt"));
        return families;
    }

    @TestFactory
    Stream<DynamicTest> aRepeatedLayoutOptionIsRefusedNamingItBeforeAnythingIsWritten() {
        return families().entrySet().stream().flatMap(family -> LAYOUT.stream().map(option -> DynamicTest.dynamicTest(
                family.getKey() + ": " + option + " a " + option + " b",
                () -> {
                    Path root = tempDirectory.resolve("case-" + cases.incrementAndGet());
                    List<String> args = new ArrayList<>(List.of(
                            option, layoutValue(root.resolve("first"), option),
                            option, layoutValue(root.resolve("second"), option)));
                    args.addAll(family.getValue());

                    Result result = run(expand(args, root));

                    assertEquals(CliExitCode.USAGE.code(), result.exitCode(), result.err());
                    assertTrue(result.err().contains("duplicate option: " + option), result.err());
                    assertFalse(Files.exists(root), "a refused invocation must write nothing: " + root);
                })));
    }

    /** Before the fix: exit code 0, and the paths printed were those of the second value. */
    @Test
    void aSecondDataDirectoryNoLongerReplacesTheFirst() {
        Path first = tempDirectory.resolve("first");
        Path second = tempDirectory.resolve("second");

        Result result = run("--data-dir", first.toString(), "--data-dir", second.toString(), "paths");

        assertEquals(CliExitCode.USAGE.code(), result.exitCode(), result.out());
        assertFalse(result.out().contains(second.toString()), result.out());
        assertTrue(result.err().contains("duplicate option: --data-dir"), result.err());
    }

    /**
     * The server parses the layout in both spellings, and they name one option: a repeat across spellings is a
     * duplicate like a repeat within one.
     */
    @TestFactory
    Stream<DynamicTest> theServerRefusesARepeatedLayoutOptionAcrossSpellings() {
        return LAYOUT.stream().flatMap(option -> spellings(option).stream().map(spelling -> DynamicTest.dynamicTest(
                "server: " + String.join(" ", spelling.apply("a", "b")),
                () -> {
                    Path root = tempDirectory.resolve("case-" + cases.incrementAndGet());
                    List<String> args = new ArrayList<>(spelling.apply(
                            layoutValue(root.resolve("first"), option), layoutValue(root.resolve("second"), option)));
                    args.addAll(List.of("server", "identity", "list", "--auth-file", root + "/remote-auth.txt"));

                    Result result = run(args.toArray(String[]::new));

                    assertEquals(CliExitCode.USAGE.code(), result.exitCode(), result.err());
                    assertTrue(result.err().contains("duplicate option: " + option), result.err());
                    assertFalse(Files.exists(root), "a refused invocation must write nothing: " + root);
                })));
    }

    /**
     * The other families do not recognise {@code --data-dir=PATH} as a layout option at all; after a first
     * {@code --data-dir} it is still refused, not read as a second value.
     */
    @TestFactory
    Stream<DynamicTest> theEqualsSpellingAfterALayoutOptionIsRefusedWhereItIsNotALayoutOption() {
        return families().entrySet().stream()
                .filter(family -> !family.getKey().equals("MorpheusServerCli"))
                .map(family -> DynamicTest.dynamicTest(family.getKey(), () -> {
                    Path root = tempDirectory.resolve("case-" + cases.incrementAndGet());
                    List<String> args = new ArrayList<>(List.of(
                            "--data-dir", root.resolve("first").toString(),
                            "--data-dir=" + root.resolve("second")));
                    args.addAll(family.getValue());

                    Result result = run(expand(args, root));

                    assertEquals(CliExitCode.USAGE.code(), result.exitCode(), result.err());
                    assertFalse(Files.exists(root), "a refused invocation must write nothing: " + root);
                }));
    }

    @TestFactory
    Stream<DynamicTest> aRepeatedCommandOptionIsRefusedNamingIt() {
        Map<List<String>, String> invocations = new java.util.LinkedHashMap<>();
        invocations.put(List.of("update-check", "--manifest", ROOT + "/a.json", "--manifest", ROOT + "/b.json"),
                "--manifest");
        invocations.put(List.of("provider-plugins", "discover",
                "--directory", ROOT + "/a", "--directory", ROOT + "/b"), "--directory");
        for (String option : List.of("--plugin", "--workspace", "--sha256")) {
            List<String> probe = new ArrayList<>(List.of("provider-plugins", "probe", "--directory", ROOT + "/plugins",
                    "--plugin", "p", "--workspace", ROOT + "/ws", "--sha256", "0".repeat(64)));
            probe.addAll(List.of(option, "second"));
            invocations.put(probe, option);
        }
        invocations.put(List.of("reason", "analyze", "--question", "q", "--max-claims", "3", "--max-claims", "4"),
                "--max-claims");
        invocations.put(List.of("reason", "analyze", "--question", "q", "--question", "r"), "--question");
        invocations.put(List.of("server", "backup", "verify", "--file", ROOT + "/a.db", "--file", ROOT + "/b.db"),
                "--file");
        invocations.put(List.of("server", "restore", "--confirm", "--confirm", "--file", ROOT + "/a.db"), "--confirm");
        return invocations.entrySet().stream().map(invocation -> DynamicTest.dynamicTest(
                String.join(" ", invocation.getKey()),
                () -> {
                    Path root = tempDirectory.resolve("case-" + cases.incrementAndGet());
                    List<String> args = new ArrayList<>(List.of("--data-dir", root.resolve("data").toString()));
                    args.addAll(invocation.getKey());

                    Result result = run(expand(args, root));

                    assertEquals(CliExitCode.USAGE.code(), result.exitCode(), result.err());
                    assertTrue(result.err().contains("duplicate option: " + invocation.getValue()), result.err());
                    assertFalse(Files.exists(root), "a refused invocation must write nothing: " + root);
                }));
    }

    /** Every single-valued option of the three launchers, in the four combinations of its two spellings. */
    @TestFactory
    Stream<DynamicTest> aLauncherRefusesARepeatedOptionAcrossSpellings() {
        List<Launcher> launchers = new ArrayList<>();
        for (String option : List.of("--host", "--port", "--data-dir", "--config-dir", "--db")) {
            launchers.add(new Launcher(option, args -> ApiLaunchOptions.parse(args, Map.of(), properties()), "api"));
        }
        for (String option : LAYOUT) {
            launchers.add(new Launcher(option, args -> McpLaunchOptions.parse(args, Map.of(), properties()),
                    "mcp", "--stdio"));
        }
        for (String option : List.of("--host", "--port", "--data-dir", "--config-dir", "--db", "--auth-file",
                "--tls-keystore", "--provider-plugin-dir", "--max-concurrent")) {
            launchers.add(new Launcher(option, args -> RemoteApiLaunchOptions.parse(args, Map.of(), properties()),
                    "api", "--remote"));
        }
        return launchers.stream().flatMap(launcher -> spellings(launcher.option()).stream().map(spelling -> {
            List<String> args = new ArrayList<>(List.of(launcher.command()));
            args.addAll(spelling.apply(launcherValue(launcher.option(), 1), launcherValue(launcher.option(), 2)));
            return DynamicTest.dynamicTest(String.join(" ", args), () -> {
                IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                        () -> launcher.parser().accept(args.toArray(String[]::new)));
                assertEquals("duplicate option: " + launcher.option(), refused.getMessage());
            });
        }));
    }

    /** A launcher refusal reaches the exit code: each launcher reports it as a usage error, before any server. */
    @Test
    void aLauncherRefusalIsAUsageError() {
        Result api = launch(err -> MorpheusMain.runApi(
                new String[]{"api", "--port", "8765", "--port=8766", "--unknown"}, err, Map.of(), properties()));
        Result mcp = launch(err -> MorpheusMain.runMcp(
                new String[]{"mcp", "--db", "a.db", "--db=b.db"}, err, Map.of(), properties()));
        Result remote = launch(err -> MorpheusMain.runRemoteApi(
                new String[]{"api", "--remote", "--auth-file=a", "--auth-file", "b"}, err, Map.of(), properties()));

        for (Result result : List.of(api, mcp, remote)) {
            assertEquals(CliExitCode.USAGE.code(), result.exitCode(), result.err());
        }
        assertTrue(api.err().contains("duplicate option: --port"), api.err());
        assertTrue(mcp.err().contains("duplicate option: --db"), mcp.err());
        assertTrue(remote.err().contains("duplicate option: --auth-file"), remote.err());
    }

    /** {@code --workspace-root} names a list: repeating it, in either spelling, still adds a root. */
    @Test
    void aRepeatedWorkspaceRootStillAddsARoot() {
        Path first = tempDirectory.resolve("first-root");
        Path second = tempDirectory.resolve("second-root");
        Path third = tempDirectory.resolve("third-root");

        RemoteApiLaunchOptions remote = RemoteApiLaunchOptions.parse(
                new String[]{"api", "--remote", "--tls-keystore", tempDirectory.resolve("server.p12").toString(),
                        "--workspace-root", first.toString(), "--workspace-root=" + second,
                        "--workspace-root", third.toString()},
                Map.of("MORPHEUS_SERVER_TLS_PASSWORD", "test-password"),
                properties());

        assertEquals(List.of(first, second, third).stream().map(path -> path.toAbsolutePath().normalize()).toList(),
                remote.allowedWorkspaceRoots());
    }

    /** A layout option given once in each launcher, in either spelling, is still accepted. */
    @Test
    void theLaunchersStillAcceptEachOptionOnceInEitherSpelling() {
        Path data = tempDirectory.resolve("data");
        Path config = tempDirectory.resolve("config");

        ApiLaunchOptions api = ApiLaunchOptions.parse(
                new String[]{"--data-dir", data.toString(), "--config-dir=" + config, "api", "--port=9000"},
                Map.of(), properties());
        McpLaunchOptions mcp = McpLaunchOptions.parse(
                new String[]{"--data-dir=" + data, "--config-dir", config.toString(), "mcp", "--stdio"},
                Map.of(), properties());

        assertEquals(9000, api.port());
        assertEquals(data.toAbsolutePath().normalize(), api.layout().dataDirectory());
        assertEquals(config.toAbsolutePath().normalize(), mcp.layout().configDirectory());
    }

    private static List<Spelling> spellings(String option) {
        return List.of(
                (a, b) -> List.of(option, a, option, b),
                (a, b) -> List.of(option + "=" + a, option + "=" + b),
                (a, b) -> List.of(option, a, option + "=" + b),
                (a, b) -> List.of(option + "=" + a, option, b));
    }

    private static String layoutValue(Path directory, String option) {
        return option.equals("--db") ? directory.resolve("morpheus.db").toString() : directory.toString();
    }

    private String launcherValue(String option, int ordinal) {
        return switch (option) {
            case "--host" -> ordinal == 1 ? "127.0.0.1" : "localhost";
            case "--port" -> String.valueOf(8764 + ordinal);
            case "--max-concurrent" -> String.valueOf(ordinal);
            default -> tempDirectory.resolve("launcher-" + ordinal).toString();
        };
    }

    private static String[] expand(List<String> template, Path root) {
        return template.stream().map(token -> token.replace(ROOT, root.toString())).toArray(String[]::new);
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

    private static Result launch(java.util.function.ToIntFunction<PrintStream> launcher) {
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        try (PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8)) {
            int exitCode = launcher.applyAsInt(err);
            return new Result(exitCode, "", errBytes.toString(StandardCharsets.UTF_8));
        }
    }

    private Properties properties() {
        Properties properties = new Properties();
        properties.setProperty("os.name", System.getProperty("os.name", "Windows"));
        properties.setProperty("user.home", tempDirectory.resolve("home").toString());
        return properties;
    }

    @FunctionalInterface
    private interface Spelling {
        List<String> apply(String first, String second);
    }

    private record Launcher(String option, Consumer<String[]> parser, String... command) {
    }

    private record Result(int exitCode, String out, String err) {
    }
}
