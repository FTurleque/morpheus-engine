package com.morpheus.architecture;

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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A warning suppressed in the product's sources says why, where it is suppressed.
 *
 * <p>{@code config/spotbugs-exclude.xml} requires that an entry "carries a comment saying why the finding is wrong for
 * that code". Silencing the same kind of finding inline required nothing: a reviewer could not tell a cast the call
 * site proves from a cast someone assumed. The rule is the same for both now.</p>
 *
 * <p>Textual by nature: no rule on the bytecode sees a comment. It cannot judge that a reason is good; it makes the
 * reason exist, next to the annotation, where a reviewer reads it. The tests' own suppressions are not judged: they
 * hide nothing from a caller of the product.</p>
 */
class WarningSuppressionJustificationTest {
    private static final Pattern SUPPRESSION = Pattern.compile("@SuppressWarnings\\(([^)]*)\\)(.*)$");

    @Test
    void everySuppressedWarningOfTheProductStatesItsReason() throws IOException {
        Path root = RepositoryRoot.locate();
        List<String> unjustified = new ArrayList<>();
        int suppressions = 0;
        for (Path source : mainSources(root)) {
            List<String> lines = Files.readAllLines(source);
            for (int index = 0; index < lines.size(); index++) {
                Matcher suppression = SUPPRESSION.matcher(lines.get(index));
                if (!suppression.find()) {
                    continue;
                }
                suppressions++;
                if (!isJustified(lines, index)) {
                    unjustified.add(root.relativize(source).toString().replace('\\', '/') + ":" + (index + 1));
                }
            }
        }

        assertTrue(suppressions > 5, "too few suppressions read (" + suppressions + "): the rule would pass on nothing");
        assertEquals(List.of(), unjustified, "a @SuppressWarnings under src/main/java must carry, on its own line or "
                + "just above it, a comment that says why the warning is wrong or accepted for that code");
    }

    @Test
    void aReasonIsReadOnTheLineOrJustAboveAndTheRuleKeyAloneIsNotOne() {
        assertTrue(isJustified(List.of("@SuppressWarnings(\"java:S106\") // System.out is the protocol stream."), 0));
        assertTrue(isJustified(List.of("// the value was checked to be a Map just above", "@SuppressWarnings(\"unchecked\")"), 1));
        assertTrue(isJustified(List.of("// catches Error on purpose: the lease must be released",
                "@Override", "@SuppressWarnings(\"java:S1181\")"), 2), "another annotation may sit in between");
        assertTrue(isJustified(List.of("     * released instead of waiting forever.", "     */",
                "@SuppressWarnings(\"java:S1181\")"), 2), "the end of a documentation comment is a comment");

        assertTrue(!isJustified(List.of("    }", "", "@SuppressWarnings(\"unchecked\")"), 2));
        assertTrue(!isJustified(List.of("int x = 1;", "@SuppressWarnings(\"unchecked\")"), 1));
        assertTrue(!isJustified(List.of("@SuppressWarnings(\"unchecked\") // unchecked"), 0),
                "repeating the key of the rule says nothing");
        assertTrue(!isJustified(List.of("// java:S1181", "@SuppressWarnings(\"java:S1181\")"), 1));
        assertTrue(!isJustified(List.of("//", "@SuppressWarnings(\"unchecked\")"), 1));
    }

    /** A reason on the annotation's line, or on the nearest line above that is not another annotation. */
    private static boolean isJustified(List<String> lines, int index) {
        Matcher suppression = SUPPRESSION.matcher(lines.get(index));
        if (!suppression.find()) {
            throw new IllegalArgumentException("not a suppression: " + lines.get(index));
        }
        String key = suppression.group(1).replace("\"", "").strip();
        String trailing = suppression.group(2).strip();
        if (trailing.startsWith("//") && saysSomething(trailing.substring(2), key)) {
            return true;
        }
        int above = index - 1;
        while (above >= 0 && lines.get(above).strip().startsWith("@")) {
            above--;
        }
        if (above < 0) {
            return false;
        }
        String line = lines.get(above).strip();
        if (line.startsWith("//")) {
            return saysSomething(line.substring(2), key);
        }
        return line.endsWith("*/");
    }

    private static boolean saysSomething(String comment, String key) {
        String words = comment.replace(key, "").replaceAll("[^A-Za-z]", "");
        return words.length() >= 12;
    }

    private static List<Path> mainSources(Path root) throws IOException {
        List<Path> sources = new ArrayList<>();
        try (Stream<Path> modules = Files.list(root)) {
            for (Path module : modules.filter(Files::isDirectory).sorted().toList()) {
                Path main = module.resolve("src/main/java");
                if (!module.getFileName().toString().startsWith("morpheus-") || !Files.isDirectory(main)) {
                    continue;
                }
                try (Stream<Path> files = Files.walk(main)) {
                    files.filter(path -> path.toString().endsWith(".java")).sorted().forEach(sources::add);
                }
            }
        }
        return sources;
    }
}
