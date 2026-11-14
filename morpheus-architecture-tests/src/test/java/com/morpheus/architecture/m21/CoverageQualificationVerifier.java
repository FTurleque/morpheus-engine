package com.morpheus.architecture.m21;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
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
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * Reads the kept coverage qualification and states what it fails to support: caps that do not equal the lowest kept
 * ratio of their scale, ratchets that spend headroom no qualification granted, module floors outside the window their
 * counters grant, and evidence that is incomplete or inconsistent.
 *
 * <p>Every method returns the problems it finds as sentences; only the reading of {@code evidence.tsv} asserts, on
 * the form of the manifest. {@link CoverageQualificationEvidenceTest} judges the kept evidence with it and exercises
 * it against fabricated sets.</p>
 */
final class CoverageQualificationVerifier {
    static final String EVIDENCE_DIRECTORY = "config/coverage-qualification";
    static final String MANIFEST = "evidence.tsv";
    static final String MANIFEST_HEADER = "scale\tplatform\trun\tcommit\tfile";
    static final String PER_MODULE_GATE =
            "morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m21/CoverageQualityGateTest.java";
    private static final String AGGREGATE_GATE =
            "morpheus-coverage-report/src/test/java/com/morpheus/coverage/AggregateCoverageGateTest.java";
    static final List<String> PLATFORMS = List.of("Linux", "Windows");
    private static final int MINIMUM_RUNS_PER_PLATFORM = 2;
    private static final double RATIO_PRECISION = 1.0e-9d;
    private static final double SIX_DIGIT_ROUNDING = 5.1e-7d;
    static final String RATCHETS = "config/m21-quality-ratchets.properties";
    // A ratchet, and a module floor above the D2 floor, leaves at least three times its spread under its lowest kept
    // count (ADR-0167): closer, an ordinary variation between runs would fail the build.
    private static final long HEADROOM_SPREADS = 3L;
    // A module floor leaves at most nine times its spread under its lowest kept count (ADR-0167): further down, the
    // module could lose most of its coverage before its own floor bites.
    private static final long MAXIMUM_HEADROOM_SPREADS = 9L;
    // Widest spread of covered counts observed across the runs of one qualification, on either scale: 24 lines and
    // 11 branches, 15/09/2026 at a1fc0a8d (CoverageQualityGateTest cites the runs).
    private static final long REFERENCE_LINE_SPREAD = 24L;
    private static final long REFERENCE_BRANCH_SPREAD = 11L;
    static final String MODULE_FLOORS = "config/m21-per-module-coverage-floors.properties";
    static final String LINE_FLOOR_SUFFIX = ".lineCoverageMinimum";
    static final String BRANCH_FLOOR_SUFFIX = ".branchCoverageMinimum";
    private static final String MODULE_PREFIX = "module.";
    static final List<String> MODULE_COUNTERS =
            List.of("lineCovered", "lineMissed", "branchCovered", "branchMissed");

    enum Scale {
        PER_MODULE("per-module", "perModule", "PER_MODULE_QUALIFIED_LINE_RATIO", "PER_MODULE_QUALIFIED_BRANCH_RATIO",
                PER_MODULE_GATE),
        AGGREGATE("aggregate", "aggregate", "AGGREGATE_QUALIFIED_LINE_RATIO", "AGGREGATE_QUALIFIED_BRANCH_RATIO",
                AGGREGATE_GATE);

        final String name;
        private final String ratchetPrefix;
        final String lineConstant;
        final String branchConstant;
        final String gate;

        Scale(String name, String ratchetPrefix, String lineConstant, String branchConstant, String gate) {
            this.name = name;
            this.ratchetPrefix = ratchetPrefix;
            this.lineConstant = lineConstant;
            this.branchConstant = branchConstant;
            this.gate = gate;
        }

        String ratchetKey(String counter) {
            return ratchetPrefix + counter + "CoverageMinimum";
        }
    }

    record Entry(Scale scale, String platform, String run, String commit, String file) {
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

    record Caps(Map<Scale, double[]> lineAndBranch) {
    }

    static List<String> verify(Path directory, Caps caps) throws IOException {
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
    static List<String> margins(Path directory, Map<String, Double> ratchets) throws IOException {
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
        Set<String> union = new TreeSet<>();
        carrying.forEach(summary -> union.addAll(moduleCounters(summary).keySet()));
        for (Summary summary : kept) {
            if (moduleCounters(summary).isEmpty()) {
                problems.add(summary.entry().file() + " carries no module counters while "
                        + carrying.get(0).entry().file() + " does; every summary of a qualification carries them");
            } else {
                problems.addAll(summaryModuleProblems(summary, union, carrying));
            }
        }
        return problems;
    }

    /** What one summary that carries module counters misses: a counter, a module another summary carries, a total. */
    private static List<String> summaryModuleProblems(Summary summary, Set<String> union, List<Summary> carrying) {
        List<String> problems = new ArrayList<>();
        String file = summary.entry().file();
        Map<String, Map<String, Long>> modules = moduleCounters(summary);
        modules.forEach((module, counters) -> MODULE_COUNTERS.stream().filter(counter -> !counters.containsKey(counter))
                .forEach(counter -> problems.add(file + " lacks " + MODULE_PREFIX + module + "." + counter)));
        union.stream().filter(module -> !modules.containsKey(module)).forEach(module -> {
            String other = carrying.stream().filter(candidate -> moduleCounters(candidate).containsKey(module))
                    .findFirst().orElseThrow().entry().file();
            problems.add(file + " carries no counters for module " + module + ", which " + other + " carries");
        });
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
        return problems;
    }

    private static Map<String, Map<String, Long>> moduleCounters(Summary summary) {
        Map<String, Map<String, Long>> modules = new TreeMap<>();
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
    static List<String> floorWindows(Path directory, Properties floors, double d2Line, double d2Branch)
            throws IOException {
        List<Summary> kept = new ArrayList<>();
        for (Entry entry : readManifest(directory)) {
            if (entry.scale() == Scale.PER_MODULE) {
                kept.add(new Summary(entry, parseSummary(directory.resolve(entry.file()))));
            }
        }
        List<String> problems = new ArrayList<>();
        for (String key : new TreeSet<>(floors.stringPropertyNames())) {
            if (key.endsWith(LINE_FLOOR_SUFFIX) || key.endsWith(BRANCH_FLOOR_SUFFIX)) {
                floorProblem(key, Double.parseDouble(floors.getProperty(key).strip()), kept, d2Line, d2Branch)
                        .ifPresent(problems::add);
            }
        }
        return problems;
    }

    /** The refusal of one module floor, read as {@link #floorWindows} describes, or nothing. */
    private static Optional<String> floorProblem(String key, double floor, List<Summary> kept, double d2Line,
            double d2Branch) {
        boolean line = key.endsWith(LINE_FLOOR_SUFFIX);
        String module = key.substring(0, key.length() - (line ? LINE_FLOOR_SUFFIX : BRANCH_FLOOR_SUFFIX).length());
        double d2Floor = line ? d2Line : d2Branch;
        if (floor < d2Floor - RATIO_PRECISION) {
            return Optional.of(key + "=" + floor + " is below the D2 floor " + d2Floor + " of module " + module);
        }
        boolean atTheD2Floor = floor <= d2Floor + RATIO_PRECISION;
        List<Map<String, Long>> measured = kept.stream().map(summary -> moduleCounters(summary).get(module)).toList();
        if (measured.isEmpty() || measured.contains(null)) {
            return atTheD2Floor ? Optional.empty() : Optional.of(key + "=" + floor + " is above the D2 floor "
                    + d2Floor + " but the kept per-module evidence carries no counters for " + module
                    + " in every summary: a floor above the D2 floor needs a Windows and Linux qualification that"
                    + " carries the module");
        }
        return windowProblem(key, module, line ? "line" : "branch", floor, atTheD2Floor, measured,
                kept.get(0).values());
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

    static Properties loadProperties(Path file) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(file)) {
            properties.load(reader);
        }
        return properties;
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

    static List<Entry> readManifest(Path directory) throws IOException {
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

    static Map<String, String> parseSummary(Path path) throws IOException {
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

    private CoverageQualificationVerifier() {
    }
}
