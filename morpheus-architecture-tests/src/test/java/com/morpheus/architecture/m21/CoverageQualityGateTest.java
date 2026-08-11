package com.morpheus.architecture.m21;

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
    private static final double MIN_LINE_RATIO = 0.40d;
    private static final double MIN_BRANCH_RATIO = 0.35d;

    @Test
    void reactorCoverageStaysAboveCurrentFloors() throws Exception {
        Path root = repoRoot();
        List<Path> reports = jacocoReports(root);
        assertTrue(reports.size() >= 8, "expected JaCoCo reports from the tested reactor modules, got " + reports.size());

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
                "reports=%d%nlineCovered=%d%nlineMissed=%d%nlineRatio=%.6f%nbranchCovered=%d%nbranchMissed=%d%nbranchRatio=%.6f%nlineFloor=%.2f%nbranchFloor=%.2f%n",
                reports.size(), lines.covered, lines.missed, lineRatio,
                branches.covered, branches.missed, branchRatio,
                MIN_LINE_RATIO, MIN_BRANCH_RATIO));

        assertTrue(lineRatio >= MIN_LINE_RATIO,
                () -> "aggregate JaCoCo line coverage " + lineRatio + " is below current floor " + MIN_LINE_RATIO);
        assertTrue(branchRatio >= MIN_BRANCH_RATIO,
                () -> "aggregate JaCoCo branch coverage " + branchRatio + " is below current floor " + MIN_BRANCH_RATIO);
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
