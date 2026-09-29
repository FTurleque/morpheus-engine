package com.morpheus.mcp;

import com.morpheus.application.reference.ExternalReferenceResolverRegistry;
import com.morpheus.architecture.m21.PublicSurfaceManifest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every MCP tool the server serves is named by the {@code mcp} column of {@code contracts/public-surfaces.tsv}, and
 * every tool that column names is served.
 *
 * <p>The manifest imposes an explicit sentinel instead of an empty cell, so an absence is declared rather than
 * implied. Its guard checked the rows that exist -- six columns, none blank -- and never the other way round, so
 * nineteen served tools had no row, and the arbitration recorded on {@code provider.plugins.probe} ("executable
 * third-party code is not model-facing") was never applied to the tools that launch a MINOS or NEXUS peer. This
 * asks the served side: it <em>calls</em> {@code MorpheusMcpServer.toolSpecifications}, so a name added to the server
 * is a name this test knows on the day it is added, and no list of tools is written here.</p>
 *
 * <p><b>What "served" means.</b> The tools of the default wiring: the constructors with no argument, no plugin
 * directory, {@code deniedWrites()} for the write capability and NEXUS disabled. {@code discover_provider_plugins}
 * is declared by a class that wiring does not instantiate with a directory, so it is not served and is not judged;
 * its row says {@code EXPLICITLY_NOT_EXPOSED}. A tool that only appears under another wiring is out of scope, and the
 * guard says nothing about it.</p>
 *
 * <p><b>Where it lives and why.</b> {@code toolSpecifications} is package-private, so this class sits in the
 * {@code com.morpheus.mcp} package of this module's tests, next to the manifest guards and reading the manifest
 * through {@link PublicSurfaceManifest}, the one reader of the file. A guard inside {@code morpheus-mcp} would have
 * needed a second manifest reader that nothing could hold equal to the first. The cost is a test-scoped declaration
 * of the SDK in this module's POM, because the guard reads the specifications' names.</p>
 *
 * <p><b>What it does not cover.</b> Only the <em>presence</em> of a row. It does not check that the {@code cli} and
 * {@code http} columns are true, that an intent is right, or that a note says what the code does. It does not tell a
 * stub ({@code check_product_update} always answers an error) from a working tool. It cannot see a sentinel written
 * for a served tool: a sentinel names nothing, so the tool then appears absent and is reported. And a cell must name
 * one tool: no grouping convention exists, and a cell that tries one is refused rather than guessed at.</p>
 *
 * <p>Two more limits. It reads the <em>compiled</em> {@code morpheus-mcp} on this module's classpath: run outside the
 * reactor, a stale jar in {@code ~/.m2} yields a stale set of tools, so build the reactor first. And a sentinel is
 * checked against the vocabulary of {@code .claude/rules/governance.md} ({@link PublicSurfaceManifest#SENTINELS}) in the
 * {@code mcp} column only; the other columns' cells are checked for the vocabulary by a separate test, not for meaning.</p>
 */
class PublicSurfaceManifestCoversEveryServedToolTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void everyServedToolIsInTheManifestAndEveryNamedToolIsServed() throws IOException {
        Set<String> served = served();
        List<String[]> rows = PublicSurfaceManifest.rows(repoRoot());

        assertEquals(List.of(), problems(served, rows),
                "contracts/public-surfaces.tsv must name every tool the server serves in its mcp column, and only those: "
                        + "give a missing tool a row (its cli and http columns read from the code, a sentinel only where "
                        + "the capability is truly absent from that transport), and remove a name the server no longer serves");
    }

    @Test
    void theGuardReadSomethingOnBothSides() throws IOException {
        Set<String> served = served();
        List<String[]> rows = PublicSurfaceManifest.rows(repoRoot());

        assertTrue(served.size() > 10, () -> "the server served " + served.size() + " tools: the wiring changed or the read is empty");
        assertTrue(rows.size() > 10, () -> "the manifest yielded " + rows.size() + " rows: the reader or the file changed");
        assertTrue(named(rows).size() > 10, "the manifest names too few tools: it is all sentinels or the column moved");
    }

    @Test
    void aServedToolWithoutARowIsNamed() {
        List<String> problems = problems(Set.of("kept", "forgotten_one", "forgotten_two"), rows(row("a", "kept")));

        assertEquals(List.of(
                "forgotten_one: served but named by no row of the manifest",
                "forgotten_two: served but named by no row of the manifest"), problems);
    }

    @Test
    void aNameTheServerNoLongerServesIsNamed() {
        List<String> problems = problems(Set.of("kept"), rows(row("a", "kept"), row("b", "ghost_tool")));

        assertEquals(List.of("ghost_tool: named by the manifest (row b) but not served"), problems);
    }

    @Test
    void aSentinelNamesNothingAndSoDoesNotHideAServedTool() {
        List<String> problems = problems(Set.of("kept", "hidden"), rows(row("a", "kept"), row("b", "EXPLICITLY_NOT_EXPOSED")));

        assertEquals(List.of("hidden: served but named by no row of the manifest"), problems);
    }

    @Test
    void anEmptyReadIsAFailureNotAPass() {
        assertEquals(List.of("no tool was served: the guard read nothing"), problems(Set.of(), rows(row("a", "kept"))));
        assertEquals(List.of("the manifest has no row: the guard read nothing"), problems(Set.of("kept"), rows()));
        assertEquals(List.of("the manifest names no tool, only sentinels: the guard read nothing"),
                problems(Set.of("kept"), rows(row("a", "EXPLICITLY_NOT_EXPOSED"))));
    }

    @Test
    void aCellNamesOneToolAndAToolIsNamedOnce() {
        assertEquals(List.of("row a: the mcp cell \"one,two\" must name a single tool"),
                problems(Set.of("one", "two"), rows(row("a", "one,two"))).stream()
                        .filter(problem -> problem.startsWith("row a")).toList());
        assertEquals(List.of("kept: named by more than one row (a, b)"),
                problems(Set.of("kept"), rows(row("a", "kept"), row("b", "kept"))));
        assertEquals(List.of("row c: the mcp cell is empty: write a tool or an EXPLICITLY_* sentinel"),
                problems(Set.of("kept"), rows(row("a", "kept"), row("c", " "))));
    }

    @Test
    void anInventedSentinelIsRefusedAndEveryOneOfTheVocabularyIsAccepted() {
        assertEquals(List.of("row b: the mcp cell \"EXPLICITLY_MAYBE\" is not a sentinel of the manifest vocabulary "
                        + "[EXPLICITLY_LOCAL_ONLY, EXPLICITLY_NOT_EXPOSED, EXPLICITLY_OFFLINE_ONLY, EXPLICITLY_REMOTE_ONLY]"),
                problems(Set.of("kept"), rows(row("a", "kept"), row("b", "EXPLICITLY_MAYBE"))));
        for (String sentinel : PublicSurfaceManifest.SENTINELS) {
            assertEquals(List.of(), problems(Set.of("kept"), rows(row("a", "kept"), row("b", sentinel))), sentinel);
        }
    }

    /** The vocabulary is a copy of the governance table; this holds the copy to it, and the manifest to the copy. */
    @Test
    void theSentinelVocabularyIsTheGovernanceOneAndEveryCellOfTheManifestUsesIt() throws IOException {
        String governance = Files.readString(repoRoot().resolve(".claude/rules/governance.md"));
        for (String sentinel : PublicSurfaceManifest.SENTINELS) {
            assertTrue(governance.contains("`" + sentinel + "`"), sentinel + " is not in the governance table");
        }
        Set<String> invented = new TreeSet<>();
        for (String[] columns : PublicSurfaceManifest.rows(repoRoot())) {
            for (int column = PublicSurfaceManifest.CLI; column <= PublicSurfaceManifest.HTTP; column++) {
                String cell = columns[column].trim();
                if (cell.startsWith(PublicSurfaceManifest.SENTINEL_PREFIX) && !PublicSurfaceManifest.SENTINELS.contains(cell)) {
                    invented.add(columns[PublicSurfaceManifest.CAPABILITY] + ": " + cell);
                }
            }
        }
        assertEquals(Set.of(), invented, "a cell invents a sentinel");
    }

    @Test
    void aWellFormedManifestPasses() {
        assertEquals(List.of(), problems(Set.of("one", "two"),
                rows(row("a", "one"), row("b", "two"), row("c", "EXPLICITLY_LOCAL_ONLY"))));
    }

    // ---------------------------------------------------------------------------------------------

    static List<String> problems(Set<String> served, List<String[]> rows) {
        List<String> problems = new ArrayList<>();
        if (served.isEmpty()) {
            problems.add("no tool was served: the guard read nothing");
            return problems;
        }
        if (rows.isEmpty()) {
            problems.add("the manifest has no row: the guard read nothing");
            return problems;
        }

        TreeMap<String, List<String>> namedBy = new TreeMap<>();
        for (String[] columns : rows) {
            String capability = columns[PublicSurfaceManifest.CAPABILITY];
            String cell = columns.length > PublicSurfaceManifest.MCP ? columns[PublicSurfaceManifest.MCP].trim() : "";
            if (cell.isEmpty()) {
                problems.add("row " + capability + ": the mcp cell is empty: write a tool or an EXPLICITLY_* sentinel");
            } else if (cell.startsWith(PublicSurfaceManifest.SENTINEL_PREFIX)) {
                if (!PublicSurfaceManifest.SENTINELS.contains(cell)) {
                    problems.add("row " + capability + ": the mcp cell \"" + cell + "\" is not a sentinel of the manifest vocabulary "
                            + new TreeSet<>(PublicSurfaceManifest.SENTINELS));
                }
            } else if (!cell.matches("[A-Za-z0-9_]+")) {
                problems.add("row " + capability + ": the mcp cell \"" + cell + "\" must name a single tool");
            } else {
                namedBy.computeIfAbsent(cell, key -> new ArrayList<>()).add(capability);
            }
        }
        if (namedBy.isEmpty()) {
            problems.add("the manifest names no tool, only sentinels: the guard read nothing");
            return problems;
        }

        for (String tool : new TreeSet<>(served)) {
            if (!namedBy.containsKey(tool)) {
                problems.add(tool + ": served but named by no row of the manifest");
            }
        }
        for (var entry : namedBy.entrySet()) {
            if (!served.contains(entry.getKey())) {
                problems.add(entry.getKey() + ": named by the manifest (row " + String.join(", ", entry.getValue()) + ") but not served");
            }
            if (entry.getValue().size() > 1) {
                problems.add(entry.getKey() + ": named by more than one row (" + String.join(", ", entry.getValue()) + ")");
            }
        }
        return problems;
    }

    private static Set<String> named(List<String[]> rows) {
        Set<String> tools = new HashSet<>();
        for (String[] columns : rows) {
            String cell = columns[PublicSurfaceManifest.MCP].trim();
            if (!cell.isEmpty() && !cell.startsWith(PublicSurfaceManifest.SENTINEL_PREFIX)) {
                tools.add(cell);
            }
        }
        return tools;
    }

    /** Not {@code List.of}: an array argument to it is read as the varargs, not as one element. */
    private static List<String[]> rows(String[]... rows) {
        return java.util.Arrays.asList(rows);
    }

    private static String[] row(String capability, String mcp) {
        return new String[] {capability, "READ", "cli", mcp, "http", "notes"};
    }

    private Set<String> served() {
        return MorpheusMcpServer.toolSpecifications(
                        temporaryDirectory.resolve("manifest-guard.db").toAbsolutePath().normalize(),
                        new ExternalReferenceResolverRegistry(List.of()),
                        MorpheusMcpServer.unconfiguredTechnicalContext(),
                        MorpheusMcpServer.deniedWriteCapability())
                .stream()
                .map(specification -> specification.tool().name())
                .collect(Collectors.toUnmodifiableSet());
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
