package com.morpheus.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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
 * Every list of the gate module that names classes of {@code morpheus-api} says what kind of list it is.
 *
 * <p>A role of the adapter is a package: the remote server, the local server, the transport plumbing and each
 * transport group of routers are selected by the package they reside in, never by a list of names. A list of class
 * names that remains is one of two other kinds, and this test records which:</p>
 *
 * <ul>
 *   <li>a <strong>debt counter</strong> -- what a set of classes does today and is expected to stop doing, exact in
 *       both directions and shrinking only, under the decision record that says so;</li>
 *   <li>a <strong>table of facts</strong> -- each entry carries its own reason, and the table is exactly the classes
 *       the reason is true of.</li>
 * </ul>
 *
 * <p>A new list of the adapter's classes fails here until it is given a kind; a list that disappears fails here too,
 * so the declaration never outlives what it declares. A list that would be a role is not declared: the classes it
 * names belong in a package instead.</p>
 */
class HttpAdapterClassListsArchitectureTest {

    private enum Kind { DEBT_COUNTER, TABLE_OF_FACTS }

    /** Each remaining list, by {@code TestClass.FIELD}, with its kind. */
    private static final Map<String, Kind> KINDS = Map.ofEntries(
            Map.entry("AdapterCompositionRootArchitectureTest.COMPOSITION_ROOTS", Kind.DEBT_COUNTER),
            Map.entry("ApiEntryTypesArchitectureTest.ENTRY_TYPES", Kind.TABLE_OF_FACTS),
            Map.entry("ErrorClassificationByTypeArchitectureTest.SIZE_CONSUMERS", Kind.TABLE_OF_FACTS),
            Map.entry("HttpFailureDisclosureArchitectureTest.AUTHORED_FOR_THE_CALLER", Kind.TABLE_OF_FACTS),
            Map.entry("HttpPathReadingArchitectureTest.FORM_DECODERS", Kind.TABLE_OF_FACTS),
            Map.entry("HttpPathReadingArchitectureTest.OTHER_GET_PATH_CALLERS", Kind.TABLE_OF_FACTS),
            Map.entry("HttpPathReadingArchitectureTest.RAW_PATH_READERS", Kind.TABLE_OF_FACTS),
            Map.entry("HttpPathReadingArchitectureTest.SLASH_SPLITTERS", Kind.TABLE_OF_FACTS),
            Map.entry("PublicHttpRouteConvergenceTest.REMOTE_ONLY", Kind.TABLE_OF_FACTS),
            Map.entry("PublishedEnumsMatchJavaEnumsTest.EXEMPT", Kind.TABLE_OF_FACTS),
            Map.entry("PublishedEnumsMatchJavaEnumsTest.TABLE", Kind.TABLE_OF_FACTS),
            Map.entry("SilentCatchArchitectureTest.EXPLAINED", Kind.TABLE_OF_FACTS));

    private static final Pattern LIST_DECLARATION = Pattern.compile(
            "static final (?:Set|List|Map)<[^=;]*?>\\s+([A-Z][A-Z0-9_]*)\\s*=\\s*(?:new [\\w<>]+\\(\\s*)?"
                    + "(?:Set|List|Map)\\.(?:of|copyOf|ofEntries)\\(");
    private static final Pattern STRING_LITERAL = Pattern.compile("\"((?:[^\"\\\\]|\\\\.)*)\"");
    private static final Pattern NAME_PIECE = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");

    @Test
    void everyListOfTheAdaptersClassesIsADebtCounterOrATableOfFacts() throws IOException {
        Set<String> adapterClasses = adapterClassNames();
        assertTrue(adapterClasses.size() > 50, () -> "too few classes of morpheus-api seen: " + adapterClasses.size());

        Map<String, Set<String>> found = new TreeMap<>();
        Path tests = ProductionSources.repositoryRoot().resolve("morpheus-architecture-tests/src/test/java");
        try (Stream<Path> tree = Files.walk(tests)) {
            for (Path source : tree.filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
                String testClass = source.getFileName().toString().replace(".java", "");
                if (testClass.equals(getClass().getSimpleName())) {
                    continue;
                }
                listsNamingClasses(Files.readString(source), adapterClasses)
                        .forEach((field, names) -> found.put(testClass + "." + field, names));
            }
        }

        assertEquals(new TreeSet<>(KINDS.keySet()), new TreeSet<>(found.keySet()), () -> "every list of the gate "
                + "module that names classes of morpheus-api is declared in KINDS as a debt counter or a table of "
                + "facts, and KINDS names no list that is gone. A list that is a role belongs in a package. Found: "
                + found);
    }

    @Test
    void theDetectionSeesANamedClassInAnyLiteralShapeAndIgnoresParenthesesInStrings() {
        String source = """
                class Sample {
                    private static final Set<String> ROLES = Set.of("com.morpheus.api.Alpha", "Beta.java#x");
                    private static final List<String> VERBS = List.of("call(", "SecureRandom");
                    private static final Map<String, String> REASONS = Map.of("Gamma", "a reason (with Alpha)");
                }
                """;

        Map<String, Set<String>> lists = listsNamingClasses(source, Set.of("Alpha", "Beta", "Gamma"));

        assertEquals(Map.of("ROLES", Set.of("Alpha", "Beta"), "REASONS", Set.of("Alpha", "Gamma")), lists);
    }

    private static Map<String, Set<String>> listsNamingClasses(String source, Set<String> classNames) {
        Map<String, Set<String>> lists = new TreeMap<>();
        Matcher declaration = LIST_DECLARATION.matcher(source);
        while (declaration.find()) {
            String initializer = source.substring(declaration.end(), closingParenthesis(source, declaration.end()));
            Set<String> named = new TreeSet<>();
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

    private static Set<String> adapterClassNames() throws IOException {
        Set<String> names = new TreeSet<>();
        Path sources = ProductionSources.repositoryRoot().resolve("morpheus-api/src/main/java");
        try (Stream<Path> tree = Files.walk(sources)) {
            tree.filter(path -> path.toString().endsWith(".java"))
                    .forEach(path -> names.add(path.getFileName().toString().replace(".java", "")));
        }
        return names;
    }
}
