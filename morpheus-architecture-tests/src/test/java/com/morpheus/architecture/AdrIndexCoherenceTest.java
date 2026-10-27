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
 *
 * <p>A decision that is no longer in force lives in {@code docs/architecture/adr/archive/}, out of the index and out
 * of Archgate's reach. The registry holds only decisions in force, the archive only retired ones, and a number is
 * unique across both: an archived record keeps its number forever, so reusing it would make an old citation point
 * at a new decision. A superseded record names its successor, and that successor must exist.</p>
 */
class AdrIndexCoherenceTest {
    private static final Pattern INDEX_ROW =
            Pattern.compile("^\\|\\s*\\[ADR-(\\d{4})\\]\\(([^)]+)\\)\\s*\\|(.*?)\\|\\s*\\*\\*(.+?)\\*\\*\\s*\\|\\s*$",
                    Pattern.MULTILINE);
    private static final Pattern RECORD_STATUS =
            Pattern.compile("^-?\\s*Statut\\s*:\\s*\\*\\*(.+?)\\*\\*", Pattern.MULTILINE);
    private static final Pattern DECLARED_VERDICT = Pattern.compile("^- \\*\\*(.+?)\\*\\*\\s*:", Pattern.MULTILINE);
    /** A record in force is {@code PREFIX-NNNN-slug.md}; an archived record keeps the name it had when retired. */
    private static final Pattern RECORD_FILE = Pattern.compile("^(?:[A-Z]+-)?(\\d{4})-.*\\.md$");
    private static final Pattern SUCCESSOR = Pattern.compile("ADR-(\\d{4})");
    private static final String REGISTRY = "docs/architecture/adr";
    private static final String ARCHIVE = "docs/architecture/adr/archive";
    private static final String SUPERSEDED = "Remplac\u00e9e";
    private static final String REJECTED = "Rejet\u00e9e";
    private static final List<String> RETIRED_VERDICTS = List.of(SUPERSEDED, REJECTED);

    @Test
    void everyDecisionRecordHasExactlyOneIndexRowAndEveryRowHasItsRecord() throws IOException {
        Path root = RepositoryRoot.locate();
        assertBijection(records(root, REGISTRY), indexRows(root));
    }

    @Test
    void theIndexAndTheRecordAgreeOnTheVerdictWhileTheRecordStaysFreeToQualifyIt() throws IOException {
        Path root = RepositoryRoot.locate();
        assertVerdictsAgree(records(root, REGISTRY), indexRows(root), declaredVerdicts(root));
    }

    @Test
    void noDecisionNumberIsAssignedTwiceAcrossTheRegistryAndTheArchive() throws IOException {
        Path root = RepositoryRoot.locate();
        List<String> fileNames = new ArrayList<>(recordFileNames(root, REGISTRY));
        recordFileNames(root, ARCHIVE).forEach(name -> fileNames.add("archive/" + name));
        assertNumbersAreUnique(fileNames);
    }

    @Test
    void theRegistryHoldsDecisionsInForceAndTheArchiveHoldsRetiredOnes() throws IOException {
        Path root = RepositoryRoot.locate();
        List<String> verdicts = declaredVerdicts(root);
        assertTrue(verdicts.containsAll(RETIRED_VERDICTS),
                () -> "the ADR index must declare the retired verdicts " + RETIRED_VERDICTS + ": " + verdicts);
        assertRetirement(records(root, REGISTRY), records(root, ARCHIVE), verdicts);
    }

    @Test
    void everyStatusUsesAVerdictTheIndexItselfDeclares() throws IOException {
        Path root = RepositoryRoot.locate();
        List<String> verdicts = declaredVerdicts(root);
        assertEquals(5, verdicts.size(), () -> "the ADR index must declare its status vocabulary: " + verdicts);
        assertClosedVocabulary(records(root, REGISTRY), indexRows(root), verdicts);
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

        AssertionError flattened = assertThrows(AssertionError.class,
                () -> assertVerdictsAgree(Map.of("0001", "Acceptee avec contraintes -- M0"), rows, verdicts),
                "Acceptee avec contraintes is its own verdict, not a qualification of Acceptee");
        assertTrue(flattened.getMessage().contains("\"Acceptee avec contraintes -- M0\" in the record"),
                () -> "the concordance must quote the record status it refuses to flatten: " + flattened.getMessage());

        AssertionError vocabulary = assertThrows(AssertionError.class,
                () -> assertClosedVocabulary(Map.of("0001", "Validee -- M0"), rows, verdicts));
        assertTrue(vocabulary.getMessage().contains("Validee"),
                () -> "the vocabulary check must quote the invented verdict: " + vocabulary.getMessage());

        AssertionError duplicate = assertThrows(AssertionError.class,
                () -> assertNumbersAreUnique(List.of("0095-first.md", "0095-second.md")));
        assertTrue(duplicate.getMessage().contains("0095"),
                () -> "the uniqueness check must name the reused number: " + duplicate.getMessage());
    }

    /** A record in force carries its domain prefix, an archived one keeps its old name: one number space for both. */
    @Test
    void aPrefixedRecordAndAnArchivedRecordShareOneNumberSpace() {
        Map<String, String> records = Map.of("0001", "Acceptee -- M0");
        assertBijection(records, Map.of("0001", new IndexRow("ARCH-0001-a.md", "Acceptee -- M0")));
        assertNumbersAreUnique(List.of("ARCH-0001-a.md", "archive/0002-b.md"));

        AssertionError reused = assertThrows(AssertionError.class,
                () -> assertNumbersAreUnique(List.of("ARCH-0095-first.md", "archive/0095-second.md")));
        assertTrue(reused.getMessage().contains("0095"),
                () -> "the uniqueness check must name a number reused across namings: " + reused.getMessage());

        AssertionError crossLinked = assertThrows(AssertionError.class,
                () -> assertBijection(records, Map.of("0001", new IndexRow("ARCH-0002-b.md", "Acceptee -- M0"))));
        assertTrue(crossLinked.getMessage().contains("ARCH-0002-b.md"),
                () -> "a prefixed row must link to its own record: " + crossLinked.getMessage());
    }

    @Test
    void theArchivePropertyRefusesARetiredRecordInForceAndARecordInForceInTheArchive() {
        List<String> verdicts = List.of("Propos\u00e9e", "Accept\u00e9e", "Accept\u00e9e avec contraintes", SUPERSEDED,
                REJECTED);
        Map<String, String> registry = Map.of("0002", "Accept\u00e9e -- M1");
        Map<String, String> archive = Map.of("0001", SUPERSEDED + " par ADR-0002", "0003", REJECTED);
        assertRetirement(registry, archive, verdicts);

        AssertionError retiredInForce = assertThrows(AssertionError.class,
                () -> assertRetirement(Map.of("0002", SUPERSEDED + " par ADR-0004"), Map.of(), verdicts));
        assertTrue(retiredInForce.getMessage().contains("ADR-0002") && retiredInForce.getMessage().contains("archive"),
                () -> "a retired record left in the registry must be named: " + retiredInForce.getMessage());

        AssertionError inForceArchived = assertThrows(AssertionError.class,
                () -> assertRetirement(registry, Map.of("0001", "Accept\u00e9e -- M0"), verdicts));
        assertTrue(inForceArchived.getMessage().contains("ADR-0001"),
                () -> "a record in force left in the archive must be named: " + inForceArchived.getMessage());

        AssertionError orphanedSupersession = assertThrows(AssertionError.class,
                () -> assertRetirement(registry, Map.of("0001", SUPERSEDED), verdicts));
        assertTrue(orphanedSupersession.getMessage().contains("ADR-0001"),
                () -> "a superseded record must name its successor: " + orphanedSupersession.getMessage());

        AssertionError missingSuccessor = assertThrows(AssertionError.class,
                () -> assertRetirement(registry, Map.of("0001", SUPERSEDED + " par ADR-0009"), verdicts));
        assertTrue(missingSuccessor.getMessage().contains("ADR-0009"),
                () -> "a successor that does not exist must be named: " + missingSuccessor.getMessage());
    }

    private static void assertRetirement(Map<String, String> registry, Map<String, String> archive,
            List<String> verdicts) {
        for (var record : new TreeMap<>(registry).entrySet()) {
            String verdict = verdictOf(record.getValue(), verdicts);
            assertFalse(RETIRED_VERDICTS.contains(verdict),
                    () -> "ADR-" + record.getKey() + " is " + verdict + " but still in the registry; move it to "
                            + ARCHIVE + " and remove its index row");
        }
        var known = new TreeSet<>(registry.keySet());
        known.addAll(archive.keySet());
        for (var record : new TreeMap<>(archive).entrySet()) {
            String verdict = verdictOf(record.getValue(), verdicts);
            assertTrue(RETIRED_VERDICTS.contains(verdict),
                    () -> "ADR-" + record.getKey() + " is in " + ARCHIVE + " but reads \"" + record.getValue()
                            + "\"; only a decision " + RETIRED_VERDICTS + " is archived");
            if (SUPERSEDED.equals(verdict)) {
                Matcher successor = SUCCESSOR.matcher(record.getValue());
                assertTrue(successor.find(), () -> "ADR-" + record.getKey()
                        + " is superseded but its status names no successor: \"" + record.getValue() + "\"");
                String number = successor.group(1);
                assertTrue(known.contains(number), () -> "ADR-" + record.getKey() + " is superseded by ADR-"
                        + number + ", which is neither in the registry nor in the archive");
            }
        }
    }

    private static void assertBijection(Map<String, String> records, Map<String, IndexRow> rows) {
        var unindexed = new TreeSet<>(records.keySet());
        unindexed.removeAll(rows.keySet());
        assertTrue(unindexed.isEmpty(),
                () -> "every decision record needs an index row in docs/architecture/adr/README.md; missing: " + unindexed);
        var orphaned = new TreeSet<>(rows.keySet());
        orphaned.removeAll(records.keySet());
        assertTrue(orphaned.isEmpty(),
                () -> "every index row must point at an existing decision record; dangling: " + orphaned);
        for (var row : new TreeMap<>(rows).entrySet()) {
            String linked = row.getValue().file();
            assertEquals(row.getKey(), numberOf(linked),
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
            Matcher matcher = RECORD_FILE.matcher(name.substring(name.lastIndexOf('/') + 1));
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
        String index = Files.readString(root.resolve("docs/architecture/adr/README.md")).replace("\r\n", "\n");
        int legend = index.indexOf("## Statuts");
        assertTrue(legend >= 0, "docs/architecture/adr/README.md must declare its status vocabulary under \"## Statuts\"");
        int next = index.indexOf("\n#", legend + 1);
        Matcher matcher = DECLARED_VERDICT.matcher(index.substring(legend, next < 0 ? index.length() : next));
        List<String> verdicts = new ArrayList<>();
        while (matcher.find()) {
            verdicts.add(matcher.group(1).trim());
        }
        return List.copyOf(verdicts);
    }

    private static Map<String, IndexRow> indexRows(Path root) throws IOException {
        String index = Files.readString(root.resolve("docs/architecture/adr/README.md")).replace("\r\n", "\n");
        Matcher matcher = INDEX_ROW.matcher(index);
        Map<String, IndexRow> rows = new LinkedHashMap<>();
        while (matcher.find()) {
            String number = matcher.group(1);
            IndexRow replaced = rows.put(number, new IndexRow(matcher.group(2).trim(), matcher.group(4).trim()));
            assertTrue(replaced == null, () -> "ADR-" + number + " appears twice in the index");
        }
        assertFalse(rows.isEmpty(), "docs/architecture/adr/README.md must index the decision records");
        return rows;
    }

    private static Map<String, String> records(Path root, String directory) throws IOException {
        Map<String, String> records = new LinkedHashMap<>();
        for (String name : recordFileNames(root, directory)) {
            String text = Files.readString(root.resolve(directory).resolve(name)).replace("\r\n", "\n");
            Matcher matcher = RECORD_STATUS.matcher(text);
            assertTrue(matcher.find(), () -> name + " must carry a bold \"Statut : **...**\" line");
            records.put(numberOf(name), matcher.group(1).trim());
        }
        return records;
    }

    /** A missing directory fails rather than reading as empty, so the archive cannot vanish unnoticed. */
    private static List<String> recordFileNames(Path root, String directory) throws IOException {
        Path location = root.resolve(directory);
        assertTrue(Files.isDirectory(location), () -> directory + " must exist");
        try (var files = Files.list(location)) {
            return files.map(path -> path.getFileName().toString())
                    .filter(name -> RECORD_FILE.matcher(name).matches())
                    .sorted()
                    .toList();
        }
    }

    private static String numberOf(String fileName) {
        Matcher matcher = RECORD_FILE.matcher(fileName);
        return matcher.matches() ? matcher.group(1) : fileName;
    }

    private record IndexRow(String file, String status) {}
}
