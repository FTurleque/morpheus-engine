package com.morpheus.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.morpheus.architecture.CliOptionParsingRefusesUnknownOptionsTest.Block;
import com.morpheus.architecture.CliOptionParsingRefusesUnknownOptionsTest.Kind;
import com.morpheus.architecture.CliOptionParsingRefusesUnknownOptionsTest.Unit;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * A blank option value of the CLI has one definition, {@code OptionValue.nonBlank}, and no parser tests the blank
 * itself.
 *
 * <p>CLI-7 gave the refusal of a blank option value a single implementation, and six parsers kept a test of their own
 * ({@code value == null || value.isBlank()}). {@code isBlank()} and {@code trim().isEmpty()} disagree in both
 * directions -- an em space U+2003 is white space that {@code trim()} keeps, a control character U+0001 is emptied by
 * {@code trim()} and is not white space -- so {@code server identity create --principal U+2003} passed
 * {@code OptionValue} and was then reported by the copy as a missing option (CLI-10). A copy with its own message
 * <em>and</em> its own definition drifts in both. This guard <em>discovers</em> every blank test of
 * {@code morpheus-cli/src/main/java} ({@code Files.walk}, no file named) and refuses any that is not the definition and
 * does not provably answer another question.</p>
 *
 * <p><b>What a blank test is.</b> {@code .isBlank()}; {@code .trim()}, {@code .strip()}, {@code .stripLeading()} or
 * {@code .stripTrailing()} followed by {@code .isEmpty()} or {@code .length() == 0}; {@code String::isBlank};
 * {@code String::trim} or one of the {@code strip} references immediately followed by
 * {@code .filter(v -> !v.isEmpty())}; and {@code .codePoints()} or {@code .chars()} followed by {@code allMatch},
 * {@code anyMatch} or {@code noneMatch} whose argument names {@code isWhitespace}, {@code isSpaceChar} or a
 * {@code <=} comparison. Comments and the contents of string literals are blanked before the scan, so a mention in
 * either is not a test.</p>
 *
 * <p><b>How the option value is told apart.</b> Each blank test found must fall in exactly one of three classes, and
 * one that falls in none is refused:</p>
 * <ol>
 *   <li><b>The definition</b>: a test inside {@code OptionValue.nonBlank}. It must decide per code point and hold both
 *   halves -- {@code c <= ' '}, what {@code trim()} removes, and {@code Character.isWhitespace}, what {@code isBlank()}
 *   holds. A whole-string test there is refused: {@code isBlank() || trim().isEmpty()} lets U+0001 next to U+2003
 *   through.</li>
 *   <li><b>The message of a throwable</b>: the {@code safeMessage} methods, which print the class name when an
 *   exception has no message. They are recognised by <em>what the test reads</em>, never by the method's name: the
 *   receiver is {@code p.getMessage()} where {@code p} is a parameter of the enclosing method whose declared type is
 *   {@code Throwable} or a name ending in {@code Exception} or {@code Error}; or a local declared from
 *   {@code p.getMessage()}; or the parameter of a lambda filtering {@code Optional.ofNullable(p.getMessage())}. A
 *   variable merely named {@code message}, a message read from something that is not such a parameter, and a
 *   {@code getMessage()} in a method without such a parameter are not recognised.</li>
 *   <li><b>A named other question</b>, in {@link #OTHER_QUESTIONS}: an option's <em>name</em>, an environment variable
 *   or JVM property (empty means unset there, ADR-0108), a message printed by the reasoning output -- and two open
 *   residuals where the test reads a <em>part</em> of an option value, not the value: the elements of
 *   {@code --providers} and the provenance field of {@code --evidence}. Each entry holds the number of blank tests of
 *   its method: a method that gains one, loses one or disappears fails the guard until the entry is written again.</li>
 * </ol>
 *
 * <p>And a method named {@code nonBlank} outside {@code OptionValue} must delegate to {@code OptionValue.nonBlank}: a
 * homonym with its own test reads, at its call sites, like the definition.</p>
 *
 * <p>What it does not cover:</p>
 * <ul>
 *   <li><b>Where a classified method is called.</b> The guard classifies where a blank is <em>tested</em>, not what the
 *   tested value is: {@code CliLayout.envPath} or {@code RemoteApiLaunchOptions.presentSetting} called on an option
 *   value would give that value their definition, and nothing here would see it.</li>
 *   <li><b>The throwable parameter is recognised by the suffix of its type's name</b>, never resolved: a parameter of
 *   a non-throwable type named {@code ...Error} with a {@code getMessage()} would be classified as an exception
 *   message.</li>
 *   <li><b>Other spellings</b>: {@code isEmpty()} with no {@code trim()}, {@code equals("")}, a loop over
 *   {@link Character#isWhitespace}, a {@code map(String::trim)} separated from its {@code filter} by another step, a
 *   code-point predicate written as a method reference to a helper, or a {@code trim()} and an {@code isEmpty()} in two
 *   statements, as the key and value of {@code --param} in {@code MorpheusReasoningCli.addAssignment}.</li>
 *   <li>A local declared from {@code getMessage()} and reassigned before the test; a blank test of a listed method
 *   replaced by another at the same count.</li>
 *   <li>Other modules: the MCP ({@code McpArguments}) and HTTP decoders define their own blank.</li>
 *   <li>That every option value reaches the definition: that is the table of {@code BlankOptionValueRefusalTest},
 *   written by hand.</li>
 * </ul>
 *
 * <p>The rule is textual because the proposition is about which method tests a string, which no dependency rule over
 * compiled classes expresses (ADR-0103).</p>
 */
class CliOptionValueHasOneBlankDefinitionTest {

    private static final String DEFINITION_FILE = "OptionValue.java";
    private static final String DEFINITION_METHOD = "nonBlank";

    /** Methods whose blank tests answer another question than "is this option value blank", with their count. */
    private static final Map<String, OtherQuestion> OTHER_QUESTIONS = Map.of(
            "MorpheusCli.java#consumeOption", new OtherQuestion(1,
                    "the option's name: a token '--' followed by nothing is an empty option, not a blank value"),
            "MorpheusReasoningCli.java#analyze", new OtherQuestion(1,
                    "the message of an adapter execution, printed only when there is one"),
            "CliLayout.java#envPath", new OtherQuestion(1,
                    "an environment variable: empty means unset (ADR-0108, CLI-7 suite)"),
            "RemoteApiLaunchOptions.java#presentSetting", new OtherQuestion(1,
                    "an environment variable or JVM property: empty means unset (ADR-0108, CLI-7 suite)"),
            "RemoteApiLaunchOptions.java#resolveWorkspaceRoots", new OtherQuestion(1,
                    "an element of MORPHEUS_SERVER_WORKSPACE_ROOTS, an environment variable or JVM property"),
            "MorpheusPortfolioCli.java#providers", new OtherQuestion(1,
                    "open residual: an element of --providers; a list made of separators is dropped (ADR-0108, CLI-7)"),
            "MorpheusReasoningCli.java#parseAssignments", new OtherQuestion(1,
                    "open residual: the optional provenance field of an --evidence value, not the value"));

    private static final String TRIMMING = "(?:trim|strip|stripLeading|stripTrailing)";
    private static final Pattern BLANK_TEST = Pattern.compile(
            "\\.\\s*isBlank\\s*\\(\\s*\\)"
                    + "|\\.\\s*" + TRIMMING + "\\s*\\(\\s*\\)\\s*\\.\\s*"
                    + "(?:isEmpty\\s*\\(\\s*\\)|length\\s*\\(\\s*\\)\\s*==\\s*0)"
                    + "|String\\s*::\\s*isBlank\\b"
                    + "|String\\s*::\\s*" + TRIMMING + "\\s*\\)\\s*\\.\\s*filter\\s*\\(\\s*(\\w+)\\s*->\\s*!\\s*\\1\\s*"
                    + "\\.\\s*isEmpty\\s*\\(\\s*\\)"
                    + "|\\.\\s*(?:codePoints|chars)\\s*\\(\\s*\\)\\s*\\.\\s*(?:allMatch|anyMatch|noneMatch)\\s*\\(");
    private static final Pattern PER_CODE_POINT = Pattern.compile("^\\.\\s*(?:codePoints|chars)");
    private static final Pattern ABOUT_BLANK = Pattern.compile("isWhitespace|isSpaceChar|<=");
    private static final Pattern WHAT_TRIM_REMOVES = Pattern.compile("<=\\s*' '");
    private static final Pattern WHAT_IS_BLANK_HOLDS = Pattern.compile("Character\\s*\\.\\s*isWhitespace\\b");
    private static final Pattern DELEGATES = Pattern.compile("\\bOptionValue\\s*\\.\\s*nonBlank\\s*\\(");
    private static final Pattern THROWABLE_PARAMETER =
            Pattern.compile("\\b(?:Throwable|\\w*Exception|\\w*Error)\\s+(\\w+)\\s*[,)]");
    private static final Pattern RECEIVER =
            Pattern.compile("([A-Za-z_$][\\w$]*(?:\\s*\\.\\s*[A-Za-z_$][\\w$]*\\s*\\(\\s*\\))*)\\s*$");

    @Test
    void everyBlankTestOfTheCliIsTheDefinitionOrAnswersAnotherQuestion() throws IOException {
        Map<String, String> sources = CliOptionParsingRefusesUnknownOptionsTest.cliSources();

        Scan scan = scan(sources, OTHER_QUESTIONS);

        assertTrue(sources.containsKey(DEFINITION_FILE), "the scan did not read " + DEFINITION_FILE);
        assertTrue(scan.throwableMessages() > 0,
                "the scan recognised no throwable message: the class would be vacuous, or the recognition broken");
        assertEquals(List.of(), scan.violations(),
                "a blank test of the CLI is neither OptionValue.nonBlank nor a question classified here");
    }

    @Test
    void aParserTestingTheBlankItselfIsRefusedInEverySpelling() {
        String isBlank = "final class A {\n    private record Parsed(Map<String, String> options) {\n"
                + "        String required(String name) {\n            String value = options.get(name);\n"
                + "            if (value == null || value.isBlank()) {\n"
                + "                throw new IllegalArgumentException(\"missing required option --\" + name);\n"
                + "            }\n            return value;\n        }\n    }\n}\n";
        String optional = "final class A {\n    Optional<String> optional(String key) {\n"
                + "        return Optional.ofNullable(values.get(key)).map(String::trim).filter(v -> !v.isEmpty());\n"
                + "    }\n}\n";
        String reference = "final class A {\n    boolean any(List<String> values) {\n"
                + "        return values.stream().anyMatch(String::isBlank);\n    }\n}\n";

        assertEquals(List.of("A.java:5 Parsed#required tests the blank itself; call OptionValue.nonBlank"),
                scan(withDefinition("A.java", isBlank), Map.of()).violations());
        for (String spelling : List.of("value.trim().isEmpty()", "value .strip() .isEmpty()",
                "value.stripLeading().isEmpty()", "value.stripTrailing().length() == 0", "value.trim().length()==0",
                "value.codePoints().allMatch(Character::isWhitespace)", "value.chars().allMatch(c -> c <= ' ')",
                "!value.codePoints().anyMatch(c -> !Character.isSpaceChar(c))")) {
            assertEquals(1, scan(withDefinition("A.java", isBlank.replace("value.isBlank()", spelling)), Map.of())
                    .violations().size(), spelling);
        }
        assertEquals(List.of("A.java:3 A#optional tests the blank itself; call OptionValue.nonBlank"),
                scan(withDefinition("A.java", optional), Map.of()).violations());
        assertEquals(1, scan(withDefinition("A.java", reference), Map.of()).violations().size());
        assertEquals(List.of(), scan(withDefinition("A.java",
                        isBlank.replace("value.isBlank()", "value.codePoints().allMatch(Character::isDigit)")), Map.of())
                .violations(), "a code-point predicate that is not about white space is not a blank test");
    }

    @Test
    void theMessageOfAThrowableIsRecognisedByWhatIsReadNotByTheMethodName() {
        String local = "final class A {\n    private static String safeMessage(RuntimeException failure) {\n"
                + "        String message = failure.getMessage();\n"
                + "        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;\n"
                + "    }\n}\n";
        String direct = "final class A {\n    private static String anyName(Throwable failure) {\n"
                + "        return failure.getMessage() == null || failure.getMessage().isBlank()\n"
                + "                ? failure.getClass().getSimpleName() : failure.getMessage();\n    }\n}\n";
        String lambda = "final class A {\n    private String text(Throwable failure) {\n"
                + "        return Optional.ofNullable(failure.getMessage()).filter(value -> !value.isBlank())\n"
                + "                .orElse(failure.getClass().getSimpleName());\n    }\n}\n";
        String namedLikeAMessage = "final class A {\n    private static String safeMessage(RuntimeException failure) {\n"
                + "        String message = options.get(\"message\");\n"
                + "        return message == null || message.isBlank() ? \"x\" : message;\n    }\n}\n";
        String notAParameter = "final class A {\n    private static String safeMessage(String other) {\n"
                + "        return failure.getMessage().isBlank() ? \"x\" : other;\n    }\n}\n";
        String anotherMessage = "final class A {\n    private static String safeMessage(RuntimeException failure) {\n"
                + "        return execution.message().isBlank() ? \"x\" : \"y\";\n    }\n}\n";

        for (String source : List.of(local, direct, lambda)) {
            Scan scan = scan(withDefinition("A.java", source), Map.of());
            assertEquals(List.of(), scan.violations(), source);
            assertEquals(1, scan.throwableMessages(), source);
        }
        for (String source : List.of(namedLikeAMessage, notAParameter, anotherMessage)) {
            Scan scan = scan(withDefinition("A.java", source), Map.of());
            assertEquals(1, scan.violations().size(), source);
            assertEquals(0, scan.throwableMessages(), source);
        }
    }

    @Test
    void aNamedQuestionHoldsExactlyItsCountAndIsRefusedWhenItVanishes() {
        String one = "final class A {\n    private static Optional<Path> envPath(Map<String, String> environment, String key) {\n"
                + "        return Optional.ofNullable(environment.get(key)).map(String::trim)\n"
                + "                .filter(value -> !value.isEmpty()).map(Path::of);\n    }\n}\n";
        String two = one.replace(".map(Path::of);", ".filter(value -> !value.isBlank()).map(Path::of);");
        Map<String, OtherQuestion> named = Map.of("A.java#envPath", new OtherQuestion(1, "an environment variable"));

        assertEquals(List.of(), scan(withDefinition("A.java", one), named).violations());
        assertEquals(List.of("A.java#envPath holds 2 blank tests where 1 is classified (an environment variable): "
                        + "classify the new one or call OptionValue.nonBlank"),
                scan(withDefinition("A.java", two), named).violations());
        assertEquals(List.of("A.java#envPath is classified but holds no blank test: remove the entry"),
                scan(withDefinition("A.java", "final class A {\n}\n"), named).violations());
    }

    @Test
    void aMethodNamedLikeTheDefinitionMustDelegateToIt() {
        String homonym = "final class A {\n    private static Optional<String> nonBlank(String value) {\n"
                + "        return Optional.ofNullable(value).map(String::trim).filter(item -> !item.isEmpty());\n"
                + "    }\n}\n";
        String wrapper = "final class A {\n    private static String nonBlank(String key, String value) {\n"
                + "        return OptionValue.nonBlank(\"--\" + key, value).trim();\n    }\n}\n";
        Map<String, OtherQuestion> named = Map.of("A.java#nonBlank", new OtherQuestion(1, "an environment variable"));

        assertEquals(List.of("A.java:2 A#nonBlank is named like the definition without delegating to it: rename it or "
                        + "call OptionValue.nonBlank"),
                scan(withDefinition("A.java", homonym), named).violations(), "classifying its test does not excuse its name");
        assertEquals(List.of(), scan(withDefinition("A.java", wrapper), Map.of()).violations());
    }

    @Test
    void aTestInACommentOrAStringIsNotABlankTestAndTheDefinitionDecidesPerCodePointWithBothHalves() {
        String mentioned = "final class A {\n    String f(String value) {\n        // value.isBlank()\n"
                + "        log(\"value.trim().isEmpty()\");\n        return value;\n    }\n}\n";
        String wholeString = DEFINITION.replace(
                "value.codePoints().allMatch(c -> c <= ' ' || Character.isWhitespace(c))",
                "value.isBlank() || value.trim().isEmpty()");
        String oneHalf = DEFINITION.replace("c -> c <= ' ' || Character.isWhitespace(c)", "Character::isWhitespace");
        String elsewhereInTheDefinitionFile = DEFINITION.replace("    static Path path(",
                "    static boolean blank(String value) {\n        return value.isBlank();\n    }\n\n    static Path path(");
        String missing = "OptionValue.java nonBlank must decide per code point with both halves: c <= ' ' (what trim() "
                + "removes) and Character.isWhitespace (what isBlank() holds)";
        String whole = " OptionValue#nonBlank does not test every code point against both halves";

        assertEquals(List.of(), scan(withDefinition("A.java", mentioned), Map.of()).violations());
        assertEquals(List.of("OptionValue.java:3" + whole, "OptionValue.java:3" + whole, missing),
                scan(Map.of(DEFINITION_FILE, wholeString), Map.of()).violations());
        assertEquals(List.of("OptionValue.java:3" + whole, missing),
                scan(Map.of(DEFINITION_FILE, oneHalf), Map.of()).violations());
        assertEquals(List.of("OptionValue.java:10 OptionValue#blank tests the blank itself; call OptionValue.nonBlank"),
                scan(Map.of(DEFINITION_FILE, elsewhereInTheDefinitionFile), Map.of()).violations());
        assertEquals(List.of(missing), scan(Map.of(), Map.of()).violations(),
                "a scan that finds no definition proves nothing");
    }

    private static final String DEFINITION = "final class OptionValue {\n"
            + "    static String nonBlank(String option, String value) {\n"
            + "        if (value.codePoints().allMatch(c -> c <= ' ' || Character.isWhitespace(c))) {\n"
            + "            throw new IllegalArgumentException(option);\n        }\n        return value;\n    }\n\n"
            + "    static Path path(String option, String value) {\n        return Path.of(nonBlank(option, value));\n"
            + "    }\n}\n";

    private static Map<String, String> withDefinition(String name, String source) {
        return Map.of(name, source, DEFINITION_FILE, DEFINITION);
    }

    record OtherQuestion(int blankTests, String question) {
    }

    record Scan(int throwableMessages, List<String> violations) {
    }

    static Scan scan(Map<String, String> sources, Map<String, OtherQuestion> otherQuestions) {
        List<String> violations = new ArrayList<>();
        Map<String, Integer> namedCounts = new TreeMap<>();
        boolean definitionFound = false;
        int throwableMessages = 0;
        for (Map.Entry<String, String> source : new TreeMap<>(sources).entrySet()) {
            Unit unit = Unit.of(source.getKey(), source.getValue().replace("\r\n", "\n"));
            boolean definitionFile = unit.name().equals(DEFINITION_FILE);
            for (Block method : unit.blocks()) {
                if (!definitionFile && method.kind() == Kind.METHOD && method.name().equals(DEFINITION_METHOD)
                        && !DELEGATES.matcher(unit.code().substring(method.open(), method.close())).find()) {
                    violations.add(unit.name() + ":" + unit.line(method.open()) + " "
                            + owner(unit, method.open(), method)
                            + " is named like the definition without delegating to it: rename it or call "
                            + "OptionValue.nonBlank");
                }
            }
            Matcher test = BLANK_TEST.matcher(unit.code());
            while (test.find()) {
                String argument = PER_CODE_POINT.matcher(test.group()).find() ? argument(unit, test.end() - 1) : null;
                if (argument != null && !ABOUT_BLANK.matcher(argument).find()) {
                    continue;
                }
                Block method = unit.innermost(test.start(), Kind.METHOD);
                String methodName = method == null ? "?" : method.name();
                String key = unit.name() + "#" + methodName;
                String where = unit.name() + ":" + unit.line(test.start()) + " " + owner(unit, test.start(), method);
                if (definitionFile && methodName.equals(DEFINITION_METHOD)) {
                    if (argument != null && WHAT_TRIM_REMOVES.matcher(argument).find()
                            && WHAT_IS_BLANK_HOLDS.matcher(argument).find()) {
                        definitionFound = true;
                    } else {
                        violations.add(where + " does not test every code point against both halves");
                    }
                } else if (readsTheMessageOfAThrowable(unit, method, test.start())) {
                    throwableMessages++;
                } else if (otherQuestions.containsKey(key)) {
                    namedCounts.merge(key, 1, Integer::sum);
                } else {
                    violations.add(where + " tests the blank itself; call OptionValue.nonBlank");
                }
            }
        }
        if (!definitionFound) {
            violations.add(DEFINITION_FILE + " " + DEFINITION_METHOD + " must decide per code point with both halves: "
                    + "c <= ' ' (what trim() removes) and Character.isWhitespace (what isBlank() holds)");
        }
        new TreeMap<>(otherQuestions).forEach((key, named) -> {
            int found = namedCounts.getOrDefault(key, 0);
            if (found == 0) {
                violations.add(key + " is classified but holds no blank test: remove the entry");
            } else if (found != named.blankTests()) {
                violations.add(key + " holds " + found + " blank tests where " + named.blankTests()
                        + " is classified (" + named.question() + "): classify the new one or call OptionValue.nonBlank");
            }
        });
        return new Scan(throwableMessages, violations);
    }

    private static String owner(Unit unit, int position, Block method) {
        Block type = unit.innermost(position, Kind.TYPE);
        return (type == null || type.name().isEmpty() ? "?" : type.name()) + "#" + (method == null ? "?" : method.name());
    }

    /** The argument of the call whose parenthesis opens at {@code open}, comments blanked, literals kept. */
    private static String argument(Unit unit, int open) {
        int depth = 0;
        for (int index = open; index < unit.code().length(); index++) {
            char character = unit.code().charAt(index);
            if (character == '(') {
                depth++;
            } else if (character == ')' && --depth == 0) {
                return unit.text().substring(open + 1, index);
            }
        }
        return unit.text().substring(open + 1);
    }

    /**
     * Whether the blank test at {@code position} reads {@code p.getMessage()} for a parameter {@code p} of the
     * enclosing method declared as a throwable: directly, through a local declared from it, or through the parameter
     * of a lambda filtering {@code Optional.ofNullable(p.getMessage())} in the same statement.
     */
    private static boolean readsTheMessageOfAThrowable(Unit unit, Block method, int position) {
        if (method == null) {
            return false;
        }
        List<String> parameters = new ArrayList<>();
        Matcher parameter = THROWABLE_PARAMETER.matcher(unit.header(method));
        while (parameter.find()) {
            parameters.add(parameter.group(1));
        }
        Matcher receiver = RECEIVER.matcher(unit.code().substring(Math.max(method.open(), position - 200), position));
        if (parameters.isEmpty() || !receiver.find()) {
            return false;
        }
        String read = receiver.group(1).replaceAll("\\s+", "");
        String body = unit.code().substring(method.open(), position);
        int statement = Math.max(body.lastIndexOf(';'), Math.max(body.lastIndexOf('{'), body.lastIndexOf('}')));
        for (String name : parameters) {
            String message = Pattern.quote(name) + "\\s*\\.\\s*getMessage\\s*\\(\\s*\\)";
            if (read.equals(name + ".getMessage()")
                    || Pattern.compile("\\b" + Pattern.quote(read) + "\\s*=\\s*" + message + "\\s*;").matcher(body).find()
                    || Pattern.compile("ofNullable\\s*\\(\\s*" + message + "\\s*\\)\\s*\\.\\s*filter\\s*\\(\\s*"
                            + Pattern.quote(read) + "\\s*->").matcher(body.substring(statement + 1)).find()) {
                return true;
            }
        }
        return false;
    }
}
