package com.morpheus.architecture.m21;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

/**
 * Per-module coverage gate: the sum of every module's own JaCoCo report, architecture-test module excluded.
 *
 * <p>This scale answers a question the canonical post-reactor measurement cannot: what each module covers
 * <em>by itself</em>. A service exercised only through a sibling module's tests reads as uncovered here, so a
 * module that stops testing itself stays visible instead of being masked by its consumers. It is a different
 * grandeur from the canonical measurement and therefore reads its own pair of ratchet keys.</p>
 */
class CoverageQualityGateTest {
    private static final double D2_MIN_LINE_RATIO = 0.40d;
    private static final double D2_MIN_BRANCH_RATIO = 0.35d;

    // Qualified exact-head baseline of the PER-MODULE scale: 62.5013% lines / 53.7997% branches.
    // Deliberately at or below the LOWEST reproducible exact-head measurement across both platforms, never the
    // best one. The two platforms run the same number of tests, but some of them no-op off their own OS -- the
    // Windows junction check is one -- so Linux covers slightly fewer lines for an identical test count.
    // Qualifying on the higher figure would pin a baseline the other platform cannot reach.
    //
    // Measured on 08/09/2026 at fix/audit-hardening-2026-09-08, two full runs per platform:
    //     Windows  62.5328% / 62.5432% lines,  53.8092% / 53.8188% branches
    //     Linux    62.5083% / 62.5013% lines,  53.7997% / 53.7997% branches   <- qualified on the lowest
    // The previous baseline (54.5801% / 47.7791%, #253) had drifted well below the measured reality: develop
    // already stood at 60.41% lines on Linux before this branch added a test.
    //
    // The per-module ratchets in config/m21-quality-ratchets.properties sit deliberately BELOW this cap rather
    // than at it. Two runs of the same commit on the same machine differed by two covered lines, so a ratchet
    // pinned to the measurement would turn ordinary run-to-run variation into a build failure. 0.620 / 0.535
    // leaves roughly 140 lines and 30 branches of headroom ON THIS SCALE only; the canonical measurement counts
    // a different population of covered lines and carries its own cap in AggregateCoverageGateTest.
    //
    // Raising these two constants requires a fresh per-module measurement on BOTH platforms, cited here.
    private static final double PER_MODULE_QUALIFIED_LINE_RATIO = 0.625013d;
    private static final double PER_MODULE_QUALIFIED_BRANCH_RATIO = 0.537997d;

    @Test
    void perModuleCoverageDoesNotRegressBelowQualifiedBaseline() throws Exception {
        Path root = repoRoot();
        Ratchets ratchets = Ratchets.load(root.resolve("config/m21-quality-ratchets.properties"));
        double minLineRatio = Math.max(D2_MIN_LINE_RATIO, ratchets.perModuleLineCoverageMinimum());
        double minBranchRatio = Math.max(D2_MIN_BRANCH_RATIO, ratchets.perModuleBranchCoverageMinimum());
        List<Path> reports = jacocoReports(root);
        assertTrue(reports.size() >= 8, "expected JaCoCo reports from the tested reactor modules, got " + reports.size());
        assertTrue(minLineRatio >= D2_MIN_LINE_RATIO, "coverage ratchet must never weaken the D2 line floor");
        assertTrue(minBranchRatio >= D2_MIN_BRANCH_RATIO, "coverage ratchet must never weaken the D2 branch floor");
        assertRatchetWithinQualifiedWindow("line", ratchets.perModuleLineCoverageMinimum(),
                D2_MIN_LINE_RATIO, PER_MODULE_QUALIFIED_LINE_RATIO);
        assertRatchetWithinQualifiedWindow("branch", ratchets.perModuleBranchCoverageMinimum(),
                D2_MIN_BRANCH_RATIO, PER_MODULE_QUALIFIED_BRANCH_RATIO);

        Counter lines = new Counter();
        Counter branches = new Counter();
        for (Path report : reports) {
            var document = parse(report);
            Node child = document.getDocumentElement().getFirstChild();
            while (child != null) {
                if (child instanceof Element element && element.getTagName().equals("counter")) {
                    Counter target = switch (element.getAttribute("type")) {
                        case "LINE" -> lines;
                        case "BRANCH" -> branches;
                        default -> null;
                    };
                    if (target != null) {
                        target.covered += Long.parseLong(element.getAttribute("covered"));
                        target.missed += Long.parseLong(element.getAttribute("missed"));
                    }
                }
                child = child.getNextSibling();
            }
        }

        double lineRatio = lines.ratio();
        double branchRatio = branches.ratio();
        Path summary = root.resolve("morpheus-architecture-tests/target/m21-per-module-coverage-summary.txt");
        Files.createDirectories(summary.getParent());
        Files.writeString(summary, String.format(
                java.util.Locale.ROOT,
                "coverageScope=per-module%nreports=%d%nlineCovered=%d%nlineMissed=%d%nlineRatio=%.6f%nbranchCovered=%d%nbranchMissed=%d%nbranchRatio=%.6f%nqualifiedLineBaseline=%.6f%nqualifiedBranchBaseline=%.6f%nlineRatchet=%.3f%nbranchRatchet=%.3f%nd2LineFloor=%.2f%nd2BranchFloor=%.2f%n",
                reports.size(), lines.covered, lines.missed, lineRatio,
                branches.covered, branches.missed, branchRatio,
                PER_MODULE_QUALIFIED_LINE_RATIO, PER_MODULE_QUALIFIED_BRANCH_RATIO,
                minLineRatio, minBranchRatio,
                D2_MIN_LINE_RATIO, D2_MIN_BRANCH_RATIO));

        assertCoverageAtLeast("line", lineRatio, minLineRatio);
        assertCoverageAtLeast("branch", branchRatio, minBranchRatio);
    }

    @Test
    void ratchetRejectsARegressionThatTheOldD2FloorWouldHaveAccepted() throws Exception {
        Ratchets ratchets = Ratchets.load(repoRoot().resolve("config/m21-quality-ratchets.properties"));
        double minLineRatio = Math.max(D2_MIN_LINE_RATIO, ratchets.perModuleLineCoverageMinimum());
        double minBranchRatio = Math.max(D2_MIN_BRANCH_RATIO, ratchets.perModuleBranchCoverageMinimum());
        assertTrue(0.49d >= D2_MIN_LINE_RATIO);
        assertTrue(0.41d >= D2_MIN_BRANCH_RATIO);
        assertThrows(AssertionError.class, () -> assertCoverageAtLeast("line", 0.49d, minLineRatio));
        assertThrows(AssertionError.class, () -> assertCoverageAtLeast("branch", 0.41d, minBranchRatio));
    }

    /**
     * The window is what makes a ratchet a ratchet: strictly above the D2 floor it may never silently return to,
     * and at or below the measurement that qualified it. Both ends are proven here rather than merely exercised
     * by whichever value happens to be configured today.
     */
    @Test
    void perModuleRatchetWindowRejectsAnUnqualifiedRaiseAndAReturnToTheD2Floor() {
        assertThrows(AssertionError.class, () -> assertRatchetWithinQualifiedWindow(
                "line", PER_MODULE_QUALIFIED_LINE_RATIO + 0.000001d, D2_MIN_LINE_RATIO, PER_MODULE_QUALIFIED_LINE_RATIO));
        assertThrows(AssertionError.class, () -> assertRatchetWithinQualifiedWindow(
                "branch", PER_MODULE_QUALIFIED_BRANCH_RATIO + 0.000001d, D2_MIN_BRANCH_RATIO, PER_MODULE_QUALIFIED_BRANCH_RATIO));
        assertThrows(AssertionError.class, () -> assertRatchetWithinQualifiedWindow(
                "line", D2_MIN_LINE_RATIO, D2_MIN_LINE_RATIO, PER_MODULE_QUALIFIED_LINE_RATIO));
        assertThrows(AssertionError.class, () -> assertRatchetWithinQualifiedWindow(
                "branch", D2_MIN_BRANCH_RATIO, D2_MIN_BRANCH_RATIO, PER_MODULE_QUALIFIED_BRANCH_RATIO));
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

    private List<Path> jacocoReports(Path root) throws IOException {
        List<Path> result = new ArrayList<>();
        try (var files = Files.walk(root, 5)) {
            for (Path file : files
                    .filter(path -> path.endsWith(Path.of("target", "site", "jacoco", "jacoco.xml")))
                    .sorted()
                    .toList()) {
                if (!file.startsWith(root.resolve("morpheus-architecture-tests/target"))) {
                    result.add(file);
                }
            }
        }
        return result;
    }

    private Path repoRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isRegularFile(current.resolve("pom.xml")) && Files.isDirectory(current.resolve("distribution"))) {
            return current;
        }
        Path parent = current.getParent();
        if (parent != null && Files.isRegularFile(parent.resolve("pom.xml")) && Files.isDirectory(parent.resolve("distribution"))) {
            return parent;
        }
        throw new IllegalStateException("MORPHEUS repository root not found from " + current);
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

    private static final class Counter {
        long covered;
        long missed;

        double ratio() {
            long total = covered + missed;
            return total == 0 ? 1.0d : (double) covered / total;
        }
    }
}
