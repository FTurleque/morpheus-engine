package com.morpheus.architecture.m21;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
 * kept ratio of its scale, and the kept set must be complete -- both platforms, at least two runs each, one commit,
 * one population.</p>
 *
 * <p>Both bindings are textual because their subjects lie outside the compiled classpath: the constants are private
 * to gates in two different modules, and the evidence is data ({@code .claude/rules/architecture.md}). The rule is
 * exercised against fabricated
 * sets, so that a verifier that accepts anything fails here.</p>
 */
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
    // Widest spread of covered counts observed across the runs of one qualification, on either scale: 24 lines and
    // 11 branches, 15/09/2026 at a1fc0a8d (CoverageQualityGateTest cites the runs).
    private static final long REFERENCE_LINE_SPREAD = 24L;
    private static final long REFERENCE_BRANCH_SPREAD = 11L;

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
        Path evidence = workspace.resolve("narrow");
        Files.createDirectories(evidence);
        for (String file : List.of("per-module/Linux-1.txt", "per-module/Linux-2.txt", "per-module/Windows-1.txt",
                "per-module/Windows-2.txt", "aggregate/Linux-1.txt", "aggregate/Linux-2.txt",
                "aggregate/Windows-1.txt", "aggregate/Windows-2.txt")) {
            Scale scale = file.startsWith("aggregate") ? Scale.AGGREGATE : Scale.PER_MODULE;
            Files.createDirectories(evidence.resolve(scale.name));
            Files.writeString(evidence.resolve(file), summaryText(scale, 0.60d, 0.65d));
        }
        Files.writeString(evidence.resolve(MANIFEST), MANIFEST_HEADER + "\n"
                + String.join("\n", Stream.of(Scale.values()).flatMap(scale -> PLATFORMS.stream()
                        .flatMap(platform -> Stream.of(1, 2).map(run -> String.join("\t", scale.name, platform,
                                Integer.toString(run), COMMIT, scale.name + "/" + platform + "-" + run + ".txt"))))
                        .toList()) + "\n");

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
     * ratios shared by the two runs of a scale (run 2 sits 0.01 above run 1 on lines and branches).
     */
    private static Path qualification(Path workspace, double[] ratios) throws IOException {
        List<String> manifest = new ArrayList<>();
        manifest.add(MANIFEST_HEADER);
        for (Scale scale : Scale.values()) {
            Files.createDirectories(workspace.resolve(scale.name));
            for (int platformIndex = 0; platformIndex < PLATFORMS.size(); platformIndex++) {
                for (int run = 1; run <= 2; run++) {
                    String platform = PLATFORMS.get(platformIndex);
                    String file = scale.name + "/" + platform + "-" + run + ".txt";
                    double line = ratios[platformIndex] + (run - 1) * 0.01d;
                    double branch = ratios[2 + platformIndex] + (run - 1) * 0.01d;
                    Files.writeString(workspace.resolve(file), summaryText(scale, line, branch));
                    manifest.add(String.join("\t", scale.name, platform, Integer.toString(run), COMMIT, file));
                }
            }
        }
        Files.writeString(workspace.resolve(MANIFEST), String.join("\n", manifest) + "\n");
        return workspace;
    }

    private static String summaryText(Scale scale, double lineRatio, double branchRatio) {
        long lines = 100_000L;
        long branches = 40_000L;
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
            if (!kept.isEmpty()) {
                double[] cap = caps.lineAndBranch().get(scale);
                compare(problems, scale.lineConstant, cap[0], kept, "lineRatio");
                compare(problems, scale.branchConstant, cap[1], kept, "branchRatio");
            }
        }
        return problems;
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
