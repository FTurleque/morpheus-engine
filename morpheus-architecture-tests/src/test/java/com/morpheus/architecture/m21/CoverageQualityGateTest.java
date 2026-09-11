package com.morpheus.architecture.m21;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import org.junit.jupiter.api.io.TempDir;
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
    private static final String PER_MODULE_REPORT = "target/site/jacoco/jacoco.xml";
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
        ReportPopulation population = reportPopulation(root);
        population.assertComplete();
        List<Path> reports = population.reports();
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
     * A guard that has never refused is not a guard (ADR-0103). The rule is exercised here against a reactor
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
        List<Path> reports = new ArrayList<>();
        List<String> neverBuilt = new ArrayList<>();
        List<String> builtWithoutReport = new ArrayList<>();
        for (String module : declaredModules(root)) {
            Path directory = root.resolve(module);
            if (!hasMainClasses(directory)) {
                continue;
            }
            Path report = directory.resolve(PER_MODULE_REPORT);
            if (Files.isRegularFile(report)) {
                reports.add(report);
            } else if (Files.isDirectory(directory.resolve("target/classes"))) {
                builtWithoutReport.add(module);
            } else {
                neverBuilt.add(module);
            }
        }
        return new ReportPopulation(List.copyOf(reports), List.copyOf(neverBuilt), List.copyOf(builtWithoutReport));
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
    private record ReportPopulation(List<Path> reports, List<String> neverBuilt, List<String> builtWithoutReport) {
        private void assertComplete() {
            if (neverBuilt.isEmpty() && builtWithoutReport.isEmpty()) {
                return;
            }
            int owed = reports.size() + neverBuilt.size() + builtWithoutReport.size();
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
            if (!builtWithoutReport.isEmpty()) {
                refusal.append(System.lineSeparator())
                        .append("  built but emitted no report: this is a regression in the module, not a ")
                        .append("mistaken invocation: ")
                        .append(String.join(", ", builtWithoutReport));
            }
            throw new AssertionError(refusal.toString());
        }
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
