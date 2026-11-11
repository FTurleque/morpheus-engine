package com.morpheus.architecture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the pull request template asks of its author.
 *
 * <p>The template is the only place where the review reads the intention behind a textual assertion and the
 * justification of a governance change: the rules ({@code .claude/rules/architecture.md},
 * {@code .claude/rules/governance.md}) leave no trace in a pull request. The build holds the template, its two
 * sections, the table the first one asks the author to fill (one row per assertion, with its intention, its mechanism
 * and its planted violation) outside any HTML comment, and the governance files the second one names; it never reads
 * the description of a pull request, which the review alone judges. The check is textual because the target is a Markdown file
 * ({@code .claude/rules/architecture.md}, "Restent textuels par nature"); the proofs below run it on fabricated
 * templates so that a check which reads nothing fails instead of passing.</p>
 */
@CoherenceCheck
class PullRequestTemplateCoherenceTest {
    static final String TEMPLATE = ".github/pull_request_template.md";
    static final String INTENTION_SECTION = "## Intention de chaque assertion textuelle";
    static final String GOVERNANCE_SECTION = "## Justification d'une modification de gouvernance";
    /** The header row of the table the intention section asks for; its separator row follows it. */
    static final String INTENTION_TABLE_HEADER = "| Fichier | Intention | Mécanisme | Violation plantée |";
    private static final Pattern HTML_COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);
    private static final Pattern FOUR_COLUMN_SEPARATOR = Pattern.compile("\\|(\\s*:?-+:?\\s*\\|){4}");
    /** The governance files the second section names, each written as code in the template. */
    static final List<String> GOVERNANCE_FILES = List.of(
            "`contracts/public-surfaces.tsv`",
            "`config/*ratchets*.properties`",
            "`config/debt-counter-ratchets.properties`",
            "`docs/developer/reference/openapi/*.yaml`",
            "`morpheus-architecture-tests/`");

    @Test
    void theTemplateAsksForTheIntentionOfEveryTextualAssertion() throws IOException {
        List<String> missing = missingIntentionRequest(RepositoryRoot.locate());
        assertTrue(missing.isEmpty(), () -> String.join(System.lineSeparator(), missing));
    }

    @Test
    void theTemplateAsksForTheJustificationOfAGovernanceChange() throws IOException {
        List<String> missing = missingGovernanceRequest(RepositoryRoot.locate());
        assertTrue(missing.isEmpty(), () -> String.join(System.lineSeparator(), missing));
    }

    @Test
    void aMissingTemplateIsRefusedByItsExpectedPath(@TempDir Path root) throws IOException {
        assertEquals(List.of(TEMPLATE + " is missing: the pull request template asks for the intention of every"
                        + " textual assertion and the justification of every governance change"),
                missingIntentionRequest(root));
        assertEquals(missingIntentionRequest(root), missingGovernanceRequest(root));
    }

    @Test
    void aTemplateWithoutTheIntentionSectionIsRefusedBySectionName(@TempDir Path root) throws IOException {
        write(root, GOVERNANCE_SECTION + "\n" + String.join("\n", GOVERNANCE_FILES) + "\n");

        assertEquals(List.of(TEMPLATE + " has no section \"" + INTENTION_SECTION + "\""),
                missingIntentionRequest(root));
        assertEquals(List.of(), missingGovernanceRequest(root));
    }

    @Test
    void anIntentionSectionKeptWithoutItsTableIsRefused(@TempDir Path root) throws IOException {
        String governance = GOVERNANCE_SECTION + "\n" + String.join("\n", GOVERNANCE_FILES) + "\n";
        String refusal = TEMPLATE + ": section \"" + INTENTION_SECTION + "\" holds no table " + INTENTION_TABLE_HEADER
                + " with its separator row";

        write(root, INTENTION_SECTION + "\n<!-- one row per assertion -->\n\n" + governance);
        assertEquals(List.of(refusal), missingIntentionRequest(root), "a heading left alone asks for nothing");

        write(root, INTENTION_SECTION + "\n| Fichier | Intention |\n|---|---|\n" + governance);
        assertEquals(List.of(refusal), missingIntentionRequest(root),
                "a table without the mechanism and the planted violation does not ask for them");

        write(root, INTENTION_SECTION + "\n<!--\n" + INTENTION_TABLE_HEADER + "\n|---|---|---|---|\n-->\n" + governance);
        assertEquals(List.of(refusal), missingIntentionRequest(root), "a table inside a comment is not shown");

        write(root, INTENTION_SECTION + "\n" + INTENTION_TABLE_HEADER + "\n\n" + governance);
        assertEquals(List.of(refusal), missingIntentionRequest(root), "a header row without its separator is no table");

        write(root, governance + INTENTION_SECTION + "\n" + INTENTION_TABLE_HEADER + "\n|---|---|---|---|\n| | | | |\n");
        assertEquals(List.of(), missingIntentionRequest(root));
    }

    @Test
    void aTemplateWithoutTheGovernanceSectionOrOneOfItsFilesIsRefused(@TempDir Path root) throws IOException {
        write(root, INTENTION_SECTION + "\n" + INTENTION_TABLE_HEADER + "\n|---|---|---|---|\n");
        assertEquals(List.of(TEMPLATE + " has no section \"" + GOVERNANCE_SECTION + "\""),
                missingGovernanceRequest(root));
        assertEquals(List.of(), missingIntentionRequest(root));

        write(root, GOVERNANCE_SECTION + "\n" + String.join("\n", GOVERNANCE_FILES.subList(1, GOVERNANCE_FILES.size()))
                + "\n" + INTENTION_SECTION + "\n" + GOVERNANCE_FILES.get(0) + "\n");
        assertEquals(List.of(TEMPLATE + ": section \"" + GOVERNANCE_SECTION + "\" does not name "
                + GOVERNANCE_FILES.get(0)), missingGovernanceRequest(root),
                "a file named only under another section is not asked for by the governance section");
    }

    static List<String> missingIntentionRequest(Path root) throws IOException {
        Path template = root.resolve(TEMPLATE);
        if (!Files.isRegularFile(template)) {
            return List.of(absence());
        }
        String intention = section(Files.readString(template), INTENTION_SECTION);
        if (intention == null) {
            return List.of(TEMPLATE + " has no section \"" + INTENTION_SECTION + "\"");
        }
        List<String> lines = HTML_COMMENT.matcher(intention).replaceAll("").lines().map(String::strip).toList();
        int header = lines.indexOf(INTENTION_TABLE_HEADER);
        if (header < 0 || header + 1 >= lines.size()
                || !FOUR_COLUMN_SEPARATOR.matcher(lines.get(header + 1)).matches()) {
            return List.of(TEMPLATE + ": section \"" + INTENTION_SECTION + "\" holds no table " + INTENTION_TABLE_HEADER
                    + " with its separator row");
        }
        return List.of();
    }

    static List<String> missingGovernanceRequest(Path root) throws IOException {
        Path template = root.resolve(TEMPLATE);
        if (!Files.isRegularFile(template)) {
            return List.of(absence());
        }
        String governance = section(Files.readString(template), GOVERNANCE_SECTION);
        if (governance == null) {
            return List.of(TEMPLATE + " has no section \"" + GOVERNANCE_SECTION + "\"");
        }
        List<String> missing = new ArrayList<>();
        for (String file : GOVERNANCE_FILES) {
            if (!governance.contains(file)) {
                missing.add(TEMPLATE + ": section \"" + GOVERNANCE_SECTION + "\" does not name " + file);
            }
        }
        return missing;
    }

    private static String absence() {
        return TEMPLATE + " is missing: the pull request template asks for the intention of every textual assertion"
                + " and the justification of every governance change";
    }

    /** The text under a level 2 heading, up to the next level 1 or 2 heading; {@code null} when the heading is absent. */
    private static String section(String text, String heading) {
        List<String> lines = text.lines().toList();
        int start = lines.indexOf(heading);
        if (start < 0) {
            return null;
        }
        StringBuilder body = new StringBuilder();
        for (int index = start + 1; index < lines.size(); index++) {
            String line = lines.get(index);
            if (line.startsWith("# ") || line.startsWith("## ")) {
                break;
            }
            body.append(line).append('\n');
        }
        return body.toString();
    }

    private static void write(Path root, String template) throws IOException {
        Path file = root.resolve(TEMPLATE);
        Files.createDirectories(file.getParent());
        Files.writeString(file, template);
    }
}
