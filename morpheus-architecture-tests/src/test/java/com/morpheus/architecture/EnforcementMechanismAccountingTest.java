package com.morpheus.architecture;

import com.morpheus.architecture.ArchitectureSuiteSources.Suite;
import com.morpheus.architecture.ArchitectureSuiteSources.TestClass;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * What the enforcement of this suite is made of, and the one part of it that may only shrink.
 *
 * <p>A textual ban of a type - {@code assertFalse(source.contains("SomeType"))} - states a rule, and states it without
 * saying which of two propositions it means: that the name is not mentioned, or that the dependency the name stands for
 * does not exist ({@code .claude/rules/architecture.md}, "Regle ArchUnit ou assertion textuelle"). Written with no
 * message, its failure reads {@code expected: <false> but was: <true>}. Those bans are the <em>unexplained family</em>:
 * an {@code assertFalse} whose single argument is {@code <identifier>.contains("<TypeIdentifier>")}, in the code of the
 * test sources of this module. Their count must equal {@link #UNEXPLAINED_TYPE_BAN_CEILING}: one more fails, one fewer
 * fails until the ceiling is lowered in the same change. The ceiling equals its key in {@value #CEILING_RATCHETS},
 * as does the ceiling of the bans awaiting a rule on the bytecode, so that raising either touches a governance file. A
 * new textual ban of a type carries its intent as the assertion's message, and so leaves the family by construction.
 * No known reason justifies raising the ceiling.</p>
 *
 * <p>The suite also publishes the composition of its enforcement in {@value #PROOF_FILE}, by the method its first line
 * names. Those counts are a reading, not a gate: nothing fails on them. Documents cite the file and its method, never
 * a figure. The intent of this class is a property of the test sources, which ArchUnit does not import: text.</p>
 *
 * <p>The expressions read the code of the sources: comments, text blocks and character literals are blanked, string
 * literals are kept for the family, which is told apart by the literal it bans, and blanked for every other count. A
 * ban written another way - {@code assertTrue(!...)}, a helper that takes the literal, a loop over literals - is
 * outside the family. Helpers that take an intent are the better form; the family is a debt counter, not a census.</p>
 */
@CoherenceCheck
class EnforcementMechanismAccountingTest {

    /**
     * The textual bans of a type that state no intent: none. A ban of a type whose target declares no compile-time
     * constant is a rule on the bytecode, and one whose target declares such a constant, or whose intent is the
     * mention itself, carries that intent as its message. Never raised; equal to its key in {@value #CEILING_RATCHETS}.
     */
    static final int UNEXPLAINED_TYPE_BAN_CEILING = 0;

    /**
     * The governance file that holds each ceiling of a population of textual bans, one key per ceiling, keyed by
     * {@code TestClass.FIELD}. A ceiling and its key move together: a raise touches this file, which the pull request
     * template asks to justify, and a lowering lowers the key in the same change.
     */
    static final String CEILING_RATCHETS = "config/enforcement-ceiling-ratchets.properties";
    static final String UNEXPLAINED_CEILING_KEY = "EnforcementMechanismAccountingTest.UNEXPLAINED_TYPE_BAN_CEILING";
    static final String AWAITING_CEILING_KEY = "TypeBansAwaitingABytecodeRuleTest.AWAITING_A_RULE_CEILING";
    private static final List<String> CEILING_KEYS = List.of(AWAITING_CEILING_KEY, UNEXPLAINED_CEILING_KEY);

    static final String PROOF_FILE = "morpheus-architecture-tests/target/enforcement-composition-summary.txt";

    static final String METHOD = "method=test sources of morpheus-architecture-tests; comments and text blocks blanked; "
            + "string literals kept for the type bans and blanked for the other counts; a class reads what the classes "
            + "of the suite it names read; textual assertions attributed by the class they are written in";

    private static final Pattern UNEXPLAINED_TYPE_BAN = Pattern.compile(
            "\\bassertFalse\\(\\s*[A-Za-z0-9_]+\\.contains\\(\"[A-Z][A-Za-z0-9_]*\"\\)\\s*\\)");
    private static final Pattern EXPLAINED_TYPE_BAN = Pattern.compile(
            "\\bassertFalse\\(\\s*[A-Za-z0-9_]+\\.contains\\(\"[A-Z][A-Za-z0-9_]*\"\\)\\s*,");
    private static final Pattern TEXTUAL_ASSERTION = Pattern.compile(
            "\\bassert(True|False)\\(\\s*!?\\s*[A-Za-z0-9_.()]+\\.contains\\(");
    private static final Pattern RULE_EVALUATION = Pattern.compile("\\.check\\(");

    private static final List<String> PROOF_KEYS = List.of(
            "testClasses",
            "testDeclarations",
            "classesEvaluatingRulesOnBytecode",
            "classesReadingAProductionSource",
            "coherenceClasses",
            "rulesEvaluated",
            "textualAssertions.inClassesReadingAProductionSource",
            "textualAssertions.inCoherenceClasses",
            "textualAssertions.inOtherClasses",
            "typeBans.withoutIntent",
            "typeBans.withIntent");

    @Test
    void theTextualBansOfATypeThatStateNoIntentDoNotGrow() {
        Map<String, Long> byFile = new TreeMap<>();
        for (TestClass testClass : ArchitectureSuiteSources.suite().testClasses()) {
            long members = unexplainedTypeBans(testClass.source());
            if (members > 0) {
                byFile.put(testClass.path(), members);
            }
        }
        long measured = byFile.values().stream().mapToLong(Long::longValue).sum();
        if (measured > UNEXPLAINED_TYPE_BAN_CEILING) {
            fail(measured + " textual bans of a type state no intent, the ceiling is " + UNEXPLAINED_TYPE_BAN_CEILING
                    + ": a textual ban of a type states its intent as the assertion's message - "
                    + "assertFalse(source.contains(\"SomeType\"), \"<the mention, or the dependency it stands for>\"). "
                    + "Members by file: " + byFile);
        }
        assertEquals(UNEXPLAINED_TYPE_BAN_CEILING, measured,
                "the textual bans of a type that state no intent fell to " + measured
                        + ": lower UNEXPLAINED_TYPE_BAN_CEILING to " + measured + " in this change");
    }

    @Test
    void theUnexplainedCeilingEqualsItsRatchetKey() {
        assertEquals(List.of(), ceilingRefusals(ceilingRatchets(), UNEXPLAINED_CEILING_KEY, UNEXPLAINED_TYPE_BAN_CEILING),
                "the ceiling of the unexplained family is held by a governance file, not by this constant alone");
    }

    @Test
    void theCeilingRatchetsDeclareTheKeyOfEachCeilingAndNoOther() {
        assertEquals(List.of(), keyRefusals(ceilingRatchets()),
                CEILING_RATCHETS + " declares one key per ceiling of a population of textual bans");
    }

    @Test
    void aCeilingThatDiffersFromItsKeyIsRefusedInBothDirectionsWithItsRemedy() {
        Properties declared = ratchets(UNEXPLAINED_CEILING_KEY + "=1");

        List<String> lowered = ceilingRefusals(declared, UNEXPLAINED_CEILING_KEY, 0);
        assertEquals(1, lowered.size(), () -> String.valueOf(lowered));
        assertTrue(lowered.get(0).contains(UNEXPLAINED_CEILING_KEY + " is 0 in its test and 1 in " + CEILING_RATCHETS)
                && lowered.get(0).contains("lower the key to 0"), lowered.get(0));

        List<String> raised = ceilingRefusals(declared, UNEXPLAINED_CEILING_KEY, 2);
        assertEquals(1, raised.size(), () -> String.valueOf(raised));
        assertTrue(raised.get(0).contains(UNEXPLAINED_CEILING_KEY + " is 2 in its test and 1 in " + CEILING_RATCHETS)
                && raised.get(0).contains("justified in the pull request"), raised.get(0));

        assertEquals(List.of(), ceilingRefusals(declared, UNEXPLAINED_CEILING_KEY, 1));
    }

    @Test
    void aMissingKeyAnOrphanKeyAndAValueThatIsNotACountAreRefusedByName() {
        Properties declared = ratchets(UNEXPLAINED_CEILING_KEY + "=0", "SomeTest.RETIRED_CEILING=0");

        List<String> missing = ceilingRefusals(declared, AWAITING_CEILING_KEY, 0);
        assertEquals(List.of(AWAITING_CEILING_KEY + " is declared by no key of " + CEILING_RATCHETS), missing);

        assertEquals(List.of(UNEXPLAINED_CEILING_KEY + " is \"1.0\" in " + CEILING_RATCHETS + ", which is not a count"),
                ceilingRefusals(ratchets(UNEXPLAINED_CEILING_KEY + "=1.0"), UNEXPLAINED_CEILING_KEY, 1));

        List<String> keys = keyRefusals(declared);
        assertEquals(List.of(AWAITING_CEILING_KEY + " is declared by no key of " + CEILING_RATCHETS,
                "SomeTest.RETIRED_CEILING is a key of " + CEILING_RATCHETS + " that no ceiling reads"), keys);
    }

    @Test
    void theFamilyCountsOnlyABanOfATypeWrittenInCodeWithNoMessage() {
        String source = """
                class Sample {
                    void bans() {
                        assertFalse(source.contains("SomeType"));
                        assertFalse(source.contains("SomeType"), "the server must not depend on SomeType");
                        assertFalse(source.contains("someMethod("));
                        assertTrue(source.contains("SomeType"));
                        // assertFalse(source.contains("SomeType"));
                        /* assertFalse(source.contains("SomeType")); */
                        String quoted = "assertFalse(source.contains(\\"SomeType\\"))";
                        String block = \"""
                                assertFalse(source.contains("SomeType"));
                                \""";
                    }
                }
                """;

        assertEquals(1, unexplainedTypeBans(source));
        assertEquals(1, explainedTypeBans(source));
    }

    @Test
    void theCompositionCountsReadCodeOnly() {
        String source = """
                class Sample {
                    void checks() {
                        noClasses().should().bePublic().check(classes);
                        // rule.check(classes);
                        assertTrue(text.contains("x"));
                        assertFalse(Files.readString(path).contains("y"), "no y");
                        assertTrue(!text.contains("z"));
                        String quoted = "assertTrue(text.contains(\\"x\\"))";
                    }
                }
                """;

        assertEquals(1, countInCode(RULE_EVALUATION, source));
        assertEquals(3, countInCode(TEXTUAL_ASSERTION, source));
    }

    @Test
    void theSuitePublishesTheCompositionOfItsEnforcementByOneStatedMethod() throws IOException {
        Map<String, Long> composition = composition(ArchitectureSuiteSources.suite());
        Path proof = RepositoryRoot.locate().resolve(PROOF_FILE);
        Files.createDirectories(proof.getParent());
        List<String> lines = new ArrayList<>();
        lines.add(METHOD);
        composition.forEach((key, value) -> lines.add(key + "=" + value));
        Files.write(proof, lines, StandardCharsets.UTF_8);

        List<String> written = Files.readAllLines(proof, StandardCharsets.UTF_8);
        assertEquals(METHOD, written.get(0), "the first line of the proof file names its method");
        List<String> keys = written.subList(1, written.size()).stream()
                .map(line -> line.substring(0, line.indexOf('=')))
                .toList();
        assertEquals(PROOF_KEYS, keys, "the proof file gives every count, in a stable order");
        assertTrue(written.subList(1, written.size()).stream().allMatch(line -> line.matches("[\\w.]+=\\d+")),
                () -> "every count of the proof file is a number: " + written);
    }

    private static Map<String, Long> composition(Suite suite) {
        Map<String, Long> composition = new LinkedHashMap<>();
        PROOF_KEYS.forEach(key -> composition.put(key, 0L));
        for (TestClass testClass : suite.testClasses()) {
            String source = testClass.source();
            boolean bytecode = ArchitectureSuiteSources.evaluatesRulesOnBytecode(source);
            boolean readsProduction = suite.readsAProductionSource(source);
            boolean coherence = suite.isCoherenceCheck(source);
            long textual = countInCode(TEXTUAL_ASSERTION, source);
            add(composition, "testClasses", 1);
            add(composition, "testDeclarations", ArchitectureSuiteSources.testDeclarations(source));
            add(composition, "classesEvaluatingRulesOnBytecode", bytecode ? 1 : 0);
            add(composition, "classesReadingAProductionSource", readsProduction ? 1 : 0);
            add(composition, "coherenceClasses", coherence ? 1 : 0);
            add(composition, "rulesEvaluated", bytecode ? countInCode(RULE_EVALUATION, source) : 0);
            add(composition, readsProduction ? "textualAssertions.inClassesReadingAProductionSource"
                    : coherence ? "textualAssertions.inCoherenceClasses" : "textualAssertions.inOtherClasses", textual);
            add(composition, "typeBans.withoutIntent", unexplainedTypeBans(source));
            add(composition, "typeBans.withIntent", explainedTypeBans(source));
        }
        return composition;
    }

    private static void add(Map<String, Long> composition, String key, long value) {
        composition.merge(key, value, Long::sum);
    }

    static Properties ceilingRatchets() {
        Properties declared = new Properties();
        try (Reader reader = Files.newBufferedReader(RepositoryRoot.locate().resolve(CEILING_RATCHETS),
                StandardCharsets.UTF_8)) {
            declared.load(reader);
        } catch (IOException failure) {
            throw new UncheckedIOException(CEILING_RATCHETS + " must exist and be readable", failure);
        }
        return declared;
    }

    /** The refusals of a ceiling whose key is missing from {@code declared} or holds another value. */
    static List<String> ceilingRefusals(Properties declared, String key, int ceiling) {
        String value = declared.getProperty(key);
        if (value == null) {
            return List.of(key + " is declared by no key of " + CEILING_RATCHETS);
        }
        if (!value.trim().matches("\\d+")) {
            return List.of(key + " is \"" + value + "\" in " + CEILING_RATCHETS + ", which is not a count");
        }
        int ratchet = Integer.parseInt(value.trim());
        if (ceiling == ratchet) {
            return List.of();
        }
        String difference = key + " is " + ceiling + " in its test and " + ratchet + " in " + CEILING_RATCHETS + ": ";
        return List.of(ceiling < ratchet
                ? difference + "lower the key to " + ceiling + " in this change, or the population could grow back to it"
                : difference + "a ceiling is raised only with its key, a governance file whose change is justified in "
                        + "the pull request");
    }

    /** The refusals of a missing key, and of a key no ceiling reads. */
    static List<String> keyRefusals(Properties declared) {
        List<String> refusals = new ArrayList<>();
        CEILING_KEYS.stream().filter(key -> !declared.containsKey(key))
                .forEach(key -> refusals.add(key + " is declared by no key of " + CEILING_RATCHETS));
        new TreeSet<>(declared.stringPropertyNames()).stream().filter(key -> !CEILING_KEYS.contains(key))
                .forEach(key -> refusals.add(key + " is a key of " + CEILING_RATCHETS + " that no ceiling reads"));
        return refusals;
    }

    private static Properties ratchets(String... lines) {
        Properties declared = new Properties();
        try {
            declared.load(new StringReader(String.join("\n", lines)));
        } catch (IOException failure) {
            throw new UncheckedIOException(failure);
        }
        return declared;
    }

    static long unexplainedTypeBans(String source) {
        return count(UNEXPLAINED_TYPE_BAN, source);
    }

    static long explainedTypeBans(String source) {
        return count(EXPLAINED_TYPE_BAN, source);
    }

    /** Matches in the code with its string literals kept: the family is told apart by the literal it bans. */
    private static long count(Pattern pattern, String source) {
        return pattern.matcher(TestSourceText.codeAndStrings(source)).results().count();
    }

    /** Matches in the code alone: a call quoted in a string literal is not a call. */
    private static long countInCode(Pattern pattern, String source) {
        return pattern.matcher(TestSourceText.code(source)).results().count();
    }
}
