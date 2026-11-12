package com.morpheus.architecture.m21;

import static com.morpheus.architecture.m21.CoverageQualificationVerifier.BRANCH_FLOOR_SUFFIX;
import static com.morpheus.architecture.m21.CoverageQualificationVerifier.EVIDENCE_DIRECTORY;
import static com.morpheus.architecture.m21.CoverageQualificationVerifier.LINE_FLOOR_SUFFIX;
import static com.morpheus.architecture.m21.CoverageQualificationVerifier.MANIFEST;
import static com.morpheus.architecture.m21.CoverageQualificationVerifier.MANIFEST_HEADER;
import static com.morpheus.architecture.m21.CoverageQualificationVerifier.MODULE_COUNTERS;
import static com.morpheus.architecture.m21.CoverageQualificationVerifier.MODULE_FLOORS;
import static com.morpheus.architecture.m21.CoverageQualificationVerifier.PER_MODULE_GATE;
import static com.morpheus.architecture.m21.CoverageQualificationVerifier.PLATFORMS;
import static com.morpheus.architecture.m21.CoverageQualificationVerifier.RATCHETS;
import static com.morpheus.architecture.m21.CoverageQualificationVerifier.floorWindows;
import static com.morpheus.architecture.m21.CoverageQualificationVerifier.loadProperties;
import static com.morpheus.architecture.m21.CoverageQualificationVerifier.margins;
import static com.morpheus.architecture.m21.CoverageQualificationVerifier.parseSummary;
import static com.morpheus.architecture.m21.CoverageQualificationVerifier.readManifest;
import static com.morpheus.architecture.m21.CoverageQualificationVerifier.verify;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.morpheus.architecture.CoherenceCheck;
import com.morpheus.architecture.RepositoryRoot;
import com.morpheus.architecture.m21.CoverageQualificationVerifier.Caps;
import com.morpheus.architecture.m21.CoverageQualificationVerifier.Entry;
import com.morpheus.architecture.m21.CoverageQualificationVerifier.Scale;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
    private static Path withModuleCounters(Path evidence, Function<String, long[]> cli)
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
