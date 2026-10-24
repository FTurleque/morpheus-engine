package com.morpheus.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A test factory that generates nothing fails; it never reports zero tests and no failure.
 *
 * <p>The presence ratchet counts what Surefire executes. A factory whose source of cases came back empty executes
 * nothing, passes, and lowers the generated count by its whole share: only the generated minimum notices, and only if
 * its margin is smaller than that share. The factory therefore asserts, in its own body, that it built at least one
 * test. The other generating annotations need no such assertion: JUnit refuses a parameterised test, a repeated test
 * or a test template that yields no invocation, unless {@code allowZeroInvocations = true} turns that refusal off,
 * which this rule forbids.</p>
 *
 * <p>The intent is "a generating method that does not assert its source is not empty must not appear"
 * ({@code .claude/rules/architecture.md}):
 * a property of the text of the test sources, which ArchUnit does not import, so the rule reads them. Comments and the
 * contents of literals are blanked first, so a Javadoc or a fixture that names an annotation is not a factory.</p>
 */
class TestFactoryAssertsItsSourceTest {

    private static final Pattern FACTORY = Pattern.compile("@TestFactory\\b");
    private static final Pattern ZERO_INVOCATIONS_ALLOWED = Pattern.compile(
            "@(ParameterizedTest|RepeatedTest|TestTemplate)\\b[^{;]*\\ballowZeroInvocations\\s*=\\s*true");
    private static final Pattern NOT_EMPTY_ASSERTION = Pattern.compile(
            "\\bnonEmpty\\s*\\(|\\bassertFalse\\s*\\([^;]*\\.isEmpty\\s*\\(\\s*\\)");
    private static final Pattern METHOD_NAME = Pattern.compile("(\\w+)\\s*\\(");

    @Test
    void everyTestFactoryOfTheRepositoryAssertsItsSourceIsNotEmpty() throws IOException {
        Map<String, String> sources = testSources();
        long factories = sources.values().stream().mapToLong(source -> factories(source).size()).sum();
        assertTrue(factories > 0, "no test factory found in " + sources.size() + " test sources: the rule matches nothing");

        List<String> violations = new ArrayList<>();
        sources.forEach((file, source) -> factoriesWithoutTheAssertion(source)
                .forEach(method -> violations.add(file + "#" + method)));
        assertEquals(List.of(), violations,
                "a test factory must assert that it built at least one test (GeneratedCases.nonEmpty, or assertFalse on "
                        + "isEmpty()), and name what was empty: otherwise an empty source reports zero tests and no failure");
    }

    @Test
    void noGeneratingTestAllowsZeroInvocations() throws IOException {
        List<String> violations = new ArrayList<>();
        testSources().forEach((file, source) -> {
            if (ZERO_INVOCATIONS_ALLOWED.matcher(code(source)).find()) {
                violations.add(file);
            }
        });
        assertEquals(List.of(), violations,
                "allowZeroInvocations = true lets a generating test run nothing and pass");
    }

    @Test
    void aFactoryThatDoesNotAssertItsSourceIsReportedByName() {
        String source = """
                class Sample {
                    @TestFactory
                    Stream<DynamicTest> asserted() {
                        return GeneratedCases.nonEmpty("cases", cases().stream().map(c -> dynamicTest("c", () -> {})));
                    }

                    @TestFactory
                    Stream<DynamicTest> checkedByHand() {
                        List<DynamicTest> tests = build();
                        assertFalse(tests.isEmpty(), "no tests");
                        return tests.stream();
                    }

                    @TestFactory
                    Stream<DynamicTest> silent() {
                        return cases().stream().map(c -> dynamicTest("{" + c, () -> {}));
                    }
                }
                """;

        assertEquals(3, factories(source).size());
        assertEquals(List.of("silent"), factoriesWithoutTheAssertion(source));
    }

    @Test
    void anAnnotationNamedInACommentOrInALiteralIsNotAFactory() {
        String source = """
                class Sample {
                    /** Not a factory: {@code @TestFactory} appears in this comment. */
                    // @TestFactory
                    String text = "@TestFactory Stream<DynamicTest> fake() { return null; }";
                    String block = \"\"\"
                            @TestFactory
                            \"\"\";
                }
                """;

        assertEquals(List.of(), factories(source));
    }

    private static List<String> factoriesWithoutTheAssertion(String source) {
        List<String> missing = new ArrayList<>();
        for (Factory factory : factories(source)) {
            if (!NOT_EMPTY_ASSERTION.matcher(factory.body()).find()) {
                missing.add(factory.name());
            }
        }
        return missing;
    }

    /** The methods that carry {@code @TestFactory}, each with the body that follows its own declaration. */
    private static List<Factory> factories(String source) {
        String code = code(source);
        List<Factory> factories = new ArrayList<>();
        Matcher annotation = FACTORY.matcher(code);
        while (annotation.find()) {
            Matcher name = METHOD_NAME.matcher(code);
            if (name.find(annotation.end()) && !code.substring(annotation.end(), name.start()).contains("{")) {
                factories.add(new Factory(name.group(1), body(code, name.end())));
            }
        }
        return factories;
    }

    /** The braced body that opens after {@code from}. */
    private static String body(String code, int from) {
        int open = code.indexOf('{', from);
        int depth = 0;
        for (int index = open; index < code.length(); index++) {
            char current = code.charAt(index);
            if (current == '{') {
                depth++;
            } else if (current == '}' && --depth == 0) {
                return code.substring(open, index + 1);
            }
        }
        throw new IllegalStateException("unbalanced braces after position " + from);
    }

    private record Factory(String name, String body) {
    }

    /** The source with the content of comments, string literals, text blocks and character literals blanked out. */
    private static String code(String source) {
        StringBuilder result = new StringBuilder(source.length());
        int index = 0;
        while (index < source.length()) {
            if (source.startsWith("//", index)) {
                index = blank(source, result, index, source.indexOf('\n', index), "\n");
            } else if (source.startsWith("/*", index)) {
                index = blank(source, result, index, source.indexOf("*/", index), "*/");
            } else if (source.startsWith("\"\"\"", index)) {
                index = blankLiteral(source, result, index, "\"\"\"");
            } else if (source.charAt(index) == '"' || source.charAt(index) == '\'') {
                index = blankLiteral(source, result, index, String.valueOf(source.charAt(index)));
            } else {
                result.append(source.charAt(index++));
            }
        }
        return result.toString();
    }

    private static int blank(String source, StringBuilder result, int from, int end, String terminator) {
        int stop = end < 0 ? source.length() : (terminator.equals("\n") ? end : end + terminator.length());
        for (int index = from; index < stop; index++) {
            result.append(source.charAt(index) == '\n' ? '\n' : ' ');
        }
        return stop;
    }

    private static int blankLiteral(String source, StringBuilder result, int from, String delimiter) {
        int index = from + delimiter.length();
        while (index < source.length() && !source.startsWith(delimiter, index)) {
            index += source.charAt(index) == '\\' ? 2 : 1;
        }
        int stop = Math.min(source.length(), index + delimiter.length());
        for (int position = from; position < stop; position++) {
            result.append(source.charAt(position) == '\n' ? '\n' : ' ');
        }
        return stop;
    }

    private static Map<String, String> testSources() throws IOException {
        Path root = repoRoot();
        Map<String, String> sources = new TreeMap<>();
        try (Stream<Path> modules = Files.list(root)) {
            for (Path module : modules.filter(path -> path.getFileName().toString().startsWith("morpheus-")).toList()) {
                Path tests = module.resolve("src/test/java");
                if (!Files.isDirectory(tests)) {
                    continue;
                }
                try (Stream<Path> tree = Files.walk(tests)) {
                    for (Path file : tree.filter(path -> path.toString().endsWith(".java")).toList()) {
                        sources.put(root.relativize(file).toString().replace('\\', '/'), Files.readString(file));
                    }
                }
            }
        }
        assertFalse(sources.isEmpty(), "no test source found under " + root);
        return sources;
    }

    private static Path repoRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isRegularFile(current.resolve("pom.xml")) && Files.isDirectory(current.resolve("distribution"))) {
            return current;
        }
        Path parent = current.getParent();
        if (parent != null && Files.isRegularFile(parent.resolve("pom.xml"))
                && Files.isDirectory(parent.resolve("distribution"))) {
            return parent;
        }
        throw new IllegalStateException("MORPHEUS repository root not found from " + current);
    }
}
