package com.morpheus.architecture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the current developer and architecture pages may say about the modules of the Maven reactor.
 *
 * <p>The root POM is the only statement of which modules exist. A page that enumerates the reactor must enumerate
 * all of it, and no current page states how many modules, projects or POMs the reactor has: a count is the copy that
 * the next module makes false, even when it is right on the day it is written. A section (cut at its level 1 and 2
 * headings) that carries a date or a commit reports a measurement and is read by neither rule. Both rules are
 * textual because the
 * targets are Markdown files and a POM (ADR-0103); the proofs below run them on fabricated corpora so that a rule
 * which reads nothing fails instead of passing.</p>
 */
class ReactorModuleDocumentationTest {
    private static final Pattern MODULE_BLOCK = Pattern.compile("<modules>(.*?)</modules>", Pattern.DOTALL);
    private static final Pattern MODULE = Pattern.compile("<module>([^<]+)</module>");

    /** Pages outside the two scanned trees that a developer opens first. */
    private static final List<String> ENTRY_PAGES = List.of("README.md", "CONTRIBUTING.md", "docs/README.md");
    /** Current documentation. Decisions, steering, records and the user guide are not scanned. */
    private static final List<String> SCANNED_TREES = List.of("docs/developer", "docs/architecture");
    private static final String DECISION_RECORDS = "docs/architecture/adr";

    /** A module written alone on its line, in a fenced list or a bullet list. */
    private static final Pattern MODULE_ON_ITS_OWN_LINE =
            Pattern.compile("^\\s*(?:[-*]\\s+)?`?(morpheus(?:-[a-z]+)+)`?\\s*$");
    /** A table cell made only of backticked module names; the names after the first may drop the prefix. */
    private static final Pattern MODULE_CELL =
            Pattern.compile("^`morpheus(?:-[a-z]+)+`(?:\\s*,\\s*`(?:morpheus)?(?:-[a-z]+)+`)*$");
    private static final Pattern MODULE_CELL_TOKEN = Pattern.compile("`(morpheus)?((?:-[a-z]+)+)`");

    /**
     * A count of the reactor: "17 modules", "18 projets Maven", "18 POMs", "14/14 modules".
     * A sentence that gives the total in words is not recognised; none exists on the scanned pages.
     */
    private static final Pattern REACTOR_TOTAL = Pattern.compile(
            "(?i)\\b\\d{1,3}(?:\\s*/\\s*\\d{1,3})?\\s+(?:sous-)?(?:modules?|projets?|projects?|POMs?)\\b");
    /** A date or a commit: what makes a measurement a record rather than a statement of the present. */
    private static final Pattern DATE_OR_COMMIT = Pattern.compile(
            "\\b\\d{2}/\\d{2}/\\d{4}\\b|\\b\\d{4}-\\d{2}-\\d{2}\\b"
                    + "|\\b(?=[0-9a-f]*\\d)(?=[0-9a-f]*[a-f])[0-9a-f]{7,40}\\b");
    private static final Pattern HEADING = Pattern.compile("^#{1,2}\\s");

    @Test
    void everyCurrentPageThatListsTheReactorListsEveryModuleTheRootPomDeclares() throws Exception {
        List<String> violations = incompleteReactorLists(repositoryRoot());
        assertTrue(violations.isEmpty(), () -> violations.size() + " module list(s) disagree with the root POM:"
                + System.lineSeparator() + String.join(System.lineSeparator(), violations));
    }

    @Test
    void noCurrentPageStatesHowManyModulesOrProjectsTheReactorHas() throws Exception {
        List<String> violations = statedReactorTotals(repositoryRoot());
        assertTrue(violations.isEmpty(), () -> violations.size() + " stated reactor total(s) on current pages;"
                + " count the <module> entries of pom.xml instead of writing the number:"
                + System.lineSeparator() + String.join(System.lineSeparator(), violations));
    }

    @Test
    void aModuleAddedToThePomAndNotToAListIsNamedWithItsPage(@TempDir Path corpus) throws Exception {
        writePom(corpus, "morpheus-a", "morpheus-b", "morpheus-c");
        writePage(corpus, "docs/developer/complete.md", fence("morpheus-a", "morpheus-b", "morpheus-c"));
        writePage(corpus, "docs/developer/stale.md", fence("morpheus-a", "morpheus-b"));
        assertEquals(List.of(
                        "docs/developer/stale.md:2 omits morpheus-c, which the root POM declares"),
                incompleteReactorLists(corpus));

        writePom(corpus, "morpheus-a", "morpheus-b", "morpheus-c", "morpheus-d");
        writePage(corpus, "docs/developer/stale.md", fence("morpheus-a", "morpheus-b", "morpheus-c"));
        assertEquals(List.of(
                        "docs/developer/complete.md:2 omits morpheus-d, which the root POM declares",
                        "docs/developer/stale.md:2 omits morpheus-d, which the root POM declares"),
                incompleteReactorLists(corpus));
    }

    @Test
    void aModuleRemovedFromThePomAndLeftInAListIsNamedWithItsPage(@TempDir Path corpus) throws Exception {
        writePom(corpus, "morpheus-a", "morpheus-b");
        writePage(corpus, "docs/architecture/removed.md", fence("morpheus-a", "morpheus-b", "morpheus-gone"));
        assertEquals(List.of(
                        "docs/architecture/removed.md:2 names morpheus-gone, which the root POM does not declare"),
                incompleteReactorLists(corpus));
    }

    @Test
    void aPageThatNamesFewModulesIsNotRequiredToNameTheOthers(@TempDir Path corpus) throws Exception {
        writePom(corpus, "morpheus-a", "morpheus-b", "morpheus-c", "morpheus-d");
        writePage(corpus, "docs/developer/complete.md", fence("morpheus-a", "morpheus-b", "morpheus-c", "morpheus-d"));
        writePage(corpus, "docs/developer/passing.md", fence("morpheus-a", "morpheus-b"));
        writePage(corpus, "docs/developer/prose.md", "Le module `morpheus-a` et `morpheus-b`, `morpheus-c`.\n");
        assertEquals(List.of(), incompleteReactorLists(corpus));
    }

    @Test
    void aListInASectionThatCarriesItsDateIsARecordOfThatRunAndIsLeftAlone(@TempDir Path corpus) throws Exception {
        writePom(corpus, "morpheus-a", "morpheus-b", "morpheus-c");
        writePage(corpus, "docs/developer/complete.md", fence("morpheus-a", "morpheus-b", "morpheus-c"));
        writePage(corpus, "docs/developer/run.md", "## Relevé du 09/10/2026\n\n" + fence("morpheus-a", "morpheus-b"));
        assertEquals(List.of(), incompleteReactorLists(corpus));

        writePage(corpus, "docs/developer/run.md", "## Relevé\n\n" + fence("morpheus-a", "morpheus-b"));
        assertEquals(List.of("docs/developer/run.md:4 omits morpheus-c, which the root POM declares"),
                incompleteReactorLists(corpus));
    }

    @Test
    void aTableOfFamiliesAndAnAbbreviatedCellAreListsToo(@TempDir Path corpus) throws Exception {
        writePom(corpus, "morpheus-a", "morpheus-b", "morpheus-c", "morpheus-d");
        writePage(corpus, "docs/architecture/families.md", """
                | Famille | Modules |
                |---|---|
                | Un | `morpheus-a`, `morpheus-b` |
                | Deux | `morpheus-c` |
                """);
        writePage(corpus, "docs/developer/abbreviated.md", """
                | Module | Traitement |
                |---|---|
                | `morpheus-a`, `-b`, `-c` | analysés |
                | `morpheus-d` | analysé |
                """);
        assertEquals(List.of(
                        "docs/architecture/families.md:1 omits morpheus-d, which the root POM declares"),
                incompleteReactorLists(corpus));
    }

    @Test
    void theListRuleFailsInsteadOfPassingWhenItFindsNothingToCheck(@TempDir Path corpus) throws Exception {
        writePom(corpus, "morpheus-a", "morpheus-b");
        writePage(corpus, "docs/developer/prose.md", "Only `morpheus-a` is mentioned in passing.\n");
        AssertionError noList = assertThrows(AssertionError.class, () -> incompleteReactorLists(corpus));
        assertTrue(noList.getMessage().contains("no page lists the reactor"), noList::getMessage);

        Path emptyPom = Files.createDirectories(corpus.resolve("empty"));
        Files.writeString(emptyPom.resolve("pom.xml"), "<project><modules></modules></project>");
        AssertionError noModule = assertThrows(AssertionError.class, () -> incompleteReactorLists(emptyPom));
        assertTrue(noModule.getMessage().contains("no <module>"), noModule::getMessage);
    }

    @Test
    void aStatedTotalIsRefusedWithItsPageEvenWhenItIsRight(@TempDir Path corpus) throws Exception {
        writePom(corpus, "morpheus-a", "morpheus-b");
        writePage(corpus, "docs/developer/total.md", """
                # Reactor

                Le dépôt contient **2 modules enfants**, soit **3 projets Maven parent inclus**.
                Les 3 POMs actifs portent la même version.
                """);
        writePage(corpus, "docs/architecture/arc42/total.md", "Le reactor agrège 2 modules.\n");
        List<String> refused = statedReactorTotals(corpus);
        assertEquals(List.of(
                        "docs/architecture/arc42/total.md:1 \"2 modules\"",
                        "docs/developer/total.md:3 \"2 modules\"",
                        "docs/developer/total.md:3 \"3 projets\"",
                        "docs/developer/total.md:4 \"3 POMs\""),
                refused);
    }

    @Test
    void aMeasurementThatCarriesItsDateOrCommitIsARecordAndIsLeftAlone(@TempDir Path corpus) throws Exception {
        writePom(corpus, "morpheus-a");
        writePage(corpus, "docs/developer/dated.md", """
                ## Mesures

                Mesuré le 07/10/2026 sur `develop`.

                | Exécution | Durée |
                |---|---|
                | analyse, 16 modules | 231 s |

                ## Relevé

                Mesuré à `fe88c856` : 19/19 projets en `SUCCESS`.

                ### Détail

                Les 16 modules sont analysés.

                ## Présent

                Le reactor agrège 17 modules.
                """);
        assertEquals(List.of("docs/developer/dated.md:19 \"17 modules\""), statedReactorTotals(corpus));
    }

    @Test
    void decisionRecordsStepsAndTheUserGuideAreNotRead(@TempDir Path corpus) throws Exception {
        writePom(corpus, "morpheus-a");
        writePage(corpus, "docs/developer/clean.md", "Compter les entrées <module> du pom.xml.\n");
        writePage(corpus, "docs/architecture/adr/0001-record.md", "Le reactor agrège 17 modules.\n");
        writePage(corpus, "docs/project/audits/AUDIT.md", "Le reactor agrège 17 modules.\n");
        writePage(corpus, "docs/user/GUIDE.md", "Le reactor agrège 17 modules.\n");
        assertEquals(List.of(), statedReactorTotals(corpus));

        writePage(corpus, "docs/architecture/arc42/05.md", "Le reactor agrège 17 modules.\n");
        writePage(corpus, "README.md", "Le reactor agrège 17 modules.\n");
        assertEquals(List.of(
                        "README.md:1 \"17 modules\"",
                        "docs/architecture/arc42/05.md:1 \"17 modules\""),
                statedReactorTotals(corpus));
    }

    @Test
    void theTotalRuleFailsInsteadOfPassingWhenTheScannedSetIsEmpty(@TempDir Path corpus) throws Exception {
        writePom(corpus, "morpheus-a");
        writePage(corpus, "docs/user/GUIDE.md", "Rien à lire ici.\n");
        AssertionError refusal = assertThrows(AssertionError.class, () -> statedReactorTotals(corpus));
        assertTrue(refusal.getMessage().contains("no current page"), refusal::getMessage);
    }

    /** The current pages that list the reactor, per page and per list, against the modules of the root POM. */
    private static List<String> incompleteReactorLists(Path root) throws IOException {
        Set<String> declared = declaredModules(root);
        List<String> violations = new ArrayList<>();
        int lists = 0;
        for (Path page : currentPages(root)) {
            List<String> lines = Files.readAllLines(page);
            Set<Integer> records = recordLines(lines);
            for (ModuleList list : moduleLists(lines)) {
                if (list.modules().size() * 2 <= declared.size() || records.contains(list.firstLine())) {
                    continue;
                }
                lists++;
                String where = relative(root, page) + ":" + list.firstLine();
                Set<String> omitted = new TreeSet<>(declared);
                omitted.removeAll(list.modules());
                if (!omitted.isEmpty()) {
                    violations.add(where + " omits " + String.join(", ", omitted) + ", which the root POM declares");
                }
                Set<String> unknown = new TreeSet<>(list.modules());
                unknown.removeAll(declared);
                if (!unknown.isEmpty()) {
                    violations.add(where + " names " + String.join(", ", unknown)
                            + ", which the root POM does not declare");
                }
            }
        }
        if (lists == 0) {
            throw new AssertionError("no page lists the reactor: the rule would pass on nothing; either a page lost"
                    + " its list or the shapes this rule recognises no longer match the pages");
        }
        return violations;
    }

    /** Every present-tense total of modules, projects or POMs on a current page. */
    private static List<String> statedReactorTotals(Path root) throws IOException {
        requireDeclaredModules(root);
        List<Path> pages = currentPages(root);
        if (pages.isEmpty()) {
            throw new AssertionError("no current page to read under " + SCANNED_TREES + " and " + ENTRY_PAGES);
        }
        List<String> violations = new ArrayList<>();
        for (Path page : pages) {
            List<String> lines = Files.readAllLines(page);
            Set<Integer> records = recordLines(lines);
            for (int index = 0; index < lines.size(); index++) {
                if (records.contains(index + 1)) {
                    continue;
                }
                Matcher total = REACTOR_TOTAL.matcher(lines.get(index));
                while (total.find()) {
                    violations.add(relative(root, page) + ":" + (index + 1) + " \""
                            + total.group().replaceAll("\\s+", " ") + "\"");
                }
            }
        }
        return violations;
    }

    /** The lines (1-based) of the sections that carry a date or a commit: measurements, not the present. */
    private static Set<Integer> recordLines(List<String> lines) {
        Set<Integer> records = new LinkedHashSet<>();
        for (Section section : sections(lines)) {
            if (DATE_OR_COMMIT.matcher(section.text()).find()) {
                for (int index = 0; index < section.lines().size(); index++) {
                    records.add(section.firstLine() + index);
                }
            }
        }
        return records;
    }

    /** Fails when the root POM declares no module, so that a rule reading the POM cannot pass on nothing. */
    private static void requireDeclaredModules(Path root) throws IOException {
        declaredModules(root);
    }

    private static Set<String> declaredModules(Path root) throws IOException {
        Matcher block = MODULE_BLOCK.matcher(Files.readString(root.resolve("pom.xml")));
        Set<String> modules = new LinkedHashSet<>();
        if (block.find()) {
            Matcher module = MODULE.matcher(block.group(1));
            while (module.find()) {
                modules.add(module.group(1).trim());
            }
        }
        if (modules.isEmpty()) {
            throw new AssertionError("no <module> read from the <modules> block of " + root.resolve("pom.xml"));
        }
        return modules;
    }

    private static List<Path> currentPages(Path root) throws IOException {
        Path records = root.resolve(DECISION_RECORDS);
        Map<String, Path> pages = new TreeMap<>();
        for (String tree : SCANNED_TREES) {
            Path base = root.resolve(tree);
            if (Files.isDirectory(base)) {
                try (Stream<Path> walk = Files.walk(base)) {
                    walk.filter(path -> Files.isRegularFile(path) && path.toString().endsWith(".md"))
                            .filter(path -> !path.startsWith(records))
                            .forEach(path -> pages.put(relative(root, path), path));
                }
            }
        }
        for (String entry : ENTRY_PAGES) {
            Path page = root.resolve(entry);
            if (Files.isRegularFile(page)) {
                pages.put(entry, page);
            }
        }
        return List.copyOf(pages.values());
    }

    /** The lists of module names on a page: one per fenced block, per table, per run of consecutive lines. */
    private static List<ModuleList> moduleLists(List<String> lines) {
        List<ModuleList> lists = new ArrayList<>();
        List<String> block = new ArrayList<>();
        int blockStart = 0;
        boolean table = false;
        boolean fenced = false;
        for (int index = 0; index <= lines.size(); index++) {
            String line = index < lines.size() ? lines.get(index) : "";
            boolean fence = line.strip().startsWith("```");
            if (fence || index == lines.size() || endsBlock(line, fenced, !block.isEmpty(), table)) {
                addIfAny(lists, block, blockStart, table);
                block = new ArrayList<>();
            }
            if (fence) {
                fenced = !fenced;
            } else if (fenced || !line.isBlank()) {
                if (block.isEmpty()) {
                    blockStart = index + 1;
                    table = !fenced && isTableRow(line);
                }
                block.add(line);
            }
        }
        return lists;
    }

    /** Outside a fence, a blank line ends a block, and so does a switch between table rows and prose. */
    private static boolean endsBlock(String line, boolean fenced, boolean blockOpen, boolean table) {
        return !fenced && (line.isBlank() || blockOpen && isTableRow(line) != table);
    }

    private static boolean isTableRow(String line) {
        return line.stripLeading().startsWith("|");
    }

    private static void addIfAny(List<ModuleList> lists, List<String> block, int firstLine, boolean table) {
        Set<String> modules = new LinkedHashSet<>();
        for (String line : block) {
            if (table) {
                modules.addAll(modulesOfTableRow(line));
            } else {
                Matcher own = MODULE_ON_ITS_OWN_LINE.matcher(line);
                if (own.matches()) {
                    modules.add(own.group(1));
                }
            }
        }
        if (!modules.isEmpty()) {
            lists.add(new ModuleList(firstLine, modules));
        }
    }

    private static Set<String> modulesOfTableRow(String row) {
        Set<String> modules = new LinkedHashSet<>();
        String inner = row.strip();
        if (inner.startsWith("|")) {
            inner = inner.substring(1);
        }
        for (String cell : inner.split("\\|")) {
            String trimmed = cell.strip();
            if (MODULE_CELL.matcher(trimmed).matches()) {
                Matcher token = MODULE_CELL_TOKEN.matcher(trimmed);
                while (token.find()) {
                    modules.add("morpheus" + token.group(2));
                }
            }
        }
        return modules;
    }

    /** A page cut at its level 1 and 2 headings; a heading inside a fenced block is not one. */
    private static List<Section> sections(List<String> lines) {
        List<Section> sections = new ArrayList<>();
        List<String> current = new ArrayList<>();
        int start = 1;
        boolean fenced = false;
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            if (line.strip().startsWith("```")) {
                fenced = !fenced;
            } else if (!fenced && HEADING.matcher(line).find() && !current.isEmpty()) {
                sections.add(new Section(start, current));
                current = new ArrayList<>();
                start = index + 1;
            }
            current.add(line);
        }
        if (!current.isEmpty()) {
            sections.add(new Section(start, current));
        }
        return sections;
    }

    private record ModuleList(int firstLine, Set<String> modules) {
    }

    private record Section(int firstLine, List<String> lines) {
        String text() {
            return String.join("\n", lines);
        }
    }

    private static String relative(Path root, Path page) {
        return root.relativize(page).toString().replace('\\', '/');
    }

    private static String fence(String... modules) {
        return "```text\n" + String.join("\n", modules) + "\n```\n";
    }

    private static void writePom(Path root, String... modules) throws IOException {
        StringBuilder pom = new StringBuilder("<project>\n<modules>\n");
        for (String module : modules) {
            pom.append("<module>").append(module).append("</module>\n");
        }
        Files.writeString(root.resolve("pom.xml"), pom.append("</modules>\n</project>\n").toString());
    }

    private static void writePage(Path root, String page, String content) throws IOException {
        Path file = root.resolve(page);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    private static Path repositoryRoot() throws IOException {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            Path pom = current.resolve("pom.xml");
            if (Files.isRegularFile(pom)) {
                String content = Files.readString(pom);
                if (content.contains("<artifactId>morpheus-engine</artifactId>") && content.contains("<modules>")) {
                    return current;
                }
            }
            current = current.getParent();
        }
        throw new IOException("cannot locate MORPHEUS repository root from " + Path.of("").toAbsolutePath());
    }
}
