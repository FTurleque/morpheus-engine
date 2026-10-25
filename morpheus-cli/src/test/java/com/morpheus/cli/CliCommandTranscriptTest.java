package com.morpheus.cli;

import com.morpheus.application.operability.OperationalLogMode;
import com.morpheus.application.operability.OperationalRuntime;
import com.morpheus.application.product.ProductMetadata;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What each command of the CLI prints, in text and in JSON, and how it refuses, recorded whole.
 *
 * <p>Every case runs on a database of its own, prepared from a copy of the {@code openspec-basic} fixture, and its
 * exit code, standard output and standard error are compared with a transcript under
 * {@code src/test/resources/com/morpheus/cli/transcripts/}. What a run cannot reproduce is replaced before the
 * comparison, and only that: the identifiers the run generated, the instants it read from the clock, the age it
 * computed from them, the temporary directory and its path separators, and the product version. A transcript is the
 * contract a command keeps whichever class serves it; when one differs, the output of the run is written under
 * {@code target/transcripts-actual/} beside the failure.</p>
 *
 * <p>The CLI reaches its database through SQLite only, so these cases build one; no command of the CLI can be given
 * the memory store.</p>
 */
class CliCommandTranscriptTest {
    private static final String ABSENT_PROJECT = "01900000-0000-7000-8000-000000000001";
    private static final Pattern UUID = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern INSTANT = Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?Z");
    private static final Pattern AGE = Pattern.compile("(ageSeconds[=\":]+)\\d+");
    private static final Pattern TEMP_PATH = Pattern.compile("<temp>[\\\\/A-Za-z0-9._-]*");

    /**
     * The traversals list their nodes and links by identifier, and two identifiers generated in the same millisecond
     * are in no fixed order (RFC 9562 UUIDv7 randomises the bits after the timestamp). These transcripts name every
     * generated identifier alike and compare each array of the JSON as a set of elements.
     */
    private static final java.util.Set<String> UNORDERED = java.util.Set.of("trace-requirement-json", "change-context-json");

    @TempDir
    static Path tempDir;

    private static Path workspace;

    @BeforeAll
    static void copyTheFixture() throws IOException {
        workspace = tempDir.resolve("workspace");
        Path source = fixture();
        try (Stream<Path> files = Files.walk(source)) {
            for (Path file : (Iterable<Path>) files::iterator) {
                Path target = workspace.resolve(source.relativize(file).toString());
                if (Files.isDirectory(file)) {
                    Files.createDirectories(target);
                } else {
                    Files.copy(file, target);
                }
            }
        }
    }

    @TestFactory
    Stream<DynamicTest> eachCommandPrintsAndRefusesAsItsTranscriptSays() {
        return GeneratedCases.nonEmpty("eachCommandPrintsAndRefusesAsItsTranscriptSays",
                cases().stream().map(item -> DynamicTest.dynamicTest(item.name(), () -> item.verify())));
    }

    private static List<Case> cases() {
        List<Case> cases = new ArrayList<>();
        cases.add(new Case("help", State.EMPTY, "help"));
        cases.add(new Case("version-text", State.EMPTY, "version"));
        cases.add(new Case("version-json", State.EMPTY, "--json", "version"));
        cases.add(new Case("unknown-command-text", State.EMPTY, "does-not-exist"));
        cases.add(new Case("unknown-command-json", State.EMPTY, "--json", "does-not-exist"));
        cases.add(new Case("paths-text", State.EMPTY, "paths"));
        cases.add(new Case("paths-json", State.EMPTY, "--json", "paths"));
        cases.add(new Case("projects-list-text", State.REGISTERED, "projects", "list"));
        cases.add(new Case("projects-list-json", State.REGISTERED, "--json", "projects", "list"));
        cases.add(new Case("projects-list-empty", State.EMPTY, "projects", "list"));
        cases.add(new Case("projects-add-text", State.REGISTERED, "projects", "add", "--workspace", "<workspace>"));
        cases.add(new Case("projects-add-json", State.REGISTERED, "--json", "projects", "add", "--workspace", "<workspace>"));
        cases.add(new Case("projects-refusal", State.EMPTY, "projects", "list", "--bogus", "x"));
        cases.add(new Case("projects-missing-subcommand", State.EMPTY, "projects"));
        cases.add(new Case("sync-first-text", State.REGISTERED, "sync", "--project", "<project>"));
        cases.add(new Case("sync-first-json", State.REGISTERED, "--json", "sync", "--project", "<project>"));
        cases.add(new Case("sync-again-text", State.SYNCED, "sync", "--project", "<project>"));
        cases.add(new Case("sync-refusal", State.EMPTY, "sync", "--project", ABSENT_PROJECT, "--bogus", "x"));
        cases.add(new Case("sync-absent-project", State.EMPTY, "sync", "--project", ABSENT_PROJECT));
        cases.add(new Case("sync-status-text", State.SYNCED, "sync-status", "--project", "<project>"));
        cases.add(new Case("sync-status-json", State.SYNCED, "--json", "sync-status", "--project", "<project>"));
        cases.add(new Case("sync-status-refusal", State.EMPTY, "sync-status", "--project", ABSENT_PROJECT,
                "--max-age-minutes", "0"));
        cases.add(new Case("requirements-find-text", State.SYNCED, "requirements", "find", "--project", "<project>",
                "--query", "session"));
        cases.add(new Case("requirements-find-json", State.SYNCED, "--json", "requirements", "find", "--project",
                "<project>", "--query", "session"));
        cases.add(new Case("requirements-find-refusal", State.SYNCED, "requirements", "find", "--project",
                "<project>", "--bogus", "x"));
        cases.add(new Case("requirements-find-absent-project", State.EMPTY, "--json", "requirements", "find",
                "--project", ABSENT_PROJECT));
        cases.add(new Case("changes-list-text", State.SYNCED, "changes", "list", "--project", "<project>"));
        cases.add(new Case("changes-list-json", State.SYNCED, "--json", "changes", "list", "--project", "<project>"));
        cases.add(new Case("changes-get-text", State.SYNCED, "changes", "get", "--project", "<project>",
                "--change", "<change>"));
        cases.add(new Case("changes-get-json", State.SYNCED, "--json", "changes", "get", "--project", "<project>",
                "--change", "<change>"));
        cases.add(new Case("changes-get-absent-change", State.SYNCED, "changes", "get", "--project", "<project>",
                "--change", ABSENT_PROJECT));
        cases.add(new Case("changes-refusal", State.SYNCED, "changes", "list", "--project", "<project>",
                "--bogus", "x"));
        cases.add(new Case("changes-absent-project", State.EMPTY, "changes", "list", "--project", ABSENT_PROJECT));
        cases.add(new Case("changes-unknown-subcommand", State.EMPTY, "changes", "remove", "--project", ABSENT_PROJECT));
        cases.add(new Case("constraints-list-text", State.SYNCED, "constraints", "list", "--project", "<project>",
                "--change", "<change>"));
        cases.add(new Case("constraints-list-json", State.SYNCED, "--json", "constraints", "list", "--project",
                "<project>", "--change", "<change>"));
        cases.add(new Case("constraints-list-refusal", State.SYNCED, "constraints", "list", "--project", "<project>",
                "--change", "<change>", "--bogus", "x"));
        cases.add(new Case("constraints-list-absent-project", State.EMPTY, "constraints", "list", "--project",
                ABSENT_PROJECT, "--change", ABSENT_PROJECT));
        cases.add(new Case("decisions-list-text", State.SYNCED, "decisions", "list", "--project", "<project>",
                "--change", "<change>"));
        cases.add(new Case("decisions-list-json", State.SYNCED, "--json", "decisions", "list", "--project",
                "<project>", "--change", "<change>"));
        cases.add(new Case("decisions-list-refusal", State.SYNCED, "decisions", "list", "--project", "<project>",
                "--change", "<change>", "--bogus", "x"));
        cases.add(new Case("decisions-list-absent-project", State.EMPTY, "decisions", "list", "--project",
                ABSENT_PROJECT, "--change", ABSENT_PROJECT));
        cases.add(new Case("tasks-list-text", State.SYNCED, "tasks", "list", "--project", "<project>",
                "--change", "<change>"));
        cases.add(new Case("tasks-list-json", State.SYNCED, "--json", "tasks", "list", "--project", "<project>",
                "--change", "<change>"));
        cases.add(new Case("tasks-list-refusal", State.SYNCED, "tasks", "list", "--project", "<project>",
                "--change", "<change>", "--bogus", "x"));
        cases.add(new Case("tasks-list-absent-project", State.EMPTY, "tasks", "list", "--project", ABSENT_PROJECT,
                "--change", ABSENT_PROJECT));
        cases.add(new Case("trace-requirement-text", State.SYNCED, "trace-requirement", "--project", "<project>",
                "--requirement", "<requirement>"));
        cases.add(new Case("trace-requirement-json", State.SYNCED, "--json", "trace-requirement", "--project",
                "<project>", "--requirement", "<requirement>"));
        cases.add(new Case("trace-requirement-refusal", State.SYNCED, "trace-requirement", "--project", "<project>",
                "--requirement", "<requirement>", "--depth", "0"));
        cases.add(new Case("trace-requirement-absent-requirement", State.SYNCED, "trace-requirement", "--project",
                "<project>", "--requirement", ABSENT_PROJECT));
        cases.add(new Case("change-context-text", State.SYNCED, "change-context", "--project", "<project>",
                "--change", "<change>"));
        cases.add(new Case("change-context-json", State.SYNCED, "--json", "change-context", "--project", "<project>",
                "--change", "<change>"));
        cases.add(new Case("change-context-refusal", State.SYNCED, "change-context", "--project", "<project>",
                "--change", "<change>", "--bogus", "x"));
        cases.add(new Case("change-context-absent-change", State.SYNCED, "change-context", "--project", "<project>",
                "--change", ABSENT_PROJECT));
        cases.add(new Case("analyze-change-text", State.SYNCED, "analyze-change", "--project", "<project>",
                "--change", "<change>"));
        cases.add(new Case("analyze-change-json", State.SYNCED, "--json", "analyze-change", "--project", "<project>",
                "--change", "<change>"));
        cases.add(new Case("analyze-change-refusal", State.SYNCED, "analyze-change", "--project", "<project>",
                "--change", "<change>", "--depth", "21"));
        cases.add(new Case("analyze-change-absent-project", State.EMPTY, "analyze-change", "--project",
                ABSENT_PROJECT, "--change", ABSENT_PROJECT));
        cases.add(new Case("quality-text", State.SYNCED, "quality", "--project", "<project>"));
        cases.add(new Case("quality-json", State.SYNCED, "--json", "quality", "--project", "<project>"));
        cases.add(new Case("quality-refusal", State.SYNCED, "quality", "--project", "<project>", "--bogus", "x"));
        cases.add(new Case("quality-absent-project", State.EMPTY, "--json", "quality", "--project", ABSENT_PROJECT));
        return List.copyOf(cases);
    }

    private enum State { EMPTY, REGISTERED, SYNCED }

    private record Case(String name, State state, String... args) {
        void verify() throws IOException {
            Path data = tempDir.resolve("data-" + name);
            Map<String, String> known = new LinkedHashMap<>();
            known.put("<workspace>", workspace.toString());
            if (state != State.EMPTY) {
                Invocation add = invoke(data, "projects", "add", "--workspace", workspace.toString());
                assertEquals(0, add.exitCode(), add.stderr());
                known.put("<project>", value(add.stdout(), "projectId"));
            }
            if (state == State.SYNCED) {
                Invocation sync = invoke(data, "sync", "--project", known.get("<project>"));
                assertEquals(0, sync.exitCode(), sync.stderr());
                known.put("<change>", firstColumn(invoke(data, "changes", "list", "--project", known.get("<project>"))));
                known.put("<requirement>", firstColumn(invoke(
                        data, "requirements", "find", "--project", known.get("<project>"), "--query", "expiration")));
            }
            String[] resolved = new String[args.length];
            for (int index = 0; index < args.length; index++) {
                resolved[index] = known.getOrDefault(args[index], args[index]);
            }
            Invocation run = invoke(data, resolved);
            String transcript = normalized(String.join(System.lineSeparator(),
                    "args: " + String.join(" ", resolved),
                    "exit: " + run.exitCode(),
                    "stdout:",
                    run.stdout(),
                    "stderr:",
                    run.stderr()), known, UNORDERED.contains(name));
            compare(name, transcript);
        }
    }

    private static String normalized(String text, Map<String, String> known, boolean unordered) {
        String result = text.replace("\r\n", "\n");
        String root = tempDir.toAbsolutePath().toString();
        result = result.replace(root.replace("\\", "\\\\"), "<temp>")
                .replace(root, "<temp>")
                .replace(root.replace('\\', '/'), "<temp>");
        Matcher path = TEMP_PATH.matcher(result);
        StringBuilder paths = new StringBuilder();
        while (path.find()) {
            path.appendReplacement(paths, Matcher.quoteReplacement(path.group().replace("\\\\", "/").replace('\\', '/')));
        }
        path.appendTail(paths);
        result = paths.toString();
        for (Map.Entry<String, String> entry : known.entrySet()) {
            if (!entry.getKey().equals("<workspace>")) {
                result = result.replace(entry.getValue(), entry.getKey());
            }
        }
        if (unordered) {
            result = UUID.matcher(result).replaceAll(match -> match.group().equals(ABSENT_PROJECT)
                    ? match.group() : "<id>");
            result = result.lines()
                    .map(line -> line.startsWith("{") ? TranscriptJson.withSortedArrays(line) : line)
                    .collect(java.util.stream.Collectors.joining("\n", "", result.endsWith("\n") ? "\n" : ""));
        }
        Map<String, String> generated = new LinkedHashMap<>();
        Matcher uuid = UUID.matcher(result);
        StringBuilder ids = new StringBuilder();
        while (uuid.find()) {
            String placeholder = uuid.group().equals(ABSENT_PROJECT) ? uuid.group()
                    : generated.computeIfAbsent(uuid.group(), key -> "<id-" + (generated.size() + 1) + ">");
            uuid.appendReplacement(ids, Matcher.quoteReplacement(placeholder));
        }
        uuid.appendTail(ids);
        result = ids.toString();
        result = INSTANT.matcher(result).replaceAll("<instant>");
        result = AGE.matcher(result).replaceAll("$1<age>");
        return result.replace(ProductMetadata.version(), "<version>");
    }

    private static void compare(String name, String actual) throws IOException {
        String resource = "transcripts/" + name + ".txt";
        String expected;
        try (InputStream stream = CliCommandTranscriptTest.class.getResourceAsStream(resource)) {
            expected = stream == null ? null : new String(stream.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
        if (!actual.equals(expected)) {
            Path written = Path.of("target", "transcripts-actual", name + ".txt");
            Files.createDirectories(written.getParent());
            Files.writeString(written, actual, StandardCharsets.UTF_8);
        }
        assertEquals(expected, actual, "the transcript of " + name + " differs; the run's output is in target/transcripts-actual");
    }

    private static Invocation invoke(Path data, String... command) {
        String[] args = new String[command.length + 2];
        args[0] = "--data-dir";
        args[1] = data.toString();
        System.arraycopy(command, 0, args, 2, command.length);
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
             PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8)) {
            Properties properties = new Properties();
            properties.setProperty("os.name", System.getProperty("os.name"));
            properties.setProperty("user.home", tempDir.resolve("home").toString());
            int exitCode = MorpheusMain.run(OperationalRuntime.of(OperationalLogMode.OFF), args, out, err, Map.of(), properties);
            return new Invocation(exitCode, outBytes.toString(StandardCharsets.UTF_8), errBytes.toString(StandardCharsets.UTF_8));
        }
    }

    private static String value(String output, String key) {
        String prefix = key + "=";
        return output.lines().filter(line -> line.startsWith(prefix)).findFirst()
                .map(line -> line.substring(prefix.length()).trim())
                .orElseThrow(() -> new AssertionError("missing " + key + " in output: " + output));
    }

    private static String firstColumn(Invocation invocation) {
        assertEquals(0, invocation.exitCode(), invocation.stderr());
        List<String> items = invocation.stdout().lines()
                .filter(line -> !line.startsWith("snapshotId=") && !line.isBlank())
                .toList();
        assertEquals(1, items.size(), "the fixture must name exactly one item here: " + invocation.stdout());
        return items.getFirst().split("\\t", 2)[0];
    }

    private static Path fixture() {
        Path current = Path.of("").toAbsolutePath().normalize();
        for (Path candidate : List.of(
                current.resolve("experiments/m0/fixtures/openspec-basic"),
                current.resolve("../experiments/m0/fixtures/openspec-basic").normalize())) {
            if (Files.isDirectory(candidate)) {
                return candidate;
            }
        }
        throw new UncheckedIOException(new IOException("M0 fixture openspec-basic not found from " + current));
    }

    private record Invocation(int exitCode, String stdout, String stderr) {}
}
