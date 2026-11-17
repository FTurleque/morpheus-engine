package com.morpheus.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The two sections an agent is briefed with stay within the budget that the briefing keeps.
 *
 * <p>{@code archgate review-context} hands an agent the {@code Decision} and {@code Do's and Don'ts} sections of each
 * applicable record, and cuts each one at {@value #BRIEFING_BUDGET} characters: whatever follows is never read by the
 * agent. A section runs from its {@code ## } heading to the next {@code ## } heading, so its {@code ###} sub-sections
 * count, and it is measured trimmed, in UTF-16 units, as Archgate measures it.</p>
 *
 * <p>The measure is taken on the text with its line endings normalised. Archgate recognises a heading only on a line
 * that ends in a bare line feed, so on a working tree checked out with CRLF it finds no section at all and reports no
 * overrun: run by hand on Windows, {@code archgate check} cannot see the budget it states, and it runs in no workflow.
 * This suite is the check that sees it on every platform, in the build. A record with an empty briefed section fails
 * too: an extraction that finds nothing would otherwise pass every record.</p>
 */
@CoherenceCheck
class AdrBriefingBudgetTest {
    static final int BRIEFING_BUDGET = 2_000;
    private static final List<String> BRIEFED_SECTIONS = List.of("Decision", "Do's and Don'ts");
    private static final String REGISTRY = "docs/architecture/adr";
    private static final Pattern RECORD_IN_FORCE = Pattern.compile("^[A-Z]+-\\d{4}-.*\\.md$");
    private static final Pattern SECTION_HEADING = Pattern.compile("^## (.+)$");

    @Test
    void everyRecordInForceKeepsEachBriefedSectionWithinTheBudget() throws IOException {
        Map<String, String> records = recordsInForce(RepositoryRoot.locate());
        assertFalse(records.isEmpty(), "the registry " + REGISTRY + " holds no record in force: the scan read nothing");

        List<String> overruns = overruns(records);

        assertTrue(overruns.isEmpty(), "a briefed section of a decision record exceeds the " + BRIEFING_BUDGET
                + "-character budget that archgate review-context keeps, or is empty; move reasons, options,"
                + " diagrams and examples to Context and keep every rule in the section:\n  "
                + String.join("\n  ", overruns));
    }

    @Test
    void aSectionRunsToTheNextLevelTwoHeadingAndMeasuresTheSameWhateverTheLineEndings() {
        String record = String.join("\n",
                "---", "id: ARCH-0001", "---", "", "# Titre", "", "## Context", "", "Raisons.", "",
                "## Decision", "", "Règle A.", "", "### 1. Détail", "", "Règle B.", "",
                "## Do's and Don'ts", "", "### Do", "", "- Faire.", "", "## Consequences", "", "Suite.");

        Map<String, String> lineFeed = briefedSections(record);
        Map<String, String> carriageReturn = briefedSections(record.replace("\n", "\r\n"));

        assertEquals("Règle A.\n\n### 1. Détail\n\nRègle B.", lineFeed.get("Decision"),
                "a ### sub-section belongs to the section that holds it");
        assertEquals("### Do\n\n- Faire.", lineFeed.get("Do's and Don'ts"));
        assertEquals(lineFeed, carriageReturn, "the measure must not depend on the line endings of the checkout");
    }

    @Test
    void anOverrunNamesTheRecordTheSectionAndItsLengthWhileASectionAtTheBudgetPasses() {
        String atBudget = "x".repeat(BRIEFING_BUDGET);
        String overBudget = "x".repeat(BRIEFING_BUDGET + 1);
        Map<String, String> records = new LinkedHashMap<>();
        records.put("ARCH-0001-a.md", "## Decision\n" + atBudget + "\n## Do's and Don'ts\n- Faire.\n");
        records.put("ARCH-0002-b.md", "## Decision\n" + overBudget + "\n## Do's and Don'ts\n- Faire.\n");
        records.put("ARCH-0003-c.md", "## Decision\nRègle.\n## Consequences\nSuite.\n");

        List<String> overruns = overruns(records);

        assertEquals(List.of(
                "ARCH-0002-b.md : Decision = " + (BRIEFING_BUDGET + 1) + " > " + BRIEFING_BUDGET,
                "ARCH-0003-c.md : Do's and Don'ts absent or empty"), overruns);
    }

    private static List<String> overruns(Map<String, String> records) {
        List<String> overruns = new ArrayList<>();
        records.forEach((file, text) -> {
            Map<String, String> sections = briefedSections(text);
            for (String section : BRIEFED_SECTIONS) {
                String content = sections.getOrDefault(section, "");
                if (content.isEmpty()) {
                    overruns.add(file + " : " + section + " absent or empty");
                } else if (content.length() > BRIEFING_BUDGET) {
                    overruns.add(file + " : " + section + " = " + content.length() + " > " + BRIEFING_BUDGET);
                }
            }
        });
        return overruns;
    }

    private static Map<String, String> briefedSections(String text) {
        Map<String, String> sections = new LinkedHashMap<>();
        String current = null;
        StringBuilder content = new StringBuilder();
        for (String line : text.replace("\r\n", "\n").split("\n", -1)) {
            Matcher heading = SECTION_HEADING.matcher(line);
            if (heading.matches()) {
                keepIfBriefed(sections, current, content);
                current = heading.group(1).strip();
                content.setLength(0);
            } else {
                content.append(line).append('\n');
            }
        }
        keepIfBriefed(sections, current, content);
        return sections;
    }

    private static void keepIfBriefed(Map<String, String> sections, String heading, StringBuilder content) {
        if (heading != null && BRIEFED_SECTIONS.contains(heading)) {
            sections.put(heading, content.toString().strip());
        }
    }

    private static Map<String, String> recordsInForce(Path root) throws IOException {
        Map<String, String> records = new LinkedHashMap<>();
        try (Stream<Path> files = Files.list(root.resolve(REGISTRY))) {
            for (Path file : files.sorted().toList()) {
                String name = file.getFileName().toString();
                if (RECORD_IN_FORCE.matcher(name).matches()) {
                    records.put(name, Files.readString(file, StandardCharsets.UTF_8));
                }
            }
        }
        return records;
    }
}
