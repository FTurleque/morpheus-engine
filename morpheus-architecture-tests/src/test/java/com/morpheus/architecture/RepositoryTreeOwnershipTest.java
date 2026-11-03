package com.morpheus.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A test walks the whole repository through {@link RepositoryTree}, and no test source walks the root on its own.
 *
 * <p>Eight gates walked the root with their own filter, most of them leaving out {@code target} and nothing else. A
 * workspace copy of the repository under {@code .claude/worktrees} made one of them count three times the reactor
 * POMs and another find a second construction site that only the copy held; a gate that looks for a forbidden
 * occurrence could as well pass on a copy of another revision. Each private filter was the place where the next
 * copy would be forgotten.</p>
 *
 * <p>The proposition is that a call must not exist outside one class, which no dependency rule can say, so the scan
 * is textual, over the test sources of every reactor module, comments and string contents blanked. A walk is of the
 * root when its starting point is {@code RepositoryRoot.locate()}, a name bound to it in the enclosing method or as a
 * field, or a parameter of the enclosing method that a call of the same file passes one of those, followed two calls
 * deep. A root reached another way, through another file or a computed expression, is not seen: the scan names the
 * shapes the suites wrote, not every possible one.</p>
 */
@CoherenceCheck
class RepositoryTreeOwnershipTest {

    /** The only test source that walks the repository root. */
    static final Path OWNER = Path.of("morpheus-architecture-tests/src/test/java/com/morpheus/architecture/RepositoryTree.java");

    private static final Pattern WALK = Pattern.compile("\\bFiles\\s*\\.\\s*(walk|find|walkFileTree)\\s*\\(");

    private static final Pattern ROOT = Pattern.compile("RepositoryRoot\\s*\\.\\s*locate\\s*\\(\\s*\\)");

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_]\\w*");

    private static final Pattern DECLARATION = Pattern.compile(
            "(?m)^[ \\t]+((?:[A-Za-z0-9_<>\\[\\],.?@]+\\s+)+)([A-Za-z_]\\w*)\\s*\\(");

    private static final Set<String> NOT_A_TYPE = Set.of("return", "new", "throw", "else", "case", "yield", "assert");

    private static final Set<String> NOT_A_NAME = Set.of(
            "if", "for", "while", "switch", "catch", "synchronized", "try", "do");

    private static final int CALL_DEPTH = 2;

    @Test
    void noTestSourceWalksTheRepositoryRootOutsideTheOwner() throws IOException {
        Path root = RepositoryRoot.locate();
        List<String> offenders = new ArrayList<>();
        for (Path source : RepositoryRootOwnershipTest.testSources(root)) {
            String relative = root.relativize(source).toString().replace('\\', '/');
            if (relative.equals(OWNER.toString().replace('\\', '/'))) {
                continue;
            }
            for (int line : rootWalkLines(Files.readString(source))) {
                offenders.add(relative + ":" + line);
            }
        }
        assertEquals(List.of(), offenders,
                "a test walks the whole repository through RepositoryTree.files, which leaves out generated output "
                        + "and workspace copies; a private walk of the root read a copy under .claude/worktrees");
    }

    @Test
    void theScanSeesTheOwnerAndEveryTestModule() throws IOException {
        Path root = RepositoryRoot.locate();
        List<Path> sources = RepositoryRootOwnershipTest.testSources(root);
        assertTrue(sources.contains(root.resolve(OWNER)), () -> "the scan must reach the owner " + OWNER);
        long modules = sources.stream().map(source -> root.relativize(source).getName(0)).distinct().count();
        assertTrue(modules > 5, () -> "the scan must cover the test sources of every module, found " + modules);
    }

    @Test
    void aWalkOfTheRootIsFoundWhateverItsShape() {
        String direct = "class A {\n"
                + "    void a() throws IOException {\n"
                + "        try (var walk = Files.walk(RepositoryRoot.locate())) {\n"
                + "        }\n"
                + "    }\n"
                + "}\n";
        String bound = "class B {\n"
                + "    void b() throws IOException {\n"
                + "        Path root = RepositoryRoot.locate();\n"
                + "        try (var files = Files.walk(root, 16)) {\n"
                + "        }\n"
                + "    }\n"
                + "}\n";
        String throughAHelper = "class C {\n"
                + "    @Test\n"
                + "    void c() throws IOException {\n"
                + "        assertEquals(List.of(), scripts(RepositoryRoot.locate(), new ArrayList<>()));\n"
                + "    }\n"
                + "\n"
                + "    private static List<String> scripts(Path root, List<String> seen) throws IOException {\n"
                + "        Files.walkFileTree(root, new SimpleFileVisitor<>() {\n"
                + "        });\n"
                + "        return seen;\n"
                + "    }\n"
                + "}\n";
        String throughAField = "class D {\n"
                + "    private static final Path REPOSITORY = RepositoryRoot.locate();\n"
                + "\n"
                + "    void d() throws IOException {\n"
                + "        try (var found = Files.find(REPOSITORY, 8, (path, attributes) -> true)) {\n"
                + "        }\n"
                + "    }\n"
                + "}\n";
        assertEquals(List.of(3), rootWalkLines(direct));
        assertEquals(List.of(4), rootWalkLines(bound));
        assertEquals(List.of(8), rootWalkLines(throughAHelper));
        assertEquals(List.of(5), rootWalkLines(throughAField));
    }

    @Test
    void aWalkBelowTheRootOrOfAnotherTreeIsNotAWalkOfTheRoot() {
        String below = "class A {\n"
                + "    void a() throws IOException {\n"
                + "        Path root = RepositoryRoot.locate();\n"
                + "        try (var files = Files.walk(root.resolve(\"docs\"))) {\n"
                + "        }\n"
                + "        read(root.resolve(\"morpheus-api/src/test/java\"));\n"
                + "    }\n"
                + "\n"
                + "    private static String read(Path root) throws IOException {\n"
                + "        try (var files = Files.walk(root)) {\n"
                + "            return \"\";\n"
                + "        }\n"
                + "    }\n"
                + "}\n";
        String module = "class B {\n"
                + "    void b(String module) throws IOException {\n"
                + "        Path root = RepositoryRoot.locate().resolve(module);\n"
                + "        try (var files = Files.walk(root)) {\n"
                + "        }\n"
                + "    }\n"
                + "}\n";
        String quoted = "class C {\n"
                + "    String c() {\n"
                + "        // Files.walk(RepositoryRoot.locate())\n"
                + "        return \"Files.walk(RepositoryRoot.locate())\";\n"
                + "    }\n"
                + "}\n";
        assertEquals(List.of(), rootWalkLines(below));
        assertEquals(List.of(), rootWalkLines(module));
        assertEquals(List.of(), rootWalkLines(quoted));
    }

    @Test
    void theWalkLeavesOutAWorkspaceCopyUnderClaudeWorktrees(@TempDir Path root) throws IOException {
        plant(root, """
                pom.xml
                morpheus-api/src/main/java/com/morpheus/api/Server.java
                .claude/hooks/pre-bash.ps1
                .claude/worktrees/agent/pom.xml
                .claude/worktrees/agent/morpheus-api/src/main/java/com/morpheus/api/Server.java
                .claude/worktrees/agent/morpheus-api/src/main/java/com/morpheus/api/Removed.java
                """);

        assertEquals("""
                        .claude/hooks/pre-bash.ps1
                        morpheus-api/src/main/java/com/morpheus/api/Server.java
                        pom.xml
                        """.lines().toList(),
                relativeFiles(root),
                "nothing of the copy may come out, and the rest of .claude is still read");
    }

    @Test
    void theWalkLeavesOutANestedCheckoutWhereverItIsAndTheGeneratedTrees(@TempDir Path root) throws IOException {
        plant(root, """
                pom.xml
                elsewhere/copy/.git
                elsewhere/copy/pom.xml
                elsewhere/checkout/.git/HEAD
                elsewhere/checkout/pom.xml
                elsewhere/kept.md
                morpheus-api/target/classes/Server.java
                dist/morpheus/pom.xml
                validation-output/m21/validation-summary.txt
                """);

        assertEquals(List.of("elsewhere/kept.md", "pom.xml"), relativeFiles(root),
                "a directory that carries its own .git, file or directory, is another checkout, never this one");
    }

    @Test
    void aRootThatIsItselfAWorktreeIsWalked(@TempDir Path root) throws IOException {
        plant(root, """
                .git
                pom.xml
                """);

        assertEquals(List.of("pom.xml"), relativeFiles(root),
                "the .git file of the root marks the checkout the suite runs in: its tree is walked, the pointer is not "
                        + "a file of the repository");
    }

    /** The lines of {@code source} where a walk starts from the repository root. */
    static List<Integer> rootWalkLines(String source) {
        String code = RepositoryRootOwnershipTest.Blanking.blank(source, false);
        List<Method> methods = methods(code);
        List<Integer> lines = new ArrayList<>();
        Matcher walk = WALK.matcher(code);
        while (walk.find()) {
            List<String> arguments = split(code, walk.end() - 1, false);
            if (!arguments.isEmpty() && isRoot(code, methods, arguments.get(0), walk.start(), CALL_DEPTH)) {
                lines.add(line(code, walk.start()));
            }
        }
        return lines;
    }

    private static boolean isRoot(String code, List<Method> methods, String expression, int at, int depth) {
        String trimmed = expression.strip();
        if (ROOT.matcher(trimmed).matches()) {
            return true;
        }
        if (!IDENTIFIER.matcher(trimmed).matches()) {
            return false;
        }
        Method enclosing = enclosing(methods, at);
        Matcher binding = Pattern.compile("\\b" + trimmed + "\\s*=\\s*" + ROOT.pattern() + "\\s*;").matcher(code);
        while (binding.find()) {
            Method holder = enclosing(methods, binding.start());
            if (holder == null || holder == enclosing) {
                return true;
            }
        }
        if (enclosing == null || depth == 0) {
            return false;
        }
        int index = enclosing.parameters().indexOf(trimmed);
        if (index < 0) {
            return false;
        }
        Matcher call = Pattern.compile("\\b" + Pattern.quote(enclosing.name()) + "\\s*\\(").matcher(code);
        while (call.find()) {
            if (call.start() == enclosing.nameStart()) {
                continue;
            }
            List<String> arguments = split(code, call.end() - 1, false);
            if (arguments.size() > index && isRoot(code, methods, arguments.get(index), call.start(), depth - 1)) {
                return true;
            }
        }
        return false;
    }

    /** A method or constructor of the code: its name, where the name starts, its parameter names and its body. */
    private record Method(String name, int nameStart, List<String> parameters, int bodyStart, int bodyEnd) {
    }

    private static List<Method> methods(String code) {
        List<Method> methods = new ArrayList<>();
        Matcher declaration = DECLARATION.matcher(code);
        while (declaration.find()) {
            String[] typeTokens = declaration.group(1).trim().split("\\s+");
            if (NOT_A_NAME.contains(declaration.group(2))
                    || List.of(typeTokens).stream().anyMatch(NOT_A_TYPE::contains)) {
                continue;
            }
            int paren = declaration.end() - 1;
            int index = matching(code, paren, '(', ')');
            while (index < code.length() && Character.isWhitespace(code.charAt(index))) {
                index++;
            }
            if (code.startsWith("throws", index)) {
                while (index < code.length() && code.charAt(index) != '{' && code.charAt(index) != ';') {
                    index++;
                }
            }
            if (index >= code.length() || code.charAt(index) != '{') {
                continue;
            }
            List<String> parameters = new ArrayList<>();
            for (String parameter : split(code, paren, true)) {
                String[] tokens = parameter.strip().split("\\s+");
                parameters.add(tokens[tokens.length - 1]);
            }
            methods.add(new Method(declaration.group(2), declaration.start(2), parameters, index,
                    matching(code, index, '{', '}')));
        }
        return methods;
    }

    /** The innermost method whose body holds {@code offset}, or null outside every body. */
    private static Method enclosing(List<Method> methods, int offset) {
        Method innermost = null;
        for (Method method : methods) {
            if (method.bodyStart() < offset && offset < method.bodyEnd()
                    && (innermost == null || method.bodyStart() > innermost.bodyStart())) {
                innermost = method;
            }
        }
        return innermost;
    }

    /** The top-level comma-separated items between the parenthesis at {@code open} and the one that closes it. */
    private static List<String> split(String code, int open, boolean typeArguments) {
        List<String> items = new ArrayList<>();
        int depth = 0;
        int start = open + 1;
        for (int index = open; index < code.length(); index++) {
            char character = code.charAt(index);
            if (character == '(' || character == '[' || character == '{' || typeArguments && character == '<') {
                depth++;
            } else if (character == ')' || character == ']' || character == '}' || typeArguments && character == '>') {
                depth--;
                if (depth == 0) {
                    String last = code.substring(start, index);
                    if (!last.isBlank() || !items.isEmpty()) {
                        items.add(last);
                    }
                    return items;
                }
            } else if (character == ',' && depth == 1) {
                items.add(code.substring(start, index));
                start = index + 1;
            }
        }
        return items;
    }

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

    private static int line(String code, int offset) {
        return (int) code.substring(0, offset).chars().filter(character -> character == '\n').count() + 1;
    }

    /** Creates each file of {@code paths}, one relative path per line; the paths live in a text block. */
    private static void plant(Path root, String paths) throws IOException {
        for (String relative : paths.lines().toList()) {
            Path file = root.resolve(relative);
            Files.createDirectories(file.getParent());
            Files.writeString(file, relative);
        }
    }

    private static List<String> relativeFiles(Path root) {
        return RepositoryTree.files(root).stream()
                .map(file -> root.relativize(file).toString().replace('\\', '/'))
                .toList();
    }
}
