package com.morpheus.cli;

import com.morpheus.application.operability.OperationalLogMode;
import com.morpheus.application.operability.OperationalRuntime;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * What every option parser of the CLI answers to the same malformed input, run end to end through
 * {@link MorpheusMain#run}.
 *
 * <p>One row per sub-command, reached by the words of a real command; one column per kind of malformed input: an
 * option the sub-command does not declare, a value option given twice, a value option with nothing after it, a value
 * option followed by another option, a blank value, a token that is not an option, and a flag given twice where the
 * sub-command has one. Each cell is the exit code and the whole error line. A refused call writes nothing under its
 * data directory.</p>
 *
 * <p>The table is the contract the parsers keep: a cell that changes is a change of what a script sees on a malformed
 * call, and it is written in the release notes.</p>
 */
class CliOptionRefusalTableTest {
    private static final String PROJECT = "01900000-0000-7000-8000-000000000001";

    /** {@code sub-command | kind | exit code | error line}. */
    private static final String TABLE = """
            acceptance-criteria list | undeclared | 2 | MORPHEUS error [2]: unknown option: --bogus
            acceptance-criteria list | duplicate | 2 | MORPHEUS error [2]: duplicate option: --project
            acceptance-criteria list | missing-value | 2 | MORPHEUS error [2]: --project requires a value
            acceptance-criteria list | dash-value | 2 | MORPHEUS error [2]: --project requires a value
            acceptance-criteria list | blank-value | 2 | MORPHEUS error [2]: --project requires a non-blank value; omit the option to leave it unset
            acceptance-criteria list | positional | 2 | MORPHEUS error [2]: unexpected positional argument: stray
            constraints evaluate | undeclared | 2 | MORPHEUS error [2]: unknown option: --bogus
            constraints evaluate | duplicate | 2 | MORPHEUS error [2]: duplicate option: --project
            constraints evaluate | missing-value | 2 | MORPHEUS error [2]: --project requires a value
            constraints evaluate | dash-value | 2 | MORPHEUS error [2]: --project requires a value
            constraints evaluate | blank-value | 2 | MORPHEUS error [2]: --project requires a non-blank value; omit the option to leave it unset
            constraints evaluate | positional | 2 | MORPHEUS error [2]: unexpected positional argument: stray
            composition status | undeclared | 2 | MORPHEUS error [2]: unknown option: --bogus
            composition status | duplicate | 2 | MORPHEUS error [2]: duplicate option: --project
            composition status | missing-value | 2 | MORPHEUS error [2]: --project requires a value
            composition status | dash-value | 2 | MORPHEUS error [2]: --project requires a value
            composition status | blank-value | 2 | MORPHEUS error [2]: --project requires a non-blank value; omit the option to leave it unset
            composition status | positional | 2 | MORPHEUS error [2]: unexpected positional argument: stray
            lifecycle apply | undeclared | 2 | MORPHEUS error [2]: unknown option: --bogus
            lifecycle apply | duplicate | 2 | MORPHEUS error [2]: duplicate option: --project
            lifecycle apply | missing-value | 2 | MORPHEUS error [2]: --project requires a value
            lifecycle apply | dash-value | 2 | MORPHEUS error [2]: --project requires a value
            lifecycle apply | blank-value | 2 | MORPHEUS error [2]: --project requires a non-blank value; omit the option to leave it unset
            lifecycle apply | positional | 2 | MORPHEUS error [2]: unexpected positional argument: stray
            lifecycle apply | repeated-flag | 2 | MORPHEUS error [2]: duplicate option: --confirm
            change-orchestration state | undeclared | 2 | MORPHEUS error [2]: unknown option: --bogus
            change-orchestration state | duplicate | 2 | MORPHEUS error [2]: duplicate option: --project
            change-orchestration state | missing-value | 2 | MORPHEUS error [2]: --project requires a value
            change-orchestration state | dash-value | 2 | MORPHEUS error [2]: --project requires a value
            change-orchestration state | blank-value | 2 | MORPHEUS error [2]: --project requires a non-blank value; omit the option to leave it unset
            change-orchestration state | positional | 2 | MORPHEUS error [2]: unexpected positional argument: stray
            change-orchestration state | repeated-flag | 2 | MORPHEUS error [2]: duplicate option: --allow-backward
            augmented-context requirement | undeclared | 2 | MORPHEUS error [2]: unknown option: --bogus
            augmented-context requirement | duplicate | 2 | MORPHEUS error [2]: duplicate option: --project
            augmented-context requirement | missing-value | 2 | MORPHEUS error [2]: --project requires a value
            augmented-context requirement | dash-value | 2 | MORPHEUS error [2]: --project requires a value
            augmented-context requirement | blank-value | 2 | MORPHEUS error [2]: --project requires a non-blank value; omit the option to leave it unset
            augmented-context requirement | positional | 2 | MORPHEUS error [2]: unexpected positional argument: stray
            augmented-context requirement | repeated-flag | 2 | MORPHEUS error [2]: duplicate option: --explain
            external-references list | undeclared | 2 | MORPHEUS error [2]: unknown options: [bogus]
            external-references list | duplicate | 2 | MORPHEUS error [2]: duplicate option: --project
            external-references list | missing-value | 2 | MORPHEUS error [2]: --project requires a value
            external-references list | dash-value | 2 | MORPHEUS error [2]: --project requires a value
            external-references list | blank-value | 2 | MORPHEUS error [2]: --project requires a non-blank value; omit the option to leave it unset
            external-references list | positional | 2 | MORPHEUS error [2]: unexpected positional argument: stray
            policy pack get | undeclared | 2 | MORPHEUS error [2]: unknown option: --bogus
            policy pack get | duplicate | 2 | MORPHEUS error [2]: duplicate option: --id
            policy pack get | missing-value | 2 | MORPHEUS error [2]: --id requires a value
            policy pack get | dash-value | 2 | MORPHEUS error [2]: --id requires a value
            policy pack get | blank-value | 2 | MORPHEUS error [2]: --id requires a non-blank value; omit the option to leave it unset
            policy pack get | positional | 2 | MORPHEUS error [2]: unexpected positional argument: stray
            portfolio overview | undeclared | 2 | MORPHEUS error [2]: unknown option: --bogus
            portfolio overview | duplicate | 2 | MORPHEUS error [2]: duplicate option: --portfolio
            portfolio overview | missing-value | 2 | MORPHEUS error [2]: --portfolio requires a value
            portfolio overview | dash-value | 2 | MORPHEUS error [2]: --portfolio requires a value
            portfolio overview | blank-value | 2 | MORPHEUS error [2]: --portfolio requires a non-blank value; omit the option to leave it unset
            portfolio overview | positional | 2 | MORPHEUS error [2]: unexpected positional argument: stray
            views get | undeclared | 2 | MORPHEUS error [2]: unknown option: --bogus
            views get | duplicate | 2 | MORPHEUS error [2]: duplicate option: --id
            views get | missing-value | 2 | MORPHEUS error [2]: --id requires a value
            views get | dash-value | 2 | MORPHEUS error [2]: --id requires a value
            views get | blank-value | 2 | MORPHEUS error [2]: --id requires a non-blank value; omit the option to leave it unset
            views get | positional | 2 | MORPHEUS error [2]: unexpected positional argument: stray
            changes list | undeclared | 2 | MORPHEUS error [2]: unknown option: --bogus
            changes list | duplicate | 2 | MORPHEUS error [2]: duplicate option: --project
            changes list | missing-value | 2 | MORPHEUS error [2]: --project requires a value
            changes list | dash-value | 2 | MORPHEUS error [2]: --project requires a value
            changes list | blank-value | 2 | MORPHEUS error [2]: --project requires a non-blank value; omit the option to leave it unset
            changes list | positional | 2 | MORPHEUS error [2]: unexpected positional argument: stray
            sync | undeclared | 2 | MORPHEUS error [2]: unknown option: --bogus
            sync | duplicate | 2 | MORPHEUS error [2]: duplicate option: --project
            sync | missing-value | 2 | MORPHEUS error [2]: --project requires a value
            sync | dash-value | 2 | MORPHEUS error [2]: --project requires a value
            sync | blank-value | 2 | MORPHEUS error [2]: --project requires a non-blank value; omit the option to leave it unset
            sync | positional | 2 | MORPHEUS error [2]: unexpected positional argument: stray
            sync | repeated-flag | 2 | MORPHEUS error [2]: duplicate option: --force
            paths | undeclared | 2 | MORPHEUS error [2]: unknown option: --bogus
            paths | positional | 2 | MORPHEUS error [2]: unexpected positional argument: stray
            update-check | undeclared | 2 | MORPHEUS usage error: unknown option: --bogus
            update-check | duplicate | 2 | MORPHEUS usage error: duplicate option: --manifest
            update-check | missing-value | 2 | MORPHEUS usage error: --manifest requires a value
            update-check | dash-value | 2 | MORPHEUS usage error: --manifest requires a value
            update-check | blank-value | 2 | MORPHEUS usage error: --manifest requires a non-blank value; omit the option to leave it unset
            update-check | positional | 2 | MORPHEUS usage error: unexpected positional argument: stray
            provider-plugins discover | undeclared | 2 | MORPHEUS provider-plugin usage error: unknown option: --bogus
            provider-plugins discover | duplicate | 2 | MORPHEUS provider-plugin usage error: duplicate option: --directory
            provider-plugins discover | missing-value | 2 | MORPHEUS provider-plugin usage error: --directory requires a value
            provider-plugins discover | dash-value | 2 | MORPHEUS provider-plugin usage error: --directory requires a value
            provider-plugins discover | blank-value | 2 | MORPHEUS provider-plugin usage error: --directory requires a non-blank value; omit the option to leave it unset
            provider-plugins discover | positional | 2 | MORPHEUS provider-plugin usage error: unexpected positional argument: stray
            reason analyze | undeclared | 2 | MORPHEUS usage error: unknown option: --bogus
            reason analyze | duplicate | 2 | MORPHEUS usage error: duplicate option: --question
            reason analyze | missing-value | 2 | MORPHEUS usage error: --question requires a value
            reason analyze | dash-value | 2 | MORPHEUS usage error: --question requires a value
            reason analyze | blank-value | 2 | MORPHEUS usage error: --question requires a non-blank value; omit the option to leave it unset
            reason analyze | positional | 2 | MORPHEUS usage error: unexpected positional argument: stray
            server identity list | undeclared | 2 | MORPHEUS server usage error: unknown option: --bogus
            server identity list | duplicate | 2 | MORPHEUS server usage error: duplicate option: --auth-file
            server identity list | missing-value | 2 | MORPHEUS server usage error: --auth-file requires a value
            server identity list | dash-value | 2 | MORPHEUS server usage error: --auth-file requires a value
            server identity list | blank-value | 2 | MORPHEUS server usage error: --auth-file requires a non-blank value; omit the option to leave it unset
            server identity list | positional | 2 | MORPHEUS server usage error: unexpected positional argument: stray
            server restore | undeclared | 2 | MORPHEUS server usage error: unknown option: --bogus
            server restore | duplicate | 2 | MORPHEUS server usage error: duplicate option: --file
            server restore | missing-value | 2 | MORPHEUS server usage error: --file requires a value
            server restore | dash-value | 2 | MORPHEUS server usage error: --file requires a value
            server restore | blank-value | 2 | MORPHEUS server usage error: --file requires a non-blank value; omit the option to leave it unset
            server restore | positional | 2 | MORPHEUS server usage error: unexpected positional argument: stray
            server restore | repeated-flag | 2 | MORPHEUS server usage error: duplicate option: --confirm
            """;

    @TempDir
    Path temp;

    private final AtomicInteger cases = new AtomicInteger();

    private record Row(List<String> words, String option, String value, String flag) {
    }

    private static Map<String, Row> rows() {
        Map<String, Row> rows = new LinkedHashMap<>();
        rows.put("acceptance-criteria list", new Row(List.of("acceptance-criteria", "list"), "--project", PROJECT, null));
        rows.put("constraints evaluate", new Row(List.of("constraints", "evaluate"), "--project", PROJECT, null));
        rows.put("composition status", new Row(List.of("composition", "status"), "--project", PROJECT, null));
        rows.put("lifecycle apply", new Row(List.of("lifecycle", "apply"), "--project", PROJECT, "--confirm"));
        rows.put("change-orchestration state",
                new Row(List.of("change-orchestration", "state"), "--project", PROJECT, "--allow-backward"));
        rows.put("augmented-context requirement",
                new Row(List.of("augmented-context", "requirement"), "--project", PROJECT, "--explain"));
        rows.put("external-references list", new Row(List.of("external-references", "list"), "--project", PROJECT, null));
        rows.put("policy pack get", new Row(List.of("policy", "pack", "get"), "--id", "x", null));
        rows.put("portfolio overview", new Row(List.of("portfolio", "overview"), "--portfolio", PROJECT, null));
        rows.put("views get", new Row(List.of("views", "get"), "--id", "x", null));
        rows.put("changes list", new Row(List.of("changes", "list"), "--project", PROJECT, null));
        rows.put("sync", new Row(List.of("sync"), "--project", PROJECT, "--force"));
        rows.put("paths", new Row(List.of("paths"), null, null, null));
        rows.put("update-check", new Row(List.of("update-check"), "--manifest", "m.json", null));
        rows.put("provider-plugins discover", new Row(List.of("provider-plugins", "discover"), "--directory", "d", null));
        rows.put("reason analyze", new Row(List.of("reason", "analyze"), "--question", "q", null));
        rows.put("server identity list", new Row(List.of("server", "identity", "list"), "--auth-file", "a.txt", null));
        rows.put("server restore", new Row(List.of("server", "restore"), "--file", "b.db", "--confirm"));
        return rows;
    }

    private static Map<String, List<String>> kinds(Row row) {
        Map<String, List<String>> kinds = new LinkedHashMap<>();
        kinds.put("undeclared", words(row, "--bogus", "x"));
        if (row.option() != null) {
            kinds.put("duplicate", words(row, row.option(), row.value(), row.option(), row.value()));
            kinds.put("missing-value", words(row, row.option()));
            kinds.put("dash-value", words(row, row.option(), "--other"));
            kinds.put("blank-value", words(row, row.option(), " "));
        }
        kinds.put("positional", words(row, "stray"));
        if (row.flag() != null) {
            kinds.put("repeated-flag", words(row, row.flag(), row.flag()));
        }
        return kinds;
    }

    @TestFactory
    Stream<DynamicTest> everyCellOfTheTableIsWhatTheSubCommandAnswers() {
        Map<String, String> expected = expected();
        List<DynamicTest> tests = new ArrayList<>();
        rows().forEach((name, row) -> kinds(row).forEach((kind, words) -> tests.add(DynamicTest.dynamicTest(
                name + " / " + kind, () -> {
                    Path data = temp.resolve("case-" + cases.incrementAndGet());
                    List<String> args = new ArrayList<>(List.of("--data-dir", data.toString()));
                    args.addAll(words);

                    Result result = run(args.toArray(String[]::new));

                    assertEquals(expected.get(name + " | " + kind), result.exitCode() + " | " + result.err(),
                            String.join(" ", words));
                    assertFalse(Files.exists(data), "a refused call wrote under its data directory: " + data);
                }))));
        return GeneratedCases.nonEmpty("everyCellOfTheTableIsWhatTheSubCommandAnswers", tests.stream());
    }

    @Test
    void theTableHasOneLinePerCellAndNoOther() {
        List<String> cells = new ArrayList<>();
        rows().forEach((name, row) -> kinds(row).keySet().forEach(kind -> cells.add(name + " | " + kind)));

        assertEquals(cells, List.copyOf(expected().keySet()));
    }

    private static Map<String, String> expected() {
        Map<String, String> expected = new LinkedHashMap<>();
        for (String line : TABLE.strip().split("\n")) {
            String[] fields = line.strip().split(" \\| ", 4);
            expected.put(fields[0] + " | " + fields[1], fields[2] + " | " + (fields.length > 3 ? fields[3] : ""));
        }
        return expected;
    }

    private static List<String> words(Row row, String... more) {
        List<String> all = new ArrayList<>(row.words());
        all.addAll(List.of(more));
        return all;
    }

    private Result run(String... args) {
        ByteArrayOutputStream outBytes = new ByteArrayOutputStream();
        ByteArrayOutputStream errBytes = new ByteArrayOutputStream();
        try (PrintStream out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
             PrintStream err = new PrintStream(errBytes, true, StandardCharsets.UTF_8)) {
            Properties properties = new Properties();
            properties.setProperty("os.name", System.getProperty("os.name", "Windows"));
            properties.setProperty("user.home", temp.resolve("home").toString());
            int exitCode = MorpheusMain.run(OperationalRuntime.of(OperationalLogMode.OFF), args, out, err, Map.of(),
                    properties);
            return new Result(exitCode, errBytes.toString(StandardCharsets.UTF_8).strip());
        }
    }

    private record Result(int exitCode, String err) {
    }
}
