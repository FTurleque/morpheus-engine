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
 * A tool result is either an error built by {@link McpToolFailure} or a success that says so (ADR-0102).
 *
 * <p>{@code apply_change_lifecycle_transition} answered a stale revision, a missing capability and a missing
 * confirmation with a result that never said {@code isError}: nothing in the module arbitrated how a result is
 * built, so the one handler whose refusals are values rather than exceptions built it a third way. This refuses
 * that third way at the source: outside {@link McpToolFailure}, {@code isError} is never anything but the literal
 * {@code false}, and every {@code CallToolResult} is built through the builder with that {@code isError} written
 * out, so a result whose {@code isError} nobody decided cannot exist.</p>
 *
 * <p>It scans text, recursively and over every {@code .java} file under the package, with comments removed
 * first. It fails rather than passes when it finds nothing to scan, and its patterns are proved against
 * synthetic sources below so the scan cannot rot into an empty one.</p>
 *
 * <p><b>What it does not cover.</b> It cannot tell whether a body <em>carries</em> a refusal: a handler that
 * answers {@code isError(false)} around a result whose state says {@code BLOCKED} passes. That is a property of
 * each tool and is tested where the state exists ({@code MorpheusControlledLifecycleMcpToolsTest} reaches every
 * state of the taxonomy). It does not see results assembled outside the SDK builder, another package, or another
 * module ({@code morpheus-mcp-transport} writes JSON-RPC errors, not tool results). Comment removal is a regular
 * expression, not a parser: a comment marker inside a string literal would hide the text that follows it.</p>
 */
class McpResultOwnershipTest {
    private static final Path SOURCES = Path.of("src/main/java/com/morpheus/mcp");
    private static final String OWNER = "McpToolFailure.java";

    /** Any {@code isError(...)} call. Whether its argument is acceptable is decided on the captured text. */
    private static final Pattern IS_ERROR = Pattern.compile("\\.isError\\(([^)]*)\\)");

    /** A result built through the SDK builder: the statement runs from the builder call to its terminating semicolon. */
    private static final Pattern BUILDER_STATEMENT = Pattern.compile("CallToolResult\\s*\\.\\s*builder\\s*\\([^;]*;");

    /** The constructor is a second way to build a result, and one that takes {@code isError} positionally. */
    private static final Pattern CONSTRUCTOR = Pattern.compile("new\\s+(?:McpSchema\\s*\\.\\s*)?CallToolResult\\s*\\(");

    private static final Pattern BLOCK_COMMENT = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL);
    private static final Pattern LINE_COMMENT = Pattern.compile("//[^\\n]*");

    @Test
    void outsideTheOwnerAResultIsNeverAnErrorAndAlwaysSaysSo() throws IOException {
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
                "a tool result is an error only through McpToolFailure, and a success writes isError(false) "
                        + "(ADR-0102): a result that never decided is how a refusal read as a success");
    }

    /** The scan is worth nothing if it is pointed at an empty tree, or at an owner that no longer owns anything. */
    @Test
    void theScanSeesTheHandlersItJudgesAndTheOwnerItExempts() throws IOException {
        List<Path> sources = sources();
        long builders = 0;
        for (Path source : sources) {
            if (!OWNER.equals(source.getFileName().toString())) {
                Matcher matcher = BUILDER_STATEMENT.matcher(withoutComments(Files.readString(source)));
                while (matcher.find()) {
                    builders++;
                }
            }
        }

        assertFalse(sources.isEmpty(), () -> "the MCP sources were not found under " + SOURCES.toAbsolutePath());
        long seen = builders;
        assertTrue(seen >= 5, () -> "only " + seen + " result builders found outside the owner; "
                + "the tool classes were not scanned or the pattern has rotted");
        Path owner = SOURCES.resolve(OWNER);
        assertTrue(Files.isRegularFile(owner), () -> OWNER + " must exist for the exemption to mean anything");
        assertTrue(IS_ERROR.matcher(withoutComments(Files.readString(owner))).find(),
                () -> OWNER + " builds no error result, so the exemption exempts nothing");
    }

    @Test
    void theRuleFlagsWhatItIsWrittenToFlag() {
        assertEquals(1, violationsIn("return McpSchema.CallToolResult.builder().addTextContent(x).isError(true).build();").size());
        assertEquals(1, violationsIn("return McpSchema.CallToolResult.builder().addTextContent(x).isError(!ok).build();").size());
        assertEquals(1, violationsIn("return McpSchema.CallToolResult.builder(List.of(content)).build();").size());
        assertEquals(1, violationsIn("return McpSchema.CallToolResult.builder()\n        .addTextContent(x)\n        .build();").size());
        assertEquals(1, violationsIn("return new McpSchema.CallToolResult(content, true);").size());
        assertEquals(1, violationsIn("return new CallToolResult(content, false);").size());
    }

    @Test
    void theRuleLetsThroughASuccessThatSaysSoAndIgnoresComments() {
        assertEquals(List.of(), violationsIn(
                "return McpSchema.CallToolResult.builder().addTextContent(x).isError(false).build();"));
        assertEquals(List.of(), violationsIn(
                "return McpSchema.CallToolResult.builder(List.of(content))\n        .isError( false )\n        .build();"));
        assertEquals(List.of(), violationsIn(
                "// CallToolResult.builder().isError(true).build();\n/* new CallToolResult(a, true); */\nint x = 1;"));
        assertEquals(List.of(), violationsIn("return McpToolFailure.refusal(body);"));
    }

    static List<String> violationsIn(String source) {
        String code = withoutComments(source);
        List<String> violations = new ArrayList<>();

        Matcher isError = IS_ERROR.matcher(code);
        while (isError.find()) {
            if (!"false".equals(isError.group(1).trim())) {
                violations.add("isError(" + isError.group(1).trim() + ") outside McpToolFailure");
            }
        }
        Matcher builder = BUILDER_STATEMENT.matcher(code);
        while (builder.find()) {
            if (!IS_ERROR.matcher(builder.group()).find()) {
                violations.add("a CallToolResult built without deciding isError");
            }
        }
        Matcher constructor = CONSTRUCTOR.matcher(code);
        while (constructor.find()) {
            violations.add("a CallToolResult built through its constructor");
        }
        return violations;
    }

    private static String withoutComments(String source) {
        String withoutBlocks = BLOCK_COMMENT.matcher(source).replaceAll(" ");
        return LINE_COMMENT.matcher(withoutBlocks).replaceAll(" ");
    }

    private static List<Path> sources() throws IOException {
        try (Stream<Path> files = Files.walk(SOURCES)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".java")).sorted().toList();
        }
    }
}
