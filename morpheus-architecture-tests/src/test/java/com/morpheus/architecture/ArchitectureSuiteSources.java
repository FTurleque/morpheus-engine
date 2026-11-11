package com.morpheus.architecture;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The test sources of this module, read as text, and what each test class reads, for the gates that account for the
 * suite's own enforcement.
 *
 * <p>A source, on its own text:</p>
 * <ul>
 *   <li><em>evaluates rules on bytecode</em> when it imports an ArchUnit type;</li>
 *   <li><em>loads a production class</em> when it lives in a package outside {@code com.morpheus.architecture}
 *       (a test placed in a production package reaches that package's types without an import), or names a
 *       {@code com.morpheus} type outside that package in its code, imports included;</li>
 *   <li><em>reads a production source</em> when a string literal of its code names a {@code src/main} path or the
 *       folder {@code "main"}, or when it calls a main-source method of {@link ProductionSources}.</li>
 * </ul>
 *
 * <p>A test class <em>reads code</em> when its own source does one of the three, or when its code names another class
 * of this source tree that reads code, followed to any depth: a class that reads production sources through a helper of
 * another class of the suite is seen. {@link ProductionSources} is not followed - it is the locator of both main and
 * test sources, and its main-source methods are recognised by name. Comments and text blocks are never read: fixtures
 * live in text blocks. A class that reads no code is a coherence check.</p>
 */
final class ArchitectureSuiteSources {

    static final String SUITE_TEST_SOURCES = "morpheus-architecture-tests/src/test/java";

    private static final Pattern TEST_DECLARATION = Pattern.compile(
            "@(Test|TestFactory|ParameterizedTest|RepeatedTest|TestTemplate)\\b");
    private static final Pattern ARCHUNIT_IMPORT = Pattern.compile(
            "^\\s*import\\s+(static\\s+)?com\\.tngtech\\.archunit\\.", Pattern.MULTILINE);
    private static final Pattern PACKAGE = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);
    private static final Pattern PRODUCTION_TYPE_REFERENCE = Pattern.compile(
            "\\bcom\\.morpheus\\.(?!architecture\\b)[a-z]\\w*(\\.[a-z]\\w*)*\\.[A-Z]");
    private static final Pattern PRODUCTION_SOURCE_READ = Pattern.compile(
            "src(/|\\\\\\\\)main|\"main\"|\\bProductionSources\\s*\\.\\s*(readMain|mainSource|relativeMainSource)\\s*\\(");
    private static final Pattern COHERENCE_MARKER = Pattern.compile("@CoherenceCheck\\b");
    private static final Pattern IDENTIFIER = Pattern.compile("\\b[A-Z]\\w*\\b");
    private static final String NOT_FOLLOWED = "ProductionSources";

    /** A test class of the suite: its path from the repository root and its source. */
    record TestClass(String path, String source) {
    }

    /** Every source of a test tree, by the simple name of its file, and the paths of those that declare a test. */
    record Suite(Map<String, String> sourcesBySimpleName, Map<String, String> testClassSourcesByPath) {

        List<TestClass> testClasses() {
            return testClassSourcesByPath.entrySet().stream()
                    .map(entry -> new TestClass(entry.getKey(), entry.getValue()))
                    .toList();
        }

        /** True when {@code source}, or a class of the suite it names, transitively, reads a production source. */
        boolean readsAProductionSource(String source) {
            return reaches(source, ArchitectureSuiteSources::readsAProductionSourceItself);
        }

        /** True when {@code source} and every class of the suite it names, transitively, read no code. */
        boolean isCoherenceCheck(String source) {
            return !reaches(source, ArchitectureSuiteSources::readsCodeItself);
        }

        private boolean reaches(String start, Predicate<String> property) {
            Set<String> visited = new HashSet<>();
            Deque<String> pending = new ArrayDeque<>();
            pending.add(start);
            while (!pending.isEmpty()) {
                String source = pending.removeFirst();
                if (property.test(source)) {
                    return true;
                }
                IDENTIFIER.matcher(TestSourceText.code(source)).results()
                        .map(match -> match.group())
                        .filter(name -> !name.equals(NOT_FOLLOWED))
                        .filter(sourcesBySimpleName::containsKey)
                        .filter(visited::add)
                        .forEach(name -> pending.add(sourcesBySimpleName.get(name)));
            }
            return false;
        }
    }

    private ArchitectureSuiteSources() {
    }

    /** The test tree of this module. */
    static Suite suite() {
        Path root = RepositoryRoot.locate();
        Path tests = root.resolve(SUITE_TEST_SOURCES);
        Map<String, String> bySimpleName = new TreeMap<>();
        Map<String, String> testClasses = new TreeMap<>();
        try (Stream<Path> tree = Files.walk(tests)) {
            for (Path file : tree.filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
                String source = Files.readString(file);
                String simpleName = file.getFileName().toString().replace(".java", "");
                if (bySimpleName.put(simpleName, source) != null) {
                    throw new IllegalStateException(simpleName + " is declared twice under " + tests);
                }
                if (declaresATest(source)) {
                    testClasses.put(root.relativize(file).toString().replace('\\', '/'), source);
                }
            }
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        if (testClasses.isEmpty()) {
            throw new IllegalStateException("no test class found under " + tests);
        }
        return new Suite(bySimpleName, testClasses);
    }

    static boolean declaresATest(String source) {
        return TEST_DECLARATION.matcher(TestSourceText.code(source)).find();
    }

    static long testDeclarations(String source) {
        return TEST_DECLARATION.matcher(TestSourceText.code(source)).results().count();
    }

    static boolean evaluatesRulesOnBytecode(String source) {
        return ARCHUNIT_IMPORT.matcher(TestSourceText.code(source)).find();
    }

    static boolean loadsAProductionClass(String source) {
        String code = TestSourceText.code(source);
        var declaredPackage = PACKAGE.matcher(code);
        boolean inAProductionPackage = declaredPackage.find()
                && !(declaredPackage.group(1).equals("com.morpheus.architecture")
                || declaredPackage.group(1).startsWith("com.morpheus.architecture."));
        return inAProductionPackage || PRODUCTION_TYPE_REFERENCE.matcher(code).find();
    }

    static boolean readsAProductionSourceItself(String source) {
        return PRODUCTION_SOURCE_READ.matcher(TestSourceText.codeAndStrings(source)).find();
    }

    static boolean readsCodeItself(String source) {
        return evaluatesRulesOnBytecode(source) || loadsAProductionClass(source) || readsAProductionSourceItself(source);
    }

    static boolean carriesTheCoherenceMarker(String source) {
        return COHERENCE_MARKER.matcher(TestSourceText.code(source)).find();
    }
}
