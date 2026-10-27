package com.morpheus.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Each test module finds the repository it runs in through one class, {@code RepositoryRoot}, and no other test
 * source climbs from the working directory on its own.
 *
 * <p>The locator had been pasted into almost every suite that reads the repository as text, under several names and
 * with several anchors: some copies recognised the root by the convergence manifest, some by the distribution
 * directory, some by the ADR registry, and one group looked only at the working directory and its parent. Moving
 * any of those files broke one group of suites and not the others, and the same condition failed with three
 * different exception types. The fixture helpers that climb to {@code experiments/m0/fixtures} are the same helper
 * with a different last step, and are held to the same rule.</p>
 *
 * <p>The proposition is that a declaration must not exist, which no dependency rule can say, so the scan is
 * textual. It looks for a shape rather than a name: a method whose body starts from the working directory and
 * climbs through parents. A copy pasted under a new name is the likely next one. Comments are blanked before the
 * scan, and string contents are blanked before braces are matched.</p>
 *
 * <p>The owners are deliberate copies, one per test module: test support is not shared through a new reactor
 * module or a new dependency edge between test modules. {@link #theOwnersStayInStep()} is what keeps the copies
 * from drifting the way the private locators did.</p>
 */
class RepositoryRootOwnershipTest {

    /** The owner of each test module that reads repository files. */
    static final List<Path> OWNERS = List.of(
            Path.of("morpheus-architecture-tests/src/test/java/com/morpheus/architecture/RepositoryRoot.java"),
            Path.of("morpheus-api/src/test/java/com/morpheus/api/RepositoryRoot.java"),
            Path.of("morpheus-cli/src/test/java/com/morpheus/cli/RepositoryRoot.java"),
            Path.of("morpheus-coverage-report/src/test/java/com/morpheus/coverage/RepositoryRoot.java"),
            Path.of("morpheus-provider-openspec/src/test/java/com/morpheus/provider/openspec/RepositoryRoot.java"),
            Path.of("morpheus-provider-synthetic/src/test/java/com/morpheus/provider/synthetic/RepositoryRoot.java"));

    /**
     * Climbs from the working directory that serve another purpose than locating the repository, by file and
     * method, with the reason. Empty: every climb found so far was a locator.
     */
    static final Map<String, String> OTHER_PURPOSE_CLIMBS = Map.of();

    private static final Pattern MODULE = Pattern.compile("<module>([^<]+)</module>");

    /**
     * A member declaration: modifiers and a return type, then a name and its parameter list. The caller checks
     * that a body follows the parameter list, which is what tells a declaration from a statement.
     */
    private static final Pattern DECLARATION = Pattern.compile(
            "(?m)^[ \\t]+((?:[A-Za-z0-9_<>\\[\\],.?]+\\s+)+)([A-Za-z_][A-Za-z0-9_]*)\\s*\\(");

    private static final Set<String> NOT_A_TYPE = Set.of(
            "return", "new", "throw", "else", "case", "yield", "assert");

    private static final Set<String> NOT_A_NAME = Set.of(
            "if", "for", "while", "switch", "catch", "synchronized", "try", "do");

    /** The working directory, under each spelling the copies used. */
    private static final Pattern WORKING_DIRECTORY = Pattern.compile(
            "Path\\.of\\(\"\"\\)|Paths\\.get\\(\"\"\\)|Path\\.of\\(\"\\.\"\\)|System\\.getProperty\\(\"user\\.dir\"\\)");

    /** A step towards the file system root, read in code: a parent. */
    private static final Pattern PARENT = Pattern.compile("\\.getParent\\(\\)");

    /** A step towards the file system root, read in a literal: a relative path that opens with {@code ..}. */
    private static final Pattern RELATIVE_CLIMB = Pattern.compile("\"\\.\\.(?:/|\\\\|\")");

    private static final String CORE_START = "    public static Path locate() {";

    private static final String FIXTURE_START = "    public static Path fixture(String name) {";

    private static final Pattern ANCHOR_DECLARATION = Pattern.compile("(?m)^    static final String ANCHOR = .*$");

    @Test
    void noTestSourceLocatesTheRepositoryOnItsOwn() throws IOException {
        Path root = RepositoryRoot.locate();
        List<String> offenders = new ArrayList<>();
        for (Path source : testSources(root)) {
            String relative = relative(root, source);
            if (isOwner(relative)) {
                continue;
            }
            for (String method : climbingMethods(Files.readString(source))) {
                String key = relative + "#" + method;
                if (!OTHER_PURPOSE_CLIMBS.containsKey(key)) {
                    offenders.add(key);
                }
            }
        }
        assertEquals(List.of(), offenders,
                "a test locates the repository root or an M0 fixture through its module's RepositoryRoot; a "
                        + "private climb from the working directory is how one module came to know eight anchors");
    }

    @Test
    void everyNamedOtherPurposeClimbStillClimbs() throws IOException {
        Path root = RepositoryRoot.locate();
        for (String key : OTHER_PURPOSE_CLIMBS.keySet()) {
            String file = key.substring(0, key.indexOf('#'));
            String method = key.substring(key.indexOf('#') + 1);
            Path source = root.resolve(file);
            assertTrue(Files.isRegularFile(source) && climbingMethods(Files.readString(source)).contains(method),
                    () -> key + " is excused as a climb with another purpose but no longer climbs: remove it");
        }
    }

    @Test
    void theShapeIsFoundWhateverTheMethodIsCalled() {
        String renamed = "class A {\n"
                + "    private static Path whereAmI() {\n"
                + "        Path current = Path.of(\"\").toAbsolutePath();\n"
                + "        while (current != null) {\n"
                + "            if (Files.isRegularFile(current.resolve(\"pom.xml\"))) {\n"
                + "                return current;\n"
                + "            }\n"
                + "            current = current.getParent();\n"
                + "        }\n"
                + "        throw new IllegalStateException(\"not found\");\n"
                + "    }\n"
                + "}\n";
        String boundedFixture = "class B {\n"
                + "    Path fixture(String name) throws java.io.IOException {\n"
                + "        Path current = Path.of(System.getProperty(\"user.dir\"));\n"
                + "        Path fromModule = current.resolve(\"../experiments/m0/fixtures\").normalize();\n"
                + "        return fromModule.resolve(name);\n"
                + "    }\n"
                + "}\n";
        String inlined = "class C {\n"
                + "    @Test\n"
                + "    void readsTheManifest() {\n"
                + "        Path root = Path.of(\"\").toAbsolutePath();\n"
                + "        while (!Files.exists(root.resolve(\"contracts\"))) {\n"
                + "            root = root.getParent();\n"
                + "        }\n"
                + "        Runnable r = new Runnable() {\n"
                + "            public void run() {\n"
                + "            }\n"
                + "        };\n"
                + "    }\n"
                + "}\n";
        assertEquals(List.of("whereAmI"), climbingMethods(renamed));
        assertEquals(List.of("fixture"), climbingMethods(boundedFixture));
        assertEquals(List.of("readsTheManifest"), climbingMethods(inlined));
    }

    @Test
    void neitherAStartWithoutAClimbNorAClimbInACommentOrAStringIsALocator() {
        String noClimb = "class A {\n"
                + "    Path here() {\n"
                + "        return Path.of(\"\").toAbsolutePath();\n"
                + "    }\n"
                + "}\n";
        String climbFromATemporaryDirectory = "class B {\n"
                + "    Path above(Path temporary) {\n"
                + "        return temporary.getParent();\n"
                + "    }\n"
                + "}\n";
        String quoted = "class C {\n"
                + "    String sample() {\n"
                + "        // Path.of(\"\").getParent() would climb\n"
                + "        return \"Path.of(\\\"\\\").toAbsolutePath().getParent()\";\n"
                + "    }\n"
                + "}\n";
        assertEquals(List.of(), climbingMethods(noClimb));
        assertEquals(List.of(), climbingMethods(climbFromATemporaryDirectory));
        assertEquals(List.of(), climbingMethods(quoted));
    }

    /**
     * The copies agree on the anchor, the failure and the fixture directory.
     *
     * <p>One shared class would be better and was rejected: it needs a new reactor module, which changes the module
     * count, the coverage population and the import set of these suites, or a dependency from one test module to
     * another, which inverts a build order the aggregate coverage gate depends on.</p>
     */
    @Test
    void theOwnersStayInStep() throws IOException {
        Path root = RepositoryRoot.locate();
        Map<Path, String> anchors = new TreeMap<>();
        Map<Path, String> cores = new TreeMap<>();
        Map<Path, String> fixtures = new TreeMap<>();
        for (Path owner : OWNERS) {
            String text = Files.readString(root.resolve(owner));
            Matcher anchor = ANCHOR_DECLARATION.matcher(text);
            assertTrue(anchor.find(), () -> owner + " must declare the anchor it names in its failure");
            anchors.put(owner, anchor.group().strip());
            int core = text.indexOf(CORE_START);
            assertTrue(core > 0, () -> owner + " must declare `" + CORE_START.trim() + "`");
            cores.put(owner, text.substring(core));
            int fixture = text.indexOf(FIXTURE_START);
            if (fixture >= 0) {
                fixtures.put(owner, text.substring(fixture, core));
            }
        }
        assertEquals(1, anchors.values().stream().distinct().count(),
                () -> "the owners must name the same anchor: " + anchors);
        assertEquals(1, cores.values().stream().distinct().count(),
                () -> "the owners must stay identical from `" + CORE_START.trim() + "` to the end of the class, "
                        + "anchor and failure included: " + cores.keySet());
        assertEquals(1, fixtures.values().stream().distinct().count(),
                () -> "the owners that serve the M0 fixtures must declare the same fixture method: "
                        + fixtures.keySet());
    }

    @Test
    void theScanCoversEveryTestModuleAndReachesEachOwner() throws IOException {
        Path root = RepositoryRoot.locate();
        List<Path> sources = testSources(root);
        assertTrue(sources.size() > 200,
                () -> "the scan must cover the whole test tree, found only " + sources.size() + " sources");
        for (Path owner : OWNERS) {
            assertTrue(sources.contains(root.resolve(owner)),
                    () -> "the scan must reach the owner " + owner + ", otherwise it skips nothing");
        }
    }

    static List<String> climbingMethods(String source) {
        String text = Blanking.blank(source, true);
        String code = Blanking.blank(source, false);
        List<int[]> bodies = new ArrayList<>();
        List<String> names = new ArrayList<>();
        Matcher declaration = DECLARATION.matcher(code);
        while (declaration.find()) {
            String[] typeTokens = declaration.group(1).trim().split("\\s+");
            if (NOT_A_NAME.contains(declaration.group(2))
                    || Stream.of(typeTokens).anyMatch(NOT_A_TYPE::contains)) {
                continue;
            }
            int open = bodyOpening(code, declaration.end() - 1);
            if (open < 0) {
                continue;
            }
            int close = matching(code, open, '{', '}');
            String bodyText = text.substring(open, close);
            boolean climbs = PARENT.matcher(code.substring(open, close)).find()
                    || RELATIVE_CLIMB.matcher(bodyText).find();
            if (WORKING_DIRECTORY.matcher(bodyText).find() && climbs) {
                bodies.add(new int[] {open, close});
                names.add(declaration.group(2));
            }
        }
        List<String> innermost = new ArrayList<>();
        for (int index = 0; index < bodies.size(); index++) {
            int[] outer = bodies.get(index);
            boolean enclosesAnother = bodies.stream().anyMatch(other -> other != outer
                    && other[0] > outer[0] && other[1] < outer[1]);
            if (!enclosesAnother) {
                innermost.add(names.get(index));
            }
        }
        return innermost;
    }

    /** The position of the body's brace after the parameter list opened at {@code paren}, or -1 for a statement. */
    private static int bodyOpening(String code, int paren) {
        int index = matching(code, paren, '(', ')');
        while (index < code.length() && Character.isWhitespace(code.charAt(index))) {
            index++;
        }
        if (code.startsWith("throws", index)) {
            while (index < code.length() && code.charAt(index) != '{' && code.charAt(index) != ';') {
                index++;
            }
        }
        return index < code.length() && code.charAt(index) == '{' ? index : -1;
    }

    /** The position just after the bracket that closes the one at {@code from}. */
    private static int matching(String code, int from, char open, char close) {
        int depth = 0;
        for (int index = from; index < code.length(); index++) {
            char character = code.charAt(index);
            if (character == open) {
                depth++;
            } else if (character == close && --depth == 0) {
                return index + 1;
            }
        }
        return code.length();
    }

    private static boolean isOwner(String relative) {
        return OWNERS.stream().anyMatch(owner -> owner.toString().replace('\\', '/').equals(relative));
    }

    private static String relative(Path root, Path source) {
        return root.relativize(source).toString().replace('\\', '/');
    }

    /** The test sources of every module the root POM declares; nothing outside the reactor is scanned. */
    static List<Path> testSources(Path root) throws IOException {
        List<Path> sources = new ArrayList<>();
        Matcher module = MODULE.matcher(Files.readString(root.resolve("pom.xml")));
        while (module.find()) {
            Path tests = root.resolve(module.group(1).trim()).resolve("src/test/java");
            if (!Files.isDirectory(tests)) {
                continue;
            }
            try (Stream<Path> tree = Files.walk(tests)) {
                tree.filter(Files::isRegularFile)
                        .filter(path -> path.getFileName().toString().endsWith(".java"))
                        .sorted()
                        .forEach(sources::add);
            }
        }
        return sources;
    }

    /** Comments blanked, and string contents too when asked; offsets and line breaks are kept. */
    static final class Blanking {

        private Blanking() {
        }

        static String blank(String source, boolean keepStrings) {
            StringBuilder out = new StringBuilder(source.length());
            int index = 0;
            while (index < source.length()) {
                char character = source.charAt(index);
                int end;
                if (source.startsWith("//", index)) {
                    end = source.indexOf('\n', index);
                    end = end < 0 ? source.length() : end;
                    spaces(out, source, index, end);
                } else if (source.startsWith("/*", index)) {
                    end = source.indexOf("*/", index + 2);
                    end = end < 0 ? source.length() : end + 2;
                    spaces(out, source, index, end);
                } else if (source.startsWith("\"\"\"", index)) {
                    end = index + 3;
                    while (end < source.length() && !source.startsWith("\"\"\"", end)) {
                        end += source.charAt(end) == '\\' ? 2 : 1;
                    }
                    end = Math.min(end + 3, source.length());
                    literal(out, source, index, end, 3, keepStrings);
                } else if (character == '"' || character == '\'') {
                    end = index + 1;
                    while (end < source.length() && source.charAt(end) != character && source.charAt(end) != '\n') {
                        end += source.charAt(end) == '\\' ? 2 : 1;
                    }
                    end = Math.min(end + 1, source.length());
                    literal(out, source, index, end, 1, keepStrings);
                } else {
                    out.append(character);
                    end = index + 1;
                }
                index = end;
            }
            return out.toString();
        }

        private static void literal(StringBuilder out, String source, int start, int end, int quote, boolean keep) {
            if (keep || end - start < 2 * quote) {
                out.append(source, start, end);
                return;
            }
            out.append(source, start, start + quote);
            spaces(out, source, start + quote, end - quote);
            out.append(source, end - quote, end);
        }

        private static void spaces(StringBuilder out, String source, int start, int end) {
            for (int index = start; index < end; index++) {
                out.append(source.charAt(index) == '\n' ? '\n' : ' ');
            }
        }
    }
}
