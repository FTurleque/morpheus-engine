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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Argument reading and failure mapping stay in one place, checked in the sources rather than hoped for.
 *
 * <p>Twelve tool classes each grew their own {@code requiredString} and their own {@code safeMessage}. Nothing
 * was wrong with any single copy on the day it was written; what went wrong is that they were edited
 * separately afterwards, until the same fault produced five different sentences. Extracting them fixes the
 * copies that existed. This refuses the next one.</p>
 *
 * <p>It scans text, the way {@code CoverageScaleSeparationTest} does, because that is the only way to catch a
 * helper that compiles perfectly well and simply should not exist.</p>
 */
class McpArgumentHelperOwnershipTest {
    private static final Path SOURCES = Path.of("src/main/java/com/morpheus/mcp");

    /** The two classes ADR-0102 makes responsible for these names. */
    private static final List<String> OWNERS = List.of("McpArguments.java", "McpToolFailure.java");

    /**
     * A method declaration of one of the names the two owners provide: an indented line opening with at least
     * one modifier, then a return type, then the name. A call site never opens a line that way, so
     * {@code McpArguments.requiredString(...)} inside a handler is left alone -- which is the point.
     */
    private static final Pattern HELPER_DECLARATION = Pattern.compile(
            "(?m)^ +(?:(?:private|protected|public|static|final|abstract)[ ]+)+"
                    + "[A-Za-z0-9_<>,.\\[\\]? ]+[ ]+"
                    + "(requiredString|optionalString|optionalText|requiredLong|requiredBoolean|requiredInteger"
                    + "|optionalInteger|optionalBoolean|optionalInt|intValue|longValue|doubleValue|integral"
                    + "|stringList|stringMap|stringKeyMap|stringObjectMap|nestedObject|rejectUnknown|safeMessage)"
                    + "[ ]*\\(");

    @Test
    void noToolClassDeclaresItsOwnArgumentReaderOrFailureMapper() throws IOException {
        List<String> offenders = new ArrayList<>();
        for (Path source : sources()) {
            String fileName = source.getFileName().toString();
            if (OWNERS.contains(fileName)) {
                continue;
            }
            Matcher matcher = HELPER_DECLARATION.matcher(Files.readString(source));
            while (matcher.find()) {
                offenders.add(fileName + " declares " + matcher.group(1) + "(...)");
            }
        }

        assertEquals(List.of(), offenders,
                "argument reading and failure mapping belong to McpArguments and McpToolFailure (ADR-0102); "
                        + "a private copy is how the five divergent messages happened the first time");
    }

    /** The scan is worth nothing if it is pointed at an empty directory or the wrong names. */
    @Test
    void theScanSeesTheOwnersItDeliberatelySkips() throws IOException {
        List<Path> sources = sources();

        assertTrue(sources.size() > 10, () -> "the MCP sources were not found: " + sources);
        for (String owner : OWNERS) {
            Path source = SOURCES.resolve(owner);
            assertTrue(Files.isRegularFile(source), () -> owner + " must exist for the exemption to mean anything");
            assertTrue(HELPER_DECLARATION.matcher(Files.readString(source)).find(),
                    () -> owner + " declares none of the names it is supposed to own; the pattern has rotted");
        }
    }

    private static List<Path> sources() throws IOException {
        try (Stream<Path> files = Files.list(SOURCES)) {
            return files.filter(path -> path.getFileName().toString().endsWith(".java")).sorted().toList();
        }
    }
}
