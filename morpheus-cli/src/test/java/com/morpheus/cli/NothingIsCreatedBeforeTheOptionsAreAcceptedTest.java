package com.morpheus.cli;

import com.morpheus.domain.project.ProjectSpecificationId;
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
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code policy}, {@code query}, {@code views} and {@code export} accept their action and their options before they
 * open the store, which creates the database, its directory and its schema (CLI-9).
 *
 * <p>Each invocation runs against a data directory that does not exist yet, so the store having been opened is
 * observable: a refused invocation leaves the case directory absent, an accepted one leaves the database file behind.
 * The two directions are asserted on every action, over the whole option vocabulary of its adapter.</p>
 *
 * <p>{@link #ACTIONS} is written out here, independently of the adapters' tables: it is the set of options each
 * {@code case} read, and allowed through its own {@code rejectUnknown}, before the validation was moved ahead of the
 * store. The tables are held to it twice. By value, in {@link #theTablesHoldExactlyTheListedActionsAndOptions}: a table
 * entry with one option more or one less than its action here fails, whatever the option is called. By behaviour, in
 * {@link #everyActionAcceptsTheOptionsItReadsAndRefusesEveryOtherBeforeTheStoreIsOpened}: the options probed are the
 * union of this list and of every value of every table, plus a misspelling, so an option that only a table knows is
 * sent to every action too.</p>
 *
 * <p>What is not covered: a refusal that needs the value of an option to be read -- a missing required option, a
 * malformed identifier, an integer that is not one, a scope given twice -- still comes after the store is opened, in
 * these four commands as in {@code portfolio}; so do refusals on the state of the store, which cannot come earlier.
 * The commands of {@code MorpheusCli} that open the store before checking their options ({@code projects},
 * {@code changes}) and {@code change-orchestration}, whose per-action option refusals come after it, are not
 * covered here (ADR-0108, amendment of 30 September 2026). The probe population is the adapters' known options and one
 * misspelling, not every string: an option neither listed here nor in a table is only held by the value comparison,
 * which it fails. A future adapter that opens a store is not discovered; this class names its commands.</p>
 */
class NothingIsCreatedBeforeTheOptionsAreAcceptedTest {
    private static final String P = ProjectSpecificationId.generate().toString();
    private static final String MISSPELLED = "projet";
    private static final List<String> EXPORT_FORMAT = List.of("--format", "json");
    /** The first thing every {@code case} of the four switches reads, when it is absent. */
    private static final java.util.regex.Pattern REACHED_ITS_CASE = java.util.regex.Pattern.compile(
            "MORPHEUS error \\[2\\]: (--[a-z-]+ is required|exactly one of --project or --portfolio is required)");

    /** The options each action read at {@code 933a63fe}, copied from its {@code case}; the table under test is not read. */
    private static final List<Action> ACTIONS = List.of(
            policy("pack-create", List.of("pack", "create"), "name", "rules", "actor", "reason"),
            policy("pack-list", List.of("pack", "list")),
            policy("pack-get", List.of("pack", "get"), "id"),
            policy("pack-versions", List.of("pack", "versions"), "id"),
            policy("pack-update", List.of("pack", "update"),
                    "id", "expected-revision", "name", "rules", "actor", "reason"),
            policy("activate", List.of("activate"),
                    "id", "version", "project", "portfolio", "expected-revision", "actor", "reason"),
            policy("activations", List.of("activations"), "project", "portfolio"),
            policy("deactivate", List.of("deactivate"),
                    "id", "project", "portfolio", "expected-revision", "actor", "reason"),
            policy("override-put", List.of("override", "put"),
                    "id", "rule", "mode", "project", "portfolio", "expected-revision", "actor", "reason"),
            policy("override-list", List.of("override", "list"), "project", "portfolio"),
            policy("override-remove", List.of("override", "remove"),
                    "id", "rule", "project", "portfolio", "expected-revision", "actor", "reason"),
            policy("evaluate", List.of("evaluate"), "id", "project", "portfolio"),
            policy("dry-run", List.of("dry-run"), "id", "version", "project", "portfolio"),
            policy("audit", List.of("audit"), "id"),
            query("query execute", List.of("query", "execute"), List.of(),
                    "project", "portfolio", "entity", "filter", "sort", "fields", "offset", "limit"),
            query("views create", List.of("views", "create"), List.of(),
                    "name", "project", "portfolio", "entity", "filter", "sort", "fields", "offset", "limit"),
            query("views list", List.of("views", "list"), List.of(), "project", "portfolio"),
            query("views get", List.of("views", "get"), List.of(), "id"),
            query("views versions", List.of("views", "versions"), List.of(), "id"),
            query("views update", List.of("views", "update"), List.of(),
                    "id", "expected-revision", "name", "entity", "filter", "sort", "fields", "offset", "limit"),
            query("views archive", List.of("views", "archive"), List.of(), "id", "expected-revision"),
            query("views execute", List.of("views", "execute"), List.of(), "id"),
            query("export query", List.of("export", "query"), EXPORT_FORMAT,
                    "format", "project", "portfolio", "entity", "filter", "sort", "fields"),
            query("export view", List.of("export", "view"), EXPORT_FORMAT, "format", "id"));

    @TempDir
    Path tempDirectory;

    private final AtomicInteger cases = new AtomicInteger();

    /**
     * Every option of the adapter's vocabulary, plus a misspelling, given alone to every action: the options the action
     * reads open the store, every other one is refused and nothing is created.
     */
    @TestFactory
    Stream<DynamicTest> everyActionAcceptsTheOptionsItReadsAndRefusesEveryOtherBeforeTheStoreIsOpened() {
        return ACTIONS.stream().flatMap(action -> vocabulary(action.family()).stream()
                .filter(option -> !action.base().contains("--" + option))
                .map(option -> DynamicTest.dynamicTest(action.label() + " --" + option, () -> {
                    Path root = caseDirectory();
                    Result result = run(root, action, "--" + option, "x");

                    if (action.options().contains(option)) {
                        assertFalse(result.err().contains("unknown option"), result.err());
                        assertFalse(result.err().contains("is not accepted by export"), result.err());
                        assertTrue(Files.isRegularFile(database(root)),
                                "an accepted option reaches the store, which creates the database: " + result.err());
                    } else {
                        assertEquals(CliExitCode.USAGE.code(), result.exitCode(), result.err());
                        assertTrue(result.err().contains(refusalOf(action, option)), result.err());
                        assertFalse(Files.exists(root), "a refused option must create nothing: " + root);
                    }
                })));
    }

    /** A blank value and a repeated option are refused before the store too, on every action that takes an option. */
    @TestFactory
    Stream<DynamicTest> aBlankOrRepeatedOptionIsRefusedBeforeTheStoreIsOpened() {
        return ACTIONS.stream()
                .filter(action -> action.options().stream().anyMatch(option -> !action.base().contains("--" + option)))
                .flatMap(action -> {
                    String option = new TreeSet<>(action.options()).stream()
                            .filter(candidate -> !action.base().contains("--" + candidate))
                            .findFirst().orElseThrow();
                    return Stream.of(
                            refusal(action.label() + " --" + option + " ''", action,
                                    "--" + option + " requires a non-blank value", "--" + option, ""),
                            refusal(action.label() + " --" + option + " twice", action,
                                    "duplicate option: --" + option, "--" + option, "a", "--" + option, "b"));
                });
    }

    /**
     * The refusal first reported (an empty {@code --id}), an unknown or missing action for each of the four commands,
     * and {@code portfolio}, whose table already preceded the store and which no test held to it.
     */
    @TestFactory
    Stream<DynamicTest> anUnknownOrMissingActionIsRefusedBeforeTheStoreIsOpened() {
        Map<List<String>, String> invocations = new java.util.LinkedHashMap<>();
        invocations.put(List.of("policy", "evaluate", "--project", P, "--id", ""), "--id requires a non-blank value");
        invocations.put(List.of("policy", "frobnicate", "--id", "x"), "unknown policy action: frobnicate");
        invocations.put(List.of("policy", "pack", "frobnicate"), "unknown policy action: pack-frobnicate");
        invocations.put(List.of("query", "frobnicate", "--entity", "change"), "query requires action execute");
        invocations.put(List.of("query", "--entity", "change"), "query requires action execute");
        invocations.put(List.of("views", "frobnicate", "--id", "x"), "unknown views action: frobnicate");
        invocations.put(List.of("views", "--id", "x"), "views requires an action");
        invocations.put(List.of("export", "frobnicate", "--format", "json"), "unknown export action: frobnicate");
        invocations.put(List.of("export", "--format", "json"), "export requires query or view");
        invocations.put(List.of("export", "view", "--format", "yaml", "--id", "x"), "--format must be json, csv or markdown");
        invocations.put(List.of("portfolio", "list", "--projet", "x"), "unknown option: --projet");
        invocations.put(List.of("portfolio", "frobnicate"), "unknown portfolio action: frobnicate");
        return invocations.entrySet().stream().map(invocation -> DynamicTest.dynamicTest(
                String.join(" ", invocation.getKey()), () -> {
                    Path root = caseDirectory();
                    List<String> args = new ArrayList<>(List.of("--data-dir", root.resolve("data").toString()));
                    args.addAll(invocation.getKey());

                    Result result = run(args.toArray(String[]::new));

                    assertEquals(CliExitCode.USAGE.code(), result.exitCode(), result.err());
                    assertTrue(result.err().contains(invocation.getValue()), result.err());
                    assertFalse(Files.exists(root), "a refused invocation must create nothing: " + root);
                }));
    }

    /** Moving the validation ahead must not stop a valid invocation from creating the database it needs. */
    @TestFactory
    Stream<DynamicTest> aValidInvocationOnAMissingDataDirectoryStillCreatesTheDatabase() {
        return Stream.of(
                        List.of("policy", "pack", "list"),
                        List.of("policy", "evaluate", "--project", P),
                        List.of("query", "execute", "--project", P, "--entity", "change"),
                        List.of("views", "list", "--project", P),
                        List.of("export", "query", "--format", "json", "--project", P, "--entity", "change"))
                .map(command -> DynamicTest.dynamicTest(String.join(" ", command), () -> {
                    Path root = caseDirectory();
                    List<String> args = new ArrayList<>(List.of("--data-dir", root.resolve("data").toString()));
                    args.addAll(command);

                    Result result = run(args.toArray(String[]::new));

                    assertEquals(CliExitCode.SUCCESS.code(), result.exitCode(), result.err());
                    assertTrue(Files.isRegularFile(database(root)), "the database must have been created: " + root);
                }));
    }

    /**
     * The adapters' tables hold exactly the actions listed here, each with exactly its listed options. A table key the
     * list does not know is an action nobody decided the options of; a listed action missing from the table is one the
     * command no longer accepts; an option added to or removed from an entry changes what the command accepts, even
     * when the {@code case} reads it and no probe would send it.
     */
    @Test
    void theTablesHoldExactlyTheListedActionsAndOptions() {
        assertEquals(expected("policy", ""), MorpheusPolicyCli.ACTION_OPTIONS);
        assertEquals(expected("query", "views "), MorpheusQueryCli.VIEW_ACTION_OPTIONS);
        assertEquals(expected("query", "export "), MorpheusQueryCli.EXPORT_ACTION_OPTIONS);
        assertEquals(expected("query", "query ").get("execute"), MorpheusQueryCli.EXECUTE_OPTIONS);
    }

    /**
     * A usage refusal comes before a store that cannot be opened: the data directory sits under a regular file, so
     * creating it fails. A valid invocation meets that failure (code 4); an invocation with an unknown option is now
     * refused for what it is (code 2) instead of reporting the store.
     */
    @Test
    void aUsageRefusalNowPrecedesAStoreThatCannotBeOpened() throws java.io.IOException {
        Path root = caseDirectory();
        Files.createDirectories(root);
        Path blocker = Files.writeString(root.resolve("blocker"), "not a directory");
        String data = blocker.resolve("data").toString();

        Result valid = run("--data-dir", data, "policy", "pack", "list");
        Result misspelled = run("--data-dir", data, "policy", "pack", "list", "--projet", "x");
        Result misspelledView = run("--data-dir", data, "views", "list", "--projet", "x");

        assertEquals(CliExitCode.STATE_ERROR.code(), valid.exitCode(), valid.err());
        assertEquals(CliExitCode.USAGE.code(), misspelled.exitCode(), misspelled.err());
        assertTrue(misspelled.err().contains("unknown option: --projet"), misspelled.err());
        assertEquals(CliExitCode.USAGE.code(), misspelledView.exitCode(), misspelledView.err());
        assertTrue(misspelledView.err().contains("unknown option: --projet"), misspelledView.err());
    }

    /**
     * An action in a table has a {@code case} in the switch that executes it. A table key without one is accepted, the
     * store is opened, and the {@code default} arm refuses it afterwards: the defect this class guards, reintroduced by
     * a table and a switch that no longer agree. Each key is run on its own, without options, against an empty store.
     * The assertion is positive, so it does not depend on what the {@code default} arm says: every {@code case} of these
     * four switches starts by reading a required option or the scope, or succeeds, so a key that reaches its
     * {@code case} ends with code 0, or with code 2 and {@code --x is required} or the scope refusal. A {@code default}
     * arm answers anything else. {@code portfolio} has the same table-and-switch shape and is held here too.
     *
     * <p>Not covered: a {@code default} arm whose message would read like a required-option refusal; and the other
     * direction, a {@code case} without a table key. That one is dead code: the action is refused as unknown before the
     * store is opened, and nothing is created. {@link #theTablesHoldExactlyTheListedActionsAndOptions} catches a listed
     * action going missing from a table, not a {@code case} added without a key.</p>
     */
    @TestFactory
    Stream<DynamicTest> everyActionOfATableHasACaseThatExecutesIt() {
        List<DynamicTest> tests = new ArrayList<>();
        MorpheusPolicyCli.ACTION_OPTIONS.keySet().stream().sorted().forEach(action -> tests.add(dispatch(
                "policy", action)));
        MorpheusQueryCli.VIEW_ACTION_OPTIONS.keySet().stream().sorted().forEach(action -> tests.add(dispatch(
                "views", action)));
        MorpheusQueryCli.EXPORT_ACTION_OPTIONS.keySet().stream().sorted().forEach(action -> tests.add(dispatch(
                "export", action, "--format", "json")));
        MorpheusPortfolioCli.ACTION_OPTIONS.keySet().stream().sorted().forEach(action -> tests.add(dispatch(
                "portfolio", action)));
        assertTrue(tests.size() > 20, "the tables were not read: " + tests.size());
        return tests.stream();
    }

    private DynamicTest dispatch(String... command) {
        return DynamicTest.dynamicTest(String.join(" ", command), () -> {
            Path root = caseDirectory();
            List<String> args = new ArrayList<>(List.of("--data-dir", root.resolve("data").toString()));
            args.addAll(List.of(command));

            Result result = run(args.toArray(String[]::new));

            assertTrue(result.exitCode() == CliExitCode.SUCCESS.code()
                            || result.exitCode() == CliExitCode.USAGE.code() && REACHED_ITS_CASE.matcher(result.err()).find(),
                    "the action did not reach its case: exit " + result.exitCode() + ", " + result.err());
        });
    }

    private DynamicTest refusal(String name, Action action, String expected, String... options) {
        return DynamicTest.dynamicTest(name, () -> {
            Path root = caseDirectory();
            Result result = run(root, action, options);

            assertEquals(CliExitCode.USAGE.code(), result.exitCode(), result.err());
            assertTrue(result.err().contains(expected), result.err());
            assertFalse(Files.exists(root), "a refused option must create nothing: " + root);
        });
    }

    private static String refusalOf(Action action, String option) {
        if (action.label().equals("export query") && (option.equals("offset") || option.equals("limit"))) {
            return "--" + option + " is not accepted by export";
        }
        return "unknown option: --" + option;
    }

    private static Set<String> vocabulary(String family) {
        Set<String> vocabulary = ACTIONS.stream().filter(action -> action.family().equals(family))
                .flatMap(action -> action.options().stream())
                .collect(Collectors.toCollection(TreeSet::new));
        tables(family).forEach(table -> table.values().forEach(vocabulary::addAll));
        vocabulary.add(MISSPELLED);
        return vocabulary;
    }

    /** Every table of the family, {@code query execute} included as a one-entry table. */
    private static List<Map<String, Set<String>>> tables(String family) {
        return family.equals("policy")
                ? List.of(MorpheusPolicyCli.ACTION_OPTIONS)
                : List.of(Map.of("execute", MorpheusQueryCli.EXECUTE_OPTIONS),
                        MorpheusQueryCli.VIEW_ACTION_OPTIONS, MorpheusQueryCli.EXPORT_ACTION_OPTIONS);
    }

    private static Map<String, Set<String>> expected(String family, String prefix) {
        return ACTIONS.stream().filter(action -> action.family().equals(family) && action.label().startsWith(prefix))
                .collect(Collectors.toMap(action -> action.label().substring(prefix.length()), Action::options));
    }

    private Path caseDirectory() {
        return tempDirectory.resolve("case-" + cases.incrementAndGet());
    }

    private static Path database(Path root) {
        return root.resolve("data").resolve("morpheus.db");
    }

    private Result run(Path root, Action action, String... options) {
        List<String> args = new ArrayList<>(List.of("--data-dir", root.resolve("data").toString()));
        args.addAll(action.command());
        args.addAll(action.base());
        args.addAll(List.of(options));
        return run(args.toArray(String[]::new));
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

    private static Action policy(String label, List<String> tokens, String... options) {
        List<String> command = new ArrayList<>(List.of("policy"));
        command.addAll(tokens);
        return new Action("policy", label, List.copyOf(command), List.of(), Set.of(options));
    }

    private static Action query(String label, List<String> command, List<String> base, String... options) {
        return new Action("query", label, command, base, Set.of(options));
    }

    private record Action(String family, String label, List<String> command, List<String> base, Set<String> options) {
    }

    private record Result(int exitCode, String out, String err) {
    }
}
