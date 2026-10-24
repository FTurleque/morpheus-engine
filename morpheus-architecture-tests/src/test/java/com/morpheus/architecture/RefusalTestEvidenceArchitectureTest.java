package com.morpheus.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * A test that expects a refusal typed by the JDK reads the refusal it received.
 *
 * <p>{@code assertThrows(IllegalArgumentException.class, ...)} as a statement passes for any
 * {@code IllegalArgumentException} the call raises, so it keeps passing after the validation it names is removed
 * and another one of the same method answers instead. The exception {@code assertThrows} returns carries the only
 * evidence of why the product refused; a call that discards it, or binds it to a variable nobody names again,
 * asserts the type and nothing else.</p>
 *
 * <p>The proposition is about the form of the source -- whether a returned value is read -- over files that are
 * not on this module's classpath, which is why it is a textual assertion ({@code .claude/rules/architecture.md}) and
 * has no ArchUnit twin.</p>
 *
 * <p>A type "of the project" is one declared under a {@code src/main/java} or {@code src/test/java} of the
 * repository: it names its own reason. Any other expected type, or one the scan cannot read, is generic.</p>
 */
class RefusalTestEvidenceArchitectureTest {

    private static final Pattern ASSERT_THROWS =
            Pattern.compile("(?:\\bAssertions\\s*\\.\\s*)?\\bassertThrows(?:Exactly)?\\s*\\(");

    private static final Pattern EXPECTED_TYPE = Pattern.compile("\\G\\s*([A-Za-z_][\\w.]*)\\s*\\.\\s*class\\b");

    private static final Pattern DECLARATION = Pattern.compile("\\b(?:class|interface|record|enum)\\s+([A-Za-z_]\\w*)");

    private static final Pattern BOUND_TO = Pattern.compile("([A-Za-z_]\\w*)\\s*=\\s*$");

    @Test
    void noTestExpectsAGenericRefusalWithoutReadingWhyTheProductRefused() throws IOException {
        Path root = repoRoot();
        Set<String> declared = declaredTypes(root);
        List<String> offenders = new ArrayList<>();
        for (Path source : testSources(root)) {
            List<Integer> lines = unreadGenericRefusals(Files.readString(source), declared);
            if (!lines.isEmpty()) {
                offenders.add(relative(root, source) + " holds " + lines.size() + " assertThrows on a generic type"
                        + " whose result nobody reads, at lines " + lines);
            }
        }

        assertEquals(List.of(), offenders,
                "an assertThrows on a type the repository does not declare must keep its result and compare the"
                        + " message or the code: asserted by type alone it passes for any other refusal of the"
                        + " same type");
    }

    @Test
    void theScanReadsTheTestSourcesItJudges() throws IOException {
        Path root = repoRoot();
        List<Path> sources = testSources(root);
        assertFalse(sources.isEmpty(), "the scan must find test sources to be worth anything");
        assertTrue(sources.size() > 200,
                () -> "the scan must cover the whole test tree, found only " + sources.size() + " sources");

        long calls = 0;
        for (Path source : sources) {
            Matcher call = ASSERT_THROWS.matcher(strip(Files.readString(source)));
            while (call.find()) {
                calls++;
            }
        }
        long found = calls;
        assertTrue(found > 100, () -> "the scan must find the assertThrows of the repository, found " + found);

        Set<String> declared = declaredTypes(root);
        assertTrue(declared.contains("KnowledgeStoreException") && declared.contains("RejectedRequestException"),
                "the scan must read the exception types the repository declares, otherwise it judges every"
                        + " project type generic");
        assertFalse(declared.contains("IllegalArgumentException"),
                "a JDK exception type must never be read as a type of the project");
    }

    /** One case per shape found in the repository that drops the result: the rule must report them. */
    @Test
    void theRuleRefusesEveryShapeThatDropsTheResultOfTheRefusal() {
        assertEquals(List.of(), wrongVerdicts(shapesThatDropTheResult()),
                "every shape found in the repository must get the verdict written for it");
    }

    /** One case per shape that reads the result, names a type of the project, or only looks like a call. */
    @Test
    void theRuleAcceptsEveryShapeThatReadsTheResultOrExpectsATypeOfTheProject() {
        assertEquals(List.of(), wrongVerdicts(shapesThatAreNotConcerned()),
                "every shape found in the repository must get the verdict written for it");
    }

    private record Shape(String name, String body, int refused) { }

    private static List<String> wrongVerdicts(List<Shape> shapes) {
        Set<String> declared = Set.of("DomainRefusal");
        List<String> wrong = new ArrayList<>();
        for (Shape shape : shapes) {
            int refused = unreadGenericRefusals(shape.body(), declared).size();
            if (refused != shape.refused()) {
                wrong.add(shape.name() + ": expected " + shape.refused() + " refusal(s), the rule reports " + refused);
            }
        }
        return wrong;
    }

    private static List<Shape> shapesThatDropTheResult() {
        return List.of(
                new Shape("a discarded statement", """
                        void a() {
                            assertThrows(IllegalArgumentException.class, () -> work());
                        }
                        """, 1),
                new Shape("a call split over two lines", """
                        void a() {
                            assertThrows(
                                    IllegalStateException.class,
                                    () -> work());
                        }
                        """, 1),
                new Shape("a qualified Assertions call", """
                        void a() {
                            Assertions.assertThrows(NullPointerException.class, () -> work());
                        }
                        """, 1),
                new Shape("assertThrowsExactly", """
                        void a() {
                            assertThrowsExactly(IOException.class, () -> work());
                        }
                        """, 1),
                new Shape("a variable never read", """
                        void a() {
                            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> work());
                            log();
                        }
                        """, 1),
                new Shape("a variable only assigned again", """
                        void a() {
                            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> work());
                            failure = assertThrows(IllegalArgumentException.class, () -> work());
                        }
                        """, 2),
                new Shape("a name read only in the next method", """
                        void a() {
                            var failure = assertThrows(IllegalArgumentException.class, () -> work());
                        }
                        void b() {
                            var failure = assertThrows(IllegalArgumentException.class, () -> work());
                            assertEquals("why", failure.getMessage());
                        }
                        """, 1),
                new Shape("a lambda body that drops the result", """
                        void a() {
                            assertAll(() -> assertThrows(IllegalArgumentException.class, () -> work()));
                        }
                        """, 1),
                new Shape("an expected type the scan cannot read", """
                        void a() {
                            assertThrows(expected, () -> work());
                        }
                        """, 1));
    }

    private static List<Shape> shapesThatAreNotConcerned() {
        return List.of(
                new Shape("a result passed straight to another call", """
                        void a() {
                            assertEquals("name", assertThrows(NullPointerException.class, () -> work()).getMessage());
                        }
                        """, 0),
                new Shape("a result returned from a helper", """
                        IllegalArgumentException refusal() {
                            return assertThrows(IllegalArgumentException.class, () -> work());
                        }
                        """, 0),
                new Shape("a variable read three lines later", """
                        void a() {
                            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class, () -> work());
                            log();
                            log();
                            assertEquals("why", failure.getMessage());
                        }
                        """, 0),
                new Shape("a type of the project", """
                        void a() {
                            assertThrows(DomainRefusal.class, () -> work());
                        }
                        """, 0),
                new Shape("a qualified type of the project", """
                        void a() {
                            assertThrows(Outer.DomainRefusal.class, () -> work());
                        }
                        """, 0),
                new Shape("a call that only a comment or a string mentions", """
                        // assertThrows(IllegalArgumentException.class, () -> work());
                        String text = "assertThrows(IllegalArgumentException.class, () -> work());";
                        """, 0));
    }

    @Test
    void aJdkTypeThatAppearsNowhereElseInTheRepositoryIsStillGeneric() {
        String planted = "void a() { assertThrows(java.time.DateTimeException.class, () -> work()); }";

        assertEquals(1, unreadGenericRefusals(planted, Set.of()).size(),
                "an expected type that is not declared by the repository is generic, whatever its name");
        assertEquals(0, unreadGenericRefusals(planted, Set.of("DateTimeException")).size(),
                "the same call is not concerned once the repository declares the type");
    }

    @Test
    void theLineOfEachRefusalIsReportedWhereTheCallStarts() {
        String planted = "class T {\n\n    void a() {\n        assertThrows(\n                IOException.class,\n"
                + "                () -> work());\n    }\n}\n";

        assertEquals(List.of(4), unreadGenericRefusals(planted, Set.of()),
                "a failure must name the line a reader opens the file at");
    }

    /** Lines of the {@code assertThrows} on a generic type whose result nobody reads. */
    static List<Integer> unreadGenericRefusals(String source, Set<String> declaredTypes) {
        String text = strip(source);
        List<Integer> lines = new ArrayList<>();
        Matcher call = ASSERT_THROWS.matcher(text);
        while (call.find()) {
            if (isTypeOfTheProject(text, call.end(), declaredTypes) || readsItsResult(text, call)) {
                continue;
            }
            lines.add(lineOf(text, call.start()));
        }
        return lines;
    }

    private static boolean isTypeOfTheProject(String text, int argumentsStart, Set<String> declaredTypes) {
        Matcher type = EXPECTED_TYPE.matcher(text);
        type.region(argumentsStart, text.length());
        if (!type.lookingAt()) {
            return false;
        }
        String name = type.group(1);
        return declaredTypes.contains(name.substring(name.lastIndexOf('.') + 1));
    }

    /** True when the value of the call is passed on, returned, or bound to a name the block uses again. */
    private static boolean readsItsResult(String text, Matcher call) {
        int before = call.start();
        while (before > 0 && Character.isWhitespace(text.charAt(before - 1))) {
            before--;
        }
        if (dropsTheValueBefore(text, before)) {
            return false;
        }
        if (text.charAt(before - 1) != '=') {
            return true;
        }
        Matcher bound = BOUND_TO.matcher(text.substring(Math.max(0, before - 160), before));
        return bound.find()
                && isNamedAgain(text, closingParenthesis(text, call.end() - 1) + 1, bound.group(1));
    }

    /** A call that opens a statement, or the body of a lambda, hands its value to nobody. */
    private static boolean dropsTheValueBefore(String text, int before) {
        if (before == 0) {
            return true;
        }
        char previous = text.charAt(before - 1);
        boolean lambdaBody = previous == '>' && before >= 2 && text.charAt(before - 2) == '-';
        return previous == ';' || previous == '{' || previous == '}' || lambdaBody;
    }

    private static boolean isNamedAgain(String text, int from, String name) {
        int depth = 0;
        int end = text.length();
        for (int index = from; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == '{') {
                depth++;
            } else if (character == '}' && --depth < 0) {
                end = index;
                break;
            }
        }
        Matcher use = Pattern.compile("\\b" + Pattern.quote(name) + "\\b(?!\\s*=(?!=))")
                .matcher(text.substring(from, end));
        return use.find();
    }

    private static int closingParenthesis(String text, int open) {
        int depth = 0;
        for (int index = open; index < text.length(); index++) {
            char character = text.charAt(index);
            if (character == '(') {
                depth++;
            } else if (character == ')' && --depth == 0) {
                return index;
            }
        }
        return text.length() - 1;
    }

    private static int lineOf(String text, int index) {
        int line = 1;
        for (int position = 0; position < index; position++) {
            if (text.charAt(position) == '\n') {
                line++;
            }
        }
        return line;
    }

    /**
     * Blanks comments and the content of string, text-block and character literals, keeping every newline and
     * every delimiter, so that indexes and line numbers of the result are those of the source.
     */
    static String strip(String source) {
        StringBuilder out = new StringBuilder(source.length());
        int index = 0;
        while (index < source.length()) {
            if (source.startsWith("//", index)) {
                index = blankLineComment(source, index, out);
            } else if (source.startsWith("/*", index)) {
                index = blankBlockComment(source, index, out);
            } else if (source.startsWith("\"\"\"", index)) {
                index = blankTextBlock(source, index, out);
            } else if (source.charAt(index) == '"' || source.charAt(index) == '\'') {
                index = blankQuoted(source, index, out);
            } else {
                out.append(source.charAt(index));
                index++;
            }
        }
        return out.toString();
    }

    private static int blankLineComment(String source, int from, StringBuilder out) {
        int index = from;
        while (index < source.length() && source.charAt(index) != '\n') {
            out.append(' ');
            index++;
        }
        return index;
    }

    private static int blankBlockComment(String source, int from, StringBuilder out) {
        int end = source.indexOf("*/", from + 2);
        int stop = end < 0 ? source.length() : end + 2;
        for (int index = from; index < stop; index++) {
            out.append(keepLineBreak(source.charAt(index)));
        }
        return stop;
    }

    private static int blankTextBlock(String source, int from, StringBuilder out) {
        out.append("\"\"\"");
        int index = from + 3;
        while (index < source.length() && !source.startsWith("\"\"\"", index)) {
            if (source.charAt(index) == '\\' && index + 1 < source.length()) {
                out.append(' ');
                index++;
            }
            out.append(keepLineBreak(source.charAt(index)));
            index++;
        }
        if (index < source.length()) {
            out.append("\"\"\"");
            index += 3;
        }
        return index;
    }

    private static int blankQuoted(String source, int from, StringBuilder out) {
        char delimiter = source.charAt(from);
        out.append(delimiter);
        int index = from + 1;
        while (index < source.length() && source.charAt(index) != delimiter && source.charAt(index) != '\n') {
            if (source.charAt(index) == '\\' && index + 1 < source.length()) {
                out.append(' ');
                index++;
            }
            out.append(' ');
            index++;
        }
        if (index < source.length() && source.charAt(index) == delimiter) {
            out.append(delimiter);
            index++;
        }
        return index;
    }

    private static char keepLineBreak(char character) {
        return character == '\n' || character == '\r' ? character : ' ';
    }

    private static Set<String> declaredTypes(Path root) throws IOException {
        Set<String> declared = new TreeSet<>();
        for (Path source : javaSources(root, "main")) {
            collect(declared, source);
        }
        for (Path source : javaSources(root, "test")) {
            collect(declared, source);
        }
        return declared;
    }

    private static void collect(Set<String> declared, Path source) throws IOException {
        Matcher declaration = DECLARATION.matcher(strip(Files.readString(source)));
        while (declaration.find()) {
            declared.add(declaration.group(1));
        }
    }

    private static List<Path> testSources(Path root) throws IOException {
        return javaSources(root, "test");
    }

    private static List<Path> javaSources(Path root, String sourceSet) throws IOException {
        List<Path> sources = new ArrayList<>();
        try (Stream<Path> modules = Files.list(root)) {
            for (Path module : modules.filter(path -> path.getFileName().toString().startsWith("morpheus-"))
                    .sorted().toList()) {
                Path tree = module.resolve("src").resolve(sourceSet).resolve("java");
                if (!Files.isDirectory(tree)) {
                    continue;
                }
                try (Stream<Path> files = Files.walk(tree)) {
                    files.filter(Files::isRegularFile)
                            .filter(path -> path.getFileName().toString().endsWith(".java"))
                            .sorted()
                            .forEach(sources::add);
                }
            }
        }
        return sources;
    }

    private static String relative(Path root, Path source) {
        return root.relativize(source).toString().replace('\\', '/');
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
