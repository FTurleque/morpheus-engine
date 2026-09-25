package com.morpheus.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * An option the CLI does not read is refused, not ignored.
 *
 * <p>{@code SimpleOptions.parse} accepts any {@code --key value} pair; only {@code rejectUnknown} refuses the
 * unknown one, and it is optional. One adapter never called it, so {@code portfolio references --projet X}
 * emptied the optional project filter and returned every reference of the portfolio, exit code 0. The rule is
 * textual because the proposition is about a call that must <em>follow</em> another in the same method, which no
 * dependency rule over compiled classes can express.</p>
 */
class CliOptionParsingRefusesUnknownOptionsTest {

    private static final String PARSE = "SimpleOptions.parse(";
    private static final String GUARD = "rejectUnknown(";
    private static final String METHOD_END = "\n    }\n";

    @Test
    void everySimpleOptionsParseIsFollowedByRejectUnknownInTheSameMethod() throws IOException {
        List<String> unguarded = new ArrayList<>();
        int parseSites = 0;
        for (Path source : cliSources()) {
            String text = Files.readString(source).replace("\r\n", "\n");
            parseSites += count(text);
            for (int line : unguardedParseLines(text)) {
                unguarded.add(source.getFileName() + ":" + line);
            }
        }
        assertTrue(parseSites > 0, "the scan found no SimpleOptions.parse call: the rule would be vacuous");
        assertEquals(List.of(), unguarded,
                "SimpleOptions.parse without rejectUnknown in the same method accepts a misspelled option silently");
    }

    @Test
    void theScannerSeesAParseThatIsNeverFollowedByTheGuard() {
        String unguarded = "final class A {\n    int run() {\n        SimpleOptions o = SimpleOptions.parse(t);\n"
                + "        return o.required(\"x\").length();\n    }\n\n    int other() {\n"
                + "        o.rejectUnknown(Set.of());\n        return 0;\n    }\n}\n";
        String guarded = "final class A {\n    int run() {\n        SimpleOptions o = SimpleOptions.parse(t);\n"
                + "        o.rejectUnknown(Set.of());\n        return 0;\n    }\n}\n";

        assertEquals(List.of(3), unguardedParseLines(unguarded),
                "a guard in a different method must not satisfy the parse of this one");
        assertFalse(unguardedParseLines(guarded).contains(3));
    }

    private static List<Integer> unguardedParseLines(String text) {
        List<Integer> lines = new ArrayList<>();
        int from = 0;
        while ((from = text.indexOf(PARSE, from)) >= 0) {
            int end = text.indexOf(METHOD_END, from);
            String rest = end < 0 ? text.substring(from) : text.substring(from, end);
            if (!rest.contains(GUARD)) {
                lines.add(1 + (int) text.substring(0, from).chars().filter(c -> c == '\n').count());
            }
            from += PARSE.length();
        }
        return lines;
    }

    private static int count(String text) {
        int total = 0;
        int from = 0;
        while ((from = text.indexOf(PARSE, from)) >= 0) {
            total++;
            from += PARSE.length();
        }
        return total;
    }

    private static List<Path> cliSources() throws IOException {
        Path directory = repoRoot().resolve("morpheus-cli/src/main/java");
        try (Stream<Path> tree = Files.walk(directory)) {
            return tree.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.getFileName().toString().equals("SimpleOptions.java"))
                    .sorted()
                    .toList();
        }
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
