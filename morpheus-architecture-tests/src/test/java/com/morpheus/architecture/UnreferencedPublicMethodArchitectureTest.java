package com.morpheus.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
 * tests of their own module. The name scan reads every Java file of the repository, tests included.</p>
 *
 * <p>A name is looked for in code only: a mention in a comment, in documentation or in a string calls nothing. And a
 * name is referenced when it occurs more often than it is declared, so two overloads nothing calls do not vouch for
 * each other. The known limit is what remains: a dead public method that shares its name with a method of another
 * visibility, or with an overriding one, is not seen.</p>
 *
 * <p>The published plugin SDK and the verification tooling are outside the scan: a method of the SDK that MORPHEUS
 * does not call is an API offered to plugins, not dead code.</p>
 */
class UnreferencedPublicMethodArchitectureTest {
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Pattern PUBLIC_METHOD = Pattern.compile(
            "^[ \\t]*public\\s+(?:static\\s+|final\\s+|synchronized\\s+|default\\s+|abstract\\s+)*(?:<[^>]+>\\s+)?"
                    + "[\\w<>\\[\\],.?\\s]+?\\s+([a-z][A-Za-z0-9_]*)\\s*\\(",
            Pattern.MULTILINE);
    /** What is not code: a text block, a string, a character, a block comment, a line comment -- in that order. */
    private static final Pattern NOT_CODE = Pattern.compile(
            "\"\"\"[\\s\\S]*?\"\"\"|\"(?:\\\\.|[^\"\\\\\\n])*\"|'(?:\\\\.|[^'\\\\\\n])+'|/\\*[\\s\\S]*?\\*/|//[^\\n]*");

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

        Map<String, String> judged = new LinkedHashMap<>();
        List<String> others = new ArrayList<>();
        for (Path source : sources) {
            String content = Files.readString(source);
            if (isJudged(root, source)) {
                judged.put(root.relativize(source).toString().replace('\\', '/'), content);
            } else {
                others.add(content);
            }
        }
        Scan scan = scan(judged, others);
        assertTrue(scan.examined() > 300,
                "too few public methods examined (" + scan.examined() + "): the rule would pass on nothing");

        Set<String> namedNowhereElse = new TreeSet<>(scan.namedNowhereElse());
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
        assertEquals("render", declared("    public Map<String,\n            List<String>>\n        render(String value) {"));
        assertEquals(null, declared("    public record Page(int offset, int limit) {"));
        assertEquals(null, declared("    public SourceScanPolicy {"));
        assertEquals(null, declared("    public static final String NAME = compute(\"x\");"));
        assertEquals(null, declared("    private static String helper(String value) {"));
    }

    @Test
    void aMethodNamedOnlyByItsOwnDeclarationIsReportedAndACallerClearsIt() {
        String declaring = "class A {\n    public String orphan() {\n        return \"\";\n    }\n}\n";

        assertEquals(Set.of("A.java#orphan"), scan(Map.of("m/src/main/java/A.java", declaring), List.of())
                .namedNowhereElse());
        assertEquals(Set.of(), scan(Map.of("m/src/main/java/A.java", declaring),
                List.of("class ATest { void t() { new A().orphan(); } }")).namedNowhereElse());
    }

    @Test
    void aMentionInACommentInDocumentationOrInAStringIsNotAReference() {
        String declaring = "class A {\n    /** See {@link #orphan}. */\n    public String orphan() {\n"
                + "        // orphan is kept for later\n        return \"orphan\" + 'o';\n    }\n}\n";
        String elsewhere = "class B {\n    /* A.orphan() */\n    String text = \"\"\"\n        orphan()\n        \"\"\";\n}\n";

        assertEquals(Set.of("A.java#orphan"),
                scan(Map.of("m/src/main/java/A.java", declaring), List.of(elsewhere)).namedNowhereElse());
    }

    @Test
    void overloadsNothingCallsDoNotVouchForEachOther() {
        String declaring = "class A {\n    public int page(int offset) {\n        return 0;\n    }\n\n"
                + "    public int page(int offset, int limit) {\n        return 0;\n    }\n}\n";

        assertEquals(Set.of("A.java#page"),
                scan(Map.of("m/src/main/java/A.java", declaring), List.of()).namedNowhereElse());
        assertEquals(Set.of(), scan(Map.of("m/src/main/java/A.java", declaring),
                List.of("class C { int n = new A().page(1); }")).namedNowhereElse());
    }

    @Test
    void anOverridingMethodIsNotJudgedWhateverSeparatesItFromItsAnnotation() {
        String declaring = "class A implements Runnable {\n    @Override\n    @SuppressWarnings(\"x\")\n"
                + "    public void run() {\n    }\n\n    @Override public String toString() {\n        return \"\";\n    }\n}\n";

        Scan scan = scan(Map.of("m/src/main/java/A.java", declaring), List.of());

        assertEquals(Set.of(), scan.namedNowhereElse());
        assertEquals(0, scan.examined());
    }

    /** What the scan found: the methods named nowhere else, and how many declarations it judged. */
    private record Scan(Set<String> namedNowhereElse, int examined) {
    }

    private static Scan scan(Map<String, String> judged, List<String> others) {
        Map<String, Integer> occurrences = new HashMap<>();
        Map<String, String> judgedCode = new LinkedHashMap<>();
        judged.forEach((path, content) -> judgedCode.put(path, code(content)));
        Stream.concat(judgedCode.values().stream(), others.stream().map(UnreferencedPublicMethodArchitectureTest::code))
                .forEach(code -> {
                    Matcher identifier = IDENTIFIER.matcher(code);
                    while (identifier.find()) {
                        occurrences.merge(identifier.group(), 1, Integer::sum);
                    }
                });

        Map<String, Integer> declarations = new HashMap<>();
        Map<String, Set<String>> declaredIn = new LinkedHashMap<>();
        int examined = 0;
        for (Map.Entry<String, String> source : judgedCode.entrySet()) {
            String fileName = source.getKey().substring(source.getKey().lastIndexOf('/') + 1);
            Matcher declaration = PUBLIC_METHOD.matcher(source.getValue());
            while (declaration.find()) {
                if (overrides(source.getValue(), declaration)) {
                    continue;
                }
                examined++;
                declarations.merge(declaration.group(1), 1, Integer::sum);
                declaredIn.computeIfAbsent(declaration.group(1), name -> new TreeSet<>())
                        .add(fileName + "#" + declaration.group(1));
            }
        }

        Set<String> namedNowhereElse = new TreeSet<>();
        declarations.forEach((name, declared) -> {
            if (occurrences.get(name).equals(declared)) {
                namedNowhereElse.addAll(declaredIn.get(name));
            }
        });
        return new Scan(namedNowhereElse, examined);
    }

    /** The source with everything that is not code blanked, line breaks kept. */
    private static String code(String source) {
        return NOT_CODE.matcher(source).replaceAll(match -> match.group().replaceAll("[^\\n]", " "));
    }

    /**
     * True when {@code @Override} is among the annotation lines just above the declaration. One written on the
     * declaration's own line needs no test: the pattern only matches a line that starts with {@code public}.
     */
    private static boolean overrides(String code, Matcher declaration) {
        int end = code.lastIndexOf('\n', declaration.start()) + 1;
        while (end > 0) {
            int start = code.lastIndexOf('\n', end - 2) + 1;
            String line = code.substring(start, end).strip();
            if (!line.startsWith("@")) {
                return false;
            }
            if (line.startsWith("@Override")) {
                return true;
            }
            end = start;
        }
        return false;
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
