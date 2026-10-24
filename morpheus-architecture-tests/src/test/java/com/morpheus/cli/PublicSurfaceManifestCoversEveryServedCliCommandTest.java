package com.morpheus.cli;

import com.morpheus.architecture.m21.PublicSurfaceManifest;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The commands the {@code cli} column of {@code contracts/public-surfaces.tsv} names and the commands the CLI serves
 * are the same set.
 *
 * <p>The {@code http} column is compared with the route table and the {@code mcp} column with the served tools; the
 * {@code cli} column was only read for being non-blank, so three sentinels said "not exposed" about commands the CLI
 * serves and several served commands had no row. The CLI declares its command paths once
 * ({@link CliCommandPaths#paths()}), refuses a path it does not declare, and
 * {@code CliCommandPathsAreRoutedTest} in {@code morpheus-cli} runs every declared path to prove the declaration is what
 * the CLI routes. This test compares that declaration with the column, in both directions.</p>
 *
 * <p><b>Two holds, kept together</b> ({@code .claude/rules/architecture.md}). The manifest side is textual: a
 * {@code .tsv} is only readable as text,
 * and a cell names a command by the words before its first option. The CLI side is an execution, in the other test,
 * because "this path is served" is a behaviour and not a mention.</p>
 *
 * <p><b>Exclusions.</b> A declared path that no row names is either a missing row or a reasoned exclusion: the manifest
 * lists capabilities, and an entry point of the process (the help, the launchers) or a form whose capability another row
 * carries is not one. {@link #EXCLUSIONS} states the reason of each; an exclusion for a path the CLI does not declare, or
 * for a path a row names, is refused so the list cannot outlive its cause.</p>
 *
 * <p><b>What it does not cover.</b> It compares command paths. An option renamed in a parser, a cell whose intent or
 * notes are wrong, or a command that fails for a reason other than "unknown" are outside it; the options are held by the
 * embedded help and its parse test. A cell that carries an option ({@code server restore --confirm}) is compared on its
 * path only. And it reads the <em>compiled</em> {@code morpheus-cli} of this module's classpath: build the reactor first.</p>
 */
class PublicSurfaceManifestCoversEveryServedCliCommandTest {
    /** The declared paths that no row names, each with the reason it is not a capability of the manifest. */
    static final Map<String, String> EXCLUSIONS = new TreeMap<>(Map.of(
            "help", "the form every command's usage is reached by; it exposes no product knowledge",
            "paths", "prints where this process keeps its data, configuration, logs and database; it describes the "
                    + "local installation and reads no product knowledge",
            "api", "launches the HTTP server, whose routes are the capabilities of the http column; starting the "
                    + "process is an entry point, as the health and readiness probes are for HTTP",
            "mcp", "launches the MCP server, whose tools are the capabilities of the mcp column; starting the "
                    + "process is an entry point",
            "reason help", "the form the reasoning commands' usage is reached by, as help is for the others",
            "product-info", "the CLI form of the product description that get_product_info answers over MCP; that tool "
                    + "is named by row product.version, whose line is pinned, and a tool is named by one row only"));

    @Test
    void everyServedCommandIsInTheManifestAndEveryNamedCommandIsServed() throws IOException {
        List<String> problems = problems(CliCommandPaths.paths(), manifestLines(), EXCLUSIONS);

        assertEquals(List.of(), problems,
                "contracts/public-surfaces.tsv must name every command path the CLI declares in its cli column, and only "
                        + "those: give a served command a row (its mcp and http columns read from the server, a sentinel "
                        + "only where the capability is truly absent from that transport) or a reasoned exclusion, and "
                        + "correct a cell that names a command the CLI does not serve");
    }

    @Test
    void theGuardReadSomethingOnBothSides() throws IOException {
        assertTrue(CliCommandPaths.paths().size() > 50,
                () -> "the CLI declared " + CliCommandPaths.paths().size() + " paths: the declaration is empty or moved");
        assertTrue(manifestLines().size() > 50, "the manifest yielded too few lines: the reader or the file changed");
        assertTrue(named(manifestLines()).size() > 50, "the cli column names too few commands: it is all sentinels");
    }

    @Test
    void aServedCommandWithoutARowIsNamed() {
        List<String> problems = problems(List.of("kept", "forgotten one", "forgotten two"), lines(line("a", "kept")), Map.of());

        assertEquals(List.of(
                "forgotten one: declared but named by no row and no exclusion",
                "forgotten two: declared but named by no row and no exclusion"), problems);
    }

    @Test
    void aCellThatNamesNoServedCommandIsNamedWithItsLine() {
        List<String> problems = problems(List.of("kept"), lines(line("a", "kept"), line("b", "ghost command")), Map.of());

        assertEquals(List.of("line 2 (b): the cli cell \"ghost command\" names the command \"ghost command\", "
                + "which the CLI does not declare"), problems);
    }

    @Test
    void aSentinelNamesNothingAndSoDoesNotHideAServedCommand() {
        List<String> problems = problems(List.of("kept", "hidden"),
                lines(line("a", "kept"), line("b", "EXPLICITLY_NOT_EXPOSED")), Map.of());

        assertEquals(List.of("hidden: declared but named by no row and no exclusion"), problems);
    }

    @Test
    void aCommandNamedTwiceIsRefusedWithBothLines() {
        List<String> problems = problems(List.of("kept"), lines(line("a", "kept"), line("b", "kept --option")), Map.of());

        assertEquals(List.of("kept: named by more than one row (line 1 (a), line 2 (b))"), problems);
    }

    @Test
    void aCellThatCarriesAnOptionIsComparedOnItsPath() {
        assertEquals(List.of(), problems(List.of("server restore"), lines(line("a", "server restore --confirm")), Map.of()));
        assertEquals("update-check", pathOfCell("update-check --manifest").orElseThrow());
    }

    @Test
    void anExclusionThatExcusesNothingIsRefused() {
        assertEquals(List.of("exclusion \"gone\": the CLI declares no such path"),
                problems(List.of("kept"), lines(line("a", "kept")), Map.of("gone", "a reason")));
        assertEquals(List.of("exclusion \"kept\": the path is named by a row (line 1 (a))"),
                problems(List.of("kept"), lines(line("a", "kept")), Map.of("kept", "a reason")));
        assertEquals(List.of("exclusion \"other\": no reason is written"),
                problems(List.of("kept", "other"), lines(line("a", "kept")), Map.of("other", " ")));
    }

    @Test
    void anEmptyReadIsAFailureNotAPass() {
        assertEquals(List.of("the CLI declared no command path: the guard read nothing"),
                problems(List.of(), lines(line("a", "kept")), Map.of()));
        assertEquals(List.of("the manifest has no row: the guard read nothing"),
                problems(List.of("kept"), lines(), Map.of()));
        assertEquals(List.of("the manifest names no command, only sentinels: the guard read nothing"),
                problems(List.of("kept"), lines(line("a", "EXPLICITLY_NOT_EXPOSED")), Map.of("kept", "a reason")));
    }

    @Test
    void anEmptyCellOrAnInventedSentinelIsRefused() {
        assertEquals(List.of("line 2 (b): the cli cell is empty: write a command or an EXPLICITLY_* sentinel"),
                problems(List.of("kept"), lines(line("a", "kept"), line("b", " ")), Map.of()));
        assertEquals(List.of("line 2 (b): the cli cell \"EXPLICITLY_MAYBE\" is not a sentinel of the manifest vocabulary "
                        + "[EXPLICITLY_LOCAL_ONLY, EXPLICITLY_NOT_EXPOSED, EXPLICITLY_OFFLINE_ONLY, EXPLICITLY_REMOTE_ONLY]"),
                problems(List.of("kept"), lines(line("a", "kept"), line("b", "EXPLICITLY_MAYBE")), Map.of()));
    }

    @Test
    void aWellFormedManifestPasses() {
        assertEquals(List.of(), problems(List.of("one", "two three", "four"),
                lines(line("a", "one"), line("b", "two three --x"), line("c", "EXPLICITLY_REMOTE_ONLY")),
                Map.of("four", "an entry point")));
    }

    // ---------------------------------------------------------------------------------------------

    static List<String> problems(List<String> declared, List<ManifestLine> manifest, Map<String, String> exclusions) {
        List<String> problems = new ArrayList<>();
        if (declared.isEmpty()) {
            problems.add("the CLI declared no command path: the guard read nothing");
            return problems;
        }
        if (manifest.isEmpty()) {
            problems.add("the manifest has no row: the guard read nothing");
            return problems;
        }

        TreeMap<String, List<ManifestLine>> namedBy = new TreeMap<>();
        for (ManifestLine line : manifest) {
            String cell = line.cli().trim();
            if (cell.isEmpty()) {
                problems.add(line.label() + ": the cli cell is empty: write a command or an EXPLICITLY_* sentinel");
            } else if (cell.startsWith(PublicSurfaceManifest.SENTINEL_PREFIX)) {
                if (!PublicSurfaceManifest.SENTINELS.contains(cell)) {
                    problems.add(line.label() + ": the cli cell \"" + cell + "\" is not a sentinel of the manifest vocabulary "
                            + new TreeSet<>(PublicSurfaceManifest.SENTINELS));
                }
            } else {
                String path = pathOfCell(cell).orElse(cell);
                if (!declared.contains(path)) {
                    problems.add(line.label() + ": the cli cell \"" + cell + "\" names the command \"" + path
                            + "\", which the CLI does not declare");
                } else {
                    namedBy.computeIfAbsent(path, key -> new ArrayList<>()).add(line);
                }
            }
        }
        if (namedBy.isEmpty() && problems.isEmpty()) {
            problems.add("the manifest names no command, only sentinels: the guard read nothing");
            return problems;
        }

        for (String path : new TreeSet<>(declared)) {
            if (!namedBy.containsKey(path) && !exclusions.containsKey(path)) {
                problems.add(path + ": declared but named by no row and no exclusion");
            }
        }
        for (var entry : namedBy.entrySet()) {
            if (entry.getValue().size() > 1) {
                problems.add(entry.getKey() + ": named by more than one row ("
                        + String.join(", ", entry.getValue().stream().map(ManifestLine::label).toList()) + ")");
            }
        }
        for (var exclusion : new TreeMap<>(exclusions).entrySet()) {
            if (!declared.contains(exclusion.getKey())) {
                problems.add("exclusion \"" + exclusion.getKey() + "\": the CLI declares no such path");
            } else if (namedBy.containsKey(exclusion.getKey())) {
                problems.add("exclusion \"" + exclusion.getKey() + "\": the path is named by a row ("
                        + String.join(", ", namedBy.get(exclusion.getKey()).stream().map(ManifestLine::label).toList()) + ")");
            } else if (exclusion.getValue().isBlank()) {
                problems.add("exclusion \"" + exclusion.getKey() + "\": no reason is written");
            }
        }
        if (namedBy.isEmpty()) {
            problems.add("the manifest names no command, only sentinels: the guard read nothing");
        }
        return problems;
    }

    private static List<String> named(List<ManifestLine> manifest) {
        List<String> paths = new ArrayList<>();
        for (ManifestLine line : manifest) {
            String cell = line.cli().trim();
            if (!cell.isEmpty() && !cell.startsWith(PublicSurfaceManifest.SENTINEL_PREFIX)) {
                paths.add(pathOfCell(cell).orElse(cell));
            }
        }
        return paths;
    }

    /** A capability row with its 1-based position among the rows, which is what a failure names. */
    record ManifestLine(int number, String capability, String cli) {
        String label() {
            return "line " + number + " (" + capability + ")";
        }
    }

    private static List<ManifestLine> manifestLines() throws IOException {
        List<ManifestLine> lines = new ArrayList<>();
        int number = 0;
        for (String text : Files.readAllLines(repoRoot().resolve(PublicSurfaceManifest.MANIFEST))) {
            number++;
            for (String[] columns : PublicSurfaceManifest.rows(List.of(text))) {
                lines.add(new ManifestLine(number, columns[PublicSurfaceManifest.CAPABILITY], columns[PublicSurfaceManifest.CLI]));
            }
        }
        return List.copyOf(lines);
    }

    /** The rows in the order they are written, numbered from one as the lines of a file are. */
    private static List<ManifestLine> lines(ManifestLine... lines) {
        List<ManifestLine> numbered = new ArrayList<>();
        for (ManifestLine line : lines) {
            numbered.add(new ManifestLine(numbered.size() + 1, line.capability(), line.cli()));
        }
        return numbered;
    }

    private static ManifestLine line(String capability, String cli) {
        return new ManifestLine(0, capability, cli);
    }

    /** The words of a cell before its first option: the command path the cell names. */
    static java.util.Optional<String> pathOfCell(String cell) {
        List<String> words = new ArrayList<>();
        for (String word : cell.trim().split("\\s+")) {
            if (word.startsWith("-")) {
                break;
            }
            words.add(word);
        }
        return words.isEmpty() ? java.util.Optional.empty() : java.util.Optional.of(String.join(" ", words));
    }

    private static Path repoRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve(PublicSurfaceManifest.MANIFEST))) {
                return current;
            }
            current = current.getParent();
        }
        throw new AssertionError("repository root with " + PublicSurfaceManifest.MANIFEST + " not found");
    }
}
