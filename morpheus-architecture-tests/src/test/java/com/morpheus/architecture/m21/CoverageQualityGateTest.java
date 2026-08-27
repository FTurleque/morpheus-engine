package com.morpheus.architecture.m21;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;

class CoverageQualityGateTest {
    private static final double D2_MIN_LINE_RATIO = 0.40d;
    private static final double D2_MIN_BRANCH_RATIO = 0.35d;

    // Qualified exact-head baseline after audit hardening #179 on Linux: 50.6353% lines / 43.0862% branches.
    // Ratchets stay below the qualified measurement to absorb deterministic cross-platform report noise.
    private static final double QUALIFIED_LINE_RATIO = 0.506353d;
    private static final double QUALIFIED_BRANCH_RATIO = 0.430862d;
    private static final double LINE_RATCHET = 0.504d;
    private static final double BRANCH_RATCHET = 0.429d;

    private static final double MIN_LINE_RATIO = Math.max(D2_MIN_LINE_RATIO, LINE_RATCHET);
    private static final double MIN_BRANCH_RATIO = Math.max(D2_MIN_BRANCH_RATIO, BRANCH_RATCHET);

    @Test
    void reactorCoverageDoesNotRegressBelowQualifiedBaseline() throws Exception {
        Path root = repoRoot();
        List<Path> reports = jacocoReports(root);
        assertTrue(reports.size() >= 8, "expected JaCoCo reports from the tested reactor modules, got " + reports.size());
        assertTrue(MIN_LINE_RATIO >= D2_MIN_LINE_RATIO, "coverage ratchet must never weaken the D2 line floor");
        assertTrue(MIN_BRANCH_RATIO >= D2_MIN_BRANCH_RATIO, "coverage ratchet must never weaken the D2 branch floor");
        assertTrue(LINE_RATCHET <= QUALIFIED_LINE_RATIO, "line ratchet must not exceed its qualified baseline");
        assertTrue(BRANCH_RATCHET <= QUALIFIED_BRANCH_RATIO, "branch ratchet must not exceed its qualified baseline");

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
        Path summary = root.resolve("morpheus-architecture-tests/target/m21-coverage-summary.txt");
        Files.createDirectories(summary.getParent());
        Files.writeString(summary, String.format(
                java.util.Locale.ROOT,
                "reports=%d%nlineCovered=%d%nlineMissed=%d%nlineRatio=%.6f%nbranchCovered=%d%nbranchMissed=%d%nbranchRatio=%.6f%nqualifiedLineBaseline=%.6f%nqualifiedBranchBaseline=%.6f%nlineRatchet=%.3f%nbranchRatchet=%.3f%nd2LineFloor=%.2f%nd2BranchFloor=%.2f%n",
                reports.size(), lines.covered, lines.missed, lineRatio,
                branches.covered, branches.missed, branchRatio,
                QUALIFIED_LINE_RATIO, QUALIFIED_BRANCH_RATIO,
                MIN_LINE_RATIO, MIN_BRANCH_RATIO,
                D2_MIN_LINE_RATIO, D2_MIN_BRANCH_RATIO));

        assertCoverageAtLeast("line", lineRatio, MIN_LINE_RATIO);
        assertCoverageAtLeast("branch", branchRatio, MIN_BRANCH_RATIO);
    }

    @Test
    void ratchetRejectsARegressionThatTheOldD2FloorWouldHaveAccepted() {
        assertTrue(0.49d >= D2_MIN_LINE_RATIO);
        assertTrue(0.41d >= D2_MIN_BRANCH_RATIO);
        assertThrows(AssertionError.class, () -> assertCoverageAtLeast("line", 0.49d, MIN_LINE_RATIO));
        assertThrows(AssertionError.class, () -> assertCoverageAtLeast("branch", 0.41d, MIN_BRANCH_RATIO));
    }

    private static void assertCoverageAtLeast(String kind, double actual, double minimum) {
        assertTrue(actual >= minimum,
                () -> "aggregate JaCoCo " + kind + " coverage " + actual + " is below qualified-baseline ratchet " + minimum);
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

    private static final class Counter {
        long covered;
        long missed;

        double ratio() {
            long total = covered + missed;
            return total == 0 ? 1.0d : (double) covered / total;
        }
    }
}
