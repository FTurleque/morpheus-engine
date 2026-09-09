package com.morpheus.coverage;

import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Canonical post-reactor coverage gate including cross-module architecture-test execution.
 *
 * <p>This is the aggregate scale. It counts a different population of covered lines from the per-module scale
 * enforced by CoverageQualityGateTest, so it reads its own pair of ratchet keys and writes its own evidence
 * file. Comparing a ratio produced here against a threshold qualified over there is the defect this separation
 * exists to prevent.</p>
 */
class AggregateCoverageGateTest {
    private static final double D2_MIN_LINE_RATIO = 0.40d;
    private static final double D2_MIN_BRANCH_RATIO = 0.35d;

    // Qualified exact-head baseline of the AGGREGATE scale.
    //
    // No aggregate measurement had ever been qualified: until the scale split, this gate read the ratchet keys
    // that CoverageQualityGateTest had qualified on the per-module scale, so its threshold carried no evidence
    // about this grandeur at all. The cap therefore starts pinned to the value inherited from that shared key
    // and moves only with an exact-head measurement of THIS report, taken twice on each platform and qualified
    // on the lowest of the four -- the same rule the per-module cap follows.
    private static final double AGGREGATE_QUALIFIED_LINE_RATIO = 0.620d;
    private static final double AGGREGATE_QUALIFIED_BRANCH_RATIO = 0.535d;

    @Test
    void aggregateCoverageIncludesCrossModuleExecutionAndMeetsRatchets() throws Exception {
        Path root = repoRoot();
        Path report = root.resolve("morpheus-coverage-report/target/site/jacoco-aggregate/jacoco.xml");
        assertTrue(Files.isRegularFile(report), "aggregate JaCoCo report is missing: " + report);

        Ratchets ratchets = Ratchets.load(root.resolve("config/m21-quality-ratchets.properties"));
        double minimumLine = Math.max(D2_MIN_LINE_RATIO, ratchets.aggregateLineCoverageMinimum());
        double minimumBranch = Math.max(D2_MIN_BRANCH_RATIO, ratchets.aggregateBranchCoverageMinimum());
        assertTrue(minimumLine >= D2_MIN_LINE_RATIO, "coverage ratchet must never weaken the D2 line floor");
        assertTrue(minimumBranch >= D2_MIN_BRANCH_RATIO, "coverage ratchet must never weaken the D2 branch floor");
        assertRatchetWithinQualifiedWindow("line", ratchets.aggregateLineCoverageMinimum(),
                D2_MIN_LINE_RATIO, AGGREGATE_QUALIFIED_LINE_RATIO);
        assertRatchetWithinQualifiedWindow("branch", ratchets.aggregateBranchCoverageMinimum(),
                D2_MIN_BRANCH_RATIO, AGGREGATE_QUALIFIED_BRANCH_RATIO);

        var document = parse(report);
        Counter lines = counter(document.getDocumentElement(), "LINE");
        Counter branches = counter(document.getDocumentElement(), "BRANCH");
        double lineRatio = lines.ratio();
        double branchRatio = branches.ratio();

        Path summary = root.resolve("morpheus-architecture-tests/target/m21-aggregate-coverage-summary.txt");
        Files.createDirectories(summary.getParent());
        Files.writeString(summary, String.format(
                Locale.ROOT,
                "coverageScope=aggregate%n"
                        + "coverageSource=jacoco-report-aggregate%n"
                        + "aggregateReport=morpheus-coverage-report/target/site/jacoco-aggregate/jacoco.xml%n"
                        + "lineCovered=%d%nlineMissed=%d%nlineRatio=%.6f%n"
                        + "branchCovered=%d%nbranchMissed=%d%nbranchRatio=%.6f%n"
                        + "qualifiedLineBaseline=%.6f%nqualifiedBranchBaseline=%.6f%n"
                        + "lineRatchet=%.3f%nbranchRatchet=%.3f%n"
                        + "d2LineFloor=%.2f%nd2BranchFloor=%.2f%n",
                lines.covered, lines.missed, lineRatio,
                branches.covered, branches.missed, branchRatio,
                AGGREGATE_QUALIFIED_LINE_RATIO, AGGREGATE_QUALIFIED_BRANCH_RATIO,
                minimumLine, minimumBranch,
                D2_MIN_LINE_RATIO, D2_MIN_BRANCH_RATIO));

        assertTrue(lineRatio >= minimumLine,
                () -> "aggregate JaCoCo line coverage " + lineRatio + " is below aggregate ratchet " + minimumLine);
        assertTrue(branchRatio >= minimumBranch,
                () -> "aggregate JaCoCo branch coverage " + branchRatio + " is below aggregate ratchet " + minimumBranch);
    }

    /**
     * The window is what makes a ratchet a ratchet: strictly above the D2 floor it may never silently return to,
     * and at or below the aggregate measurement that qualified it. Both ends are proven here rather than merely
     * exercised by whichever value happens to be configured today.
     */
    @Test
    void aggregateRatchetWindowRejectsAnUnqualifiedRaiseAndAReturnToTheD2Floor() {
        assertThrows(AssertionError.class, () -> assertRatchetWithinQualifiedWindow(
                "line", AGGREGATE_QUALIFIED_LINE_RATIO + 0.000001d, D2_MIN_LINE_RATIO, AGGREGATE_QUALIFIED_LINE_RATIO));
        assertThrows(AssertionError.class, () -> assertRatchetWithinQualifiedWindow(
                "branch", AGGREGATE_QUALIFIED_BRANCH_RATIO + 0.000001d, D2_MIN_BRANCH_RATIO, AGGREGATE_QUALIFIED_BRANCH_RATIO));
        assertThrows(AssertionError.class, () -> assertRatchetWithinQualifiedWindow(
                "line", D2_MIN_LINE_RATIO, D2_MIN_LINE_RATIO, AGGREGATE_QUALIFIED_LINE_RATIO));
        assertThrows(AssertionError.class, () -> assertRatchetWithinQualifiedWindow(
                "branch", D2_MIN_BRANCH_RATIO, D2_MIN_BRANCH_RATIO, AGGREGATE_QUALIFIED_BRANCH_RATIO));
    }

    /** Every key is required, and a missing one must name itself rather than surface as a parse failure. */
    @Test
    void ratchetLoadingNamesAMissingAggregateKeyInsteadOfFailingObscurely() {
        Properties incomplete = new Properties();
        incomplete.setProperty("aggregateLineCoverageMinimum", "0.620");
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> Ratchets.of(incomplete));
        assertTrue(failure.getMessage().contains("aggregateBranchCoverageMinimum"),
                () -> "a missing ratchet must name itself, got: " + failure.getMessage());
    }

    private static void assertRatchetWithinQualifiedWindow(String kind, double ratchet, double floor, double cap) {
        assertTrue(ratchet > floor,
                () -> "aggregate " + kind + " ratchet " + ratchet + " must stay stricter than the D2 floor " + floor);
        assertTrue(ratchet <= cap,
                () -> "aggregate " + kind + " ratchet " + ratchet + " exceeds its qualified aggregate baseline " + cap);
    }

    private static Counter counter(Element root, String type) {
        Counter direct = directCounter(root, type);
        if (direct.total() > 0) {
            return direct;
        }

        // JaCoCo normally emits report-level counters. Keep a group-level fallback for report layouts that wrap
        // module reports in <group> elements; summing only each group's direct counter avoids package/class double count.
        Counter grouped = new Counter();
        Node child = root.getFirstChild();
        while (child != null) {
            if (child instanceof Element element && element.getTagName().equals("group")) {
                grouped.add(directCounter(element, type));
            }
            child = child.getNextSibling();
        }
        if (grouped.total() == 0) {
            throw new IllegalArgumentException("aggregate JaCoCo report has no " + type + " counter");
        }
        return grouped;
    }

    private static Counter directCounter(Element parent, String type) {
        Counter result = new Counter();
        Node child = parent.getFirstChild();
        while (child != null) {
            if (child instanceof Element element
                    && element.getTagName().equals("counter")
                    && element.getAttribute("type").equals(type)) {
                result.covered += Long.parseLong(element.getAttribute("covered"));
                result.missed += Long.parseLong(element.getAttribute("missed"));
            }
            child = child.getNextSibling();
        }
        return result;
    }

    private static org.w3c.dom.Document parse(Path report) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setExpandEntityReferences(false);
        var builder = factory.newDocumentBuilder();
        builder.setEntityResolver((publicId, systemId) -> new InputSource(new StringReader("")));
        return builder.parse(report.toFile());
    }

    private static Path repoRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("pom.xml"))
                    && Files.isDirectory(current.resolve("morpheus-application"))
                    && Files.isDirectory(current.resolve("distribution"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("MORPHEUS repository root not found");
    }

    private record Ratchets(double aggregateLineCoverageMinimum, double aggregateBranchCoverageMinimum) {
        private static Ratchets load(Path path) throws IOException {
            Properties properties = new Properties();
            try (var reader = Files.newBufferedReader(path)) {
                properties.load(reader);
            }
            return of(properties);
        }

        private static Ratchets of(Properties properties) {
            return new Ratchets(
                    requiredRatio(properties, "aggregateLineCoverageMinimum"),
                    requiredRatio(properties, "aggregateBranchCoverageMinimum"));
        }

        private static double requiredRatio(Properties properties, String key) {
            String value = properties.getProperty(key);
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("missing M21 quality ratchet: " + key);
            }
            double parsed;
            try {
                parsed = Double.parseDouble(value.trim());
            } catch (NumberFormatException failure) {
                throw new IllegalArgumentException("invalid M21 quality ratchet: " + key, failure);
            }
            if (parsed <= 0.0d || parsed > 1.0d) {
                throw new IllegalArgumentException("M21 quality ratchet must be in (0, 1]: " + key);
            }
            return parsed;
        }
    }

    private static final class Counter {
        private long covered;
        private long missed;

        private void add(Counter other) {
            covered += other.covered;
            missed += other.missed;
        }

        private long total() {
            return covered + missed;
        }

        private double ratio() {
            long total = total();
            return total == 0 ? 1.0d : (double) covered / total;
        }
    }
}
