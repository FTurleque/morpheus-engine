package com.morpheus.cli;

import com.morpheus.application.operability.OperationalLogMode;
import com.morpheus.application.operability.OperationalRuntime;
import com.morpheus.application.product.ProductMetadata;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.TestInstance;
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
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
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
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CliCommandTranscriptTest {
    private static final String ABSENT = "01900000-0000-7000-8000-000000000001";
    private static final Pattern UUID = Pattern.compile(
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    private static final Pattern INSTANT = Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?Z");
    private static final Pattern AGE = Pattern.compile("(ageSeconds[=\":]+)\\d+");
    private static final Pattern TEMP_PATH = Pattern.compile("<temp>[\\\\/A-Za-z0-9._-]*");

    /**
     * The answers that list entities in the order of their generated identifiers. Two identifiers generated in the same
     * millisecond are in no fixed order (RFC 9562 UUIDv7 randomises the bits after the timestamp), so these
     * transcripts name every generated identifier alike and compare the entities as a set: the item lines of a text
     * listing, the arrays of objects of a JSON answer. The order of the values inside an entity is still compared.
     */
    private static final Set<String> LISTED_BY_GENERATED_IDENTIFIER = Set.of(
            "requirements-find-text", "requirements-find-json",
            "constraints-list-text", "constraints-list-json",
            "decisions-list-text", "decisions-list-json",
            "tasks-list-text", "tasks-list-json",
            "trace-requirement-json", "change-context-json");

    private Path tempDir;

    private Path workspace;

    @BeforeAll
    void copyTheFixture(@TempDir Path directory) throws IOException {
        tempDir = directory;
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
        List<Case> cases = new ArrayList<>();
        cases.addAll(dispatcherCases());
        cases.addAll(projectCases());
        cases.addAll(syncCases());
        cases.addAll(requirementCases());
        cases.addAll(changeCases());
        cases.addAll(changeContentCases());
        cases.addAll(analysisCases());
        return GeneratedCases.nonEmpty("eachCommandPrintsAndRefusesAsItsTranscriptSays",
                cases.stream().map(item -> DynamicTest.dynamicTest(item.name(), () -> verify(item))));
    }

    private static List<Case> dispatcherCases() {
        return List.of(
                new Case("help", State.EMPTY, "help"),
                new Case("version-text", State.EMPTY, "version"),
                new Case("version-json", State.EMPTY, "--json", "version"),
                new Case("unknown-command-text", State.EMPTY, "does-not-exist"),
                new Case("unknown-command-json", State.EMPTY, "--json", "does-not-exist"),
                new Case("paths-text", State.EMPTY, "paths"),
                new Case("paths-json", State.EMPTY, "--json", "paths"),
                new Case("paths-extra-option", State.EMPTY, "paths", "--bogus", "x"));
    }

    private static List<Case> projectCases() {
        return List.of(
                new Case("projects-list-text", State.REGISTERED, "projects", "list"),
                new Case("projects-list-json", State.REGISTERED, "--json", "projects", "list"),
                new Case("projects-list-empty", State.EMPTY, "projects", "list"),
                new Case("projects-add-text", State.REGISTERED, "projects", "add", "--workspace", "<workspace>"),
                new Case("projects-add-json", State.REGISTERED, "--json", "projects", "add", "--workspace", "<workspace>"),
                new Case("projects-refusal", State.EMPTY, "projects", "list", "--bogus", "x"),
                new Case("projects-missing-subcommand", State.EMPTY, "projects"));
    }

    private static List<Case> syncCases() {
        return List.of(
                new Case("sync-first-text", State.REGISTERED, "sync", "--project", "<project>"),
                new Case("sync-first-json", State.REGISTERED, "--json", "sync", "--project", "<project>"),
                new Case("sync-again-text", State.SYNCED, "sync", "--project", "<project>"),
                new Case("sync-refusal", State.EMPTY, "sync", "--project", ABSENT, "--bogus", "x"),
                new Case("sync-absent-project", State.EMPTY, "sync", "--project", ABSENT),
                new Case("sync-status-text", State.SYNCED, "sync-status", "--project", "<project>"),
                new Case("sync-status-json", State.SYNCED, "--json", "sync-status", "--project", "<project>"),
                new Case("sync-status-refusal", State.EMPTY, "sync-status", "--project", ABSENT,
                        "--max-age-minutes", "0"),
                new Case("sync-status-absent-project", State.EMPTY, "--json", "sync-status", "--project", ABSENT));
    }

    private static List<Case> requirementCases() {
        return List.of(
                new Case("requirements-find-text", State.SYNCED, "requirements", "find", "--project", "<project>",
                        "--query", "session"),
                new Case("requirements-find-json", State.SYNCED, "--json", "requirements", "find", "--project",
                        "<project>", "--query", "session"),
                new Case("requirements-find-refusal", State.SYNCED, "requirements", "find", "--project", "<project>",
                        "--bogus", "x"),
                new Case("requirements-find-absent-project", State.EMPTY, "--json", "requirements", "find",
                        "--project", ABSENT),
                new Case("trace-requirement-text", State.SYNCED, "trace-requirement", "--project", "<project>",
                        "--requirement", "<requirement>"),
                new Case("trace-requirement-json", State.SYNCED, "--json", "trace-requirement", "--project",
                        "<project>", "--requirement", "<requirement>"),
                new Case("trace-requirement-refusal", State.SYNCED, "trace-requirement", "--project", "<project>",
                        "--requirement", "<requirement>", "--depth", "0"),
                new Case("trace-requirement-absent-requirement", State.SYNCED, "trace-requirement", "--project",
                        "<project>", "--requirement", ABSENT),
                new Case("trace-requirement-absent-project", State.EMPTY, "trace-requirement", "--project", ABSENT,
                        "--requirement", ABSENT));
    }

    private static List<Case> changeCases() {
        return List.of(
                new Case("changes-list-text", State.SYNCED, "changes", "list", "--project", "<project>"),
                new Case("changes-list-json", State.SYNCED, "--json", "changes", "list", "--project", "<project>"),
                new Case("changes-get-text", State.SYNCED, "changes", "get", "--project", "<project>",
                        "--change", "<change>"),
                new Case("changes-get-json", State.SYNCED, "--json", "changes", "get", "--project", "<project>",
                        "--change", "<change>"),
                new Case("changes-get-absent-change", State.SYNCED, "changes", "get", "--project", "<project>",
                        "--change", ABSENT),
                new Case("changes-get-refusal", State.SYNCED, "changes", "get", "--project", "<project>",
                        "--change", "<change>", "--offset", "1"),
                new Case("changes-refusal", State.SYNCED, "changes", "list", "--project", "<project>", "--bogus", "x"),
                new Case("changes-absent-project", State.EMPTY, "changes", "list", "--project", ABSENT),
                new Case("changes-unknown-subcommand", State.EMPTY, "changes", "remove", "--project", ABSENT));
    }

    private static List<Case> changeContentCases() {
        List<Case> cases = new ArrayList<>();
        for (String command : List.of("constraints", "decisions", "tasks")) {
            cases.add(new Case(command + "-list-text", State.SYNCED, command, "list", "--project", "<project>",
                    "--change", "<change>"));
            cases.add(new Case(command + "-list-json", State.SYNCED, "--json", command, "list", "--project",
                    "<project>", "--change", "<change>"));
            cases.add(new Case(command + "-list-refusal", State.SYNCED, command, "list", "--project", "<project>",
                    "--change", "<change>", "--bogus", "x"));
            cases.add(new Case(command + "-list-absent-project", State.EMPTY, command, "list", "--project", ABSENT,
                    "--change", ABSENT));
            cases.add(new Case(command + "-list-absent-change", State.SYNCED, "--json", command, "list", "--project",
                    "<project>", "--change", ABSENT));
        }
        return List.copyOf(cases);
    }

    private static List<Case> analysisCases() {
        return List.of(
                new Case("change-context-text", State.SYNCED, "change-context", "--project", "<project>",
                        "--change", "<change>"),
                new Case("change-context-json", State.SYNCED, "--json", "change-context", "--project", "<project>",
                        "--change", "<change>"),
                new Case("change-context-refusal", State.SYNCED, "change-context", "--project", "<project>",
                        "--change", "<change>", "--bogus", "x"),
                new Case("change-context-absent-change", State.SYNCED, "change-context", "--project", "<project>",
                        "--change", ABSENT),
                new Case("analyze-change-text", State.SYNCED, "analyze-change", "--project", "<project>",
                        "--change", "<change>"),
                new Case("analyze-change-json", State.SYNCED, "--json", "analyze-change", "--project", "<project>",
                        "--change", "<change>"),
                new Case("analyze-change-refusal", State.SYNCED, "analyze-change", "--project", "<project>",
                        "--change", "<change>", "--depth", "21"),
                new Case("analyze-change-absent-project", State.EMPTY, "analyze-change", "--project", ABSENT,
                        "--change", ABSENT),
                new Case("quality-text", State.SYNCED, "quality", "--project", "<project>"),
                new Case("quality-json", State.SYNCED, "--json", "quality", "--project", "<project>"),
                new Case("quality-refusal", State.SYNCED, "quality", "--project", "<project>", "--bogus", "x"),
                new Case("quality-absent-project", State.EMPTY, "--json", "quality", "--project", ABSENT));
    }

    private enum State { EMPTY, REGISTERED, SYNCED }

    private record Case(String name, State state, String... args) {
    }

    private void verify(Case item) throws IOException {
        Path dataDirectory = tempDir.resolve("data-" + item.name());
        Map<String, String> known = prepare(dataDirectory, item.state());
        String[] resolved = new String[item.args().length];
        for (int index = 0; index < resolved.length; index++) {
            resolved[index] = known.getOrDefault(item.args()[index], item.args()[index]);
        }
        Invocation run = invoke(dataDirectory, resolved);
        boolean unordered = LISTED_BY_GENERATED_IDENTIFIER.contains(item.name());
        String transcript = String.join("\n",
                normalized("args: " + String.join(" ", resolved), known, false),
                "exit: " + run.exitCode(),
                "stdout:",
                normalized(run.stdout(), known, unordered),
                "stderr:",
                normalized(run.standardError(), known, false));
        compare(item.name(), transcript);
    }

    /** The project, change and requirement a case names, created as far as its state says. */
    private Map<String, String> prepare(Path dataDirectory, State state) {
        Map<String, String> known = new LinkedHashMap<>();
        known.put("<workspace>", workspace.toString());
        if (state == State.EMPTY) {
            return known;
        }
        Invocation add = invoke(dataDirectory, "projects", "add", "--workspace", workspace.toString());
        assertEquals(0, add.exitCode(), add.standardError());
        String project = value(add.stdout(), "projectId");
        known.put("<project>", project);
        if (state == State.SYNCED) {
            Invocation sync = invoke(dataDirectory, "sync", "--project", project);
            assertEquals(0, sync.exitCode(), sync.standardError());
            known.put("<change>", onlyItemIdentifier(invoke(dataDirectory, "changes", "list", "--project", project)));
            known.put("<requirement>", onlyItemIdentifier(invoke(
                    dataDirectory, "requirements", "find", "--project", project, "--query", "expiration")));
        }
        return known;
    }

    private String normalized(String text, Map<String, String> known, boolean unordered) {
        String result = withTemporaryDirectoryNamed(text.replace("\r\n", "\n"));
        for (Map.Entry<String, String> entry : known.entrySet()) {
            if (!entry.getKey().equals("<workspace>")) {
                result = result.replace(entry.getValue(), entry.getKey());
            }
        }
        if (unordered) {
            result = asASetOfEntities(UUID.matcher(result).replaceAll(match ->
                    match.group().equals(ABSENT) ? match.group() : "<id>"));
        }
        result = withGeneratedIdentifiersNumbered(result);
        result = INSTANT.matcher(result).replaceAll("<instant>");
        result = AGE.matcher(result).replaceAll("$1<age>");
        return result.replace(ProductMetadata.version(), "<version>");
    }

    private String withTemporaryDirectoryNamed(String text) {
        String root = tempDir.toAbsolutePath().toString();
        String named = text.replace(root.replace("\\", "\\\\"), "<temp>")
                .replace(root, "<temp>")
                .replace(root.replace('\\', '/'), "<temp>");
        Matcher path = TEMP_PATH.matcher(named);
        StringBuilder result = new StringBuilder();
        while (path.find()) {
            path.appendReplacement(result, Matcher.quoteReplacement(
                    path.group().replace("\\\\", "/").replace('\\', '/')));
        }
        path.appendTail(result);
        return result.toString();
    }

    /** A JSON answer with its arrays of objects sorted; a text listing with its lines after the first sorted. */
    private static String asASetOfEntities(String output) {
        List<String> lines = output.lines().toList();
        if (lines.size() == 1 && lines.getFirst().startsWith("{")) {
            return TranscriptJson.withObjectArraysSorted(lines.getFirst()) + "\n";
        }
        if (lines.isEmpty()) {
            return output;
        }
        return Stream.concat(Stream.of(lines.getFirst()), lines.stream().skip(1).sorted())
                .collect(Collectors.joining("\n", "", "\n"));
    }

    private static String withGeneratedIdentifiersNumbered(String text) {
        Map<String, String> generated = new LinkedHashMap<>();
        Matcher uuid = UUID.matcher(text);
        StringBuilder result = new StringBuilder();
        while (uuid.find()) {
            String placeholder = uuid.group().equals(ABSENT) ? uuid.group()
                    : generated.computeIfAbsent(uuid.group(), key -> "<id-" + (generated.size() + 1) + ">");
            uuid.appendReplacement(result, Matcher.quoteReplacement(placeholder));
        }
        uuid.appendTail(result);
        return result.toString();
    }

    private static void compare(String name, String actual) throws IOException {
        String expected;
        try (InputStream stream = CliCommandTranscriptTest.class.getResourceAsStream("transcripts/" + name + ".txt")) {
            expected = stream == null ? null : new String(stream.readAllBytes(), StandardCharsets.UTF_8).replace("\r\n", "\n");
        }
        if (!actual.equals(expected)) {
            Path written = Path.of("target", "transcripts-actual", name + ".txt");
            Files.createDirectories(written.getParent());
            Files.writeString(written, actual, StandardCharsets.UTF_8);
        }
        assertEquals(expected, actual, "the transcript of " + name + " differs; the run's output is in target/transcripts-actual");
    }

    private Invocation invoke(Path dataDirectory, String... command) {
        String[] args = new String[command.length + 2];
        args[0] = "--data-dir";
        args[1] = dataDirectory.toString();
        System.arraycopy(command, 0, args, 2, command.length);
        ByteArrayOutputStream outputBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errorBytes = new ByteArrayOutputStream();
        try (PrintStream output = new PrintStream(outputBytes, true, StandardCharsets.UTF_8);
             PrintStream error = new PrintStream(errorBytes, true, StandardCharsets.UTF_8)) {
            Properties properties = new Properties();
            properties.setProperty("os.name", System.getProperty("os.name"));
            properties.setProperty("user.home", tempDir.resolve("home").toString());
            int exitCode = MorpheusMain.run(
                    OperationalRuntime.of(OperationalLogMode.OFF), args, output, error, Map.of(), properties);
            return new Invocation(exitCode, outputBytes.toString(StandardCharsets.UTF_8),
                    errorBytes.toString(StandardCharsets.UTF_8));
        }
    }

    private static String value(String output, String key) {
        String prefix = key + "=";
        return output.lines().filter(line -> line.startsWith(prefix)).findFirst()
                .map(line -> line.substring(prefix.length()).trim())
                .orElseThrow(() -> new AssertionError("missing " + key + " in output: " + output));
    }

    private static String onlyItemIdentifier(Invocation invocation) {
        assertEquals(0, invocation.exitCode(), invocation.standardError());
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

    private record Invocation(int exitCode, String stdout, String standardError) {}
}
