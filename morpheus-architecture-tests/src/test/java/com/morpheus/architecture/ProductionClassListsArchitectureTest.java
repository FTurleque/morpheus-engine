package com.morpheus.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
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

    /** A debt counter carries the identifier of the decision record that names it; a table of facts carries none. */
    private record Kind(Optional<String> decision) {
        static final Kind TABLE_OF_FACTS = new Kind(Optional.empty());

        static Kind debtCounterUnder(String decisionRecord) {
            return new Kind(Optional.of(decisionRecord));
        }
    }

    private static final String DECISION_RECORDS = "docs/architecture/adr";
    private static final String DEBT_COUNTER_RATCHETS = "config/debt-counter-ratchets.properties";
    private static final String ACCEPTED = "Statut : **Acceptée**";

    /** Each remaining list, by {@code TestClass.FIELD}, with its kind. */
    private static final Map<String, Kind> KINDS = Map.ofEntries(
            Map.entry("AdapterCompositionRootArchitectureTest.COMPOSITION_ROOTS", Kind.debtCounterUnder("ARCH-0156")),
            Map.entry("ApiEntryTypesArchitectureTest.ENTRY_TYPES", Kind.TABLE_OF_FACTS),
            Map.entry("AppendOnlyJournalReadsArchitectureTest.JOURNAL_PAGES", Kind.TABLE_OF_FACTS),
            Map.entry("AppendOnlyJournalReadsArchitectureTest.JOURNAL_PORTS", Kind.TABLE_OF_FACTS),
            Map.entry("AppendOnlyJournalReadsArchitectureTest.JOURNAL_RECORDS", Kind.TABLE_OF_FACTS),
            Map.entry("ApplicationTechnologyBoundaryArchitectureTest.CONFINED_FAMILIES", Kind.debtCounterUnder("ARCH-0150")),
            Map.entry("ApplicationTechnologyBoundaryArchitectureTest.REFLECTION_AND_LOADING", Kind.debtCounterUnder("ARCH-0150")),
            Map.entry("CliArgumentBaseArchitectureTest.OTHER_PURPOSES", Kind.TABLE_OF_FACTS),
            Map.entry("CliOptionValueHasOneBlankDefinitionTest.OTHER_QUESTIONS", Kind.TABLE_OF_FACTS),
            Map.entry("CurrentRequirementReaderArchitectureTest.FULL_HISTORY_READERS", Kind.TABLE_OF_FACTS),
            Map.entry("CurrentRequirementReaderArchitectureTest.WHOLE_CURRENT_READERS", Kind.TABLE_OF_FACTS),
            Map.entry("DispatchersHoldNoUseCaseArchitectureTest.CLI_ALLOWANCE", Kind.TABLE_OF_FACTS),
            Map.entry("DispatchersHoldNoUseCaseArchitectureTest.MCP_ALLOWANCE", Kind.TABLE_OF_FACTS),
            Map.entry("ErrorClassificationByTypeArchitectureTest.SIZE_CONSUMERS", Kind.TABLE_OF_FACTS),
            Map.entry("FailureTextOwnershipArchitectureTest.OTHER_PURPOSES", Kind.TABLE_OF_FACTS),
            Map.entry("FailureTextOwnershipArchitectureTest.OTHER_SPELLINGS", Kind.TABLE_OF_FACTS),
            Map.entry("FailureTextOwnershipArchitectureTest.OWNER_FILES", Kind.TABLE_OF_FACTS),
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
            List<Path> matching;
            try (Stream<Path> files = Files.list(records)) {
                matching = files.filter(file -> file.getFileName().toString().startsWith(counter.getValue() + "-"))
                        .filter(file -> file.getFileName().toString().endsWith(".md"))
                        .sorted()
                        .toList();
            }
            if (matching.size() != 1) {
                refusals.add(counter.getKey() + " declares " + counter.getValue() + ", found in the registry as "
                        + matching);
                continue;
            }
            String text = Files.readString(matching.get(0));
            if (!text.contains(ACCEPTED)) {
                refusals.add(counter.getKey() + " declares " + counter.getValue() + ", which is not accepted");
            }
            if (!text.contains(counter.getKey())) {
                refusals.add(counter.getKey() + " declares " + counter.getValue() + ", which does not name it");
            }
        }
        assertEquals(List.of(), refusals, "a debt counter shrinks under the decision record that says so: the record "
                + "it declares is in " + DECISION_RECORDS + ", accepted, and names it by TestClass.FIELD");
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
            int dot = counter.indexOf('.');
            Field field = testClasses.get(counter.substring(0, dot)).getDeclaredField(counter.substring(dot + 1));
            field.setAccessible(true);
            int size = sizeOf(field.get(null));
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
        KINDS.forEach((list, kind) -> kind.decision().ifPresent(decision -> counters.put(list, decision)));
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
