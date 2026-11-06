package com.morpheus.architecture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
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
 * Every list of the gate module that names classes of a production module says what kind of list it is.
 *
 * <p>A role is a package: in {@code morpheus-api} the remote server, the local server, the transport plumbing, the
 * services and each transport group of routers; in {@code morpheus-store-sqlite} the adapter and its runtime; in
 * {@code morpheus-cli} and {@code morpheus-mcp} the capability classes. A rule over a role selects its classes by the
 * package they reside in, never by a list of names. A list of class names that remains is one of two other kinds, and
 * this test records which:</p>
 *
 * <ul>
 *   <li>a <strong>debt counter</strong> -- what a set of classes does today and is expected to stop doing, exact in
 *       both directions and shrinking only, under the decision record that says so. Both promises are checked: the
 *       accepted record it declares names it by {@code TestClass.FIELD}, and its size equals its key in
 *       {@code config/debt-counter-ratchets.properties}, a key that is only ever lowered;</li>
 *   <li>a <strong>table of facts</strong> -- exactly the classes a stated fact is true of, each entry with its reason
 *       or the whole table with one.</li>
 * </ul>
 *
 * <p>A class of any module with sources under {@code src/main/java} counts, so a list of the domain, the application,
 * a provider, a store or an adapter is held to the same declaration. A new list fails here until it is given a kind; a
 * list that disappears fails here too, so the declaration never outlives what it declares. A list that would be a role
 * is not declared: the classes it names belong in a package instead (ADR-0155).</p>
 */
class ProductionClassListsArchitectureTest {

    /**
     * A debt counter carries the identifier of the decision record that names it; a table of facts carries one only
     * when a decision record states it. The enumeration root, when present, is the package against which the names the
     * record writes between its delimiters are resolved: a list its record enumerates is compared to that enumeration,
     * a list its record only references by {@code TestClass.FIELD} is not.
     */
    private record Kind(boolean debtCounter, Optional<String> decision, Optional<String> enumerationRoot) {
        static final Kind TABLE_OF_FACTS = new Kind(false, Optional.empty(), Optional.empty());

        static Kind debtCounterUnder(String decisionRecord) {
            return new Kind(true, Optional.of(decisionRecord), Optional.empty());
        }

        static Kind debtCounterEnumeratedBy(String decisionRecord, String packageRoot) {
            return new Kind(true, Optional.of(decisionRecord), Optional.of(packageRoot));
        }

        static Kind tableOfFactsEnumeratedBy(String decisionRecord, String packageRoot) {
            return new Kind(false, Optional.of(decisionRecord), Optional.of(packageRoot));
        }
    }

    private static final String APPLICATION_ROOT = "com.morpheus.application";
    private static final String SIMPLE_NAMES = "";
    private static final String ENUMERATION_END = "<!-- fin de liste -->";
    private static final Pattern CODE_SPAN = Pattern.compile("`([^`]+)`");
    private static final Pattern TABLE_SEPARATOR = Pattern.compile("^\\|[\\s|:-]+\\|$");

    /** The closed vocabulary of the family column of an enumerated table, each word mapped to the gate's family name. */
    private static final Map<String, String> FAMILY_WORDS = Map.of(
            "fichier", "file access",
            "environnement", "process environment",
            "flux standard", "standard streams",
            "horloge murale", "wall clock",
            "réflexion", "reflection and class loading");

    private static final String DECISION_RECORDS = "docs/architecture/adr";
    private static final String DEBT_COUNTER_RATCHETS = "config/debt-counter-ratchets.properties";
    private static final String ACCEPTED = "Statut : **Acceptée**";

    /** Each remaining list, by {@code TestClass.FIELD}, with its kind. */
    private static final Map<String, Kind> KINDS = Map.ofEntries(
            Map.entry("AdapterCompositionRootArchitectureTest.COMPOSITION_ROOTS", Kind.debtCounterUnder("ARCH-0162")),
            Map.entry("ApiEntryTypesArchitectureTest.ENTRY_TYPES", Kind.tableOfFactsEnumeratedBy("ARCH-0155", SIMPLE_NAMES)),
            Map.entry("AppendOnlyJournalReadsArchitectureTest.JOURNAL_PAGES", Kind.TABLE_OF_FACTS),
            Map.entry("AppendOnlyJournalReadsArchitectureTest.JOURNAL_PORTS", Kind.TABLE_OF_FACTS),
            Map.entry("AppendOnlyJournalReadsArchitectureTest.JOURNAL_RECORDS", Kind.TABLE_OF_FACTS),
            Map.entry("ApplicationPackageCyclesArchitectureTest.QUERY_DSL_AND_SAVED_EDGES", Kind.debtCounterUnder("ARCH-0162")),
            Map.entry("ApplicationTechnologyBoundaryArchitectureTest.CONFINED_FAMILIES",
                    Kind.debtCounterEnumeratedBy("ARCH-0150", APPLICATION_ROOT)),
            Map.entry("ApplicationTechnologyBoundaryArchitectureTest.REFLECTION_AND_LOADING",
                    Kind.debtCounterEnumeratedBy("ARCH-0150", APPLICATION_ROOT)),
            Map.entry("CliArgumentBaseArchitectureTest.OTHER_PURPOSES", Kind.TABLE_OF_FACTS),
            Map.entry("CliOptionValueHasOneBlankDefinitionTest.OTHER_QUESTIONS", Kind.TABLE_OF_FACTS),
            Map.entry("CliTechnologyBoundaryArchitectureTest.CONFINED_FAMILIES", Kind.TABLE_OF_FACTS),
            Map.entry("CurrentRequirementReaderArchitectureTest.FULL_HISTORY_READERS", Kind.TABLE_OF_FACTS),
            Map.entry("CurrentRequirementReaderArchitectureTest.WHOLE_CURRENT_READERS", Kind.TABLE_OF_FACTS),
            Map.entry("DispatchersHoldNoUseCaseArchitectureTest.CLI_ALLOWANCE", Kind.TABLE_OF_FACTS),
            Map.entry("DispatchersHoldNoUseCaseArchitectureTest.MCP_ALLOWANCE", Kind.TABLE_OF_FACTS),
            Map.entry("ErrorClassificationByTypeArchitectureTest.SIZE_CONSUMERS", Kind.TABLE_OF_FACTS),
            Map.entry("HttpFailureDisclosureArchitectureTest.AUTHORED_FOR_THE_CALLER", Kind.TABLE_OF_FACTS),
            Map.entry("HttpPathReadingArchitectureTest.FORM_DECODERS", Kind.TABLE_OF_FACTS),
            Map.entry("HttpPathReadingArchitectureTest.OTHER_GET_PATH_CALLERS", Kind.TABLE_OF_FACTS),
            Map.entry("HttpPathReadingArchitectureTest.RAW_PATH_READERS", Kind.TABLE_OF_FACTS),
            Map.entry("HttpPathReadingArchitectureTest.SLASH_SPLITTERS", Kind.TABLE_OF_FACTS),
            Map.entry("IntegrationStatusProjectionArchitectureTest.STATUS_SOURCES", Kind.TABLE_OF_FACTS),
            Map.entry("McpEntryTypesArchitectureTest.ENTRY_TYPES", Kind.TABLE_OF_FACTS),
            Map.entry("McpPeerSettingsResolutionArchitectureTest.PEERS", Kind.TABLE_OF_FACTS),
            Map.entry("ModulePackageCorrespondenceTest.ROOTS", Kind.TABLE_OF_FACTS),
            Map.entry("PeerLauncherArchitectureTest.SETTINGS", Kind.TABLE_OF_FACTS),
            Map.entry("ProviderProjectRootArchitectureTest.EXPECTED_ROOTS", Kind.TABLE_OF_FACTS),
            Map.entry("PublicHttpRouteConvergenceTest.REMOTE_ONLY", Kind.TABLE_OF_FACTS),
            Map.entry("PublicPayloadConvergenceTest.CLI_ABSENT_AS_NULL_KEYS", Kind.TABLE_OF_FACTS),
            Map.entry("PublicPayloadConvergenceTest.CLI_OMITTED_KEYS", Kind.TABLE_OF_FACTS),
            Map.entry("PublishedEnumsMatchJavaEnumsTest.EXEMPT", Kind.TABLE_OF_FACTS),
            Map.entry("PublishedEnumsMatchJavaEnumsTest.TABLE", Kind.TABLE_OF_FACTS),
            Map.entry("ReflectionAndClassLoadingBoundaryArchitectureTest.LOADING", Kind.TABLE_OF_FACTS),
            Map.entry("ReflectionAndClassLoadingBoundaryArchitectureTest.REFLECTION", Kind.TABLE_OF_FACTS),
            Map.entry("RefusalPathTextArchitectureTest.FORBIDDEN", Kind.TABLE_OF_FACTS),
            Map.entry("RefusalPathTextArchitectureTest.KNOWN_REFUSAL_BUILDERS", Kind.TABLE_OF_FACTS),
            Map.entry("SharedDomainProjectionArchitectureTest.ADAPTER_SHAPES_DIFFERING_FROM_THE_SHARED_ONE", Kind.TABLE_OF_FACTS),
            Map.entry("SharedDomainProjectionArchitectureTest.SHARED_TYPES", Kind.TABLE_OF_FACTS),
            Map.entry("ShippedRequirementStoresPageThemselvesArchitectureTest.SHIPPED_STORES", Kind.TABLE_OF_FACTS),
            Map.entry("SilentCatchArchitectureTest.EXPLAINED", Kind.TABLE_OF_FACTS),
            Map.entry("SqliteConnectionScopeUseArchitectureTest.ONE_STORE_PER_OPERATION", Kind.TABLE_OF_FACTS),
            Map.entry("SqliteConnectionScopeUseArchitectureTest.SCOPE_OWNERS", Kind.TABLE_OF_FACTS),
            Map.entry("SqliteStorePackagesArchitectureTest.ADAPTER_COLLABORATORS", Kind.TABLE_OF_FACTS),
            Map.entry("SqliteStorePackagesArchitectureTest.RUNTIME_ENTRY_POINTS", Kind.debtCounterUnder("ARCH-0158")),
            Map.entry("SqliteStorePackagesArchitectureTest.RUNTIME_PUBLIC_TYPES", Kind.debtCounterUnder("ARCH-0158")),
            Map.entry("StaticOperationalRuntimeUsersArchitectureTest.MAIN_USERS", Kind.TABLE_OF_FACTS),
            Map.entry("StaticOperationalRuntimeUsersArchitectureTest.PROCESS_RUNTIME_TAKERS", Kind.TABLE_OF_FACTS),
            Map.entry("TextDigestOwnershipArchitectureTest.DIGEST_SITES", Kind.TABLE_OF_FACTS),
            Map.entry("TextDigestOwnershipArchitectureTest.HELPER_OWNERS", Kind.TABLE_OF_FACTS));

    private static final Pattern LIST_DECLARATION = Pattern.compile(
            "static final (?:java\\.util\\.)?(?:Set|List|Map)<[^=;]*?>\\s+([A-Z][A-Z0-9_]*)\\s*=\\s*"
                    + "(?:new [\\w.<>]+\\(\\s*)?(?:java\\.util\\.)?(?:Set|List|Map)\\.(?:of|copyOf|ofEntries)\\(");
    private static final Pattern STRING_LITERAL = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern NAME_PIECE = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Pattern CLASS_LITERAL = Pattern.compile("\\b([A-Z][A-Za-z0-9_]*)\\.class\\b");

    @Test
    void everyListOfProductionClassesIsADebtCounterOrATableOfFacts() throws IOException {
        Set<String> productionClasses = productionClassNames();

        Map<String, Set<String>> found = new TreeMap<>();
        Path tests = RepositoryRoot.locate().resolve("morpheus-architecture-tests/src/test/java");
        try (Stream<Path> tree = Files.walk(tests)) {
            for (Path source : tree.filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
                String testClass = source.getFileName().toString().replace(".java", "");
                if (testClass.equals(getClass().getSimpleName())) {
                    continue;
                }
                listsNamingClasses(Files.readString(source), productionClasses)
                        .forEach((field, names) -> found.put(testClass + "." + field, names));
            }
        }

        assertEquals(new TreeSet<>(KINDS.keySet()), new TreeSet<>(found.keySet()), () -> "every list of the gate "
                + "module that names classes of a production module is declared in KINDS as a debt counter or a "
                + "table of facts, and KINDS names no list that is gone. A list that is a role belongs in a package. "
                + "Found: " + found);
    }

    @Test
    void everyDebtCounterIsNamedByTheAcceptedDecisionRecordItDeclares() throws IOException {
        Path records = RepositoryRoot.locate().resolve(DECISION_RECORDS);
        List<String> refusals = new ArrayList<>();
        for (Map.Entry<String, String> counter : debtCounters().entrySet()) {
            Optional<String> text = acceptedRecord(records, counter.getKey(), counter.getValue(), refusals);
            if (text.isPresent() && !text.get().contains(counter.getKey())) {
                refusals.add(counter.getKey() + " declares " + counter.getValue() + ", which does not name it");
            }
        }
        assertEquals(List.of(), refusals, "a debt counter shrinks under the decision record that says so: the record "
                + "it declares is in " + DECISION_RECORDS + ", accepted, and names it by TestClass.FIELD");
    }

    /**
     * A decision record that enumerates a list, rather than naming it by {@code TestClass.FIELD} only, holds the same
     * names as the constant of the gate: a class added to one and not to the other fails here, whichever side moved.
     */
    @Test
    void everyListADecisionRecordEnumeratesEqualsItsEnumeration() throws IOException, ReflectiveOperationException {
        Path records = RepositoryRoot.locate().resolve(DECISION_RECORDS);
        Map<String, Class<?>> testClasses = testClassesBySimpleName();
        List<String> refusals = new ArrayList<>();
        int compared = 0;
        for (Map.Entry<String, Kind> list : new TreeMap<>(KINDS).entrySet()) {
            Optional<String> root = list.getValue().enumerationRoot();
            if (root.isEmpty()) {
                continue;
            }
            compared++;
            String decision = list.getValue().decision().orElseThrow();
            Optional<String> text = acceptedRecord(records, list.getKey(), decision, refusals);
            if (text.isPresent()) {
                refusals.addAll(enumerationRefusals(list.getKey(), decision, text.get(), root.get(),
                        namesByFamily(listValue(testClasses, list.getKey()))));
            }
        }
        assertTrue(compared > 0, "KINDS declares no list that a decision record enumerates");
        assertEquals(List.of(), refusals, "a list a decision record enumerates is written in that record between "
                + "<!-- liste TestClass.FIELD --> and " + ENUMERATION_END + ", with the same names as the constant");
    }

    @Test
    void aNameAddedToTheGateAndNotToTheRecordIsNamedWithTheRecordAndTheList() {
        List<String> refusals = enumerationRefusals("Gate.NAMES", "ARCH-9999",
                block("Gate.NAMES", "`alpha.First`"), "com.example", Map.of("", Set.of("com.example.alpha.First",
                        "com.example.beta.Second")));

        assertEquals(1, refusals.size(), refusals::toString);
        assertTrue(refusals.get(0).contains("Gate.NAMES") && refusals.get(0).contains("ARCH-9999")
                && refusals.get(0).contains("com.example.beta.Second"), refusals.get(0));
    }

    @Test
    void aNameTheRecordWritesAndTheListNoLongerHoldsIsNamed() {
        List<String> refusals = enumerationRefusals("Gate.NAMES", "ARCH-9999",
                block("Gate.NAMES", "`alpha.First`, `product.Removed`"), "com.example",
                Map.of("", Set.of("com.example.alpha.First")));

        assertEquals(1, refusals.size(), refusals::toString);
        assertTrue(refusals.get(0).contains("com.example.product.Removed"), refusals.get(0));
    }

    @Test
    void theSameSimpleNameInAnotherPackageIsNamedOnBothSides() {
        List<String> refusals = enumerationRefusals("Gate.FAMILIES", "ARCH-9999", block("Gate.FAMILIES", """
                | Classe | Familles | Sort |
                |---|---|---|
                | `security.Hardener` | fichier, environnement | reste |
                """), "com.example", Map.of("file access", Set.of("com.example.files.Hardener"),
                "process environment", Set.of("com.example.files.Hardener")));

        String all = String.join("\n", refusals);
        assertTrue(all.contains("com.example.security.Hardener") && all.contains("com.example.files.Hardener")
                && all.contains("file access") && all.contains("process environment"), all);
    }

    @Test
    void anEnumerationTheBuildCannotReadIsRefusedBySayingWhatItCouldNotRead() {
        Map<String, Set<String>> families = Map.of("file access", Set.of("com.example.files.Reader"));

        List<String> absent = enumerationRefusals("Gate.FAMILIES", "ARCH-9999", "# A record without a block\n",
                "com.example", families);
        List<String> empty = enumerationRefusals("Gate.FAMILIES", "ARCH-9999", block("Gate.FAMILIES", ""),
                "com.example", families);
        List<String> unknownFamily = enumerationRefusals("Gate.FAMILIES", "ARCH-9999", block("Gate.FAMILIES",
                "| `files.Reader` | réseau | reste |"), "com.example", families);
        List<String> noSpan = enumerationRefusals("Gate.NAMES", "ARCH-9999", block("Gate.NAMES",
                "`alpha.First`\nalpha.Second, written without a span"), "com.example",
                Map.of("", Set.of("com.example.alpha.First")));

        assertTrue(absent.size() == 1 && absent.get(0).contains("no block"), absent::toString);
        assertTrue(empty.size() == 1 && empty.get(0).contains("names no class"), empty::toString);
        assertTrue(unknownFamily.size() == 1 && unknownFamily.get(0).contains("réseau")
                && unknownFamily.get(0).contains("outside the vocabulary"), unknownFamily::toString);
        assertTrue(noSpan.size() == 1 && noSpan.get(0).contains("alpha.Second"), noSpan::toString);
    }

    @Test
    void aTableOfFactsDeclaringARecordThatDoesNotEnumerateItIsRefusedByNameAndRecord(@TempDir Path registry)
            throws IOException {
        Files.writeString(registry.resolve("ARCH-9001-proposed.md"), "- Statut : **Proposée**\n"
                + block("Facts.TYPES", "`Alpha`"));
        Files.writeString(registry.resolve("ARCH-9002-silent.md"), "- " + ACCEPTED + "\n\nNo enumeration here.\n");
        List<String> refusals = new ArrayList<>();

        Optional<String> missing = acceptedRecord(registry, "Facts.TYPES", "ARCH-9000", refusals);
        Optional<String> proposed = acceptedRecord(registry, "Facts.TYPES", "ARCH-9001", refusals);
        Optional<String> silent = acceptedRecord(registry, "Facts.TYPES", "ARCH-9002", refusals);
        silent.ifPresent(text -> refusals.addAll(enumerationRefusals("Facts.TYPES", "ARCH-9002", text, SIMPLE_NAMES,
                Map.of("", Set.of("Alpha")))));

        assertTrue(missing.isEmpty() && proposed.isEmpty() && silent.isPresent(), refusals::toString);
        assertEquals(3, refusals.size(), refusals::toString);
        for (String decision : List.of("ARCH-9000", "ARCH-9001", "ARCH-9002")) {
            assertTrue(refusals.stream().anyMatch(refusal -> refusal.contains("Facts.TYPES")
                    && refusal.contains(decision)), decision + " not named in " + refusals);
        }
    }

    @Test
    void aListItsRecordOnlyReferencesIsNotAskedForAnEnumeration() throws IOException {
        String list = "AdapterCompositionRootArchitectureTest.COMPOSITION_ROOTS";
        Kind kind = KINDS.get(list);
        List<String> refusals = new ArrayList<>();

        Optional<String> text = acceptedRecord(RepositoryRoot.locate().resolve(DECISION_RECORDS), list,
                kind.decision().orElseThrow(), refusals);

        assertTrue(kind.enumerationRoot().isEmpty(), "ARCH-0156 names the composition roots by reference only");
        assertTrue(text.isPresent() && text.get().contains(list) && !text.get().contains("<!-- liste " + list),
                () -> "ARCH-0156 is accepted and references the list without enumerating it: " + refusals);
    }

    private static String block(String list, String content) {
        return "Some prose.\n\n<!-- liste " + list + " -->\n" + content + "\n" + ENUMERATION_END + "\n\nMore prose.\n";
    }

    /** The text of the accepted record a list declares, or empty with the reason added to {@code refusals}. */
    private static Optional<String> acceptedRecord(Path records, String list, String decision, List<String> refusals)
            throws IOException {
        List<Path> matching;
        try (Stream<Path> files = Files.list(records)) {
            matching = files.filter(file -> file.getFileName().toString().startsWith(decision + "-"))
                    .filter(file -> file.getFileName().toString().endsWith(".md"))
                    .sorted()
                    .toList();
        }
        if (matching.size() != 1) {
            refusals.add(list + " declares " + decision + ", found in the registry as " + matching);
            return Optional.empty();
        }
        String text = Files.readString(matching.get(0));
        if (!text.contains(ACCEPTED)) {
            refusals.add(list + " declares " + decision + ", which is not accepted");
            return Optional.empty();
        }
        return Optional.of(text);
    }

    /**
     * What keeps the enumeration of {@code list} in the record's text from holding exactly the names of the constant.
     * A table enumerates a list grouped by family, one class per row with its families in the second column; any other
     * block enumerates every code span it contains. An enumeration that cannot be read is refused before any comparison,
     * so that a misread never concludes that the two sides agree.
     */
    private static List<String> enumerationRefusals(String list, String decision, String text, String root,
                                                    Map<String, Set<String>> constant) {
        String where = list + " (" + decision + "): ";
        String opening = "<!-- liste " + list + " -->";
        int start = text.indexOf(opening);
        if (start < 0 || text.indexOf(opening, start + 1) >= 0) {
            return List.of(where + (start < 0 ? "the record has no block " : "the record has more than one block ")
                    + opening + " ... " + ENUMERATION_END);
        }
        int end = text.indexOf(ENUMERATION_END, start);
        if (end < 0) {
            return List.of(where + "the block " + opening + " is not closed by " + ENUMERATION_END);
        }
        List<String> lines = text.substring(start + opening.length(), end).lines().map(String::trim)
                .filter(line -> !line.isEmpty()).toList();
        boolean grouped = !constant.keySet().equals(Set.of(""));
        List<String> unreadable = new ArrayList<>();
        Map<String, Set<String>> written = new TreeMap<>();
        for (int index = 0; index < lines.size(); index++) {
            String line = lines.get(index);
            boolean header = index + 1 < lines.size() && TABLE_SEPARATOR.matcher(lines.get(index + 1)).matches();
            if (header || TABLE_SEPARATOR.matcher(line).matches()) {
                continue;
            }
            if (!grouped) {
                List<String> spans = codeSpans(line);
                if (spans.isEmpty()) {
                    unreadable.add(where + "a line of the block names no class in a code span: " + line);
                }
                spans.forEach(name -> written.computeIfAbsent("", family -> new TreeSet<>()).add(qualified(root, name)));
                continue;
            }
            String[] cells = line.replaceAll("^\\|", "").replaceAll("\\|$", "").split("\\|");
            List<String> names = codeSpans(cells[0]);
            if (cells.length < 2 || names.isEmpty()) {
                unreadable.add(where + "a row of the table has no class in a code span and its families: " + line);
                continue;
            }
            for (String word : cells[1].split(",")) {
                String family = FAMILY_WORDS.get(word.trim());
                if (family == null) {
                    unreadable.add(where + "the family '" + word.trim() + "' is outside the vocabulary "
                            + new TreeSet<>(FAMILY_WORDS.keySet()) + ": " + line);
                    continue;
                }
                names.forEach(name -> written.computeIfAbsent(family, key -> new TreeSet<>()).add(qualified(root, name)));
            }
        }
        if (!unreadable.isEmpty()) {
            return unreadable;
        }
        if (written.isEmpty()) {
            return List.of(where + "the block " + opening + " names no class");
        }
        List<String> refusals = new ArrayList<>();
        Set<String> families = new TreeSet<>(constant.keySet());
        families.addAll(written.keySet());
        for (String family : families) {
            String group = family.isEmpty() ? "" : " for " + family;
            Set<String> missing = new TreeSet<>(constant.getOrDefault(family, Set.of()));
            missing.removeAll(written.getOrDefault(family, Set.of()));
            Set<String> extra = new TreeSet<>(written.getOrDefault(family, Set.of()));
            extra.removeAll(constant.getOrDefault(family, Set.of()));
            if (!missing.isEmpty()) {
                refusals.add(where + "the list holds" + group + " " + missing + ", which the record does not write");
            }
            if (!extra.isEmpty()) {
                refusals.add(where + "the record writes" + group + " " + extra + ", which the list does not hold");
            }
        }
        return refusals;
    }

    private static List<String> codeSpans(String text) {
        List<String> spans = new ArrayList<>();
        Matcher span = CODE_SPAN.matcher(text);
        while (span.find()) {
            spans.add(span.group(1).trim());
        }
        return spans;
    }

    private static String qualified(String root, String name) {
        return root.isEmpty() ? name : root + "." + name;
    }

    /**
     * The names of a list by family: a set or a list is one group under the empty family; a map of collections groups
     * its values by the name of their key, a string or a record with a {@code name} component; any other map is one
     * group of its keys.
     */
    private static Map<String, Set<String>> namesByFamily(Object list) throws ReflectiveOperationException {
        Map<String, Set<String>> families = new TreeMap<>();
        if (list instanceof Collection<?> collection) {
            families.put("", strings(collection));
        } else if (list instanceof Map<?, ?> map && !map.isEmpty()
                && map.values().stream().allMatch(Collection.class::isInstance)) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                families.put(familyName(entry.getKey()), strings((Collection<?>) entry.getValue()));
            }
        } else if (list instanceof Map<?, ?> map) {
            families.put("", strings(map.keySet()));
        } else {
            throw new IllegalArgumentException("an enumerated list is a Set, a List or a Map, not a "
                    + list.getClass().getName());
        }
        return families;
    }

    private static String familyName(Object key) throws ReflectiveOperationException {
        if (key instanceof String name) {
            return name;
        }
        if (key.getClass().isRecord()) {
            for (RecordComponent component : key.getClass().getRecordComponents()) {
                if (component.getName().equals("name")) {
                    Method accessor = component.getAccessor();
                    accessor.setAccessible(true);
                    return (String) accessor.invoke(key);
                }
            }
        }
        throw new IllegalArgumentException("the key of a family is a String or a record with a name, not a "
                + key.getClass().getName());
    }

    private static Set<String> strings(Collection<?> values) {
        Set<String> names = new TreeSet<>();
        values.forEach(value -> names.add(value instanceof Class<?> type ? type.getSimpleName() : value.toString()));
        return names;
    }

    private static Object listValue(Map<String, Class<?>> testClasses, String list) throws ReflectiveOperationException {
        int dot = list.indexOf('.');
        Field field = testClasses.get(list.substring(0, dot)).getDeclaredField(list.substring(dot + 1));
        field.setAccessible(true);
        return field.get(null);
    }

    @Test
    void everyDebtCounterHoldsExactlyTheSizeItsRatchetAllows() throws IOException, ReflectiveOperationException {
        Properties ratchets = new Properties();
        try (var reader = Files.newBufferedReader(RepositoryRoot.locate().resolve(DEBT_COUNTER_RATCHETS))) {
            ratchets.load(reader);
        }
        assertEquals(new TreeSet<>(debtCounters().keySet()), new TreeSet<>(ratchets.stringPropertyNames()),
                "every debt counter has one key in " + DEBT_COUNTER_RATCHETS + ", and no key outlives its counter");

        Map<String, Class<?>> testClasses = testClassesBySimpleName();
        List<String> grown = new ArrayList<>();
        List<String> shrunk = new ArrayList<>();
        for (String counter : debtCounters().keySet()) {
            int size = sizeOf(listValue(testClasses, counter));
            int ratchet = Integer.parseInt(ratchets.getProperty(counter).trim());
            if (size > ratchet) {
                grown.add(counter + " holds " + size + " for a ratchet of " + ratchet);
            } else if (size < ratchet) {
                shrunk.add(counter + " holds " + size + " for a ratchet of " + ratchet);
            }
        }
        assertEquals(List.of(), grown, "a debt counter grew: it only shrinks. Remove the new entry, or replace its "
                + "decision record by a consolidated one that names it before raising the ratchet");
        assertEquals(List.of(), shrunk, "a debt counter shrank: lower its key in " + DEBT_COUNTER_RATCHETS
                + " in the same change, so that it cannot grow back");
    }

    @Test
    void theSizeOfACounterIsItsElementsAndAMapOfCollectionsCountsTheElementsOfItsValues() {
        assertEquals(2, sizeOf(Set.of("Alpha", "Beta")));
        assertEquals(3, sizeOf(List.of("Alpha", "Beta", "Gamma")));
        assertEquals(2, sizeOf(Map.of("Alpha", "a reason", "Beta", "another reason")));
        assertEquals(3, sizeOf(Map.of("first family", Set.of("Alpha", "Beta"), "second family", Set.of("Alpha"))));
        IllegalArgumentException refusal = assertThrows(IllegalArgumentException.class, () -> sizeOf("Alpha"));
        assertTrue(refusal.getMessage().contains("java.lang.String"), refusal.getMessage());
    }

    @Test
    void theDetectionSeesANamedClassInAStringOrAClassLiteralAndIgnoresParenthesesInStrings() {
        String source = """
                class Sample {
                    private static final Set<String> ROLES = Set.of("com.morpheus.api.Alpha", "Beta.java#x");
                    private static final List<String> VERBS = List.of("call(", "SecureRandom");
                    private static final Map<String, String> REASONS = Map.of("Gamma", "a reason (with Alpha)");
                    private static final List<Class<?>> TYPES = List.of(Delta.class, String.class);
                    private static final java.util.List<String> QUALIFIED = java.util.List.of("Epsilon");
                }
                """;

        Map<String, Set<String>> lists = listsNamingClasses(source,
                Set.of("Alpha", "Beta", "Gamma", "Delta", "Epsilon"));

        assertEquals(Map.of("ROLES", Set.of("Alpha", "Beta"), "REASONS", Set.of("Alpha", "Gamma"),
                "TYPES", Set.of("Delta"), "QUALIFIED", Set.of("Epsilon")), lists);
    }

    private static Map<String, Set<String>> listsNamingClasses(String source, Set<String> classNames) {
        Map<String, Set<String>> lists = new TreeMap<>();
        Matcher declaration = LIST_DECLARATION.matcher(source);
        while (declaration.find()) {
            String initializer = source.substring(declaration.end(), closingParenthesis(source, declaration.end()));
            Set<String> named = new TreeSet<>();
            Matcher classLiteral = CLASS_LITERAL.matcher(initializer);
            while (classLiteral.find()) {
                if (classNames.contains(classLiteral.group(1))) {
                    named.add(classLiteral.group(1));
                }
            }
            Matcher literal = STRING_LITERAL.matcher(initializer);
            while (literal.find()) {
                Matcher piece = NAME_PIECE.matcher(literal.group(1));
                while (piece.find()) {
                    if (classNames.contains(piece.group())) {
                        named.add(piece.group());
                    }
                }
            }
            if (!named.isEmpty()) {
                lists.put(declaration.group(1), named);
            }
        }
        return lists;
    }

    private static Map<String, String> debtCounters() {
        Map<String, String> counters = new TreeMap<>();
        KINDS.forEach((list, kind) -> {
            if (kind.debtCounter()) {
                counters.put(list, kind.decision().orElseThrow());
            }
        });
        return counters;
    }

    /**
     * A set or a list counts its elements; a map counts its entries, or the elements of its values when they are all
     * collections, so that a counter of classes by family counts each class it names in each family.
     */
    private static int sizeOf(Object list) {
        if (list instanceof Collection<?> collection) {
            return collection.size();
        }
        if (list instanceof Map<?, ?> map) {
            if (!map.isEmpty() && map.values().stream().allMatch(Collection.class::isInstance)) {
                return map.values().stream().mapToInt(value -> ((Collection<?>) value).size()).sum();
            }
            return map.size();
        }
        throw new IllegalArgumentException("a debt counter is a Set, a List or a Map, not a "
                + list.getClass().getName());
    }

    private static Map<String, Class<?>> testClassesBySimpleName() throws IOException, ClassNotFoundException {
        Path tests = RepositoryRoot.locate().resolve("morpheus-architecture-tests/src/test/java");
        List<Path> sources;
        try (Stream<Path> tree = Files.walk(tests)) {
            sources = tree.filter(path -> path.toString().endsWith(".java")).sorted().toList();
        }
        Map<String, Class<?>> classes = new TreeMap<>();
        for (Path source : sources) {
            String binaryName = tests.relativize(source).toString().replace('\\', '.').replace('/', '.')
                    .replaceAll("\\.java$", "");
            classes.put(source.getFileName().toString().replace(".java", ""),
                    Class.forName(binaryName, false, ProductionClassListsArchitectureTest.class.getClassLoader()));
        }
        return classes;
    }

    /** The index of the parenthesis that closes the one opened just before {@code start}, string literals skipped. */
    private static int closingParenthesis(String source, int start) {
        int depth = 1;
        boolean inString = false;
        for (int index = start; index < source.length(); index++) {
            char c = source.charAt(index);
            if (inString) {
                if (c == '\\') {
                    index++;
                } else if (c == '"') {
                    inString = false;
                }
            } else if (c == '"') {
                inString = true;
            } else if (c == '(') {
                depth++;
            } else if (c == ')' && --depth == 0) {
                return index;
            }
        }
        throw new IllegalStateException("unclosed list initializer at " + start);
    }

    /**
     * The simple names of the classes of every reactor module with main sources, each module required to contribute:
     * a module whose sources moved would otherwise leave its lists undeclared in silence.
     */
    private static Set<String> productionClassNames() throws IOException {
        Set<String> names = new TreeSet<>();
        Path root = RepositoryRoot.locate();
        List<Path> modules;
        try (Stream<Path> children = Files.list(root)) {
            modules = children.filter(child -> Files.isDirectory(child.resolve("src/main/java"))).sorted().toList();
        }
        assertTrue(modules.size() > 10, () -> "too few modules with main sources seen: " + modules);
        for (Path module : modules) {
            Set<String> moduleNames = new TreeSet<>();
            try (Stream<Path> tree = Files.walk(module.resolve("src/main/java"))) {
                tree.filter(path -> path.toString().endsWith(".java"))
                        .forEach(path -> moduleNames.add(path.getFileName().toString().replace(".java", "")));
            }
            assertTrue(!moduleNames.isEmpty(), () -> "no class found under " + module.resolve("src/main/java"));
            names.addAll(moduleNames);
        }
        return names;
    }
}
