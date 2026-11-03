package com.morpheus.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A test source that changes state of the whole JVM carries {@code @Isolated}, in every module.
 *
 * <p>The default locale, the default time zone, a system property, the standard streams and the drivers registered
 * with {@code DriverManager} are read by every thread of the JVM. A test that sets one is read by every other test
 * running at the same time: under JUnit parallel execution (ADR-0115) the locale a Turkish-locale test sets makes
 * another test upper-case {@code i} wrongly, and the {@code java.home} a launcher test points at a temporary directory
 * makes another resolve no launcher. {@code @Isolated} runs the class alone, which is the only guarantee that holds
 * against a reader that declares nothing; a {@code @ResourceLock} only orders the tests that declare the same lock,
 * and the readers of these values never do.</p>
 *
 * <p>The rule holds in every module, parallel or not, so that switching a module to parallel execution never finds a
 * mutation the rule has not already placed. Intent: a mention in the test sources, which ArchUnit does not import -
 * text. This module is not scanned: its sources name these calls as the literals they ban.</p>
 */
@CoherenceCheck
class JvmMutatingTestsAreIsolatedArchitectureTest {

    /** The calls that change state every thread of the JVM reads. */
    private static final Pattern JVM_MUTATION = Pattern.compile(
            "\\b(System\\.(setProperty|clearProperty|setProperties|setOut|setErr|setIn)"
                    + "|Locale\\.setDefault|TimeZone\\.setDefault"
                    + "|DriverManager\\.(registerDriver|deregisterDriver))\\s*\\(");

    /** {@code @Isolated} on a top-level type: written at the start of a line, before the class it annotates. */
    private static final Pattern TOP_LEVEL_ISOLATED = Pattern.compile(
            "(?m)^@(org\\.junit\\.jupiter\\.api\\.parallel\\.)?Isolated\\b");

    @Test
    void everyTestSourceThatChangesStateOfTheWholeJvmIsIsolated() throws IOException {
        Set<String> mutating = new TreeSet<>();
        Set<String> unisolated = new TreeSet<>();
        Path root = RepositoryRoot.locate();
        for (Path file : testSources(root)) {
            String source = Files.readString(file);
            List<String> mutations = mutations(source);
            if (mutations.isEmpty()) {
                continue;
            }
            String name = root.relativize(file).toString().replace('\\', '/');
            mutating.add(name);
            if (!isIsolated(source)) {
                unisolated.add(name + " " + mutations);
            }
        }

        assertFalse(mutating.isEmpty(), "no test source changes state of the JVM: the scan or its pattern is broken, "
                + "since the locale and launcher tests do");
        assertEquals(Set.of(), unisolated, "these test sources change state every thread of the JVM reads, and run "
                + "beside other tests: annotate the class @Isolated, or stop changing the JVM and hand the code under "
                + "test the value instead");
    }

    @Test
    void aMutationIsFoundAndOnlyATopLevelIsolatedClassIsAccepted() {
        String unannotated = """
                class SetsTheLocale {
                    @BeforeEach
                    void hostile() {
                        Locale.setDefault(Locale.forLanguageTag("tr-TR"));
                    }
                }
                """;
        String annotated = "@Isolated\n" + unannotated;
        String nestedOnly = """
                class Outer {
                    @Isolated
                    class Inner {
                        void set() {
                            System.setProperty ("java.home", "elsewhere");
                        }
                    }
                }
                """;

        assertEquals(List.of("Locale.setDefault"), mutations(unannotated));
        assertFalse(isIsolated(unannotated));
        assertTrue(isIsolated(annotated));
        assertEquals(List.of("System.setProperty"), mutations(nestedOnly));
        assertFalse(isIsolated(nestedOnly), "an @Isolated nested class leaves its outer class running beside others");
        assertEquals(List.of("DriverManager.registerDriver"),
                mutations("void wrap() throws Exception { DriverManager.registerDriver(new Counting()); }"));
        assertEquals(List.of(), mutations("String home = System.getProperty(\"java.home\");"));
    }

    private static List<String> mutations(String source) {
        Matcher matcher = JVM_MUTATION.matcher(source);
        Set<String> found = new TreeSet<>();
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        return List.copyOf(found);
    }

    private static boolean isIsolated(String source) {
        return TOP_LEVEL_ISOLATED.matcher(source).find();
    }

    private static List<Path> testSources(Path root) throws IOException {
        List<Path> sources = new ArrayList<>();
        try (Stream<Path> modules = Files.list(root)) {
            for (Path module : modules.filter(Files::isDirectory).sorted().toList()) {
                String name = module.getFileName().toString();
                Path tests = module.resolve("src/test/java");
                if (!name.startsWith("morpheus-") || name.equals("morpheus-architecture-tests")
                        || !Files.isDirectory(tests)) {
                    continue;
                }
                try (Stream<Path> files = Files.walk(tests)) {
                    files.filter(path -> path.toString().endsWith(".java")).sorted().forEach(sources::add);
                }
            }
        }
        return sources;
    }
}
