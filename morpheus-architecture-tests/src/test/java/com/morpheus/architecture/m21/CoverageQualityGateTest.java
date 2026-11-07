package com.morpheus.architecture.m21;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.morpheus.architecture.RepositoryRoot;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

/**
 * Per-module coverage gate: every module's own JaCoCo report, architecture-test module excluded, judged twice.
 *
 * <p>This scale answers a question the canonical post-reactor measurement cannot: what each module covers
 * <em>by itself</em>. A service exercised only through a sibling module's tests reads as uncovered here. It is a
 * different grandeur from the canonical measurement and therefore reads its own keys.</p>
 *
 * <p>The sum of the reports is compared to the total ratchet pair of {@code config/m21-quality-ratchets.properties},
 * bounded by the D2 floor and by the qualified cap of this scale. A sum alone hides a module that stops testing
 * itself: its loss dilutes into the covered lines of the others. So each report is also compared, on its own, to the
 * floor that {@code config/m21-per-module-coverage-floors.properties} declares for its module, and the refusal names
 * every module under its floor at once, with the remedy -- tests written in that module (ADR-0163).</p>
 */
class CoverageQualityGateTest {
    private static final String PER_MODULE_REPORT = "target/site/jacoco/jacoco.xml";
    private static final String MODULE_FLOORS = "config/m21-per-module-coverage-floors.properties";
    private static final String LINE_FLOOR_SUFFIX = ".lineCoverageMinimum";
    private static final String BRANCH_FLOOR_SUFFIX = ".branchCoverageMinimum";
    private static final double D2_MIN_LINE_RATIO = 0.40d;
    private static final double D2_MIN_BRANCH_RATIO = 0.35d;

    // Qualified exact-head baseline of the PER-MODULE scale: 90.4147% lines / 77.6803% branches.
    //
    // Requalified on 07/11/2026 at develop dbd65062, the merge of PR #616: two workflow_dispatch runs of develop
    // (42306627340 and 42311778667), both checking out that SHA; the push run of the same SHA was cancelled by the
    // workflow's concurrency group. Read from each run's m21-integrity-<OS> artifact,
    // per-module-coverage-summary.txt, coverageScope=per-module, reports=16, 29900 lines and 10148 branches on all
    // four, with the counters of every module (kept in config/coverage-qualification/):
    //     Windows  90.4348% / 90.4448% lines,  77.6902% / 77.7099% branches
    //     Linux    90.4147% / 90.4147% lines,  77.6803% / 77.6803% branches   <- qualified on the lowest
    // Spread across the four: 9 lines and 3 branches, inside the 24 / 11 of 15/09, which stays the one margins are
    // sized against. Previous cap: 90.2012% / 77.4564% (30/10/2026, below). The ratchets keep 0.899 / 0.771.
    //
    // Requalified on 30/10/2026 at d3580dc9, the head of PR #597: its pull_request run (41107774651) and a
    // workflow_dispatch run of the same branch (41107784162), both checking out that SHA. Read from each run's
    // m21-integrity-<OS> artifact, per-module-coverage-summary.txt, coverageScope=per-module, reports=16, 29922 lines
    // and 10198 branches on all four (kept in config/coverage-qualification/):
    //     Windows  90.2480% / 90.2480% lines,  77.4760% / 77.4858% branches
    //     Linux    90.2012% / 90.2045% lines,  77.4564% / 77.4564% branches   <- qualified on the lowest
    // Spread across the four: 14 lines and 3 branches, inside the 24 / 11 of 15/09, which stays the one margins
    // are sized against. Previous cap: 71.4642% / 62.8560% (28/10/2026, below), overtaken by 18.7 points on lines
    // and 14.6 on branches through the tests that morpheus-application and morpheus-domain gained in their own
    // modules (AUD-TRV-02).
    // Both keys raised inside this cap, each the highest thousandth that keeps 3x the 24 / 11 spread:
    // line 0.712 -> 0.899 leaves 90 lines (3.8x), branch 0.625 -> 0.771 leaves 36 branches (3.3x). One more
    // thousandth would drop either under 3x (60 lines, 26 branches).
    //
    // Requalified on 28/10/2026 at 61198c68, the head of PR #570: its pull_request run (40604205899) and a
    // workflow_dispatch run of the same branch (40604205079), both checking out that SHA. Read from each run's
    // m21-integrity-<OS> artifact, per-module-coverage-summary.txt, coverageScope=per-module, reports=16, 29696 lines
    // and 10112 branches on all four (kept in config/coverage-qualification/):
    //     Windows  71.5147% / 71.5113% lines,  62.9055% / 62.8956% branches
    //     Linux    71.4642% / 71.4709% lines,  62.8560% / 62.8758% branches   <- qualified on the lowest
    // Spread across the four: 15 lines and 5 branches, inside the 24 / 11 of 15/09, which stays the one margins
    // are sized against. Previous cap: 69.4341% / 61.5727% (09/10/2026, below), overtaken by 2.0 points on lines
    // and 1.3 on branches.
    // Both keys raised inside this cap, each the highest thousandth that keeps 3x the 24 / 11 spread:
    // line 0.691 -> 0.712 leaves 78 lines (3.3x), branch 0.612 -> 0.625 leaves 36 branches (3.3x). One more
    // thousandth would drop either under 3x (48 lines, 25 branches). CoverageQualificationEvidenceTest computes this
    // headroom from the kept evidence and refuses a ratchet that spends it.
    //
    // Requalified on 09/10/2026 at develop 8e3fda5a, same method: the push run of develop (37925178678) and the
    // pull_request run of the promotion PR #274 (37925186306). Read from each run's m21-integrity-<OS> artifact,
    // per-module-coverage-summary.txt, coverageScope=per-module, reports=16, 29245 lines and 10326 branches on all
    // four:
    //     Windows  69.4888% / 69.4888% lines,  61.6502% / 61.6308% branches
    //     Linux    69.4341% / 69.4409% lines,  61.5727% / 61.5921% branches   <- qualified on the lowest
    // Spread across the four: 16 lines and 8 branches, inside the 24 / 11 of 15/09, which stays the one margins
    // are sized against. The measurement had overtaken the 22/09 cap by about 4.5 points on lines and 4.3 on
    // branches, through the tests merged since then. Previous cap: 64.9302% / 57.2366% (22/09/2026, below).
    // Both keys raised inside this cap, each the highest thousandth that keeps 3x the 24 / 11 spread: line
    // 0.646 -> 0.691 leaves 97 lines (4.0x), branch 0.569 -> 0.612 leaves 38 branches (3.5x). One more
    // thousandth would drop either under 3x (68 lines, 28 branches).
    //
    // Requalified on 22/09/2026 at develop e8e0f0ca, same method as 15/09: the push run of develop (35756505624)
    // and the pull_request run of the promotion PR #274 (35756512657). Read from each run's m21-integrity-<OS>
    // artifact, per-module-coverage-summary.txt, coverageScope=per-module, reports=16, 28352 lines and 10212
    // branches on all four:
    //     Windows  64.9831% / 64.9831% lines,  57.3051% / 57.3051% branches
    //     Linux    64.9302% / 64.9302% lines,  57.2464% / 57.2366% branches   <- qualified on the lowest
    // The two Windows runs were identical; the two Linux runs had the same lines and differed by 1 branch; the
    // two platforms by 15 lines and up to 7 branches -- inside the 24 / 11 spread of 15/09, which stays the one
    // margins are sized against. Everything between e8e0f0ca and this change touches only the architecture-test
    // module, which this scale excludes. Previous cap: 64.5143% / 56.2964% (15/09/2026, below), exceeded
    // since 22/09/2026.
    //
    // History of this cap, kept as measured:
    // Deliberately at or below the LOWEST reproducible exact-head measurement across both platforms, never the
    // best one. The two platforms run the same number of tests, but some of them no-op off their own OS -- the
    // Windows junction check is one -- so Linux covers slightly fewer lines for an identical test count.
    // Qualifying on the higher figure would pin a baseline the other platform cannot reach.
    //
    // Measured on 15/09/2026 at develop a1fc0a8d, on the GitHub-hosted runners, two full runs per platform: the
    // push run of develop (34991048836) and the pull_request run of the promotion PR #274 (34991052539), which
    // check out the same SHA. Read from each run's m21-integrity-<OS> artifact, per-module-coverage-summary.txt,
    // reports=16, 28434 lines and 10228 branches on all four:
    //     Windows  64.5952% / 64.5987% lines,  56.3942% / 56.4040% branches
    //     Linux    64.5143% / 64.5495% lines,  56.2964% / 56.3453% branches   <- qualified on the lowest
    // The two Linux runs differed by 10 covered lines and 5 branches, the two platforms by up to 24 lines and
    // 11 branches: wider than the two lines observed on 08/09, so quote the spread, not a point value.
    // Previous cap: 62.5013% / 53.7997% (08/09/2026, fix/audit-hardening-2026-09-08), exceeded since 09/09.
    //
    // The per-module ratchets in config/m21-quality-ratchets.properties sit deliberately BELOW this cap rather
    // than at it, so a ratchet pinned to the measurement cannot turn ordinary run-to-run variation into a build
    // failure. Raised 0.620 / 0.535 -> 0.640 / 0.560 on 16/09/2026, inside this same cap and with no new
    // measurement: what was left is 146 lines and 30 branches of headroom ON THIS SCALE only, against a
    // cross-platform spread of 24 lines and 11 branches. The branch key is now the tight one -- 2.8x the spread,
    // against 6.1x for the line key -- so a further raise must treat the two keys separately. The canonical measurement counts a different population of covered lines and carries its own
    // cap in AggregateCoverageGateTest.
    // Line key alone raised 0.640 -> 0.642 on 22/09/2026, same cap, no new measurement: 89 lines of headroom
    // (3.7x the spread); the branch key keeps 0.560 and its 30 branches.
    // Both keys raised on 22/09/2026 inside the requalified cap above, each sized separately to at least 3x the
    // 24-line / 11-branch spread: line 0.642 -> 0.646 leaves 93 lines (3.9x), branch 0.560 -> 0.569 leaves 34
    // branches (3.1x). One more thousandth on either key would drop it under 3x (65 lines, 24 branches).
    //
    // Raising these two constants requires a fresh per-module measurement on BOTH platforms, cited here.
    private static final double PER_MODULE_QUALIFIED_LINE_RATIO = 0.904147d;
    private static final double PER_MODULE_QUALIFIED_BRANCH_RATIO = 0.776803d;

    @Test
    void perModuleCoverageDoesNotRegressBelowQualifiedBaseline() throws Exception {
        Path root = RepositoryRoot.locate();
        Ratchets ratchets = Ratchets.load(root.resolve("config/m21-quality-ratchets.properties"));
        double minLineRatio = Math.max(D2_MIN_LINE_RATIO, ratchets.perModuleLineCoverageMinimum());
        double minBranchRatio = Math.max(D2_MIN_BRANCH_RATIO, ratchets.perModuleBranchCoverageMinimum());
        ReportPopulation population = reportPopulation(root);
        population.assertComplete();
        assertTrue(minLineRatio >= D2_MIN_LINE_RATIO, "coverage ratchet must never weaken the D2 line floor");
        assertTrue(minBranchRatio >= D2_MIN_BRANCH_RATIO, "coverage ratchet must never weaken the D2 branch floor");
        assertRatchetWithinQualifiedWindow("line", ratchets.perModuleLineCoverageMinimum(),
                D2_MIN_LINE_RATIO, PER_MODULE_QUALIFIED_LINE_RATIO);
        assertRatchetWithinQualifiedWindow("branch", ratchets.perModuleBranchCoverageMinimum(),
                D2_MIN_BRANCH_RATIO, PER_MODULE_QUALIFIED_BRANCH_RATIO);

        List<ModuleCoverage> modules = measure(population);
        Counter lines = new Counter();
        Counter branches = new Counter();
        for (ModuleCoverage module : modules) {
            lines.add(module.lines());
            branches.add(module.branches());
        }

        double lineRatio = lines.ratio();
        double branchRatio = branches.ratio();
        Path summary = root.resolve("morpheus-architecture-tests/target/m21-per-module-coverage-summary.txt");
        Files.createDirectories(summary.getParent());
        StringBuilder evidence = new StringBuilder(String.format(
                Locale.ROOT,
                "coverageScope=per-module%nreports=%d%nlineCovered=%d%nlineMissed=%d%nlineRatio=%.6f%nbranchCovered=%d%nbranchMissed=%d%nbranchRatio=%.6f%nqualifiedLineBaseline=%.6f%nqualifiedBranchBaseline=%.6f%nlineRatchet=%.3f%nbranchRatchet=%.3f%nd2LineFloor=%.2f%nd2BranchFloor=%.2f%n",
                modules.size(), lines.covered, lines.missed, lineRatio,
                branches.covered, branches.missed, branchRatio,
                PER_MODULE_QUALIFIED_LINE_RATIO, PER_MODULE_QUALIFIED_BRANCH_RATIO,
                minLineRatio, minBranchRatio,
                D2_MIN_LINE_RATIO, D2_MIN_BRANCH_RATIO));
        for (ModuleCoverage module : modules) {
            evidence.append(String.format(Locale.ROOT,
                    "module.%1$s.lineCovered=%2$d%nmodule.%1$s.lineMissed=%3$d%nmodule.%1$s.branchCovered=%4$d%nmodule.%1$s.branchMissed=%5$d%n",
                    module.name(), module.lines().covered, module.lines().missed,
                    module.branches().covered, module.branches().missed));
        }
        Files.writeString(summary, evidence);

        assertCoverageAtLeast("line", lineRatio, minLineRatio);
        assertCoverageAtLeast("branch", branchRatio, minBranchRatio);
    }

    @Test
    void ratchetRejectsARegressionThatTheOldD2FloorWouldHaveAccepted() throws Exception {
        Ratchets ratchets = Ratchets.load(RepositoryRoot.locate().resolve("config/m21-quality-ratchets.properties"));
        double minLineRatio = Math.max(D2_MIN_LINE_RATIO, ratchets.perModuleLineCoverageMinimum());
        double minBranchRatio = Math.max(D2_MIN_BRANCH_RATIO, ratchets.perModuleBranchCoverageMinimum());
        assertTrue(0.49d >= D2_MIN_LINE_RATIO);
        assertTrue(0.41d >= D2_MIN_BRANCH_RATIO);
        AssertionError lineRegression = assertThrows(
                AssertionError.class, () -> assertCoverageAtLeast("line", 0.49d, minLineRatio));
        assertTrue(lineRegression.getMessage().contains("per-module JaCoCo line coverage 0.49 is below qualified-baseline ratchet"),
                () -> "the line regression must be refused for falling below the ratchet: " + lineRegression.getMessage());
        AssertionError branchRegression = assertThrows(
                AssertionError.class, () -> assertCoverageAtLeast("branch", 0.41d, minBranchRatio));
        assertTrue(branchRegression.getMessage().contains("per-module JaCoCo branch coverage 0.41 is below qualified-baseline ratchet"),
                () -> "the branch regression must be refused for falling below the ratchet: " + branchRegression.getMessage());
    }

    /**
     * The sum hides a module that stops testing itself; its own report does not. Each module of the population is
     * compared to the floor declared for it, once the population has been proven complete and current.
     */
    @Test
    void everyModuleOfThePopulationHoldsItsOwnFloor() throws Exception {
        Path root = RepositoryRoot.locate();
        assertEveryModuleHoldsItsOwnFloor(root, root.resolve(MODULE_FLOORS));
    }

    /**
     * Which modules owe a floor depends on the root POM and the sources only, so the declaration is checked without a
     * build: a module added without its two keys, or a key left behind by a retired module, fails before any
     * measurement exists.
     */
    @Test
    void theFloorFileDeclaresEveryModuleOfThePopulationAndNoOther() throws Exception {
        Path root = RepositoryRoot.locate();
        List<String> problems = floorDeclarationProblems(reportPopulation(root).owing(),
                loadFloors(root.resolve(MODULE_FLOORS)), MODULE_FLOORS);
        assertTrue(problems.isEmpty(), () -> MODULE_FLOORS + " does not declare the population of the per-module scale:"
                + System.lineSeparator() + "  " + String.join(System.lineSeparator() + "  ", problems));
    }

    /**
     * The defect the floors exist for, reproduced: a reactor whose total stays above the ratchets of this scale while
     * one module has stopped testing its lines and another its branches. Both are refused at once, under their own
     * names, and the refusal stops once each module is covered again -- not before, and not for another reason.
     */
    @Test
    void aModuleUnderItsOwnFloorIsRefusedWhileTheTotalStillHolds(@TempDir Path reactor) throws Exception {
        Files.writeString(reactor.resolve("pom.xml"), reactorPom("well-tested", "stopped-testing", "branch-gap"));
        writeMeasuredModule(reactor.resolve("well-tested"), 9_600, 100, 3_000, 100);
        writeMeasuredModule(reactor.resolve("stopped-testing"), 30, 70, 40, 10);
        writeMeasuredModule(reactor.resolve("branch-gap"), 300, 0, 10, 40);
        Path floors = writeFloors(reactor, "well-tested", "stopped-testing", "branch-gap");

        Ratchets ratchets = Ratchets.load(RepositoryRoot.locate().resolve("config/m21-quality-ratchets.properties"));
        assertCoverageAtLeast("line", 9_930d / 10_100d,
                Math.max(D2_MIN_LINE_RATIO, ratchets.perModuleLineCoverageMinimum()));
        assertCoverageAtLeast("branch", 3_050d / 3_200d,
                Math.max(D2_MIN_BRANCH_RATIO, ratchets.perModuleBranchCoverageMinimum()));

        AssertionError refusal = assertThrows(AssertionError.class,
                () -> assertEveryModuleHoldsItsOwnFloor(reactor, floors));
        String message = refusal.getMessage();
        assertTrue(message.contains("stopped-testing: line coverage 0.300000 of its own report is below its floor 0.40"),
                () -> "the module that stopped testing its lines must be named with its ratio and floor: " + message);
        assertTrue(message.contains("branch-gap: branch coverage 0.200000 of its own report is below its floor 0.35"),
                () -> "branches are judged apart from lines and must be named as such: " + message);
        assertTrue(message.contains("write tests in stopped-testing") && message.contains("write tests in branch-gap"),
                () -> "the refusal must name the remedy, a test written in the module: " + message);
        assertFalse(message.contains("well-tested"), () -> "a module above its floors is not refused: " + message);
        assertFalse(message.contains("stopped-testing: branch") || message.contains("branch-gap: line"),
                () -> "a quantity above its floor is not refused: " + message);

        writeMeasuredModule(reactor.resolve("stopped-testing"), 70, 30, 40, 10);
        writeMeasuredModule(reactor.resolve("branch-gap"), 300, 0, 30, 20);
        assertEveryModuleHoldsItsOwnFloor(reactor, floors);
    }

    /**
     * The floor file follows the derived population exactly. A module added without its keys gets no default floor,
     * a key that outlived its module is not ignored, and a module without main classes owes nothing -- so a key that
     * names it is an orphan too.
     */
    @Test
    void theFloorFileMustNameEveryModuleOfThePopulationAndNoOther(@TempDir Path reactor) throws Exception {
        Files.writeString(reactor.resolve("pom.xml"),
                reactorPom("declared", "added-without-floor", "half-declared", "without-main-classes"));
        writeMeasuredModule(reactor.resolve("declared"), 90, 10, 9, 1);
        writeMeasuredModule(reactor.resolve("added-without-floor"), 10, 90, 1, 9);
        writeMeasuredModule(reactor.resolve("half-declared"), 90, 10, 9, 1);
        Files.createDirectories(reactor.resolve("without-main-classes/src/test/java"));
        Path floors = reactor.resolve("floors.properties");
        Files.writeString(floors, String.join(System.lineSeparator(),
                "declared.lineCoverageMinimum=0.40",
                "declared.branchCoverageMinimum=0.35",
                "half-declared.lineCoverageMinimum=0.40",
                "retired-module.lineCoverageMinimum=0.40",
                "without-main-classes.branchCoverageMinimum=0.35",
                "declared.coverageMinimum=0.40",
                ""));

        AssertionError refusal = assertThrows(AssertionError.class,
                () -> assertEveryModuleHoldsItsOwnFloor(reactor, floors));
        String message = refusal.getMessage();
        for (String missing : List.of("added-without-floor.lineCoverageMinimum",
                "added-without-floor.branchCoverageMinimum", "half-declared.branchCoverageMinimum")) {
            assertTrue(message.contains("declares no " + missing),
                    () -> "a module of the population without its key must be named with the key: " + message);
        }
        for (String orphan : List.of("retired-module.lineCoverageMinimum",
                "without-main-classes.branchCoverageMinimum")) {
            assertTrue(message.contains(orphan + " names no module of the population"),
                    () -> "a key that names no module with main classes is an orphan: " + message);
        }
        assertTrue(message.contains("declared.coverageMinimum is neither"),
                () -> "a key of no known form must be refused rather than ignored: " + message);
        assertFalse(message.contains("added-without-floor: line"),
                () -> "a module without its keys must not be judged against a default floor: " + message);

        Files.writeString(floors, String.join(System.lineSeparator(),
                "declared.lineCoverageMinimum=0.40",
                "declared.branchCoverageMinimum=0.35",
                "added-without-floor.lineCoverageMinimum=0.40",
                "added-without-floor.branchCoverageMinimum=0.35",
                "half-declared.lineCoverageMinimum=0.40",
                "half-declared.branchCoverageMinimum=0.35",
                ""));
        AssertionError underFloor = assertThrows(AssertionError.class,
                () -> assertEveryModuleHoldsItsOwnFloor(reactor, floors));
        assertTrue(underFloor.getMessage().contains("added-without-floor: line coverage 0.100000")
                        && !underFloor.getMessage().contains("names no module")
                        && !underFloor.getMessage().contains("declares no"),
                () -> "once declared, the module is judged against its own floor and nothing else: "
                        + underFloor.getMessage());
        writeMeasuredModule(reactor.resolve("added-without-floor"), 90, 10, 9, 1);
        assertEveryModuleHoldsItsOwnFloor(reactor, floors);
    }

    private static String reactorPom(String... modules) {
        StringBuilder pom = new StringBuilder("<project>\n  <modules>\n");
        for (String module : modules) {
            pom.append("    <module>").append(module).append("</module>\n");
        }
        return pom.append("  </modules>\n</project>\n").toString();
    }

    private static void writeMeasuredModule(Path module, long lineCovered, long lineMissed, long branchCovered,
            long branchMissed) throws IOException {
        declareMainClass(module);
        Path report = module.resolve(PER_MODULE_REPORT);
        Files.createDirectories(report.getParent());
        Files.writeString(report, "<report name=\"" + module.getFileName() + "\">"
                + "<counter type=\"BRANCH\" missed=\"" + branchMissed + "\" covered=\"" + branchCovered + "\"/>"
                + "<counter type=\"LINE\" missed=\"" + lineMissed + "\" covered=\"" + lineCovered + "\"/>"
                + "</report>");
    }

    private static Path writeFloors(Path reactor, String... modules) throws IOException {
        StringBuilder floors = new StringBuilder();
        for (String module : modules) {
            floors.append(module).append(LINE_FLOOR_SUFFIX).append('=').append(D2_MIN_LINE_RATIO).append('\n')
                    .append(module).append(BRANCH_FLOOR_SUFFIX).append('=').append(D2_MIN_BRANCH_RATIO).append('\n');
        }
        Path file = reactor.resolve("floors.properties");
        Files.writeString(file, floors);
        return file;
    }

    /**
     * Refuses, in one message, every declaration problem of the floor file and every module whose own report falls
     * under {@code max(D2 floor, its floor)}. A module without a valid key is named as such and not judged: no default
     * floor stands in for a missing one.
     */
    private void assertEveryModuleHoldsItsOwnFloor(Path root, Path floorFile) throws Exception {
        ReportPopulation population = reportPopulation(root);
        population.assertComplete();
        Properties floors = loadFloors(floorFile);
        String page = floorFile.getFileName().toString();
        List<String> refusals = new ArrayList<>(floorDeclarationProblems(population.owing(), floors, page));
        for (ModuleCoverage module : measure(population)) {
            refuseUnderFloor(refusals, module.name(), "line", module.lines().ratio(),
                    floors.getProperty(module.name() + LINE_FLOOR_SUFFIX), D2_MIN_LINE_RATIO);
            refuseUnderFloor(refusals, module.name(), "branch", module.branches().ratio(),
                    floors.getProperty(module.name() + BRANCH_FLOOR_SUFFIX), D2_MIN_BRANCH_RATIO);
        }
        if (!refusals.isEmpty()) {
            throw new AssertionError("the per-module scale holds each module to its own floor (" + page + "), and "
                    + refusals.size() + " problem(s) stand:" + System.lineSeparator() + "  "
                    + String.join(System.lineSeparator() + "  ", refusals));
        }
    }

    private static void refuseUnderFloor(List<String> refusals, String module, String kind, double ratio,
            String declared, double d2Floor) {
        Double floor = floorValue(declared);
        if (floor == null) {
            return;
        }
        double effective = Math.max(d2Floor, floor);
        if (ratio < effective) {
            refusals.add(String.format(Locale.ROOT,
                    "%1$s: %2$s coverage %3$.6f of its own report is below its floor %4$.2f -- write tests in %1$s; "
                            + "tests that live in another module do not count on this scale", module, kind, ratio,
                    effective));
        }
    }

    private static List<String> floorDeclarationProblems(List<String> owing, Properties floors, String page) {
        List<String> problems = new ArrayList<>();
        for (String module : owing) {
            for (String suffix : List.of(LINE_FLOOR_SUFFIX, BRANCH_FLOOR_SUFFIX)) {
                if (floors.getProperty(module + suffix) == null) {
                    problems.add(module + " is in the population but " + page + " declares no " + module + suffix
                            + " -- declare it at the D2 floor until a Windows and Linux qualification carries the module");
                }
            }
        }
        Set<String> population = new LinkedHashSet<>(owing);
        for (String key : new TreeSet<>(floors.stringPropertyNames())) {
            String module = moduleOf(key);
            if (module == null) {
                problems.add(key + " is neither <module>" + LINE_FLOOR_SUFFIX + " nor <module>" + BRANCH_FLOOR_SUFFIX);
            } else if (!population.contains(module)) {
                problems.add(key + " names no module of the population (a <module> of the root POM with a class "
                        + "under src/main/java) -- remove it in the change that removes or renames its module");
            } else if (floorValue(floors.getProperty(key)) == null) {
                problems.add(key + "=" + floors.getProperty(key) + " is not a ratio between 0 and 1");
            }
        }
        return problems;
    }

    private static String moduleOf(String key) {
        for (String suffix : List.of(LINE_FLOOR_SUFFIX, BRANCH_FLOOR_SUFFIX)) {
            if (key.endsWith(suffix) && key.length() > suffix.length()) {
                return key.substring(0, key.length() - suffix.length());
            }
        }
        return null;
    }

    private static Double floorValue(String declared) {
        if (declared == null) {
            return null;
        }
        try {
            double value = Double.parseDouble(declared.strip());
            return value >= 0.0d && value <= 1.0d ? value : null;
        } catch (NumberFormatException notARatio) {
            return null;
        }
    }

    private static Properties loadFloors(Path file) throws IOException {
        Properties floors = new Properties();
        try (var reader = Files.newBufferedReader(file)) {
            floors.load(reader);
        }
        return floors;
    }

    /** Each module's own counters, in the order of the root POM; the sum of the scale is computed from them. */
    private List<ModuleCoverage> measure(ReportPopulation population) throws Exception {
        List<ModuleCoverage> measured = new ArrayList<>();
        for (ModuleReport report : population.reports()) {
            Map<String, Counter> counters = new LinkedHashMap<>();
            counters.put("LINE", new Counter());
            counters.put("BRANCH", new Counter());
            Node child = parse(report.report()).getDocumentElement().getFirstChild();
            while (child != null) {
                if (child instanceof Element element && element.getTagName().equals("counter")) {
                    Counter target = counters.get(element.getAttribute("type"));
                    if (target != null) {
                        target.covered += Long.parseLong(element.getAttribute("covered"));
                        target.missed += Long.parseLong(element.getAttribute("missed"));
                    }
                }
                child = child.getNextSibling();
            }
            measured.add(new ModuleCoverage(report.module(), counters.get("LINE"), counters.get("BRANCH")));
        }
        return measured;
    }

    /**
     * A guard that has never refused is not a guard ({@code .claude/rules/architecture.md}). The rule is exercised here
     * against a reactor
     * assembled on purpose, holding one module of every kind it must tell apart, and then completed to prove it
     * stops refusing for the right reason rather than for none.
     */
    @Test
    void theDerivedPopulationRefusesAnAmputatedReactorAndNamesWhatIsMissing(@TempDir Path reactor) throws Exception {
        Files.writeString(reactor.resolve("pom.xml"), """
                <project>
                  <modules>
                    <module>reported</module>
                    <module>built-without-report</module>
                    <module>never-built</module>
                    <module>without-main-classes</module>
                  </modules>
                </project>
                """);
        declareMainClass(reactor.resolve("reported"));
        declareMainClass(reactor.resolve("built-without-report"));
        declareMainClass(reactor.resolve("never-built"));
        Files.createDirectories(reactor.resolve("without-main-classes/src/test/java"));
        Files.createDirectories(reactor.resolve("built-without-report/target/classes"));
        writeReport(reactor.resolve("reported"));

        ReportPopulation amputated = reportPopulation(reactor);
        assertEquals(1, amputated.reports().size(), "only a module that emitted a report may be summed");
        AssertionError refusal = assertThrows(AssertionError.class, amputated::assertComplete);
        assertTrue(refusal.getMessage().contains("1 of 3"),
                () -> "the refusal must count the modules that owe a report: " + refusal.getMessage());
        assertTrue(refusal.getMessage().contains("never-built"),
                () -> "the refusal must name the module that was never built: " + refusal.getMessage());
        assertTrue(refusal.getMessage().contains("built-without-report"),
                () -> "the refusal must name the module that stopped reporting: " + refusal.getMessage());
        assertFalse(refusal.getMessage().contains("without-main-classes"),
                () -> "a module without main classes owes no report: " + refusal.getMessage());
        assertTrue(refusal.getMessage().contains("./mvnw clean verify"),
                () -> "a partial reactor is an invocation mistake and the refusal must say so: " + refusal.getMessage());
        assertTrue(refusal.getMessage().contains("regression in the module"),
                () -> "a module that stopped reporting is not an invocation mistake: " + refusal.getMessage());

        writeReport(reactor.resolve("built-without-report"));
        writeReport(reactor.resolve("never-built"));
        ReportPopulation whole = reportPopulation(reactor);
        assertEquals(3, whole.reports().size(), "every module with main classes must be summed once complete");
        whole.assertComplete();
    }

    /**
     * A report that predates the code it measures says nothing about that code. The reference is the module's
     * sources and tests, not its {@code target/classes} directory: a directory's time moves only when an entry is
     * created or removed in it, so a class file rewritten below it leaves it untouched and the report would always
     * look newer. Times are set explicitly; a test that waited for the file system clock to tick would be one more
     * flaky test.
     */
    @Test
    void aReportOlderThanTheSourcesItMeasuresIsRefusedUnderItsOwnCause(@TempDir Path reactor) throws Exception {
        Files.writeString(reactor.resolve("pom.xml"), """
                <project>
                  <modules>
                    <module>fresh</module>
                    <module>stale-main</module>
                    <module>stale-test</module>
                    <module>stale-nested</module>
                    <module>never-built</module>
                    <module>built-without-report</module>
                  </modules>
                </project>
                """);
        FileTime reportTime = FileTime.from(Instant.parse("2026-01-01T12:00:00Z"));
        FileTime older = FileTime.from(reportTime.toInstant().minus(Duration.ofHours(1)));
        FileTime newer = FileTime.from(reportTime.toInstant().plus(Duration.ofSeconds(1)));

        for (String module : List.of("fresh", "stale-main", "stale-test", "stale-nested")) {
            Path directory = reactor.resolve(module);
            declareMainClass(directory);
            Files.createDirectories(directory.resolve("src/test/java"));
            Files.writeString(directory.resolve("src/test/java/PlaceholderTest.java"), "class PlaceholderTest {}");
            Files.setLastModifiedTime(directory.resolve("src/main/java/Placeholder.java"), older);
            Files.setLastModifiedTime(directory.resolve("src/test/java/PlaceholderTest.java"), older);
            Files.createDirectories(directory.resolve("target/classes"));
            Files.setLastModifiedTime(directory.resolve("target/classes"), older);
            writeReport(directory);
            Files.setLastModifiedTime(directory.resolve(PER_MODULE_REPORT), reportTime);
        }
        Files.setLastModifiedTime(reactor.resolve("stale-main/src/main/java/Placeholder.java"), newer);
        Files.setLastModifiedTime(reactor.resolve("stale-test/src/test/java/PlaceholderTest.java"), newer);
        Path nested = reactor.resolve("stale-nested/src/main/java/deep/er/pkg/Deep.java");
        Files.createDirectories(nested.getParent());
        Files.writeString(nested, "class Deep {}");
        Files.setLastModifiedTime(nested, newer);
        declareMainClass(reactor.resolve("never-built"));
        declareMainClass(reactor.resolve("built-without-report"));
        Files.createDirectories(reactor.resolve("built-without-report/target/classes"));

        ReportPopulation population = reportPopulation(reactor);
        assertEquals(1, population.reports().size(), "only the module whose report postdates its sources may be summed");
        AssertionError refusal = assertThrows(AssertionError.class, population::assertComplete);
        String message = refusal.getMessage();
        for (String stale : List.of("stale-main", "stale-test", "stale-nested")) {
            assertTrue(message.contains(stale), () -> "the refusal must name the module whose report is stale: " + message);
        }
        assertFalse(message.contains("fresh"), () -> "a module whose report postdates its sources is not stale: " + message);
        assertTrue(message.contains("older than the sources it measures"),
                () -> "the third cause must be named apart: " + message);
        assertTrue(message.contains("never built") && message.contains("never-built"),
                () -> "the first cause must still be named: " + message);
        assertTrue(message.contains("built but emitted no report") && message.contains("built-without-report"),
                () -> "the second cause must still be named: " + message);
        assertTrue(message.contains("./mvnw clean verify"), () -> "the refusal must say what to run: " + message);

        Files.setLastModifiedTime(reactor.resolve("stale-main/src/main/java/Placeholder.java"), older);
        Files.setLastModifiedTime(reactor.resolve("stale-test/src/test/java/PlaceholderTest.java"), older);
        Files.setLastModifiedTime(nested, older);
        assertEquals(4, reportPopulation(reactor).reports().size(),
                "once no source postdates its report, the four modules with a report are summed again");
    }

    private static void declareMainClass(Path module) throws IOException {
        Path sources = module.resolve("src/main/java");
        Files.createDirectories(sources);
        Files.writeString(sources.resolve("Placeholder.java"), "class Placeholder {}");
    }

    private static void writeReport(Path module) throws IOException {
        Path report = module.resolve(PER_MODULE_REPORT);
        Files.createDirectories(report.getParent());
        Files.writeString(report, "<report/>");
    }

    /**
     * The window is what makes a ratchet a ratchet: strictly above the D2 floor it may never silently return to,
     * and at or below the measurement that qualified it. Both ends are proven here rather than merely exercised
     * by whichever value happens to be configured today.
     */
    @Test
    void perModuleRatchetWindowRejectsAnUnqualifiedRaiseAndAReturnToTheD2Floor() {
        AssertionError lineAboveCap = assertThrows(AssertionError.class, () -> assertRatchetWithinQualifiedWindow(
                "line", PER_MODULE_QUALIFIED_LINE_RATIO + 0.000001d, D2_MIN_LINE_RATIO, PER_MODULE_QUALIFIED_LINE_RATIO));
        assertTrue(lineAboveCap.getMessage().contains("per-module line ratchet")
                        && lineAboveCap.getMessage().contains("exceeds its qualified per-module baseline"),
                () -> "a line ratchet above its qualified baseline must be refused as such: " + lineAboveCap.getMessage());
        AssertionError branchAboveCap = assertThrows(AssertionError.class, () -> assertRatchetWithinQualifiedWindow(
                "branch", PER_MODULE_QUALIFIED_BRANCH_RATIO + 0.000001d, D2_MIN_BRANCH_RATIO, PER_MODULE_QUALIFIED_BRANCH_RATIO));
        assertTrue(branchAboveCap.getMessage().contains("per-module branch ratchet")
                        && branchAboveCap.getMessage().contains("exceeds its qualified per-module baseline"),
                () -> "a branch ratchet above its qualified baseline must be refused as such: " + branchAboveCap.getMessage());
        AssertionError lineAtFloor = assertThrows(AssertionError.class, () -> assertRatchetWithinQualifiedWindow(
                "line", D2_MIN_LINE_RATIO, D2_MIN_LINE_RATIO, PER_MODULE_QUALIFIED_LINE_RATIO));
        assertTrue(lineAtFloor.getMessage().contains("per-module line ratchet")
                        && lineAtFloor.getMessage().contains("must stay stricter than the D2 floor"),
                () -> "a line ratchet back at the D2 floor must be refused as such: " + lineAtFloor.getMessage());
        AssertionError branchAtFloor = assertThrows(AssertionError.class, () -> assertRatchetWithinQualifiedWindow(
                "branch", D2_MIN_BRANCH_RATIO, D2_MIN_BRANCH_RATIO, PER_MODULE_QUALIFIED_BRANCH_RATIO));
        assertTrue(branchAtFloor.getMessage().contains("per-module branch ratchet")
                        && branchAtFloor.getMessage().contains("must stay stricter than the D2 floor"),
                () -> "a branch ratchet back at the D2 floor must be refused as such: " + branchAtFloor.getMessage());
    }

    private static void assertRatchetWithinQualifiedWindow(String kind, double ratchet, double floor, double cap) {
        assertTrue(ratchet > floor,
                () -> "per-module " + kind + " ratchet " + ratchet + " must stay stricter than the D2 floor " + floor);
        assertTrue(ratchet <= cap,
                () -> "per-module " + kind + " ratchet " + ratchet + " exceeds its qualified per-module baseline " + cap);
    }

    private static void assertCoverageAtLeast(String kind, double actual, double minimum) {
        assertTrue(actual >= minimum,
                () -> "per-module JaCoCo " + kind + " coverage " + actual + " is below qualified-baseline ratchet " + minimum);
    }

    private org.w3c.dom.Document parse(Path report) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setExpandEntityReferences(false);
        var builder = factory.newDocumentBuilder();
        builder.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
        return builder.parse(report.toFile());
    }

    /**
     * Which modules owe a report, derived from the reactor rather than counted by hand.
     *
     * <p>Every module the root POM declares that carries at least one class under {@code src/main/java} owes its
     * own report. The rule maintains itself -- a module becomes mandatory the day it is declared -- and it
     * <em>explains</em> the two modules that legitimately produce nothing, morpheus-architecture-tests and
     * morpheus-coverage-report, instead of naming one of them in an exclusion list that could never match. A
     * hand-written floor does neither, and its error runs the permissive way: dropping a module removes its
     * missed lines along with its covered ones, so a module covered below the mean raises this ratio by
     * disappearing. The gate was easier to pass on an amputated reactor than on a whole one.</p>
     */
    private ReportPopulation reportPopulation(Path root) throws Exception {
        List<ModuleReport> reports = new ArrayList<>();
        List<String> owing = new ArrayList<>();
        List<String> neverBuilt = new ArrayList<>();
        List<String> builtWithoutReport = new ArrayList<>();
        List<String> stale = new ArrayList<>();
        for (String module : declaredModules(root)) {
            Path directory = root.resolve(module);
            if (!hasMainClasses(directory)) {
                continue;
            }
            owing.add(module);
            Path report = directory.resolve(PER_MODULE_REPORT);
            if (Files.isRegularFile(report)) {
                String staleness = stalenessOf(report, directory);
                if (staleness == null) {
                    reports.add(new ModuleReport(module, report));
                } else {
                    stale.add(module + " (" + staleness + ")");
                }
            } else if (Files.isDirectory(directory.resolve("target/classes"))) {
                builtWithoutReport.add(module);
            } else {
                neverBuilt.add(module);
            }
        }
        return new ReportPopulation(List.copyOf(reports), List.copyOf(owing), List.copyOf(neverBuilt),
                List.copyOf(builtWithoutReport), List.copyOf(stale));
    }

    /**
     * Why a module's report cannot be trusted to describe its code, or {@code null} when it can.
     *
     * <p>The reference is the newest regular file under {@code src/main/java} and {@code src/test/java}, compared
     * strictly: a report is stale only when something it should have measured is later than it. Tests count
     * because deleting one lowers coverage without touching a main source. The {@code target/classes} directory
     * is not used -- its time moves only when an entry is created or removed in it, never when a class file below
     * it is rewritten, so a report would always look newer than it.</p>
     */
    private static String stalenessOf(Path report, Path module) throws IOException {
        FileTime reportTime = Files.getLastModifiedTime(report);
        Path newest = null;
        FileTime newestTime = null;
        for (String sources : List.of("src/main/java", "src/test/java")) {
            Path directory = module.resolve(sources);
            if (!Files.isDirectory(directory)) {
                continue;
            }
            try (var files = Files.walk(directory)) {
                for (Path file : files.filter(Files::isRegularFile).toList()) {
                    FileTime time = Files.getLastModifiedTime(file);
                    if (newestTime == null || time.compareTo(newestTime) > 0) {
                        newest = file;
                        newestTime = time;
                    }
                }
            }
        }
        if (newestTime == null || newestTime.compareTo(reportTime) <= 0) {
            return null;
        }
        return "report " + reportTime + " is older than " + module.relativize(newest).toString().replace('\\', '/')
                + " " + newestTime;
    }

    private List<String> declaredModules(Path root) throws Exception {
        Path pom = root.resolve("pom.xml");
        List<String> modules = new ArrayList<>();
        Node child = parse(pom).getDocumentElement().getFirstChild();
        while (child != null) {
            if (child instanceof Element element && element.getTagName().equals("modules")) {
                Node declared = element.getFirstChild();
                while (declared != null) {
                    if (declared instanceof Element module && module.getTagName().equals("module")) {
                        modules.add(module.getTextContent().trim());
                    }
                    declared = declared.getNextSibling();
                }
            }
            child = child.getNextSibling();
        }
        assertFalse(modules.isEmpty(), () -> "no reactor module is declared in " + pom);
        return modules;
    }

    private boolean hasMainClasses(Path module) throws IOException {
        Path sources = module.resolve("src/main/java");
        if (!Files.isDirectory(sources)) {
            return false;
        }
        try (var files = Files.walk(sources)) {
            return files.anyMatch(path -> path.getFileName().toString().endsWith(".java") && Files.isRegularFile(path));
        }
    }

    /**
     * A missing report has two causes that call for opposite reactions, so the refusal names them apart. A module
     * with no {@code target/classes} was never built by this invocation -- {@code -pl morpheus-architecture-tests}
     * alone reaches this state -- and the fix is to run the reactor. A module that compiled and still emitted no
     * report has stopped reporting, and the fix is to find out why.
     */
    private record ReportPopulation(List<ModuleReport> reports, List<String> owing, List<String> neverBuilt,
            List<String> builtWithoutReport, List<String> stale) {
        private void assertComplete() {
            if (neverBuilt.isEmpty() && builtWithoutReport.isEmpty() && stale.isEmpty()) {
                return;
            }
            int owed = reports.size() + neverBuilt.size() + builtWithoutReport.size() + stale.size();
            StringBuilder refusal = new StringBuilder()
                    .append("the per-module scale refuses to conclude from a partial population: ")
                    .append(reports.size()).append(" of ").append(owed)
                    .append(" reactor modules with main classes produced ").append(PER_MODULE_REPORT);
            if (!neverBuilt.isEmpty()) {
                refusal.append(System.lineSeparator())
                        .append("  never built by this invocation, so no ratio can be computed -- run ")
                        .append("./mvnw clean verify over the whole reactor first: ")
                        .append(String.join(", ", neverBuilt));
            }
            if (!stale.isEmpty()) {
                refusal.append(System.lineSeparator())
                        .append("  report older than the sources it measures, so it describes code that is no longer ")
                        .append("there -- run ./mvnw clean verify over the whole reactor: ")
                        .append(String.join("; ", stale));
            }
            if (!builtWithoutReport.isEmpty()) {
                refusal.append(System.lineSeparator())
                        .append("  built but emitted no report: this is a regression in the module, not a ")
                        .append("mistaken invocation: ")
                        .append(String.join(", ", builtWithoutReport));
            }
            throw new AssertionError(refusal.toString());
        }
    }

    private record Ratchets(double perModuleLineCoverageMinimum, double perModuleBranchCoverageMinimum) {
        private static Ratchets load(Path path) throws IOException {
            Properties properties = new Properties();
            try (var reader = Files.newBufferedReader(path)) {
                properties.load(reader);
            }
            return new Ratchets(
                    requiredDouble(properties, "perModuleLineCoverageMinimum"),
                    requiredDouble(properties, "perModuleBranchCoverageMinimum"));
        }

        private static double requiredDouble(Properties properties, String key) {
            String value = properties.getProperty(key);
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("missing M21 quality ratchet: " + key);
            }
            try {
                double parsed = Double.parseDouble(value.trim());
                if (parsed < 0.0d || parsed > 1.0d) {
                    throw new IllegalArgumentException("M21 quality ratchet must be between 0 and 1: " + key);
                }
                return parsed;
            } catch (NumberFormatException failure) {
                throw new IllegalArgumentException("invalid M21 quality ratchet: " + key, failure);
            }
        }
    }

    private record ModuleReport(String module, Path report) {
    }

    private record ModuleCoverage(String name, Counter lines, Counter branches) {
    }

    private static final class Counter {
        long covered;
        long missed;

        void add(Counter other) {
            covered += other.covered;
            missed += other.missed;
        }

        double ratio() {
            long total = covered + missed;
            return total == 0 ? 1.0d : (double) covered / total;
        }
    }
}
