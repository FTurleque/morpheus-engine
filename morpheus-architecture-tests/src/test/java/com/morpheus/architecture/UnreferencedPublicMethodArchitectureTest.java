package com.morpheus.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A public method of the product is named somewhere else in the repository, or says why it is public alone.
 *
 * <p>Sixteen public methods were named nowhere, tests included. They sat in the coverage denominator and protected
 * nothing, and one of them, on a security path, built an environment for an MCP peer that the real launch never
 * used.</p>
 *
 * <p>This is a scan of names over the sources, on purpose. A rule on the bytecode -- "this method has a caller" -- was
 * tried first and is the exact form of the question, but the architecture module only sees the compiled main classes
 * of the other modules, not their tests: measured on 12/10/2026, it reported 96 methods, most of them used only by the
 * tests of their own module. The name scan reads every Java file of the repository, tests included. Its known limit is
 * the other way round: a dead method that shares its name with a live one is not seen.</p>
 *
 * <p>The published plugin SDK and the verification tooling are outside the scan: a method of the SDK that MORPHEUS
 * does not call is an API offered to plugins, not dead code.</p>
 */
class UnreferencedPublicMethodArchitectureTest {
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Pattern PUBLIC_METHOD = Pattern.compile(
            "^\\s*public\\s+(?:static\\s+|final\\s+|synchronized\\s+|default\\s+|abstract\\s+)*(?:<[^>]+>\\s+)?"
                    + "[\\w<>\\[\\],.? ]+?\\s+([a-z][A-Za-z0-9_]*)\\s*\\(");

    /** Modules whose public methods are an API for others, or tooling: not judged by MORPHEUS's own references. */
    private static final Set<String> NOT_JUDGED = Set.of(
            "morpheus-provider-sdk", "morpheus-provider-testkit", "morpheus-provider-reference",
            "morpheus-architecture-tests", "morpheus-coverage-report");

    /** Methods that are public and named nowhere else, {@code File.java#method}, each with the reason. */
    private static final Map<String, String> PUBLIC_AND_NAMED_NOWHERE_ELSE = Map.of();

    @Test
    void everyPublicMethodOfAProductModuleIsNamedSomewhereElseOrSaysWhy() throws IOException {
        Path root = repositoryRoot();
        List<Path> sources = javaSources(root);
        assertTrue(sources.size() > 500, "too few Java sources read (" + sources.size() + ")");

        Map<String, Integer> occurrences = new HashMap<>();
        Map<Path, List<String>> lines = new HashMap<>();
        for (Path source : sources) {
            List<String> content = Files.readAllLines(source);
            lines.put(source, content);
            for (String line : content) {
                Matcher identifier = IDENTIFIER.matcher(line);
                while (identifier.find()) {
                    occurrences.merge(identifier.group(), 1, Integer::sum);
                }
            }
        }

        Set<String> namedNowhereElse = new TreeSet<>();
        int examined = 0;
        for (Path source : sources) {
            if (!isJudged(root, source)) {
                continue;
            }
            List<String> content = lines.get(source);
            for (int index = 0; index < content.size(); index++) {
                Matcher declaration = PUBLIC_METHOD.matcher(content.get(index));
                if (!declaration.find() || (index > 0 && content.get(index - 1).contains("@Override"))) {
                    continue;
                }
                examined++;
                if (occurrences.get(declaration.group(1)) == 1) {
                    namedNowhereElse.add(source.getFileName() + "#" + declaration.group(1));
                }
            }
        }
        assertTrue(examined > 300, "too few public methods examined (" + examined + "): the rule would pass on nothing");

        List<String> staleExcuses = new ArrayList<>();
        PUBLIC_AND_NAMED_NOWHERE_ELSE.forEach((method, reason) -> {
            assertFalse(reason.isBlank(), "a public method named nowhere else needs its reason: " + method);
            if (!namedNowhereElse.remove(method)) {
                staleExcuses.add(method);
            }
        });
        assertEquals(List.of(), staleExcuses, "these excuses name a method that is now used or no longer exists");
        assertTrue(namedNowhereElse.isEmpty(), () -> "public method(s) named nowhere else in the repository, tests "
                + "included; remove them, or list each with the reason it is public: " + namedNowhereElse);
    }

    @Test
    void theDeclarationPatternSeesTheShapesTheProductUses() {
        assertEquals("resolve", declared("    public static Settings resolve(Map<String, String> environment) {"));
        assertEquals("page", declared("    public <T> SnapshotPage<T> page("));
        assertEquals("asBoolean", declared("    public Optional<Boolean> asBoolean() {"));
        assertEquals(null, declared("    public record Page(int offset, int limit) {"));
        assertEquals(null, declared("    public SourceScanPolicy {"));
        assertEquals(null, declared("    private static String helper(String value) {"));
    }

    private static String declared(String line) {
        Matcher declaration = PUBLIC_METHOD.matcher(line);
        return declaration.find() ? declaration.group(1) : null;
    }

    private static boolean isJudged(Path root, Path source) {
        Path relative = root.relativize(source);
        String module = relative.getName(0).toString();
        return !NOT_JUDGED.contains(module) && relative.toString().replace('\\', '/').contains("/src/main/java/");
    }

    private static List<Path> javaSources(Path root) throws IOException {
        List<Path> sources = new ArrayList<>();
        try (Stream<Path> modules = Files.list(root)) {
            for (Path module : modules.filter(Files::isDirectory).sorted().toList()) {
                Path src = module.resolve("src");
                if (!module.getFileName().toString().startsWith("morpheus-") || !Files.isDirectory(src)) {
                    continue;
                }
                try (Stream<Path> files = Files.walk(src)) {
                    files.filter(path -> path.toString().endsWith(".java")).sorted().forEach(sources::add);
                }
            }
        }
        return sources;
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
