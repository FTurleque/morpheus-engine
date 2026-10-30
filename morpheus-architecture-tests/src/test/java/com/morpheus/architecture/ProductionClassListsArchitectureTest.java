package com.morpheus.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
 *       both directions and shrinking only, under the decision record that says so;</li>
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

    private enum Kind { DEBT_COUNTER, TABLE_OF_FACTS }

    /** Each remaining list, by {@code TestClass.FIELD}, with its kind. */
    private static final Map<String, Kind> KINDS = Map.ofEntries(
            Map.entry("AdapterCompositionRootArchitectureTest.COMPOSITION_ROOTS", Kind.DEBT_COUNTER),
            Map.entry("ApiEntryTypesArchitectureTest.ENTRY_TYPES", Kind.TABLE_OF_FACTS),
            Map.entry("AppendOnlyJournalReadsArchitectureTest.JOURNAL_PAGES", Kind.TABLE_OF_FACTS),
            Map.entry("AppendOnlyJournalReadsArchitectureTest.JOURNAL_PORTS", Kind.TABLE_OF_FACTS),
            Map.entry("AppendOnlyJournalReadsArchitectureTest.JOURNAL_RECORDS", Kind.TABLE_OF_FACTS),
            Map.entry("ApplicationTechnologyBoundaryArchitectureTest.CONFINED_FAMILIES", Kind.DEBT_COUNTER),
            Map.entry("ApplicationTechnologyBoundaryArchitectureTest.REFLECTION_AND_LOADING", Kind.DEBT_COUNTER),
            Map.entry("CliArgumentBaseArchitectureTest.OTHER_PURPOSES", Kind.TABLE_OF_FACTS),
            Map.entry("CliOptionValueHasOneBlankDefinitionTest.OTHER_QUESTIONS", Kind.TABLE_OF_FACTS),
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
            Map.entry("ReflectionAndClassLoadingBoundaryArchitectureTest.LOADING", Kind.DEBT_COUNTER),
            Map.entry("ReflectionAndClassLoadingBoundaryArchitectureTest.REFLECTION", Kind.DEBT_COUNTER),
            Map.entry("RefusalPathTextArchitectureTest.FORBIDDEN", Kind.TABLE_OF_FACTS),
            Map.entry("RefusalPathTextArchitectureTest.KNOWN_REFUSAL_BUILDERS", Kind.TABLE_OF_FACTS),
            Map.entry("RemoteIdentityDecompositionContractTest.FILESYSTEM_VERBS", Kind.TABLE_OF_FACTS),
            Map.entry("SharedDomainProjectionArchitectureTest.ADAPTER_SHAPES_DIFFERING_FROM_THE_SHARED_ONE", Kind.DEBT_COUNTER),
            Map.entry("SharedDomainProjectionArchitectureTest.SHARED_TYPES", Kind.TABLE_OF_FACTS),
            Map.entry("ShippedRequirementStoresPageThemselvesArchitectureTest.SHIPPED_STORES", Kind.TABLE_OF_FACTS),
            Map.entry("SilentCatchArchitectureTest.EXPLAINED", Kind.TABLE_OF_FACTS),
            Map.entry("SqliteConnectionScopeUseArchitectureTest.ONE_STORE_PER_OPERATION", Kind.TABLE_OF_FACTS),
            Map.entry("SqliteConnectionScopeUseArchitectureTest.SCOPE_OWNERS", Kind.TABLE_OF_FACTS),
            Map.entry("SqliteStorePackagesArchitectureTest.ADAPTER_COLLABORATORS", Kind.TABLE_OF_FACTS),
            Map.entry("SqliteStorePackagesArchitectureTest.RUNTIME_ENTRY_POINTS", Kind.DEBT_COUNTER),
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
