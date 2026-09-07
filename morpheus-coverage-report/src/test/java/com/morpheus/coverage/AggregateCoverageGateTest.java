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

import static org.junit.jupiter.api.Assertions.assertTrue;

/** Canonical post-reactor coverage gate including cross-module architecture-test execution. */
class AggregateCoverageGateTest {
    private static final double D2_MIN_LINE_RATIO = 0.40d;
    private static final double D2_MIN_BRANCH_RATIO = 0.35d;

    @Test
    void aggregateCoverageIncludesCrossModuleExecutionAndMeetsRatchets() throws Exception {
        Path root = repoRoot();
        Path report = root.resolve("morpheus-coverage-report/target/site/jacoco-aggregate/jacoco.xml");
        assertTrue(Files.isRegularFile(report), "aggregate JaCoCo report is missing: " + report);

        Ratchets ratchets = Ratchets.load(root.resolve("config/m21-quality-ratchets.properties"));
        double minimumLine = Math.max(D2_MIN_LINE_RATIO, ratchets.lineCoverageMinimum());
        double minimumBranch = Math.max(D2_MIN_BRANCH_RATIO, ratchets.branchCoverageMinimum());

        var document = parse(report);
        Counter lines = counter(document.getDocumentElement(), "LINE");
        Counter branches = counter(document.getDocumentElement(), "BRANCH");
        double lineRatio = lines.ratio();
        double branchRatio = branches.ratio();

        Path summary = root.resolve("morpheus-architecture-tests/target/m21-coverage-summary.txt");
        Files.createDirectories(summary.getParent());
        Files.writeString(summary, String.format(
                Locale.ROOT,
                "coverageSource=jacoco-report-aggregate%n"
                        + "aggregateReport=morpheus-coverage-report/target/site/jacoco-aggregate/jacoco.xml%n"
                        + "lineCovered=%d%nlineMissed=%d%nlineRatio=%.6f%n"
                        + "branchCovered=%d%nbranchMissed=%d%nbranchRatio=%.6f%n"
                        + "lineRatchet=%.3f%nbranchRatchet=%.3f%n"
                        + "d2LineFloor=%.2f%nd2BranchFloor=%.2f%n",
                lines.covered, lines.missed, lineRatio,
                branches.covered, branches.missed, branchRatio,
                minimumLine, minimumBranch,
                D2_MIN_LINE_RATIO, D2_MIN_BRANCH_RATIO));

        assertTrue(lineRatio >= minimumLine,
                () -> "aggregate JaCoCo line coverage " + lineRatio + " is below ratchet " + minimumLine);
        assertTrue(branchRatio >= minimumBranch,
                () -> "aggregate JaCoCo branch coverage " + branchRatio + " is below ratchet " + minimumBranch);
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

    private record Ratchets(double lineCoverageMinimum, double branchCoverageMinimum) {
        private static Ratchets load(Path path) throws IOException {
            Properties properties = new Properties();
            try (var reader = Files.newBufferedReader(path)) {
                properties.load(reader);
            }
            return new Ratchets(
                    requiredRatio(properties, "lineCoverageMinimum"),
                    requiredRatio(properties, "branchCoverageMinimum"));
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
