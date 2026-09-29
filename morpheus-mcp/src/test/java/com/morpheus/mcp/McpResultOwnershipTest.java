package com.morpheus.mcp;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A tool result is either an error built by {@link McpToolFailure} or a success built through the SDK builder
 * with its {@code isError} written out (ADR-0102).
 *
 * <p>{@code apply_change_lifecycle_transition} answered a stale revision, a missing capability and a missing
 * confirmation with a result that never said {@code isError}: nothing in the module arbitrated how a result is
 * built, so the one handler whose refusals are values rather than exceptions built it a third way. This refuses
 * that third way at the source. Outside {@link McpToolFailure}: every {@code CallToolResult.builder(...)} call
 * chain decides {@code isError} itself and decides the literal {@code false}; {@code isError} is never called
 * with anything else; and the other ways to reach a builder or a result (static import of the builder, its
 * {@code Builder} type, the constructor) are refused, so the shape above is the only one left to check.</p>
 *
 * <p>It reads code, not text: comments are removed and string literals emptied by a small scanner that knows
 * text blocks and character literals, so a {@code //} inside a string does not swallow the rest of a line. Each
 * builder call is judged on its own chain (the calls that follow it, up to the end of the expression), which is
 * why two builders in one statement, or a {@code ;} inside a lambda argument, do not blur the verdict. It is
 * recursive over every {@code .java} file under the package, refuses an empty scan, and its rules are proved
 * against synthetic sources below in both directions.</p>
 *
 * <p><b>What it does not cover.</b></p>
 * <ul>
 *   <li>Whether a body <em>carries</em> a refusal. A handler that answers {@code isError(false)} around a result
 *   whose state says {@code BLOCKED} passes; that is a property of each tool, tested where the state exists
 *   ({@code MorpheusControlledLifecycleMcpToolsTest} reaches every state of the taxonomy).</li>
 *   <li>A builder held in a variable and decided in a later statement: the chain ends at the semicolon, so it is
 *   refused as undecided, which is conservative rather than exact.</li>
 *   <li>Results assembled outside this module's main sources: another package tree, another module
 *   ({@code morpheus-mcp-transport} writes JSON-RPC errors, not tool results), reflection, or a helper that
 *   returns a builder.</li>
 *   <li>Unicode escapes standing for a quote, which the scanner does not decode, and a
 *   {@code McpToolFailure.refusal(...)} call fed a body that is not a refusal.</li>
 *   <li>That the tools judged are the tools served: it reads sources, not the registered specifications.</li>
 * </ul>
 */
class McpResultOwnershipTest {
    private static final Path SOURCES = Path.of("src/main/java/com/morpheus/mcp");
    private static final String OWNER = "McpToolFailure.java";

    private static final Pattern BUILDER_CALL = Pattern.compile("CallToolResult\\s*\\.\\s*builder\\s*(?=\\()");
    private static final Pattern BUILDER_TYPE = Pattern.compile("CallToolResult\\s*\\.\\s*Builder\\b");
    private static final Pattern STATIC_IMPORT = Pattern.compile("import\\s+static\\s+[\\w.]*CallToolResult\\b");
    private static final Pattern CONSTRUCTOR = Pattern.compile("new\\s+(?:McpSchema\\s*\\.\\s*)?CallToolResult\\s*\\(");
    private static final Pattern IS_ERROR_CALL = Pattern.compile("\\.\\s*isError\\s*(?=\\()");
    private static final Pattern RESULT_RETURNING_METHOD = Pattern.compile("CallToolResult\\s+\\w+\\s*\\(");

    @Test
    void outsideTheOwnerAResultIsNeverAnErrorAndAlwaysDecidesItself() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path source : sources()) {
            String fileName = source.getFileName().toString();
            if (OWNER.equals(fileName)) {
                continue;
            }
            for (String violation : violationsIn(Files.readString(source))) {
                offenders.add(fileName + ": " + violation);
            }
        }

        assertEquals(List.of(), offenders,
                "a tool result is an error only through McpToolFailure, and every other result builder writes "
                        + "isError(false) itself (ADR-0102): a result that never decided is how a refusal read "
                        + "as a success");
    }

    /**
     * The scan is worth nothing if it read nothing. Rather than an arbitrary minimum, this ties it to the code it
     * exists to judge: every class that declares a method returning a {@code CallToolResult} must have had a
     * builder judged in it, or route through the owner.
     */
    @Test
    void theScanJudgedSomethingInEveryClassThatReturnsAToolResult() throws IOException {
        List<String> unread = new ArrayList<>();
        int handlers = 0;
        for (Path source : sources()) {
            String fileName = source.getFileName().toString();
            if (OWNER.equals(fileName)) {
                continue;
            }
            String code = code(Files.readString(source));
            if (RESULT_RETURNING_METHOD.matcher(code).find()) {
                handlers++;
                if (!BUILDER_CALL.matcher(code).find() && !code.contains("McpToolFailure.")) {
                    unread.add(fileName);
                }
            }
        }

        assertTrue(handlers > 0, () -> "no class returning a CallToolResult was found under " + SOURCES.toAbsolutePath());
        assertEquals(List.of(), unread, "classes that return a tool result and were judged on nothing");
    }

    /** The exemption is only worth granting to a class that really is the single place an error is built. */
    @Test
    void theOwnerSetsIsErrorTrueExactlyOnce() throws IOException {
        Path owner = SOURCES.resolve(OWNER);
        assertTrue(Files.isRegularFile(owner), () -> OWNER + " must exist for the exemption to mean anything");
        Matcher errors = Pattern.compile("\\.\\s*isError\\s*\\(\\s*true\\s*\\)").matcher(code(Files.readString(owner)));
        int count = 0;
        while (errors.find()) {
            count++;
        }

        assertEquals(1, count, OWNER + " must build the one error result every handler goes through");
    }

    @Test
    void theRuleFlagsWhatItIsWrittenToFlag() {
        assertViolations(1, "return McpSchema.CallToolResult.builder().addTextContent(x).isError(true).build();");
        assertViolations(1, "return McpSchema.CallToolResult.builder().addTextContent(x).isError(!ok).build();");
        assertViolations(1, "return McpSchema.CallToolResult.builder(List.of(content)).build();");
        assertViolations(1, "return McpSchema.CallToolResult.builder()\n        .addTextContent(x)\n        .build();");
        assertViolations(1, "return new McpSchema.CallToolResult(content, true);");
        assertViolations(1, "return new CallToolResult(content, false);");
        assertViolations(1, "import static io.modelcontextprotocol.spec.McpSchema.CallToolResult.builder;");
        assertViolations(1, "import static io.modelcontextprotocol.spec.McpSchema.CallToolResult.*;");
        assertViolations(1, "var b = new McpSchema.CallToolResult.Builder();");
        assertViolations(1, "import io.modelcontextprotocol.spec.McpSchema.CallToolResult.Builder;\nBuilder b;");
    }

    @Test
    void eachBuilderInAStatementIsJudgedOnItsOwn() {
        assertViolations(1, "return ok ? CallToolResult.builder().isError(false).build() : CallToolResult.builder().build();");
        assertViolations(1, "return ok ? CallToolResult.builder().build() : CallToolResult.builder().isError(false).build();");
        assertViolations(0, "return ok ? CallToolResult.builder().isError(false).build()"
                + " : CallToolResult.builder().isError(false).build();");
        assertViolations(1, "return CallToolResult.builder().addTextContent(x.isError(false)).build();");
    }

    @Test
    void aSemicolonInsideAnArgumentDoesNotEndTheChain() {
        assertViolations(0, "return CallToolResult.builder().addTextContent(text(() -> { int a = 1; return \"x\"; }))"
                + ".isError(false).build();");
        assertViolations(1, "return CallToolResult.builder().addTextContent(text(() -> { int a = 1; return \"x\"; }))"
                + ".build();");
    }

    @Test
    void theScannerReadsCodeNotText() {
        assertViolations(0, "// CallToolResult.builder().isError(true).build();\n/* new CallToolResult(a, true); */\nint x = 1;");
        assertViolations(0, "String s = \"CallToolResult.builder().isError(true)\";");
        assertViolations(0, "String s = \"\"\"\n  CallToolResult.builder().isError(true)\n  \"\"\";");
        assertViolations(1, "String url = \"http://x\"; return CallToolResult.builder().build();");
        assertViolations(1, "char c = '\"'; return CallToolResult.builder().build();");
        assertViolations(0, "return CallToolResult.builder().addTextContent(\"a // b\").isError(false).build();");
    }

    @Test
    void aSuccessThatSaysSoAndACallIntoTheOwnerAreLeftAlone() {
        assertViolations(0, "return McpSchema.CallToolResult.builder().addTextContent(x).isError(false).build();");
        assertViolations(0, "return McpSchema.CallToolResult.builder(List.of(content))\n        .isError( false )\n        .build();");
        assertViolations(0, "return McpToolFailure.refusal(body);");
    }

    private static void assertViolations(int expected, String source) {
        assertEquals(expected, violationsIn(source).size(), () -> source + " -> " + violationsIn(source));
    }

    static List<String> violationsIn(String source) {
        String code = code(source);
        List<String> violations = new ArrayList<>();

        if (STATIC_IMPORT.matcher(code).find()) {
            violations.add("a static import of CallToolResult members hides the builder from this scan");
        }
        if (BUILDER_TYPE.matcher(code).find()) {
            violations.add("CallToolResult.Builder used directly");
        }
        Matcher constructor = CONSTRUCTOR.matcher(code);
        while (constructor.find()) {
            violations.add("a CallToolResult built through its constructor");
        }

        Matcher builder = BUILDER_CALL.matcher(code);
        while (builder.find()) {
            List<String> decisions = chainArguments(code, builder.end(), "isError");
            if (decisions.isEmpty()) {
                violations.add("a CallToolResult built without deciding isError");
            }
        }
        Matcher isError = IS_ERROR_CALL.matcher(code);
        while (isError.find()) {
            String argument = argument(code, isError.end());
            if (!"false".equals(argument)) {
                violations.add("isError(" + argument + ") outside McpToolFailure");
            }
        }
        return violations;
    }

    /**
     * The arguments of every call named {@code method} in the chain that starts at {@code open}, an opening
     * parenthesis: {@code builder(...).a(...).b(...)}. Only calls made on the chain count, not calls nested in
     * an argument.
     */
    private static List<String> chainArguments(String code, int open, String method) {
        List<String> found = new ArrayList<>();
        int at = open;
        while (true) {
            int close = closing(code, at);
            if (close < 0) {
                return found;
            }
            int next = skipSpaces(code, close + 1);
            if (next >= code.length() || code.charAt(next) != '.') {
                return found;
            }
            int nameStart = skipSpaces(code, next + 1);
            int nameEnd = nameStart;
            while (nameEnd < code.length() && Character.isJavaIdentifierPart(code.charAt(nameEnd))) {
                nameEnd++;
            }
            int paren = skipSpaces(code, nameEnd);
            if (paren >= code.length() || code.charAt(paren) != '(') {
                return found;
            }
            if (code.substring(nameStart, nameEnd).equals(method)) {
                found.add(argument(code, paren));
            }
            at = paren;
        }
    }

    private static String argument(String code, int open) {
        int close = closing(code, open);
        return close < 0 ? "" : code.substring(open + 1, close).trim();
    }

    /** The index of the parenthesis closing the one at {@code open}, or -1. Strings are already emptied. */
    private static int closing(String code, int open) {
        int depth = 0;
        for (int index = open; index < code.length(); index++) {
            char current = code.charAt(index);
            if (current == '(') {
                depth++;
            } else if (current == ')' && --depth == 0) {
                return index;
            }
        }
        return -1;
    }

    private static int skipSpaces(String code, int from) {
        int index = from;
        while (index < code.length() && Character.isWhitespace(code.charAt(index))) {
            index++;
        }
        return index;
    }

    /**
     * The source with comments turned into spaces and the inside of string, text-block and character literals
     * emptied (the quotes stay). Offsets are preserved so a violation still points where it was written.
     */
    static String code(String source) {
        StringBuilder out = new StringBuilder(source.length());
        int index = 0;
        int length = source.length();
        while (index < length) {
            char current = source.charAt(index);
            if (source.startsWith("//", index)) {
                while (index < length && source.charAt(index) != '\n') {
                    out.append(' ');
                    index++;
                }
            } else if (source.startsWith("/*", index)) {
                int end = source.indexOf("*/", index + 2);
                int stop = end < 0 ? length : end + 2;
                for (; index < stop; index++) {
                    out.append(source.charAt(index) == '\n' ? '\n' : ' ');
                }
            } else if (source.startsWith("\"\"\"", index)) {
                out.append("\"\"\"");
                index += 3;
                while (index < length && !source.startsWith("\"\"\"", index)) {
                    if (source.charAt(index) == '\\') {
                        out.append(' ');
                        index++;
                    }
                    out.append(index < length && source.charAt(index) == '\n' ? '\n' : ' ');
                    index++;
                }
                if (index < length) {
                    out.append("\"\"\"");
                    index += 3;
                }
            } else if (current == '"' || current == '\'') {
                out.append(current);
                index++;
                while (index < length && source.charAt(index) != current && source.charAt(index) != '\n') {
                    if (source.charAt(index) == '\\') {
                        out.append(' ');
                        index++;
                    }
                    if (index < length) {
                        out.append(' ');
                        index++;
                    }
                }
                if (index < length && source.charAt(index) == current) {
                    out.append(current);
                    index++;
                }
            } else {
                out.append(current);
                index++;
            }
        }
        return out.toString();
    }

    private static List<Path> sources() throws IOException {
        try (Stream<Path> files = Files.walk(SOURCES)) {
            List<Path> sources = files.filter(path -> path.getFileName().toString().endsWith(".java")).sorted().toList();
            assertFalse(sources.isEmpty(), () -> "the MCP sources were not found under " + SOURCES.toAbsolutePath());
            return sources;
        }
    }
}
