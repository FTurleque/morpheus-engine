package com.morpheus.architecture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A decision record a living OpenSpec document cites is one in force.
 *
 * <p>A living document is a change outside {@code openspec/changes/archive/} or a specification under
 * {@code openspec/specs/}: whoever reads it takes its citations for the decisions that hold today. A record retired to
 * {@code docs/architecture/adr/archive/} says itself that it is no longer kept up to date, so a citation of it in the
 * present tense sends the reader to a stale text. A cited identifier is {@code ADR-NNNN} or a domain prefix of the
 * registry followed by four digits; its number is looked up among the records in force and the archived ones. A record
 * in force cited under another domain's prefix is refused, naming its own. A number found only in the archive is
 * refused unless the mark {@value #ARCHIVED} follows it on the same line, which tells the reader the record is cited
 * as history; a number found nowhere is refused. The archive of changes is a dated record
 * and is not read. No list of exceptions is kept here.</p>
 */
@CoherenceCheck
class OpenSpecDecisionRecordCitationTest {

    static final String ARCHIVED = "(archivé)";

    private static final Path REGISTRY = Path.of("docs", "architecture", "adr");
    private static final Path CHANGES = Path.of("openspec", "changes");
    private static final Path CHANGE_ARCHIVE = CHANGES.resolve("archive");
    private static final Path SPECS = Path.of("openspec", "specs");
    private static final Pattern RECORD_IN_FORCE = Pattern.compile("^([A-Z]+)-(\\d{4})-.+\\.md$");
    private static final Pattern ARCHIVED_RECORD = Pattern.compile("^(\\d{4})-.+\\.md$");
    /** What may sit between a cited identifier and its mark: a closing code span, a comma, spaces. */
    private static final Pattern MARK = Pattern.compile("^`*,?\\s*\\(archivé\\)");

    @Test
    void everyDecisionRecordALivingOpenSpecDocumentCitesIsInForce() throws IOException {
        Path root = RepositoryRoot.locate();
        Registry registry = registry(root);
        assertTrue(!registry.inForce().isEmpty() && !registry.archived().isEmpty(),
                () -> "the registry read no record in force or no archived record: " + registry);
        assertEquals(List.of(), refusals(root, RepositoryTree.files(root), registry),
                "a living OpenSpec document cites a decision record that is not in force; cite the record that "
                        + "replaces it, or mark the citation " + ARCHIVED + " right after it when it is told as history");
    }

    @Test
    void anArchivedRecordIsRefusedInAChangeAndASpecificationUnlessMarkedAndTheArchiveIsNotRead(@TempDir Path root)
            throws IOException {
        plantRegistry(root);
        plant(root, "openspec/changes/a-change/design.md", """
                - ARCH-0162, décision 3, reste vrai ; `ARCH-0010` aussi.
                - ADR-0156 décision 3 dit la même chose.
                - ADR-0156 (archivé) a été remplacé par ARCH-0162.
                - `ARCH-0156` (archivé), comme le dit ARCH-0156 plus loin.
                """);
        plant(root, "openspec/specs/a-capability/spec.md", "- **GIVEN** `ARCH-0156`, qui nomme une liste\n");
        plant(root, "openspec/changes/archive/2026-01-01-old/design.md", "ADR-0156 décision 3\n");

        assertEquals(List.of(
                "openspec/changes/a-change/design.md:2 ADR-0156 is archived",
                "openspec/changes/a-change/design.md:4 ARCH-0156 is archived",
                "openspec/specs/a-capability/spec.md:1 ARCH-0156 is archived"),
                refusals(root, RepositoryTree.files(root), registry(root)),
                "a record in force is admitted, an archived one only with its mark, and the archive is not read");
    }

    @Test
    void aNumberNoRecordCarriesIsRefusedAndAnotherFourDigitIdentifierIsNotACitation(@TempDir Path root)
            throws IOException {
        plantRegistry(root);
        plant(root, "openspec/changes/a-change/proposal.md", """
                - ADR-0999 décide cela.
                - The SHA-2560 field and AUD-0156 are not decision records.
                - SEC-0162 names a record of the architecture domain, SEC-0011 one of its own.
                """);

        assertEquals(List.of("openspec/changes/a-change/proposal.md:1 ADR-0999 is no decision record",
                        "openspec/changes/a-change/proposal.md:3 SEC-0162 is ARCH-0162"),
                refusals(root, RepositoryTree.files(root), registry(root)),
                "only ADR and the prefixes of the registry are read as citations");
    }

    /** Each refusal names the file, the line and the identifier. */
    private static List<String> refusals(Path root, List<Path> files, Registry registry) throws IOException {
        Pattern cited = Pattern.compile("(?<![\\w-])(ADR|" + String.join("|", registry.prefixes())
                + ")-(\\d{4})(?!\\d)");
        List<String> refusals = new ArrayList<>();
        for (Path file : files) {
            Path relative = root.relativize(file);
            boolean living = (relative.startsWith(CHANGES) && !relative.startsWith(CHANGE_ARCHIVE))
                    || relative.startsWith(SPECS);
            if (!living || !file.toString().endsWith(".md")) {
                continue;
            }
            String name = relative.toString().replace('\\', '/');
            List<String> lines = Files.readAllLines(file);
            for (int index = 0; index < lines.size(); index++) {
                lineRefusals(name + ":" + (index + 1), lines.get(index), cited, registry, refusals);
            }
        }
        return refusals;
    }

    /**
     * A record in force is cited as {@code ADR-NNNN} or under its own domain prefix, an archived one only with its mark,
     * and a number no record carries never.
     */
    private static void lineRefusals(String where, String line, Pattern cited, Registry registry, List<String> refusals) {
        Matcher citation = cited.matcher(line);
        while (citation.find()) {
            String prefix = citation.group(1);
            String number = citation.group(2);
            String inForce = registry.inForce().get(number);
            String refused = where + " " + citation.group();
            if (inForce != null) {
                if (!prefix.equals("ADR") && !prefix.equals(inForce)) {
                    refusals.add(refused + " is " + inForce + "-" + number);
                }
            } else if (!registry.archived().contains(number)) {
                refusals.add(refused + " is no decision record");
            } else if (!MARK.matcher(line.substring(citation.end())).find()) {
                refusals.add(refused + " is archived");
            }
        }
    }

    /** The domain prefixes, each number in force with its prefix, and the archived numbers. */
    private record Registry(Set<String> prefixes, Map<String, String> inForce, Set<String> archived) {
    }

    private static Registry registry(Path root) throws IOException {
        Set<String> prefixes = new TreeSet<>();
        Map<String, String> inForce = new TreeMap<>();
        Set<String> archived = new TreeSet<>();
        try (var records = Files.list(root.resolve(REGISTRY))) {
            for (Path record : records.toList()) {
                Matcher name = RECORD_IN_FORCE.matcher(record.getFileName().toString());
                if (name.matches()) {
                    prefixes.add(name.group(1));
                    inForce.put(name.group(2), name.group(1));
                }
            }
        }
        try (var records = Files.list(root.resolve(REGISTRY).resolve("archive"))) {
            for (Path record : records.toList()) {
                Matcher name = ARCHIVED_RECORD.matcher(record.getFileName().toString());
                if (name.matches()) {
                    archived.add(name.group(1));
                }
            }
        }
        return new Registry(prefixes, inForce, archived);
    }

    private static void plantRegistry(Path root) throws IOException {
        plant(root, "docs/architecture/adr/ARCH-0162-consolidated.md", "# ARCH-0162\n");
        plant(root, "docs/architecture/adr/ARCH-0010-older.md", "# ARCH-0010\n");
        plant(root, "docs/architecture/adr/SEC-0011-security.md", "# SEC-0011\n");
        plant(root, "docs/architecture/adr/archive/0156-retired.md", "# ADR-0156\n");
    }

    private static void plant(Path root, String relative, String content) throws IOException {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }
}
