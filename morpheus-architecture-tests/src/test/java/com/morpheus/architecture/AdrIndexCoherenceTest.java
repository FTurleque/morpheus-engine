package com.morpheus.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * The decision record is the document; the index is a view of it. This suite makes that relation executable.
 *
 * <p>{@code rules/meta.md} already states the principle -- a value written twice has one original and one copy --
 * but nothing enforced it for the status of a decision, and fifteen copies had drifted. The clearest evidence is
 * that ADR-0103, written the day before, never reached the index at all: a suite that only compared statuses
 * would have missed it entirely, because a record absent from the index has no status to disagree with. So the
 * bijection is checked first and separately.</p>
 *
 * <p>Concordance is asserted on the <em>verdict</em> -- the opening term of the status, with "Acceptee avec
 * contraintes" counting as its own verdict rather than a qualified "Acceptee" -- and never on the whole string.
 * Five records qualify their status more richly than the index summarises it. Demanding string equality would
 * push an author to delete that qualification to make a test pass, trading a real document for a green build.
 * The record stays free to say more than its row.</p>
 */
class AdrIndexCoherenceTest {
    private static final Pattern INDEX_ROW =
            Pattern.compile("^\\|\\s*\\[ADR-(\\d{4})\\]\\(([^)]+)\\)\\s*\\|(.*?)\\|\\s*\\*\\*(.+?)\\*\\*\\s*\\|\\s*$",
                    Pattern.MULTILINE);
    private static final Pattern RECORD_STATUS =
            Pattern.compile("^-?\\s*Statut\\s*:\\s*\\*\\*(.+?)\\*\\*", Pattern.MULTILINE);
    private static final Pattern DECLARED_VERDICT = Pattern.compile("^- \\*\\*(.+?)\\*\\*\\s*:", Pattern.MULTILINE);
    private static final Pattern RECORD_FILE = Pattern.compile("^(\\d{4})-.*\\.md$");

    @Test
    void everyDecisionRecordHasExactlyOneIndexRowAndEveryRowHasItsRecord() throws IOException {
        Path root = repositoryRoot();
        assertBijection(records(root), indexRows(root));
    }

    @Test
    void theIndexAndTheRecordAgreeOnTheVerdictWhileTheRecordStaysFreeToQualifyIt() throws IOException {
        Path root = repositoryRoot();
        assertVerdictsAgree(records(root), indexRows(root), declaredVerdicts(root));
    }

    @Test
    void noDecisionNumberIsAssignedTwice() throws IOException {
        assertNumbersAreUnique(recordFileNames(repositoryRoot()));
    }

    @Test
    void everyStatusUsesAVerdictTheIndexItselfDeclares() throws IOException {
        Path root = repositoryRoot();
        List<String> verdicts = declaredVerdicts(root);
        assertEquals(5, verdicts.size(), () -> "the ADR index must declare its status vocabulary: " + verdicts);
        assertClosedVocabulary(records(root), indexRows(root), verdicts);
    }

    /**
     * Each of the four properties is proven by breaking it. A rule that has only ever seen a conforming
     * repository is indistinguishable from a rule that accepts everything -- which is exactly how the index
     * stayed wrong for a day without anything noticing.
     */
    @Test
    void eachPropertyRefusesTheDriftItExistsToCatch() {
        List<String> verdicts = List.of("Proposee", "Acceptee", "Acceptee avec contraintes", "Remplacee", "Rejetee");
        Map<String, String> records = new LinkedHashMap<>(Map.of("0001", "Acceptee -- M0"));
        Map<String, IndexRow> rows = new LinkedHashMap<>(Map.of("0001", new IndexRow("0001-a.md", "Acceptee -- M0")));
        assertBijection(records, rows);
        assertVerdictsAgree(records, rows, verdicts);
        assertClosedVocabulary(records, rows, verdicts);
        assertNumbersAreUnique(List.of("0001-a.md", "0002-b.md"));

        Map<String, String> unindexed = new LinkedHashMap<>(records);
        unindexed.put("0103", "Acceptee -- pilote livre");
        AssertionError missingRow = assertThrows(AssertionError.class, () -> assertBijection(unindexed, rows));
        assertTrue(missingRow.getMessage().contains("0103"),
                () -> "the bijection must name the unindexed record: " + missingRow.getMessage());

        Map<String, IndexRow> orphaned = new LinkedHashMap<>(rows);
        orphaned.put("0104", new IndexRow("0104-absent.md", "Acceptee"));
        AssertionError missingRecord = assertThrows(AssertionError.class, () -> assertBijection(records, orphaned));
        assertTrue(missingRecord.getMessage().contains("0104"),
                () -> "the bijection must name the row without a record: " + missingRecord.getMessage());

        AssertionError crossLinked = assertThrows(AssertionError.class,
                () -> assertBijection(records, Map.of("0001", new IndexRow("0002-b.md", "Acceptee -- M0"))));
        assertTrue(crossLinked.getMessage().contains("0002-b.md"),
                () -> "a row must link to its own record: " + crossLinked.getMessage());

        AssertionError disagreement = assertThrows(AssertionError.class,
                () -> assertVerdictsAgree(Map.of("0001", "Proposee -- a valider pendant C0"), rows, verdicts));
        assertTrue(disagreement.getMessage().contains("0001"),
                () -> "the concordance must name the record it disagrees with: " + disagreement.getMessage());

        assertVerdictsAgree(Map.of("0001", "Acceptee -- M0, durcie le 19 aout 2026"), rows, verdicts);

        assertThrows(AssertionError.class,
                () -> assertVerdictsAgree(Map.of("0001", "Acceptee avec contraintes -- M0"), rows, verdicts),
                "Acceptee avec contraintes is its own verdict, not a qualification of Acceptee");

        AssertionError vocabulary = assertThrows(AssertionError.class,
                () -> assertClosedVocabulary(Map.of("0001", "Validee -- M0"), rows, verdicts));
        assertTrue(vocabulary.getMessage().contains("Validee"),
                () -> "the vocabulary check must quote the invented verdict: " + vocabulary.getMessage());

        AssertionError duplicate = assertThrows(AssertionError.class,
                () -> assertNumbersAreUnique(List.of("0095-first.md", "0095-second.md")));
        assertTrue(duplicate.getMessage().contains("0095"),
                () -> "the uniqueness check must name the reused number: " + duplicate.getMessage());
    }

    private static void assertBijection(Map<String, String> records, Map<String, IndexRow> rows) {
        var unindexed = new TreeSet<>(records.keySet());
        unindexed.removeAll(rows.keySet());
        assertTrue(unindexed.isEmpty(),
                () -> "every decision record needs an index row in docs/adr/README.md; missing: " + unindexed);
        var orphaned = new TreeSet<>(rows.keySet());
        orphaned.removeAll(records.keySet());
        assertTrue(orphaned.isEmpty(),
                () -> "every index row must point at an existing decision record; dangling: " + orphaned);
        for (var row : new TreeMap<>(rows).entrySet()) {
            String linked = row.getValue().file();
            assertTrue(linked.startsWith(row.getKey() + "-"),
                    () -> "the index row for ADR-" + row.getKey() + " links to " + linked + ", another record");
        }
    }

    private static void assertVerdictsAgree(Map<String, String> records, Map<String, IndexRow> rows,
            List<String> verdicts) {
        for (var record : new TreeMap<>(records).entrySet()) {
            IndexRow row = rows.get(record.getKey());
            if (row == null) {
                continue;
            }
            assertEquals(verdictOf(row.status(), verdicts), verdictOf(record.getValue(), verdicts),
                    () -> "ADR-" + record.getKey() + " reads \"" + row.status() + "\" in the index and \""
                            + record.getValue() + "\" in the record; the record is the document, so correct "
                            + "whichever is wrong -- but never flatten a status that merely says more");
        }
    }

    private static void assertClosedVocabulary(Map<String, String> records, Map<String, IndexRow> rows,
            List<String> verdicts) {
        for (var record : new TreeMap<>(records).entrySet()) {
            assertTrue(verdictOf(record.getValue(), verdicts) != null,
                    () -> "ADR-" + record.getKey() + " opens on \"" + record.getValue()
                            + "\", which is not a verdict the index declares: " + verdicts);
        }
        for (var row : new TreeMap<>(rows).entrySet()) {
            assertTrue(verdictOf(row.getValue().status(), verdicts) != null,
                    () -> "the index row for ADR-" + row.getKey() + " opens on \"" + row.getValue().status()
                            + "\", which is not a verdict it declares: " + verdicts);
        }
    }

    private static void assertNumbersAreUnique(List<String> fileNames) {
        Map<String, List<String>> byNumber = new TreeMap<>();
        for (String name : fileNames) {
            Matcher matcher = RECORD_FILE.matcher(name);
            if (matcher.matches()) {
                byNumber.computeIfAbsent(matcher.group(1), number -> new ArrayList<>()).add(name);
            }
        }
        byNumber.values().removeIf(files -> files.size() == 1);
        assertTrue(byNumber.isEmpty(), () -> "a decision number is assigned twice, as 0095 already was: " + byNumber);
    }

    /**
     * The longest declared verdict wins, so "Acceptee avec contraintes" is never read as a qualified "Acceptee".
     */
    private static String verdictOf(String status, List<String> verdicts) {
        return verdicts.stream()
                .sorted(Comparator.comparingInt(String::length).reversed())
                .filter(status::startsWith)
                .findFirst()
                .orElse(null);
    }

    /** The vocabulary is read from the index legend rather than restated here, so the two cannot drift apart. */
    private static List<String> declaredVerdicts(Path root) throws IOException {
        String index = Files.readString(root.resolve("docs/adr/README.md")).replace("\r\n", "\n");
        int legend = index.indexOf("## Statuts");
        assertTrue(legend >= 0, "docs/adr/README.md must declare its status vocabulary under \"## Statuts\"");
        int next = index.indexOf("\n#", legend + 1);
        Matcher matcher = DECLARED_VERDICT.matcher(index.substring(legend, next < 0 ? index.length() : next));
        List<String> verdicts = new ArrayList<>();
        while (matcher.find()) {
            verdicts.add(matcher.group(1).trim());
        }
        return List.copyOf(verdicts);
    }

    private static Map<String, IndexRow> indexRows(Path root) throws IOException {
        String index = Files.readString(root.resolve("docs/adr/README.md")).replace("\r\n", "\n");
        Matcher matcher = INDEX_ROW.matcher(index);
        Map<String, IndexRow> rows = new LinkedHashMap<>();
        while (matcher.find()) {
            String number = matcher.group(1);
            IndexRow replaced = rows.put(number, new IndexRow(matcher.group(2).trim(), matcher.group(4).trim()));
            assertTrue(replaced == null, () -> "ADR-" + number + " appears twice in the index");
        }
        assertFalse(rows.isEmpty(), "docs/adr/README.md must index the decision records");
        return rows;
    }

    private static Map<String, String> records(Path root) throws IOException {
        Map<String, String> records = new LinkedHashMap<>();
        for (String name : recordFileNames(root)) {
            String text = Files.readString(root.resolve("docs/adr").resolve(name)).replace("\r\n", "\n");
            Matcher matcher = RECORD_STATUS.matcher(text);
            assertTrue(matcher.find(), () -> name + " must carry a bold \"Statut : **...**\" line");
            records.put(name.substring(0, 4), matcher.group(1).trim());
        }
        return records;
    }

    private static List<String> recordFileNames(Path root) throws IOException {
        try (var files = Files.list(root.resolve("docs/adr"))) {
            return files.map(path -> path.getFileName().toString())
                    .filter(name -> RECORD_FILE.matcher(name).matches())
                    .sorted()
                    .toList();
        }
    }

    private record IndexRow(String file, String status) {}

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isDirectory(current.resolve("docs/adr")) && Files.isRegularFile(current.resolve("pom.xml"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("MORPHEUS repository root not found");
    }
}
