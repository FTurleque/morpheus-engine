package com.morpheus.architecture;

import com.morpheus.architecture.ArchitectureSuiteSources.Suite;
import com.morpheus.architecture.ArchitectureSuiteSources.TestClass;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
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
 * fails until the ceiling is lowered in the same change. A new textual ban of a type carries its intent as the
 * assertion's message, and so leaves the family by construction. No known reason justifies raising the ceiling.</p>
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
     * The textual bans of a type that state no intent, measured on {@code develop} at {@code 6b3f6304} on 29/10/2026.
     * Lowered with every member removed; never raised.
     */
    static final int UNEXPLAINED_TYPE_BAN_CEILING = 196;

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
