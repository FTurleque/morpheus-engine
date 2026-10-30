package com.morpheus.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A rule of the architecture suite that retains no class fails, and nothing committed lets it pass.
 *
 * <p>Every rule of the suite that selects by package goes vacuous when its package is renamed or emptied, and a vacuous
 * rule passes unless ArchUnit refuses it. ArchUnit refuses it when {@code archRule.failOnEmptyShould} is true, its
 * default. The setting has three entrances, read in ArchUnit 1.5.1 ({@code ArchRule.SimpleArchRule},
 * {@code AllowEmptyShould}, {@code ArchConfiguration}): the file {@code archunit.properties} at the root of the test
 * classpath, the system property {@code archunit.archRule.failOnEmptyShould}, and {@code ArchRule.allowEmptyShould(true)}
 * on one rule. Only the value {@code false}, in any case, turns the guard off.</p>
 *
 * <p>The first test evaluates a vacuous rule and expects the failure ArchUnit 1.5.1 raises for it, an
 * {@code AssertionError} whose message reads "Rule '...' failed to check any classes". It holds against both global
 * entrances and against a change of the library's default. The message is not an API of the library: when an upgrade of
 * {@code archunit.version} turns this test red, re-read {@code ArchRule.SimpleArchRule#verifyNoEmptyShouldIfEnabled}
 * and pin the new wording. The two other tests are the textual second line: the per-rule opt-out, which the first test
 * cannot see, and a committed global setting that only applies to another invocation of the gates.</p>
 */
class AnEmptyArchitectureRuleFailsTest {

    private static final String EMPTINESS = "failed to check any classes";
    private static final Pattern OPT_OUT_CALL = Pattern.compile("\\.\\s*allowEmptyShould\\s*\\(");
    private static final Pattern PROPERTY_FILE_SETTING = Pattern.compile(
            "^\\s*archRule\\.failOnEmptyShould\\s*[=:]\\s*(\\S*)", Pattern.MULTILINE);
    private static final Pattern BUILD_SETTING = Pattern.compile(
            "(?<!/)archunit\\.archRule\\.failOnEmptyShould\\s*(?:=|>)\\s*\"?([^\\s\"<;']*)");
    /** Build output, version control and the agents' worktrees, which hold copies of the tree, not settings of it. */
    private static final Set<String> EXCLUDED_DIRECTORIES = Set.of(".git", "target", "node_modules", ".claude");

    @Test
    void aRuleThatSelectsNoClassFailsForHavingCheckedNone() {
        JavaClasses imported = new ClassFileImporter().importPackages("com.morpheus.domain");
        assertFalse(imported.isEmpty(), "the import retained no class: the vacuous rule below would prove nothing");
        ArchRule vacuous = classes()
                .that().resideInAPackage("com.morpheus.architecture.no.such.package..")
                .should().bePublic();

        AssertionError failure = assertThrows(AssertionError.class, () -> vacuous.check(imported),
                "a rule that selects nothing now passes: archRule.failOnEmptyShould is turned off for this run, "
                        + "by an archunit.properties on the test classpath or by a system property");
        assertTrue(failure.getMessage().contains(EMPTINESS),
                "the vacuous rule failed for another reason than emptiness: " + failure.getMessage());
    }

    @Test
    void noRuleOfTheSuiteOptsOutOfFailingWhenItSelectsNothing() throws IOException {
        Path root = RepositoryRoot.locate();
        Path tests = root.resolve("morpheus-architecture-tests/src/test/java");
        List<String> optOuts = new ArrayList<>();
        try (Stream<Path> tree = Files.walk(tests)) {
            for (Path file : tree.filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
                optOutLines(Files.readString(file)).forEach(line ->
                        optOuts.add(root.relativize(file).toString().replace('\\', '/') + ":" + line));
            }
        }
        assertEquals(List.of(), optOuts,
                "allowEmptyShould lets a rule pass while its selection is empty: rewrite or delete the rule instead");
    }

    @Test
    void anOptOutNamedInACommentOrALiteralIsNotACall() {
        String source = """
                class Sample {
                    /** {@code rule.allowEmptyShould(true)} is refused. */
                    // rule.allowEmptyShould(true)
                    String text = "rule.allowEmptyShould(true)";
                    void check() {
                        classes().should().bePublic()
                                .allowEmptyShould(true)
                                .check(imported);
                    }
                }
                """;

        assertEquals(List.of(7), optOutLines(source));
    }

    @Test
    void noCommittedSettingTurnsTheGuardOffForAnyInvocationOfTheGates() throws IOException {
        Path root = RepositoryRoot.locate();
        List<String> settings = new ArrayList<>();
        for (Path file : settingCarriers(root)) {
            String text = Files.readString(file);
            boolean propertyFile = file.getFileName().toString().equals("archunit.properties");
            if (turnsTheGuardOff(text, propertyFile)) {
                settings.add(root.relativize(file).toString().replace('\\', '/'));
            }
        }
        assertEquals(List.of(), settings,
                "archRule.failOnEmptyShould is set to something other than true: a rule that selects nothing would "
                        + "pass in the invocation this file configures");
    }

    @Test
    void theSettingScanRefusesOffAndAcceptsTrueAndOtherProperties() {
        assertTrue(turnsTheGuardOff("archRule.failOnEmptyShould=false\n", true));
        assertTrue(turnsTheGuardOff("  archRule.failOnEmptyShould : FALSE\n", true));
        assertFalse(turnsTheGuardOff("archRule.failOnEmptyShould=true\n", true));
        assertFalse(turnsTheGuardOff("cycles.maxNumberToDetect=50\n", true));
        assertTrue(turnsTheGuardOff("./mvnw test -Darchunit.archRule.failOnEmptyShould=false", false));
        assertTrue(turnsTheGuardOff(
                "<archunit.archRule.failOnEmptyShould>false</archunit.archRule.failOnEmptyShould>", false));
        assertFalse(turnsTheGuardOff("./mvnw test -Darchunit.archRule.failOnEmptyShould=true", false));
        assertFalse(turnsTheGuardOff(
                "<archunit.archRule.failOnEmptyShould>true</archunit.archRule.failOnEmptyShould>", false));
        assertFalse(turnsTheGuardOff("./mvnw test -Darchunit.cycles.maxNumberToDetect=50", false));
    }

    /** The 1-based lines of {@code source} that call the per-rule opt-out in code. */
    private static List<Integer> optOutLines(String source) {
        String code = TestSourceText.code(source);
        List<Integer> lines = new ArrayList<>();
        Matcher call = OPT_OUT_CALL.matcher(code);
        while (call.find()) {
            lines.add((int) code.substring(0, call.start()).chars().filter(character -> character == '\n').count() + 1);
        }
        return lines;
    }

    private static boolean turnsTheGuardOff(String text, boolean propertyFile) {
        Matcher setting = (propertyFile ? PROPERTY_FILE_SETTING : BUILD_SETTING).matcher(text);
        while (setting.find()) {
            if (!setting.group(1).toLowerCase(Locale.ROOT).equals("true")) {
                return true;
            }
        }
        return false;
    }

    /** Every {@code archunit.properties}, every POM, the Maven configuration, the validators and the workflows. */
    private static List<Path> settingCarriers(Path root) throws IOException {
        List<Path> carriers = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                return directory.equals(root) || !EXCLUDED_DIRECTORIES.contains(directory.getFileName().toString())
                        ? FileVisitResult.CONTINUE
                        : FileVisitResult.SKIP_SUBTREE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                if (isSettingCarrier(root.relativize(file))) {
                    carriers.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        carriers.sort(null);
        assertTrue(carriers.stream().anyMatch(path -> path.getFileName().toString().equals("pom.xml")),
                "no POM found under " + root + ": the scan reads nothing");
        return carriers;
    }

    private static boolean isSettingCarrier(Path relative) {
        String path = relative.toString().replace('\\', '/');
        String name = relative.getFileName().toString();
        return name.equals("archunit.properties")
                || name.equals("pom.xml")
                || path.startsWith(".mvn/")
                || path.startsWith("scripts/")
                || path.startsWith(".github/workflows/");
    }
}
