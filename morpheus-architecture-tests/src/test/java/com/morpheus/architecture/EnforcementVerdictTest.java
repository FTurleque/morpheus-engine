package com.morpheus.architecture;

import com.morpheus.architecture.EnforcementVerdict.Kind;
import com.morpheus.architecture.EnforcementVerdict.Outcome;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The classification of a run of the suite into two verdicts: a failure of a coherence check is documentation drift,
 * any other failure is an architecture violation. The listener that feeds it only reads the JUnit platform; the
 * decision is here, on plain values.
 */
@CoherenceCheck
class EnforcementVerdictTest {

    private static final Map<String, String> PARENTS = Map.of(
            "factory", "DocumentsTest",
            "dynamic[1]", "factory",
            "method", "DocumentsTest",
            "rule", "LayersTest");
    private static final Map<String, Set<String>> TAGS = Map.of("DocumentsTest", Set.of(CoherenceCheck.TAG));

    @Test
    void aTestInheritsTheCoherenceTagOfItsClassAtAnyDepth() {
        assertTrue(coherence("method"), "a method of a marked class is a coherence check");
        assertTrue(coherence("dynamic[1]"), "a generated test under a marked class is a coherence check");
        assertFalse(coherence("rule"), "a method of an unmarked class is not");
        assertFalse(coherence("LayersTest"), "an unmarked class is not");
    }

    @Test
    void onlyAFailedCoherenceCheckIsDocumentationDrift() {
        EnforcementVerdict verdict = new EnforcementVerdict(List.of(
                new Outcome("DocumentsTest", Kind.TEST_CLASS, true, false),
                new Outcome("DocumentsTest#pageSaysIt()", Kind.TEST, true, true),
                new Outcome("LayersTest", Kind.TEST_CLASS, false, false),
                new Outcome("LayersTest#noCycle()", Kind.TEST, false, false)));

        assertEquals(List.of("DocumentsTest#pageSaysIt()"), verdict.documentationDrift());
        assertEquals(List.of(), verdict.architectureViolations(), "no rule on the code failed");
        assertTrue(verdict.failed());
    }

    @Test
    void onlyAFailedCodeCheckIsAnArchitectureViolation() {
        EnforcementVerdict verdict = new EnforcementVerdict(List.of(
                new Outcome("DocumentsTest#pageSaysIt()", Kind.TEST, true, false),
                new Outcome("LayersTest#noCycle()", Kind.TEST, false, true)));

        assertEquals(List.of(), verdict.documentationDrift(), "no coherence check failed");
        assertEquals(List.of("LayersTest#noCycle()"), verdict.architectureViolations());
    }

    @Test
    void bothNaturesFailingTogetherGiveTwoVerdictsEachWithItsOwnTestsSorted() {
        EnforcementVerdict verdict = new EnforcementVerdict(List.of(
                new Outcome("ZTest#z()", Kind.TEST, false, true),
                new Outcome("DocumentsTest#b()", Kind.TEST, true, true),
                new Outcome("ATest", Kind.TEST_CLASS, false, true),
                new Outcome("DocumentsTest#a()", Kind.TEST, true, true)));

        assertEquals(List.of("DocumentsTest#a()", "DocumentsTest#b()"), verdict.documentationDrift());
        assertEquals(List.of("ATest", "ZTest#z()"), verdict.architectureViolations(),
                "a failed container is named with the failed tests of its nature");
    }

    @Test
    void aGreenRunWritesTwoEmptyVerdictsAndCountsTheExecutionsOfEachNature() {
        EnforcementVerdict verdict = new EnforcementVerdict(List.of(
                new Outcome("DocumentsTest", Kind.TEST_CLASS, true, false),
                new Outcome("DocumentsTest#a()", Kind.TEST, true, false),
                new Outcome("DocumentsTest#b()", Kind.TEST, true, false),
                new Outcome("LayersTest", Kind.TEST_CLASS, false, false),
                new Outcome("LayersTest#noCycle()", Kind.TEST, false, false),
                new Outcome("factory", Kind.OTHER_CONTAINER, false, false)));

        assertFalse(verdict.failed());
        assertEquals(List.of(
                EnforcementVerdict.METHOD,
                "testClasses=2",
                "executions.coherence=2",
                "executions.code=1",
                "documentationDrift=0",
                "architectureViolations=0"), verdict.proofLines());
    }

    @Test
    void theProofFileNamesTheTestsOfEachVerdictUnderItsCount() {
        EnforcementVerdict verdict = new EnforcementVerdict(List.of(
                new Outcome("DocumentsTest#a()", Kind.TEST, true, true),
                new Outcome("LayersTest#noCycle()", Kind.TEST, false, true)));

        assertEquals(List.of(
                EnforcementVerdict.METHOD,
                "testClasses=0",
                "executions.coherence=1",
                "executions.code=1",
                "documentationDrift=1",
                "  DocumentsTest#a()",
                "architectureViolations=1",
                "  LayersTest#noCycle()"), verdict.proofLines());
        assertEquals(List.of(
                "[enforcement verdict] documentation drift: 1 test(s) - DocumentsTest#a()",
                "[enforcement verdict] architecture violation: 1 test(s) - LayersTest#noCycle()"),
                verdict.consoleLines());
    }

    private static boolean coherence(String node) {
        return EnforcementVerdict.carriesTheTag(node, id -> Optional.ofNullable(PARENTS.get(id)),
                id -> TAGS.getOrDefault(id, Set.of()), CoherenceCheck.TAG);
    }
}
