package com.morpheus.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * A workflow that sends the maintainer to the risk register names an entry that is not closed.
 *
 * <p>The messages of {@code security.yml} and {@code nightly.yml} tell the maintainer, the moment a gate fails, which
 * entry of {@code docs/architecture/risks/register.md} explains it. On 26/10/2026 those messages named {@code RT-13}
 * while the register had dated it closed and removed its row, although the risk still blocked pull requests: a failing
 * gate sent its reader to read that the problem was settled (AUD-DEP-26).</p>
 *
 * <p>The register writes the state of an entry by its place, not by a field: a row of the first table of
 * {@value #RISK_SECTION} or {@value #DEBT_SECTION} is open, an identifier in back quotes in a {@code ###} heading that
 * calls it a position is a position, any other mention is closed. An identifier the register never mentions is
 * refused under its own name, because the fix is not the same: correct the reference, not reopen the entry. A row
 * kept in a table while the detail heading of the same entry calls it closed is a register that contradicts itself,
 * refused before any reference is judged admissible. The rule reads text because both targets are a {@code .yml}
 * and a {@code .md}: the text is the only hold there.</p>
 */
@CoherenceCheck
class RiskRegisterReferencesTest {

    static final String REGISTER = "docs/architecture/risks/register.md";

    static final String WORKFLOWS = ".github/workflows";

    static final String RISK_SECTION = "Risques techniques";

    static final String DEBT_SECTION = "Dette technique / documentaire";

    private static final List<String> ACTIVE_SECTIONS = List.of(RISK_SECTION, DEBT_SECTION);

    private static final Pattern IDENTIFIER = Pattern.compile("\\b(?:RT|DT)-[0-9]+\\b");

    private static final Pattern SECTION = Pattern.compile("^##\\s+(.+?)\\s*$");

    private static final Pattern TABLE_ROW_IDENTIFIER = Pattern.compile("^\\|\\s*((?:RT|DT)-[0-9]+)\\s*\\|");

    private static final Pattern QUOTED_IDENTIFIER = Pattern.compile("`((?:RT|DT)-[0-9]+)`");

    private static final Pattern DETAIL_HEADING = Pattern.compile(
            "^###\\s+((?:RT|DT)-[0-9]+)\\s+\\S.*\\(([^()]*)\\)\\s*$");

    private static final Pattern CLOSED_WORD = Pattern.compile("\\bclos(?:e|es)?\\b", Pattern.CASE_INSENSITIVE);

    @Test
    void theWorkflowsOfTheRepositoryPointOnlyAtEntriesThatAreNotClosed() throws IOException {
        Path root = RepositoryRoot.locate();
        String register = Files.readString(root.resolve(REGISTER));
        Map<String, String> workflows = new TreeMap<>();
        try (Stream<Path> files = Files.list(root.resolve(WORKFLOWS))) {
            for (Path file : files.filter(path -> path.getFileName().toString().endsWith(".yml")).sorted().toList()) {
                workflows.put(WORKFLOWS + "/" + file.getFileName(), Files.readString(file));
            }
        }

        assertEquals(List.of(), problems(register, workflows),
                "a workflow points at an entry of " + REGISTER + " that is closed or unknown, or the register"
                        + " contradicts itself");
    }

    @Test
    void anEntryClosedAndStillCitedIsRefusedWithItsFileLineAndState() {
        String closed = REGISTER_CORPUS
                .replace(RT13_ROW + "\n", "")
                .replace("(constaté le 09/09/2026, ouvert)", "(constaté le 09/09/2026, clos le 22/09/2026)");

        List<String> problems = problems(closed, Map.of(SECURITY, "# RT-13\nrun: echo \"see RT-13\"\n"));

        assertEquals(List.of(
                SECURITY + ":1 points at RT-13, which is closed in " + REGISTER,
                SECURITY + ":2 points at RT-13, which is closed in " + REGISTER), problems);
    }

    @Test
    void anUnknownIdentifierIsRefusedAsUnknown() {
        List<String> problems = problems(REGISTER_CORPUS, Map.of(SECURITY, "x\nrun: echo \"see RT-99\"\n"));

        assertEquals(List.of(SECURITY + ":2 points at RT-99, which is unknown to " + REGISTER), problems);
    }

    @Test
    void aPathToTheRegisterWithoutAnIdentifierIsNotJudged() {
        List<String> problems = problems(REGISTER_CORPUS,
                Map.of(SECURITY, "# see " + REGISTER + "\n# RT-13 explains the refresh\n"));

        assertEquals(List.of(), problems);
    }

    @Test
    void aRiskAcceptedWithoutAFixIsOpenAndCitable() {
        assertEquals(List.of(), problems(REGISTER_CORPUS, Map.of(SECURITY, "# RT-14, accepted\n")));
    }

    @Test
    void aPositionNamedByARequalificationHeadingIsCitable() {
        assertEquals(List.of(), problems(REGISTER_CORPUS, Map.of(SECURITY, "# DT-13 explains the double run\n")));
    }

    @Test
    void aDebtRemovedFromTheTableIsClosed() {
        List<String> problems = problems(REGISTER_CORPUS, Map.of(SECURITY, "# DT-06\n"));

        assertEquals(List.of(SECURITY + ":1 points at DT-06, which is closed in " + REGISTER), problems);
    }

    @Test
    void aRowOutsideTheFirstTableOfAnActiveSectionIsNotActive() {
        List<String> problems = problems(REGISTER_CORPUS, Map.of(SECURITY, "# DT-99\n# DT-98\n"));

        assertEquals(List.of(
                SECURITY + ":1 points at DT-99, which is closed in " + REGISTER,
                SECURITY + ":2 points at DT-98, which is closed in " + REGISTER), problems);
    }

    @Test
    void aClosureWrittenHalfwayIsRefusedAsARegisterThatContradictsItself() {
        String halfway = REGISTER_CORPUS
                .replace("(constaté le 09/09/2026, ouvert)", "(constaté le 09/09/2026, clos le 22/09/2026)");

        List<String> problems = problems(halfway, Map.of(SECURITY, "# RT-13\n"));

        assertEquals(List.of(
                REGISTER + " keeps RT-13 in an active table at line 7 while its detail heading at line 9 calls it"
                        + " closed",
                SECURITY + ":1 points at RT-13, which the register both keeps active and calls closed"), problems);
    }

    @Test
    void aRenamedSectionReadsNoActiveRowAndFails() {
        String renamed = REGISTER_CORPUS.replace("## " + RISK_SECTION, "## Risques");

        List<String> problems = problems(renamed, Map.of(SECURITY, "# DT-10\n"));

        assertTrue(problems.contains(REGISTER + ": no row of an active table read in section \"" + RISK_SECTION
                + "\""), () -> String.join("\n", problems));
    }

    @Test
    void workflowsWithoutAnyIdentifierFail() {
        List<String> problems = problems(REGISTER_CORPUS, Map.of(SECURITY, "# see " + REGISTER + "\n"));

        assertEquals(List.of("no RT-NN or DT-NN identifier read in " + WORKFLOWS), problems);
    }

    /**
     * Every problem of the references written in {@code workflows} (path to text, in reading order) against
     * {@code register}, in the order they are found; an empty list when the references hold.
     */
    static List<String> problems(String register, Map<String, String> workflows) {
        List<String> problems = new ArrayList<>();
        String[] lines = register.split("\n", -1);

        Map<String, Integer> activeRows = new LinkedHashMap<>();
        Map<String, Integer> closedHeadings = new LinkedHashMap<>();
        Set<String> positions = new TreeSet<>();
        Set<String> mentioned = new TreeSet<>();
        Map<String, Integer> rowsBySection = new LinkedHashMap<>();
        String section = null;
        boolean tableSeen = false;
        boolean tableEnded = false;
        for (int index = 0; index < lines.length; index++) {
            String line = lines[index];
            int number = index + 1;
            Matcher identifiers = IDENTIFIER.matcher(line);
            while (identifiers.find()) {
                mentioned.add(identifiers.group());
            }
            Matcher sectionHeading = SECTION.matcher(line);
            if (sectionHeading.matches()) {
                section = ACTIVE_SECTIONS.contains(sectionHeading.group(1)) ? sectionHeading.group(1) : null;
                tableSeen = false;
                tableEnded = false;
                continue;
            }
            if (line.startsWith("###")) {
                if (line.toLowerCase(Locale.ROOT).contains("position")) {
                    Matcher quoted = QUOTED_IDENTIFIER.matcher(line);
                    while (quoted.find()) {
                        positions.add(quoted.group(1));
                    }
                }
                Matcher detail = DETAIL_HEADING.matcher(line);
                if (detail.matches() && CLOSED_WORD.matcher(detail.group(2)).find()) {
                    closedHeadings.putIfAbsent(detail.group(1), number);
                }
                continue;
            }
            if (section == null || tableEnded) {
                continue;
            }
            if (line.startsWith("|")) {
                tableSeen = true;
                Matcher row = TABLE_ROW_IDENTIFIER.matcher(line);
                if (row.find()) {
                    activeRows.putIfAbsent(row.group(1), number);
                    rowsBySection.merge(section, 1, Integer::sum);
                }
            } else if (tableSeen) {
                tableEnded = true;
            }
        }

        for (String activeSection : ACTIVE_SECTIONS) {
            if (!rowsBySection.containsKey(activeSection)) {
                problems.add(REGISTER + ": no row of an active table read in section \"" + activeSection + "\"");
            }
        }
        Set<String> contradicted = new TreeSet<>();
        for (Map.Entry<String, Integer> row : activeRows.entrySet()) {
            Integer heading = closedHeadings.get(row.getKey());
            if (heading != null) {
                contradicted.add(row.getKey());
                problems.add(REGISTER + " keeps " + row.getKey() + " in an active table at line " + row.getValue()
                        + " while its detail heading at line " + heading + " calls it closed");
            }
        }

        int references = 0;
        for (Map.Entry<String, String> workflow : workflows.entrySet()) {
            String[] workflowLines = workflow.getValue().split("\n", -1);
            for (int index = 0; index < workflowLines.length; index++) {
                Matcher identifier = IDENTIFIER.matcher(workflowLines[index]);
                while (identifier.find()) {
                    references++;
                    String id = identifier.group();
                    String where = workflow.getKey() + ":" + (index + 1) + " points at " + id;
                    if (contradicted.contains(id)) {
                        problems.add(where + ", which the register both keeps active and calls closed");
                    } else if (activeRows.containsKey(id) || positions.contains(id)) {
                        continue;
                    } else if (mentioned.contains(id)) {
                        problems.add(where + ", which is closed in " + REGISTER);
                    } else {
                        problems.add(where + ", which is unknown to " + REGISTER);
                    }
                }
            }
        }
        if (references == 0) {
            problems.add("no RT-NN or DT-NN identifier read in " + WORKFLOWS);
        }
        return problems;
    }

    private static final String SECURITY = WORKFLOWS + "/security.yml";

    private static final String RT13_ROW = "| RT-13 | Le gate CVE dépend du NVD | 1 | 2 | **2** | Deux sources | Permanent |";

    /** A register in the shape of the real one: the entries are those the register holds at the time of writing. */
    private static final String REGISTER_CORPUS = String.join("\n",
            "# Registre des risques",
            "",
            "## " + RISK_SECTION,
            "",
            "| ID | Risque | P | I | E | Mitigation actuelle | Révision |",
            "|----|--------|:-:|:-:|:-:|---------------------|----------|",
            RT13_ROW,
            "| RT-14 | Distribution Windows non signée | 3 | 2 | **6** | **Risque accepté** | Certificat |",
            "### RT-13 — le gate CVE dépend du NVD (constaté le 09/09/2026, ouvert)",
            "",
            "### Risques de gouvernance résolus le 27/08/2026",
            "",
            "Les anciens risques `RT-10` et `RT-11` ne sont plus actifs.",
            "",
            "## " + DEBT_SECTION,
            "",
            "| ID | Dette | Domaine | Priorité | Action |",
            "|----|-------|---------|----------|--------|",
            "| DT-10 | Couverture historique | Qualité | **Moyenne** | Ratchets |",
            "",
            "Les anciennes dettes `DT-06` et `DT-09` sont résolues et retirées du tableau actif.",
            "",
            "| DT-99 | Un second tableau de la section n'est pas le tableau actif | x | x | x |",
            "",
            "### Requalification du 22/09/2026 — `DT-13` est une position, pas une dette",
            "",
            "## État CI actif",
            "",
            "| DT-98 | Une ligne de tableau hors des deux sections n'est pas active | x |",
            "");
}
