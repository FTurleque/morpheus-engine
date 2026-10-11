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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every {@code MORPHEUS_*} environment variable the product mentions is named where an operator looks for it.
 *
 * <p>The variable that tells the remote server where to find provider plugins was read by the launcher and named
 * in no guide. Variable names are compile-time constants read through several helper shapes, so there is no single
 * read site and no bytecode trace of a mention: the population is every such literal in the main sources, and a
 * literal that is not an environment variable is excused by name with a reason. No name is built by concatenation
 * today; one that were would escape this scan.</p>
 */
class LaunchEnvironmentDisclosureTest {
    private static final Pattern MORPHEUS_NAME = Pattern.compile("\"(MORPHEUS_[A-Z0-9_]+)\"");
    private static final List<String> OPERATOR_DOCUMENTATION = List.of("docs/user", "docs/developer");

    /** Literals of the same shape that are not environment variables, each with the reason it is one. */
    private static final Map<String, String> NOT_AN_ENVIRONMENT_VARIABLE = Map.of(
            "MORPHEUS_VERSION_TOO_NEW", "refusal code of the update manifest check, not read from the environment",
            "MORPHEUS_VERSION_TOO_OLD", "refusal code of the update manifest check, not read from the environment");

    @Test
    void everyMorpheusEnvironmentVariableTheMainSourcesMentionIsNamedInTheOperatorDocumentation() throws IOException {
        Path root = repositoryRoot();
        Map<String, Path> mentioned = mentionedNames(root);
        String documentation = operatorDocumentation(root);

        assertTrue(mentioned.size() > NOT_AN_ENVIRONMENT_VARIABLE.size(),
                "the scan found no MORPHEUS_* environment variable in the main sources; it must not pass on nothing");

        List<String> undocumented = new ArrayList<>();
        for (Map.Entry<String, Path> entry : mentioned.entrySet()) {
            String name = entry.getKey();
            if (NOT_AN_ENVIRONMENT_VARIABLE.containsKey(name)) {
                continue;
            }
            if (!wholeWord(name).matcher(documentation).find()) {
                undocumented.add(name + " (mentioned by " + root.relativize(entry.getValue()) + ")");
            }
        }

        assertTrue(undocumented.isEmpty(),
                "environment variable(s) read by the product and named in no page under " + OPERATOR_DOCUMENTATION
                        + ": " + undocumented);
    }

    @Test
    void everyExcusedLiteralStillExistsAndCarriesAReason() throws IOException {
        Map<String, Path> mentioned = mentionedNames(repositoryRoot());

        for (Map.Entry<String, String> excused : NOT_AN_ENVIRONMENT_VARIABLE.entrySet()) {
            assertFalse(excused.getValue().isBlank(), "an excuse without a reason: " + excused.getKey());
            assertTrue(mentioned.containsKey(excused.getKey()),
                    "an excuse names a literal no main source mentions any more: " + excused.getKey());
        }
    }

    @Test
    void aLongerNameDoesNotDocumentAShorterOne() {
        Pattern name = wholeWord("MORPHEUS_SERVER_PROVIDER_PLUGIN_DIR");

        assertFalse(name.matcher("set MORPHEUS_SERVER_PROVIDER_PLUGIN_DIRECTORY to a path").find());
        assertFalse(name.matcher("set X_MORPHEUS_SERVER_PROVIDER_PLUGIN_DIR to a path").find());
        assertTrue(name.matcher("`MORPHEUS_SERVER_PROVIDER_PLUGIN_DIR` alternative").find());
    }

    private static Pattern wholeWord(String name) {
        return Pattern.compile("(?<![A-Z0-9_])" + Pattern.quote(name) + "(?![A-Z0-9_])");
    }

    private static Map<String, Path> mentionedNames(Path root) throws IOException {
        Map<String, Path> mentioned = new TreeMap<>();
        try (var modules = Files.list(root)) {
            for (Path module : modules.sorted().toList()) {
                Path sources = module.resolve("src/main/java");
                if (Files.isDirectory(sources)) {
                    collectNames(sources, mentioned);
                }
            }
        }
        return mentioned;
    }

    private static void collectNames(Path sources, Map<String, Path> mentioned) throws IOException {
        try (var files = Files.walk(sources)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
                Matcher literal = MORPHEUS_NAME.matcher(Files.readString(file));
                while (literal.find()) {
                    mentioned.putIfAbsent(literal.group(1), file);
                }
            }
        }
    }

    private static String operatorDocumentation(Path root) throws IOException {
        StringBuilder pages = new StringBuilder();
        for (String directory : OPERATOR_DOCUMENTATION) {
            try (var files = Files.walk(root.resolve(directory))) {
                for (Path file : files.filter(path -> path.toString().endsWith(".md")).sorted().toList()) {
                    pages.append(Files.readString(file)).append('\n');
                }
            }
        }
        return pages.toString();
    }

    private static Path repositoryRoot() throws IOException {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("contracts/public-surfaces.tsv"))
                    && Files.isRegularFile(current.resolve("pom.xml"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IOException("cannot locate MORPHEUS repository root from " + Path.of("").toAbsolutePath());
    }
}
