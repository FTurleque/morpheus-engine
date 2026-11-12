package com.morpheus.architecture.m21;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.morpheus.architecture.CoherenceCheck;
import com.morpheus.architecture.RepositoryRoot;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * A coverage cap equals the lowest measurement of its scale in evidence kept with the code.
 *
 * <p>{@code config/coverage-qualification/} holds the coverage summaries of the qualification in force and
 * {@code evidence.tsv}, which names each one's scale, platform, run and commit. This suite binds the four
 * {@code *_QUALIFIED_*_RATIO} constants of the two coverage gates to those files: a constant must equal the lowest
 * kept ratio of its scale, and the kept set must be complete -- both platforms, at least two runs each, one commit
 * shared by both scales, one population per scale.</p>
 *
 * <p>The per-module summaries also carry each module's own counters, which must make their totals, and each floor of
 * {@code config/m21-per-module-coverage-floors.properties} stays in the window they grant: between three and nine
 * times the module's spread under its lowest kept count, or the D2 floor when no higher floor fits, its reference
 * spread being the scale's in proportion to the module's share of the population (ADR-0167).</p>
 *
 * <p>Both bindings are textual because their subjects lie outside the compiled classpath: the constants are private
 * to gates in two different modules, and the evidence is data ({@code .claude/rules/architecture.md}). The rule is
 * exercised against fabricated
 * sets, so that a verifier that accepts anything fails here.</p>
 */
@CoherenceCheck
class CoverageQualificationEvidenceTest {
    private static final String COMMIT = "1111111111111111111111111111111111111111";
    private static final String OTHER_COMMIT = "2222222222222222222222222222222222222222";
    private static final String EVIDENCE_DIRECTORY = "config/coverage-qualification";
    private static final String MANIFEST = "evidence.tsv";
    private static final String MANIFEST_HEADER = "scale\tplatform\trun\tcommit\tfile";
    private static final String PER_MODULE_GATE =
            "morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m21/CoverageQualityGateTest.java";
    private static final String AGGREGATE_GATE =
            "morpheus-coverage-report/src/test/java/com/morpheus/coverage/AggregateCoverageGateTest.java";
    private static final List<String> PLATFORMS = List.of("Linux", "Windows");
    private static final int MINIMUM_RUNS_PER_PLATFORM = 2;
    private static final double RATIO_PRECISION = 1.0e-9d;
    private static final double SIX_DIGIT_ROUNDING = 5.1e-7d;
    private static final String RATCHETS = "config/m21-quality-ratchets.properties";
    private static final long HEADROOM_SPREADS = 3L;
    // A module floor leaves at most three times its margin under its lowest kept count (ADR-0167): further down, the
    // module could lose most of its coverage before its own floor bites.
    private static final long MAXIMUM_HEADROOM_SPREADS = 9L;
    // Widest spread of covered counts observed across the runs of one qualification, on either scale: 24 lines and
    // 11 branches, 15/09/2026 at a1fc0a8d (CoverageQualityGateTest cites the runs).
    private static final long REFERENCE_LINE_SPREAD = 24L;
    private static final long REFERENCE_BRANCH_SPREAD = 11L;
    private static final String MODULE_FLOORS = "config/m21-per-module-coverage-floors.properties";
    private static final String LINE_FLOOR_SUFFIX = ".lineCoverageMinimum";
    private static final String BRANCH_FLOOR_SUFFIX = ".branchCoverageMinimum";
    private static final String MODULE_PREFIX = "module.";
    private static final List<String> MODULE_COUNTERS =
            List.of("lineCovered", "lineMissed", "branchCovered", "branchMissed");

    private enum Scale {
        PER_MODULE("per-module", "perModule", "PER_MODULE_QUALIFIED_LINE_RATIO", "PER_MODULE_QUALIFIED_BRANCH_RATIO",
                PER_MODULE_GATE),
        AGGREGATE("aggregate", "aggregate", "AGGREGATE_QUALIFIED_LINE_RATIO", "AGGREGATE_QUALIFIED_BRANCH_RATIO",
                AGGREGATE_GATE);

        private final String name;
        private final String ratchetPrefix;
        private final String lineConstant;
        private final String branchConstant;
        private final String gate;

        Scale(String name, String ratchetPrefix, String lineConstant, String branchConstant, String gate) {
            this.name = name;
            this.ratchetPrefix = ratchetPrefix;
            this.lineConstant = lineConstant;
            this.branchConstant = branchConstant;
            this.gate = gate;
        }

        private String ratchetKey(String counter) {
            return ratchetPrefix + counter + "CoverageMinimum";
        }
    }

    private record Entry(Scale scale, String platform, String run, String commit, String file) {
    }

    private record Summary(Entry entry, Map<String, String> values) {
        private double ratio(String key) {
            return Double.parseDouble(values.get(key));
        }

        private String population() {
            return values.getOrDefault("population", "") + "|populationModules="
                    + values.getOrDefault("populationModules", "") + "|reports=" + values.getOrDefault("reports", "");
        }

        private String denominators() {
            return (Long.parseLong(values.get("lineCovered")) + Long.parseLong(values.get("lineMissed"))) + " lines, "
                    + (Long.parseLong(values.get("branchCovered")) + Long.parseLong(values.get("branchMissed")))
                    + " branches";
        }
    }

    private record Caps(Map<Scale, double[]> lineAndBranch) {
    }

    @Test
    void everyCapEqualsTheLowestMeasurementOfItsScaleInTheKeptEvidence() throws IOException {
        Path root = RepositoryRoot.locate();
        List<String> problems = verify(root.resolve(EVIDENCE_DIRECTORY), readCaps(root));
        assertTrue(problems.isEmpty(), () -> "the coverage caps do not rest on kept evidence:\n  "
                + String.join("\n  ", problems));
    }

    @Test
    void everyCoverageRatchetKeepsThreeTimesTheSpreadUnderTheLowestKeptMeasurement() throws IOException {
        Path root = RepositoryRoot.locate();
        List<String> problems = margins(root.resolve(EVIDENCE_DIRECTORY), readRatchets(root));
        assertTrue(problems.isEmpty(), () -> "a coverage ratchet spends headroom no qualification granted:\n  "
                + String.join("\n  ", problems));
    }

    @Test
    void aRatchetAtExactlyThreeTimesTheSpreadIsAcceptedAndOneThousandthMoreIsRefused(@TempDir Path workspace)
            throws IOException {
        Path evidence = qualification(workspace, new double[] {0.60d, 0.70d, 0.65d, 0.75d});

        assertEquals(List.of(), margins(evidence, ratchets(0.27d, 0.32d, 0.27d, 0.32d)));

        List<String> problems = margins(evidence, ratchets(0.271d, 0.32d, 0.27d, 0.321d));
        assertEquals(2, problems.size(), problems::toString);
        assertTrue(problems.get(0).contains("perModuleLineCoverageMinimum") && problems.get(0).contains("32900")
                && problems.get(0).contains("33000"), problems.get(0));
        assertTrue(problems.get(1).contains("aggregateBranchCoverageMinimum") && problems.get(1).contains("13160")
                && problems.get(1).contains("13200"), problems.get(1));
    }

    @Test
    void aSpreadNarrowerThanTheReferenceIsSizedAgainstTheReference(@TempDir Path workspace) throws IOException {
        Path evidence = qualification(workspace, new double[] {0.60d, 0.60d, 0.65d, 0.65d}, 0.0d);

        List<String> problems = margins(evidence, ratchets(0.5993d, 0.65d, 0.60d, 0.65d));

        assertTrue(problems.stream().anyMatch(problem -> problem.contains("perModuleLineCoverageMinimum")
                && problem.contains("72") && problem.contains("reference")), problems::toString);
        assertTrue(problems.stream().anyMatch(problem -> problem.contains("perModuleBranchCoverageMinimum")
                && problem.contains("33")), problems::toString);
    }

    @Test
    void aCapAboveTheLowestKeptMeasurementIsRefusedAndNamed(@TempDir Path workspace) throws IOException {
        Path evidence = qualification(workspace, new double[] {0.60d, 0.70d, 0.65d, 0.75d});
        Map<Scale, double[]> caps = honestCaps();

        caps.put(Scale.PER_MODULE, new double[] {0.600001d, 0.65d});
        List<String> problems = verify(evidence, new Caps(caps));

        assertEquals(1, problems.size(), problems::toString);
        assertTrue(problems.get(0).contains("PER_MODULE_QUALIFIED_LINE_RATIO")
                && problems.get(0).contains("0.600001") && problems.get(0).contains("0.6"), problems.get(0));
    }

    @Test
    void aCapQualifiedOnTheBestPlatformIsRefused(@TempDir Path workspace) throws IOException {
        Path evidence = qualification(workspace, new double[] {0.60d, 0.70d, 0.65d, 0.75d});
        Map<Scale, double[]> caps = honestCaps();

        caps.put(Scale.AGGREGATE, new double[] {0.60d, 0.75d});
        List<String> problems = verify(evidence, new Caps(caps));

        assertEquals(1, problems.size(), problems::toString);
        assertTrue(problems.get(0).contains("AGGREGATE_QUALIFIED_BRANCH_RATIO"), problems.get(0));
    }

    @Test
    void evidenceReplacedWithoutTheConstantIsRefusedAndNamesTheConstant(@TempDir Path workspace) throws IOException {
        Path evidence = qualification(workspace, new double[] {0.61d, 0.71d, 0.66d, 0.76d});

        List<String> problems = verify(evidence, new Caps(honestCaps()));

        assertEquals(4, problems.size(), problems::toString);
        for (Scale scale : Scale.values()) {
            assertTrue(problems.stream().anyMatch(problem -> problem.contains(scale.lineConstant)), problems::toString);
            assertTrue(problems.stream().anyMatch(problem -> problem.contains(scale.branchConstant)),
                    problems::toString);
        }
    }

    @Test
    void aQualificationThatLacksAPlatformIsRefusedAndNamesIt(@TempDir Path workspace) throws IOException {
        Path evidence = qualification(workspace, new double[] {0.60d, 0.70d, 0.65d, 0.75d});
        dropEntries(evidence, line -> line.startsWith("aggregate\tWindows\t"));

        List<String> problems = verify(evidence, new Caps(honestCaps()));

        assertTrue(problems.stream().anyMatch(problem -> problem.contains("aggregate")
                && problem.contains("Windows") && problem.contains("lacks")), problems::toString);
    }

    @Test
    void aPlatformMeasuredOnlyOnceIsRefused(@TempDir Path workspace) throws IOException {
        Path evidence = qualification(workspace, new double[] {0.60d, 0.70d, 0.65d, 0.75d});
        dropEntries(evidence, line -> line.startsWith("per-module\tLinux\t2"));

        List<String> problems = verify(evidence, new Caps(honestCaps()));

        assertTrue(problems.stream().anyMatch(problem -> problem.contains("per-module")
                && problem.contains("Linux") && problem.contains("at least 2")), problems::toString);
    }

    @Test
    void summariesOfTwoCommitsAreRefusedAndNameBoth(@TempDir Path workspace) throws IOException {
        Path evidence = qualification(workspace, new double[] {0.60d, 0.70d, 0.65d, 0.75d});
        Path manifest = evidence.resolve(MANIFEST);
        String text = Files.readString(manifest);
        int last = text.lastIndexOf(COMMIT);
        Files.writeString(manifest, text.substring(0, last) + OTHER_COMMIT + text.substring(last + COMMIT.length()));

        List<String> problems = verify(evidence, new Caps(honestCaps()));

        assertTrue(problems.stream().anyMatch(problem -> problem.contains("aggregate")
                && problem.contains(COMMIT) && problem.contains(OTHER_COMMIT)), problems::toString);
    }

    @Test
    void twoScalesQualifiedAtDifferentHeadsAreRefusedAndNameBothHeads(@TempDir Path workspace) throws IOException {
        Path evidence = qualification(workspace, new double[] {0.60d, 0.70d, 0.65d, 0.75d});
        Path manifest = evidence.resolve(MANIFEST);
        Files.writeString(manifest, Files.readAllLines(manifest).stream()
                .map(line -> line.startsWith(Scale.AGGREGATE.name + "\t") ? line.replace(COMMIT, OTHER_COMMIT) : line)
                .reduce((left, right) -> left + "\n" + right).orElseThrow() + "\n");

        List<String> problems = verify(evidence, new Caps(honestCaps()));

        assertEquals(1, problems.size(), problems::toString);
        assertTrue(problems.get(0).contains("different heads") && problems.get(0).contains("per-module at " + COMMIT)
                && problems.get(0).contains("aggregate at " + OTHER_COMMIT), problems.get(0));
    }

    @Test
    void twoPopulationsOnTheAggregateScaleAreRefused(@TempDir Path workspace) throws IOException {
        Path evidence = qualification(workspace, new double[] {0.60d, 0.70d, 0.65d, 0.75d});
        Path altered = evidence.resolve("aggregate/Windows-2.txt");
        Files.writeString(altered, Files.readString(altered).replace("morpheus-cli", "morpheus-other"));

        List<String> problems = verify(evidence, new Caps(honestCaps()));

        assertTrue(problems.stream().anyMatch(problem -> problem.contains("aggregate")
                && problem.contains("population")), problems::toString);
    }

    @Test
    void aSummaryOfTheOtherScaleKeptAsEvidenceIsRefusedAndNamesTheFile(@TempDir Path workspace) throws IOException {
        Path evidence = qualification(workspace, new double[] {0.60d, 0.70d, 0.65d, 0.75d});
        Files.copy(evidence.resolve("per-module/Linux-1.txt"), evidence.resolve("aggregate/Linux-1.txt"),
                StandardCopyOption.REPLACE_EXISTING);

        List<String> problems = verify(evidence, new Caps(honestCaps()));

        assertTrue(problems.stream().anyMatch(problem -> problem.contains("aggregate/Linux-1.txt")
                && problem.contains("coverageScope=per-module")), problems::toString);
    }

    @Test
    void aSummaryWhoseRatioDisagreesWithItsCountsIsRefused(@TempDir Path workspace) throws IOException {
        Path evidence = qualification(workspace, new double[] {0.60d, 0.70d, 0.65d, 0.75d});
        Path edited = evidence.resolve("per-module/Linux-1.txt");
        Files.writeString(edited, Files.readString(edited).replace("lineCovered=60000", "lineCovered=61000"));

        List<String> problems = verify(evidence, new Caps(honestCaps()));

        assertTrue(problems.stream().anyMatch(problem -> problem.contains("per-module/Linux-1.txt")
                && problem.contains("lineRatio")), problems::toString);
    }

    @Test
    void aFileKeptOutsideTheManifestAndAManifestedFileThatIsAbsentAreRefused(@TempDir Path workspace)
            throws IOException {
        Path evidence = qualification(workspace, new double[] {0.60d, 0.70d, 0.65d, 0.75d});
        Files.writeString(evidence.resolve("aggregate/Linux-orphan.txt"), "coverageScope=aggregate\n");
        Files.delete(evidence.resolve("per-module/Windows-2.txt"));

        List<String> problems = verify(evidence, new Caps(honestCaps()));

        assertTrue(problems.stream().anyMatch(problem -> problem.contains("aggregate/Linux-orphan.txt")
                && problem.contains("not listed")), problems::toString);
        assertTrue(problems.stream().anyMatch(problem -> problem.contains("per-module/Windows-2.txt")
                && problem.contains("absent")), problems::toString);
    }

    @Test
    void anEmptyEvidenceDirectoryIsRefusedBecauseTheCapsRestOnNothing(@TempDir Path workspace) throws IOException {
        List<String> problems = verify(workspace, new Caps(honestCaps()));

        assertEquals(1, problems.size(), problems::toString);
        assertTrue(problems.get(0).contains("rest on nothing a build can read"), problems.get(0));
    }

    @Test
    void everyModuleFloorStaysInsideItsQualifiedWindow() throws IOException {
        Path root = RepositoryRoot.locate();
        String gate = Files.readString(root.resolve(PER_MODULE_GATE));
        List<String> problems = floorWindows(root.resolve(EVIDENCE_DIRECTORY), loadProperties(root.resolve(MODULE_FLOORS)),
                constant(gate, PER_MODULE_GATE, "D2_MIN_LINE_RATIO"), constant(gate, PER_MODULE_GATE, "D2_MIN_BRANCH_RATIO"));
        assertTrue(problems.isEmpty(), () -> "a per-module floor is set outside the window its evidence grants:\n  "
                + String.join("\n  ", problems));
    }

    @Test
    void keptModuleCountersThatMakeTheTotalsAreAcceptedAndOnesThatDoNotAreRefusedWithBothValues(@TempDir Path workspace)
            throws IOException {
        Path evidence = withModuleCounters(qualification(workspace, new double[] {0.60d, 0.70d, 0.65d, 0.75d}),
                file -> new long[] {10_000L, 10_000L, 6_000L, 2_000L});
        assertEquals(List.of(), verify(evidence, new Caps(honestCaps())));

        Path edited = evidence.resolve("per-module/Windows-2.txt");
        Files.writeString(edited, Files.readString(edited).replace("module.morpheus-cli.lineCovered=10000",
                "module.morpheus-cli.lineCovered=10005"));

        List<String> problems = verify(evidence, new Caps(honestCaps()));
        assertTrue(problems.stream().anyMatch(problem -> problem.contains("per-module/Windows-2.txt")
                && problem.contains("lineCovered") && problem.contains("71005") && problem.contains("71000")),
                problems::toString);
    }

    @Test
    void aKeptSummaryThatOmitsAModuleIsRefusedAndNamesTheFileAndTheModule(@TempDir Path workspace) throws IOException {
        Path evidence = withModuleCounters(qualification(workspace, new double[] {0.60d, 0.70d, 0.65d, 0.75d}),
                file -> new long[] {10_000L, 10_000L, 6_000L, 2_000L});
        Path amputated = evidence.resolve("per-module/Linux-1.txt");
        Files.writeString(amputated, Files.readAllLines(amputated).stream()
                .filter(line -> !line.startsWith("module.morpheus-cli.branch"))
                .reduce((left, right) -> left + "\n" + right).orElseThrow() + "\n");
        Path stripped = evidence.resolve("per-module/Windows-2.txt");
        Files.writeString(stripped, Files.readAllLines(stripped).stream()
                .filter(line -> !line.startsWith("module."))
                .reduce((left, right) -> left + "\n" + right).orElseThrow() + "\n");

        List<String> problems = verify(evidence, new Caps(honestCaps()));

        assertTrue(problems.stream().anyMatch(problem -> problem.contains("per-module/Linux-1.txt")
                && problem.contains("module.morpheus-cli.branchCovered")), problems::toString);
        assertTrue(problems.stream().anyMatch(problem -> problem.contains("per-module/Windows-2.txt")
                && problem.contains("carries no module counters")), problems::toString);
    }

    @Test
    void twoKeptSummariesThatNameDifferentModulesAreRefusedAndNameTheDifference(@TempDir Path workspace)
            throws IOException {
        Path evidence = withModuleCounters(qualification(workspace, new double[] {0.60d, 0.70d, 0.65d, 0.75d}),
                file -> new long[] {10_000L, 10_000L, 6_000L, 2_000L});
        Path renamed = evidence.resolve("per-module/Windows-1.txt");
        Files.writeString(renamed, Files.readString(renamed).replace("module.morpheus-cli.", "module.morpheus-other."));

        List<String> problems = verify(evidence, new Caps(honestCaps()));

        assertTrue(problems.stream().anyMatch(problem -> problem.contains("per-module/Windows-1.txt")
                && problem.contains("morpheus-cli")), problems::toString);
        assertTrue(problems.stream().anyMatch(problem -> problem.contains("morpheus-other")), problems::toString);
    }

    @Test
    void aModuleLessStableThanTheReferenceIsSizedAgainstItsOwnSpread(@TempDir Path workspace) throws IOException {
        Path evidence = withModuleCounters(qualification(workspace, new double[] {0.60d, 0.70d, 0.65d, 0.75d}),
                file -> new long[] {file.equals("per-module/Linux-1.txt") ? 10_000L : 10_100L,
                        file.equals("per-module/Linux-1.txt") ? 10_000L : 9_900L, 6_000L, 2_000L});

        assertEquals(List.of(), floorWindows(evidence, floors("morpheus-cli", 0.485d, 0.748d), 0.40d, 0.35d));

        List<String> problems = floorWindows(evidence, floors("morpheus-cli", 0.4851d, 0.748d), 0.40d, 0.35d);
        assertEquals(1, problems.size(), problems::toString);
        assertTrue(problems.get(0).contains("morpheus-cli.lineCoverageMinimum") && problems.get(0).contains("10000")
                && problems.get(0).contains("needs 300") && problems.get(0).contains("spread of 100"), problems.get(0));
    }

    @Test
    void aStableModuleIsSizedAgainstItsShareOfTheReferenceSpreadNeverAgainstZero(@TempDir Path workspace)
            throws IOException {
        Path evidence = withModuleCounters(qualification(workspace, new double[] {0.60d, 0.70d, 0.65d, 0.75d}),
                file -> new long[] {10_000L, 10_000L, 6_000L, 2_000L});

        assertEquals(List.of(), floorWindows(evidence, floors("morpheus-cli", 0.49925d, 0.7488d), 0.40d, 0.35d));

        List<String> problems = floorWindows(evidence, floors("morpheus-cli", 0.4993d, 0.7489d), 0.40d, 0.35d);
        assertEquals(2, problems.size(), problems::toString);
        assertTrue(problems.get(0).contains("morpheus-cli.branchCoverageMinimum") && problems.get(0).contains("needs 9")
                && problems.get(0).contains("reference spread of 3")
                && problems.get(0).contains("8000 of the 40000 branch units"), problems.get(0));
        assertTrue(problems.get(1).contains("morpheus-cli.lineCoverageMinimum") && problems.get(1).contains("needs 15")
                && problems.get(1).contains("reference spread of 5")
                && problems.get(1).contains("20000 of the 100000 line units"), problems.get(1));
    }

    @Test
    void aSmallStableModuleKeepsAMarginOfThreeUnitsAndARefusalNamesItsShare(@TempDir Path workspace)
            throws IOException {
        Path evidence = withModuleCounters(
                qualification(workspace, new double[] {0.60d, 0.70d, 0.65d, 0.75d}, 0.01d, 29_900L, 10_148L),
                file -> new long[] {51L, 12L, 22L, 10L});

        assertEquals(List.of(), floorWindows(evidence, floors("morpheus-cli", 0.761d, 0.593d), 0.40d, 0.35d));

        List<String> problems = floorWindows(evidence, floors("morpheus-cli", 0.762d, 0.594d), 0.40d, 0.35d);
        assertEquals(2, problems.size(), problems::toString);
        assertTrue(problems.get(0).contains("morpheus-cli.branchCoverageMinimum") && problems.get(0).contains("needs 3")
                && problems.get(0).contains("reference spread of 1")
                && problems.get(0).contains("32 of the 10148 branch units"), problems.get(0));
        assertTrue(problems.get(1).contains("morpheus-cli.lineCoverageMinimum") && problems.get(1).contains("needs 3")
                && problems.get(1).contains("reference spread of 1")
                && problems.get(1).contains("63 of the 29900 line units"), problems.get(1));
    }

    @Test
    void aFloorAbandonedFarUnderItsMeasurementIsRefusedAndNamesTheLowestAdmittedFloor(@TempDir Path workspace)
            throws IOException {
        Path evidence = withModuleCounters(qualification(workspace, new double[] {0.60d, 0.70d, 0.65d, 0.75d}),
                file -> new long[] {10_000L, 10_000L, 6_000L, 2_000L});

        assertEquals(List.of(), floorWindows(evidence, floors("morpheus-cli", 0.498d, 0.748d), 0.40d, 0.35d));

        List<String> problems = floorWindows(evidence, floors("morpheus-cli", 0.45d, 0.748d), 0.40d, 0.35d);
        assertEquals(1, problems.size(), problems::toString);
        assertTrue(problems.get(0).contains("morpheus-cli.lineCoverageMinimum") && problems.get(0).contains("leaves 1000")
                && problems.get(0).contains("at most 45") && problems.get(0).contains("raise it to at least 0.498"),
                problems.get(0));
    }

    @Test
    void aFloorAtTheD2FloorIsRefusedWhenTheBandLiesAboveItAndAdmittedWhenTheWindowIsEmpty(@TempDir Path workspace)
            throws IOException {
        Path banded = withModuleCounters(
                qualification(workspace.resolve("banded"), new double[] {0.60d, 0.70d, 0.65d, 0.75d}),
                file -> new long[] {10_000L, 10_000L, 6_000L, 2_000L});
        List<String> problems = floorWindows(banded, floors("morpheus-cli", 0.40d, 0.748d), 0.40d, 0.35d);
        assertEquals(1, problems.size(), problems::toString);
        assertTrue(problems.get(0).contains("morpheus-cli.lineCoverageMinimum")
                && problems.get(0).contains("raise it to at least 0.498"), problems.get(0));

        Path empty = withModuleCounters(
                qualification(workspace.resolve("empty"), new double[] {0.60d, 0.70d, 0.65d, 0.75d}),
                file -> new long[] {30L, 70L, 30L, 70L});
        assertEquals(List.of(), floorWindows(empty, floors("morpheus-cli", 0.40d, 0.35d), 0.40d, 0.35d));
    }

    @Test
    void aModuleFloorUnderTheD2FloorIsRefusedAndNamesTheModuleAndTheKey(@TempDir Path workspace) throws IOException {
        Path evidence = withModuleCounters(qualification(workspace, new double[] {0.60d, 0.70d, 0.65d, 0.75d}),
                file -> new long[] {10_000L, 10_000L, 6_000L, 2_000L});

        List<String> problems = floorWindows(evidence, floors("morpheus-cli", 0.498d, 0.34d), 0.40d, 0.35d);

        assertEquals(1, problems.size(), problems::toString);
        assertTrue(problems.get(0).contains("morpheus-cli.branchCoverageMinimum") && problems.get(0).contains("0.34")
                && problems.get(0).contains("below the D2 floor"), problems.get(0));
    }

    @Test
    void aModuleTheEvidenceDoesNotCarryMayDeclareTheD2FloorAndNothingAbove(@TempDir Path workspace)
            throws IOException {
        Path withModules = withModuleCounters(
                qualification(workspace.resolve("with-modules"), new double[] {0.60d, 0.70d, 0.65d, 0.75d}),
                file -> new long[] {10_000L, 10_000L, 6_000L, 2_000L});
        Path totalsOnly = qualification(workspace.resolve("totals-only"), new double[] {0.60d, 0.70d, 0.65d, 0.75d});

        Properties atTheFloor = floors("morpheus-new", 0.40d, 0.35d);
        assertEquals(List.of(), floorWindows(withModules, atTheFloor, 0.40d, 0.35d));
        assertEquals(List.of(), floorWindows(totalsOnly, atTheFloor, 0.40d, 0.35d));
        assertEquals(List.of(), floorWindows(totalsOnly, floors("morpheus-cli", 0.40d, 0.35d), 0.40d, 0.35d));

        for (Path evidence : List.of(withModules, totalsOnly)) {
            List<String> problems = floorWindows(evidence, floors("morpheus-new", 0.41d, 0.35d), 0.40d, 0.35d);
            assertEquals(1, problems.size(), problems::toString);
            assertTrue(problems.get(0).contains("morpheus-new.lineCoverageMinimum")
                    && problems.get(0).contains("carries no counters for morpheus-new")
                    && problems.get(0).contains("Windows and Linux"), problems.get(0));
        }
        List<String> unqualified = floorWindows(totalsOnly, floors("morpheus-cli", 0.40d, 0.36d), 0.40d, 0.35d);
        assertTrue(unqualified.size() == 1 && unqualified.get(0).contains("morpheus-cli.branchCoverageMinimum"),
                unqualified::toString);
    }

    /** The two floors of one module, the only keys the window under test reads. */
    private static Properties floors(String module, double line, double branch) {
        Properties floors = new Properties();
        floors.setProperty(module + LINE_FLOOR_SUFFIX, Double.toString(line));
        floors.setProperty(module + BRANCH_FLOOR_SUFFIX, Double.toString(branch));
        return floors;
    }

    /**
     * Appends module counters to every per-module summary of a fabricated qualification: {@code morpheus-cli} takes
     * the four counts the function gives for the file (line covered, line missed, branch covered, branch missed), and
     * {@code morpheus-domain} the remainder of the summary's totals, so that the module counters make the totals.
     */
    private static Path withModuleCounters(Path evidence, java.util.function.Function<String, long[]> cli)
            throws IOException {
        for (Entry entry : readManifest(evidence)) {
            if (entry.scale() != Scale.PER_MODULE) {
                continue;
            }
            Path file = evidence.resolve(entry.file());
            Map<String, String> totals = parseSummary(file);
            long[] counts = cli.apply(entry.file());
            StringBuilder text = new StringBuilder(Files.readString(file));
            for (int index = 0; index < MODULE_COUNTERS.size(); index++) {
                String counter = MODULE_COUNTERS.get(index);
                text.append("module.morpheus-cli.").append(counter).append('=').append(counts[index]).append('\n');
            }
            for (int index = 0; index < MODULE_COUNTERS.size(); index++) {
                String counter = MODULE_COUNTERS.get(index);
                text.append("module.morpheus-domain.").append(counter).append('=')
                        .append(Long.parseLong(totals.get(counter)) - counts[index]).append('\n');
            }
            Files.writeString(file, text);
        }
        return evidence;
    }

    /**
     * Lowest line ratio then lowest branch ratio of each scale are 0.60 / 0.65 per module and 0.60 / 0.65
     * aggregate once {@link #qualification} has written them; the fabricated sets use these four numbers.
     */
    private static Map<Scale, double[]> honestCaps() {
        Map<Scale, double[]> caps = new LinkedHashMap<>();
        caps.put(Scale.PER_MODULE, new double[] {0.60d, 0.65d});
        caps.put(Scale.AGGREGATE, new double[] {0.60d, 0.65d});
        return caps;
    }

    /**
     * Writes a complete fabricated qualification: both scales, both platforms, two runs each, one commit, one
     * population. {@code ratios} are, in order, the Linux line, Windows line, Linux branch and Windows branch
     * ratios shared by the two runs of a scale; run 2 sits {@code runStep} above run 1 on lines and branches,
     * 0.01 unless a test asks otherwise.
     */
    private static Path qualification(Path workspace, double[] ratios) throws IOException {
        return qualification(workspace, ratios, 0.01d);
    }

    private static Path qualification(Path workspace, double[] ratios, double runStep) throws IOException {
        return qualification(workspace, ratios, runStep, 100_000L, 40_000L);
    }

    private static Path qualification(Path workspace, double[] ratios, double runStep, long lines, long branches)
            throws IOException {
        List<String> manifest = new ArrayList<>();
        manifest.add(MANIFEST_HEADER);
        for (Scale scale : Scale.values()) {
            Files.createDirectories(workspace.resolve(scale.name));
            for (int platformIndex = 0; platformIndex < PLATFORMS.size(); platformIndex++) {
                for (int run = 1; run <= 2; run++) {
                    String platform = PLATFORMS.get(platformIndex);
                    String file = scale.name + "/" + platform + "-" + run + ".txt";
                    double line = ratios[platformIndex] + (run - 1) * runStep;
                    double branch = ratios[2 + platformIndex] + (run - 1) * runStep;
                    Files.writeString(workspace.resolve(file), summaryText(scale, line, branch, lines, branches));
                    manifest.add(String.join("\t", scale.name, platform, Integer.toString(run), COMMIT, file));
                }
            }
        }
        Files.writeString(workspace.resolve(MANIFEST), String.join("\n", manifest) + "\n");
        return workspace;
    }

    private static String summaryText(Scale scale, double lineRatio, double branchRatio, long lines, long branches) {
        StringBuilder text = new StringBuilder("coverageScope=").append(scale.name).append('\n');
        if (scale == Scale.AGGREGATE) {
            text.append("population=morpheus-domain,morpheus-cli\npopulationModules=2\n");
        } else {
            text.append("reports=2\n");
        }
        long lineCovered = Math.round(lines * lineRatio);
        long branchCovered = Math.round(branches * branchRatio);
        text.append("lineCovered=").append(lineCovered).append('\n')
                .append("lineMissed=").append(lines - lineCovered).append('\n')
                .append(String.format(Locale.ROOT, "lineRatio=%.6f%n", lineCovered / (double) lines))
                .append("branchCovered=").append(branchCovered).append('\n')
                .append("branchMissed=").append(branches - branchCovered).append('\n')
                .append(String.format(Locale.ROOT, "branchRatio=%.6f%n", branchCovered / (double) branches));
        return text.toString();
    }

    private static void dropEntries(Path evidence, Predicate<String> drop) throws IOException {
        Path manifest = evidence.resolve(MANIFEST);
        List<String> kept = Files.readAllLines(manifest).stream().filter(line -> !drop.test(line)).toList();
        Files.writeString(manifest, String.join("\n", kept) + "\n");
    }

    private static List<String> verify(Path directory, Caps caps) throws IOException {
        List<String> problems = new ArrayList<>();
        if (!Files.isRegularFile(directory.resolve(MANIFEST))) {
            problems.add("the caps rest on nothing a build can read: " + directory.resolve(MANIFEST) + " is absent");
            return problems;
        }
        List<Entry> entries = readManifest(directory);
        Set<String> listed = new LinkedHashSet<>();
        Map<Scale, List<Summary>> summaries = new LinkedHashMap<>();
        for (Entry entry : entries) {
            listed.add(entry.file());
            Path path = directory.resolve(entry.file());
            if (!Files.isRegularFile(path)) {
                problems.add(entry.file() + " is listed in " + MANIFEST + " but absent");
                continue;
            }
            Map<String, String> values = parseSummary(path);
            String opening = firstLine(path);
            if (!opening.equals("coverageScope=" + entry.scale().name)) {
                problems.add(entry.file() + " is kept as " + entry.scale().name + " evidence but begins with '"
                        + opening + "'");
                continue;
            }
            Summary summary = new Summary(entry, values);
            problems.addAll(ratioDisagreements(summary));
            summaries.computeIfAbsent(entry.scale(), key -> new ArrayList<>()).add(summary);
        }
        problems.addAll(unlistedFiles(directory, listed));
        for (Scale scale : Scale.values()) {
            List<Summary> kept = summaries.getOrDefault(scale, List.of());
            problems.addAll(completeness(scale, entries, kept));
            if (scale == Scale.PER_MODULE) {
                problems.addAll(moduleEvidence(kept));
            }
            if (!kept.isEmpty()) {
                double[] cap = caps.lineAndBranch().get(scale);
                compare(problems, scale.lineConstant, cap[0], kept, "lineRatio");
                compare(problems, scale.branchConstant, cap[1], kept, "branchRatio");
            }
        }
        problems.addAll(headsAcrossScales(entries));
        return problems;
    }

    /**
     * One qualification measures every scale at one head. Two scales kept at two heads differ by the drift between
     * those heads as well as by what they measure, so the gap between them -- the indicator the two scales exist to
     * expose -- can no longer be read from the repository. A scale that already mixes commits is refused by
     * {@link #completeness}; this names the scales whose single heads disagree.
     */
    private static List<String> headsAcrossScales(List<Entry> entries) {
        Map<Scale, String> heads = new LinkedHashMap<>();
        for (Scale scale : Scale.values()) {
            Set<String> ofScale = new LinkedHashSet<>();
            entries.stream().filter(entry -> entry.scale() == scale).forEach(entry -> ofScale.add(entry.commit()));
            if (ofScale.size() == 1) {
                heads.put(scale, ofScale.iterator().next());
            }
        }
        if (new LinkedHashSet<>(heads.values()).size() <= 1) {
            return List.of();
        }
        List<String> named = new ArrayList<>();
        heads.forEach((scale, head) -> named.add(scale.name + " at " + head));
        return List.of("the scales are qualified at different heads, " + String.join(" and ", named)
                + "; requalify every scale at one head");
    }

    /**
     * Headroom of each coverage ratchet under the lowest kept measurement of its scale, counted in covered lines or
     * branches: the lowest kept count minus the count the ratchet demands of the same population. It must be at
     * least three times the spread of that count across the kept runs, and never less than three times the
     * reference spread: a qualification whose runs happen to agree closely does not license a ratchet closer to
     * its cap.
     */
    private static List<String> margins(Path directory, Map<String, Double> ratchets) throws IOException {
        List<String> problems = new ArrayList<>();
        Map<Scale, List<Summary>> summaries = new LinkedHashMap<>();
        for (Entry entry : readManifest(directory)) {
            summaries.computeIfAbsent(entry.scale(), key -> new ArrayList<>())
                    .add(new Summary(entry, parseSummary(directory.resolve(entry.file()))));
        }
        for (Scale scale : Scale.values()) {
            List<Summary> kept = summaries.getOrDefault(scale, List.of());
            if (kept.isEmpty()) {
                problems.add(scale.name + " has no kept evidence to size its ratchets against");
                continue;
            }
            margin(problems, scale.ratchetKey("Line"), ratchets.get(scale.ratchetKey("Line")), kept, "line",
                    REFERENCE_LINE_SPREAD);
            margin(problems, scale.ratchetKey("Branch"), ratchets.get(scale.ratchetKey("Branch")), kept, "branch",
                    REFERENCE_BRANCH_SPREAD);
        }
        return problems;
    }

    private static void margin(List<String> problems, String key, double ratchet, List<Summary> kept, String counter,
            long referenceSpread) {
        List<Long> covered = kept.stream().map(summary -> Long.parseLong(summary.values().get(counter + "Covered")))
                .sorted().toList();
        long lowest = covered.get(0);
        long keptSpread = covered.get(covered.size() - 1) - lowest;
        Map<String, String> sample = kept.get(0).values();
        long total = Long.parseLong(sample.get(counter + "Covered")) + Long.parseLong(sample.get(counter + "Missed"));
        long spread = Math.max(keptSpread, referenceSpread);
        long demanded = (long) Math.ceil(ratchet * total - SIX_DIGIT_ROUNDING);
        long headroom = lowest - demanded;
        if (headroom < HEADROOM_SPREADS * spread) {
            String source = keptSpread >= referenceSpread ? "the kept runs' spread of " + keptSpread
                    : "the reference spread of " + referenceSpread;
            problems.add(key + "=" + ratchet + " leaves " + headroom + " covered " + counter + " units under the"
                    + " lowest kept measurement (" + lowest + " of " + total + "); it needs "
                    + HEADROOM_SPREADS * spread + ", " + HEADROOM_SPREADS + "x " + source
                    + " -- a higher ratchet needs a requalified cap");
        }
    }

    /**
     * Module counters of the kept per-module summaries. A qualification kept before the gate wrote them carries none,
     * and qualifies no floor above the D2 floor; once one summary carries them, every summary must, for the same
     * modules, complete, as many as its {@code reports}, and summing to its totals.
     */
    private static List<String> moduleEvidence(List<Summary> kept) {
        List<String> problems = new ArrayList<>();
        List<Summary> carrying = kept.stream().filter(summary -> !moduleCounters(summary).isEmpty()).toList();
        if (carrying.isEmpty()) {
            return problems;
        }
        Set<String> union = new java.util.TreeSet<>();
        carrying.forEach(summary -> union.addAll(moduleCounters(summary).keySet()));
        for (Summary summary : kept) {
            String file = summary.entry().file();
            Map<String, Map<String, Long>> modules = moduleCounters(summary);
            if (modules.isEmpty()) {
                problems.add(file + " carries no module counters while " + carrying.get(0).entry().file()
                        + " does; every summary of a qualification carries them");
                continue;
            }
            modules.forEach((module, counters) -> MODULE_COUNTERS.stream().filter(counter -> !counters.containsKey(counter))
                    .forEach(counter -> problems.add(file + " lacks " + MODULE_PREFIX + module + "." + counter)));
            for (String module : union) {
                if (!modules.containsKey(module)) {
                    String other = carrying.stream().filter(candidate -> moduleCounters(candidate).containsKey(module))
                            .findFirst().orElseThrow().entry().file();
                    problems.add(file + " carries no counters for module " + module + ", which " + other + " carries");
                }
            }
            String reports = summary.values().get("reports");
            if (reports != null && Long.parseLong(reports) != modules.size()) {
                problems.add(file + " declares reports=" + reports + " but carries counters for " + modules.size()
                        + " module(s)");
            }
            for (String counter : MODULE_COUNTERS) {
                long sum = modules.values().stream().mapToLong(counters -> counters.getOrDefault(counter, 0L)).sum();
                long total = Long.parseLong(summary.values().get(counter));
                if (sum != total) {
                    problems.add(file + ": the module " + counter + " counters sum to " + sum + " but " + counter + "="
                            + total);
                }
            }
        }
        return problems;
    }

    private static Map<String, Map<String, Long>> moduleCounters(Summary summary) {
        Map<String, Map<String, Long>> modules = new java.util.TreeMap<>();
        summary.values().forEach((key, value) -> {
            if (key.startsWith(MODULE_PREFIX)) {
                int separator = key.lastIndexOf('.');
                if (separator > MODULE_PREFIX.length()) {
                    modules.computeIfAbsent(key.substring(MODULE_PREFIX.length(), separator), name -> new LinkedHashMap<>())
                            .put(key.substring(separator + 1), Long.parseLong(value));
                }
            }
        });
        return modules;
    }

    /**
     * Window of each per-module floor: never under the D2 floor of its quantity; a floor above it needs the module's
     * counters in every kept per-module summary, and leaves at least three times the module's spread -- the kept runs'
     * or its reference, whichever is wider -- under its lowest kept count. A floor of a module the evidence carries,
     * the D2 floor included, leaves at most nine times that spread; a module the evidence does not carry stays at D2.
     * A module's reference is the scale's, times the module's share of the population of that quantity, rounded up:
     * the scale's reference is a spread of the whole population, which a module of a few dozen lines cannot carry.
     * A key of no known form is left to CoverageQualityGateTest, which refuses it.
     */
    private static List<String> floorWindows(Path directory, Properties floors, double d2Line, double d2Branch)
            throws IOException {
        List<Summary> kept = new ArrayList<>();
        for (Entry entry : readManifest(directory)) {
            if (entry.scale() == Scale.PER_MODULE) {
                kept.add(new Summary(entry, parseSummary(directory.resolve(entry.file()))));
            }
        }
        List<String> problems = new ArrayList<>();
        for (String key : new java.util.TreeSet<>(floors.stringPropertyNames())) {
            boolean line = key.endsWith(LINE_FLOOR_SUFFIX);
            if (!line && !key.endsWith(BRANCH_FLOOR_SUFFIX)) {
                continue;
            }
            String module = key.substring(0, key.length() - (line ? LINE_FLOOR_SUFFIX : BRANCH_FLOOR_SUFFIX).length());
            String counter = line ? "line" : "branch";
            double d2Floor = line ? d2Line : d2Branch;
            double floor = Double.parseDouble(floors.getProperty(key).strip());
            if (floor < d2Floor - RATIO_PRECISION) {
                problems.add(key + "=" + floor + " is below the D2 floor " + d2Floor + " of module " + module);
                continue;
            }
            boolean atTheD2Floor = floor <= d2Floor + RATIO_PRECISION;
            List<Map<String, Long>> measured = kept.stream().map(summary -> moduleCounters(summary).get(module)).toList();
            if (measured.isEmpty() || measured.contains(null)) {
                if (!atTheD2Floor) {
                    problems.add(key + "=" + floor + " is above the D2 floor " + d2Floor + " but the kept per-module"
                            + " evidence carries no counters for " + module + " in every summary: a floor above the D2"
                            + " floor needs a Windows and Linux qualification that carries the module");
                }
                continue;
            }
            windowProblem(key, module, counter, floor, atTheD2Floor, measured, kept.get(0).values())
                    .ifPresent(problems::add);
        }
        return problems;
    }

    /**
     * The refusal of a floor that leaves less than three times the module's spread under its lowest kept count (the
     * D2 floor excepted, which the gate applies anyway), or more than nine times, or nothing. The module's reference
     * spread is the scale's times the module's share of the population of {@code counter}, read in the first kept
     * summary, rounded up and never under one unit. A floor at the D2 floor leaves more than nine spreads only when a
     * higher floor fits between three and nine: when the window is empty its headroom is under three spreads.
     */
    private static Optional<String> windowProblem(String key, String module, String counter, double floor,
            boolean atTheD2Floor, List<Map<String, Long>> measured, Map<String, String> totals) {
        List<Long> covered = measured.stream().map(counters -> counters.get(counter + "Covered")).sorted().toList();
        long lowest = covered.get(0);
        long keptSpread = covered.get(covered.size() - 1) - lowest;
        long total = measured.get(0).get(counter + "Covered") + measured.get(0).get(counter + "Missed");
        long population = Long.parseLong(totals.get(counter + "Covered")) + Long.parseLong(totals.get(counter + "Missed"));
        long scaleReference = "line".equals(counter) ? REFERENCE_LINE_SPREAD : REFERENCE_BRANCH_SPREAD;
        long referenceSpread = Math.max(1L, Math.ceilDiv(scaleReference * total, population));
        long spread = Math.max(keptSpread, referenceSpread);
        long headroom = lowest - (long) Math.ceil(floor * total - SIX_DIGIT_ROUNDING);
        String source = keptSpread >= referenceSpread ? "the kept runs' spread of " + keptSpread
                : "the reference spread of " + referenceSpread + ", the module's share (" + total + " of the "
                        + population + " " + counter + " units) of the scale reference " + scaleReference;
        String measurement = key + "=" + floor + " leaves " + headroom + " covered " + counter + " units under"
                + " the lowest kept measurement of " + module + " (" + lowest + " of " + total + ")";
        if (!atTheD2Floor && headroom < HEADROOM_SPREADS * spread) {
            return Optional.of(measurement + "; it needs " + HEADROOM_SPREADS * spread + ", " + HEADROOM_SPREADS
                    + "x " + source + " -- a higher floor needs a requalification");
        }
        if (headroom > MAXIMUM_HEADROOM_SPREADS * spread) {
            double lowestAdmitted = Math.ceil((lowest - MAXIMUM_HEADROOM_SPREADS * spread) * 1000.0d / total
                    - SIX_DIGIT_ROUNDING) / 1000.0d;
            return Optional.of(measurement + "; a floor leaves at most " + MAXIMUM_HEADROOM_SPREADS * spread + ", "
                    + MAXIMUM_HEADROOM_SPREADS + "x " + source + " -- raise it to at least "
                    + String.format(Locale.ROOT, "%.3f", lowestAdmitted));
        }
        return Optional.empty();
    }

    private static Properties loadProperties(Path file) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file)) {
            properties.load(reader);
        }
        return properties;
    }

    private static Map<String, Double> ratchets(double perModuleLine, double perModuleBranch, double aggregateLine,
            double aggregateBranch) {
        Map<String, Double> ratchets = new LinkedHashMap<>();
        ratchets.put(Scale.PER_MODULE.ratchetKey("Line"), perModuleLine);
        ratchets.put(Scale.PER_MODULE.ratchetKey("Branch"), perModuleBranch);
        ratchets.put(Scale.AGGREGATE.ratchetKey("Line"), aggregateLine);
        ratchets.put(Scale.AGGREGATE.ratchetKey("Branch"), aggregateBranch);
        return ratchets;
    }

    private static Map<String, Double> readRatchets(Path root) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(root.resolve(RATCHETS))) {
            properties.load(reader);
        }
        Map<String, Double> ratchets = new LinkedHashMap<>();
        for (Scale scale : Scale.values()) {
            for (String counter : List.of("Line", "Branch")) {
                String key = scale.ratchetKey(counter);
                String value = properties.getProperty(key);
                assertTrue(value != null, () -> RATCHETS + " must declare " + key);
                ratchets.put(key, Double.parseDouble(value.strip()));
            }
        }
        return ratchets;
    }

    private static List<String> completeness(Scale scale, List<Entry> entries, List<Summary> kept) {
        List<String> problems = new ArrayList<>();
        List<Entry> ofScale = entries.stream().filter(entry -> entry.scale() == scale).toList();
        for (String platform : PLATFORMS) {
            long runs = ofScale.stream().filter(entry -> entry.platform().equals(platform)).count();
            if (runs == 0) {
                problems.add(scale.name + " lacks a summary from " + platform);
            } else if (runs < MINIMUM_RUNS_PER_PLATFORM) {
                problems.add(scale.name + " holds " + runs + " run(s) on " + platform
                        + "; a qualification needs at least " + MINIMUM_RUNS_PER_PLATFORM);
            }
        }
        Set<String> commits = new LinkedHashSet<>();
        ofScale.forEach(entry -> commits.add(entry.commit()));
        if (commits.size() > 1) {
            problems.add(scale.name + " mixes commits " + String.join(" and ", commits));
        }
        Set<String> populations = new LinkedHashSet<>();
        Set<String> denominators = new LinkedHashSet<>();
        kept.forEach(summary -> {
            populations.add(summary.population());
            denominators.add(summary.denominators());
        });
        if (populations.size() > 1) {
            problems.add(scale.name + " mixes populations: " + String.join(" versus ", populations));
        }
        if (denominators.size() > 1) {
            problems.add(scale.name + " mixes populations by size: " + String.join(" versus ", denominators));
        }
        return problems;
    }

    private static void compare(List<String> problems, String constant, double cap, List<Summary> kept, String key) {
        Summary lowest = kept.stream().min(Comparator.comparingDouble(summary -> summary.ratio(key)))
                .orElseThrow();
        double measured = lowest.ratio(key);
        if (Math.abs(cap - measured) > RATIO_PRECISION) {
            problems.add(constant + " is " + cap + " but the lowest kept measurement is " + measured + " ("
                    + lowest.entry().file() + ")");
        }
    }

    private static List<String> ratioDisagreements(Summary summary) {
        List<String> problems = new ArrayList<>();
        for (String[] pair : new String[][] {{"line", "lineRatio"}, {"branch", "branchRatio"}}) {
            double covered = Double.parseDouble(summary.values().get(pair[0] + "Covered"));
            double missed = Double.parseDouble(summary.values().get(pair[0] + "Missed"));
            double fromCounts = covered / (covered + missed);
            if (Math.abs(fromCounts - summary.ratio(pair[1])) > SIX_DIGIT_ROUNDING) {
                problems.add(summary.entry().file() + ": " + pair[1] + " " + summary.ratio(pair[1])
                        + " disagrees with its own counts, which give " + fromCounts);
            }
        }
        return problems;
    }

    private static List<String> unlistedFiles(Path directory, Set<String> listed) throws IOException {
        List<String> problems = new ArrayList<>();
        try (Stream<Path> files = Files.walk(directory)) {
            for (Path path : files.filter(Files::isRegularFile).sorted().toList()) {
                String relative = directory.relativize(path).toString().replace('\\', '/');
                if (!relative.equals(MANIFEST) && !listed.contains(relative)) {
                    problems.add(relative + " is kept but not listed in " + MANIFEST);
                }
            }
        }
        return problems;
    }

    private static List<Entry> readManifest(Path directory) throws IOException {
        List<String> lines = Files.readAllLines(directory.resolve(MANIFEST)).stream().map(String::strip)
                .filter(line -> !line.isEmpty()).toList();
        assertEquals(MANIFEST_HEADER, lines.get(0), MANIFEST + " must open with its header");
        List<Entry> entries = new ArrayList<>();
        for (String line : lines.subList(1, lines.size())) {
            String[] cells = line.split("\t", -1);
            assertEquals(5, cells.length, MANIFEST + " row must have five columns: " + line);
            Scale scale = Stream.of(Scale.values()).filter(candidate -> candidate.name.equals(cells[0])).findFirst()
                    .orElseThrow(() -> new AssertionError(MANIFEST + " names an unknown scale: " + cells[0]));
            entries.add(new Entry(scale, cells[1], cells[2], cells[3], cells[4]));
        }
        return entries;
    }

    private static Map<String, String> parseSummary(Path path) throws IOException {
        Map<String, String> values = new LinkedHashMap<>();
        for (String line : Files.readAllLines(path)) {
            int separator = line.indexOf('=');
            if (separator > 0) {
                values.put(line.substring(0, separator).strip(), line.substring(separator + 1).strip());
            }
        }
        return values;
    }

    private static String firstLine(Path path) throws IOException {
        List<String> lines = Files.readAllLines(path);
        return lines.isEmpty() ? "" : lines.get(0).strip();
    }

    private static Caps readCaps(Path root) throws IOException {
        Map<Scale, double[]> caps = new LinkedHashMap<>();
        for (Scale scale : Scale.values()) {
            String source = Files.readString(root.resolve(scale.gate));
            caps.put(scale, new double[] {constant(source, scale.gate, scale.lineConstant),
                    constant(source, scale.gate, scale.branchConstant)});
        }
        return new Caps(caps);
    }

    private static double constant(String source, String gate, String name) {
        Matcher matcher = Pattern.compile("private static final double " + name + " = ([0-9.]+)d;").matcher(source);
        assertTrue(matcher.find(), () -> gate + " must declare " + name + " as a private static final double literal");
        return Double.parseDouble(matcher.group(1));
    }
}
