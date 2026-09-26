package com.morpheus.architecture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * A coverage ratio over an empty population is decided in one place: {@code QualityReportMetrics}.
 *
 * <p>The policy frontier used to hold the predicate in a private method, and the CLI published the 1.0 filler as a
 * measurement because it could not call it (CLI-4). The predicate is now public on the metrics record; a second copy
 * would drift, and the weaker copy is the one that publishes 1.0. The rule is textual -- the proposition is that a
 * comparison is not <em>written</em> elsewhere, which no dependency rule expresses (ADR-0103) -- and has two
 * halves:</p>
 *
 * <ol>
 *   <li>no main source compares {@code totalRequirements()} or {@code totalTasks()} with zero, and the name of the old
 *   private predicate does not come back;</li>
 *   <li>every main source outside {@code com.morpheus.application.quality} that reads a coverage ratio also reads its
 *   status.</li>
 * </ol>
 *
 * <p>What it does not see: an emptiness test written on another expression -- a list's {@code isEmpty()}, the sum of
 * linked and orphan requirements -- and a consumer that reads the status and then ignores it.</p>
 */
class CoverageRatioHasOneEmptyPopulationPredicateTest {

    private static final Pattern COMPARED_WITH_ZERO = Pattern.compile(
            "\\btotal(Requirements|Tasks)\\(\\)\\s*(==|<=|<|!=)\\s*0\\b"
                    + "|\\b0\\s*(==|>=|>|!=)\\s*[\\w.()]*\\btotal(Requirements|Tasks)\\(\\)");
    private static final Pattern OLD_PREDICATE = Pattern.compile("\\bemptyRatioPopulation\\b");
    private static final Map<String, String> STATUS_OF = Map.of(
            "requirementCoverageRatio()", "requirementCoverageStatus()",
            "taskCoverageRatio()", "taskCoverageStatus()");
    private static final String PRODUCER_PACKAGE = "package com.morpheus.application.quality;";

    @Test
    void theEmptyPopulationOfACoverageRatioIsDecidedOnlyByTheMetricsRecord() throws IOException {
        Map<String, String> sources = mainSources();
        String metrics = sources.entrySet().stream()
                .filter(entry -> entry.getKey().endsWith("/QualityReportMetrics.java"))
                .map(Map.Entry::getValue).findFirst().orElseThrow();
        assertTrue(metrics.contains("public CoverageRatioStatus requirementCoverageStatus()")
                && metrics.contains("public CoverageRatioStatus taskCoverageStatus()"),
                "QualityReportMetrics no longer declares the two statuses: the rule would be vacuous");

        List<String> violations = violations(sources);

        assertEquals(List.of(), violations, "a coverage ratio's empty population is decided outside QualityReportMetrics");
    }

    @Test
    void theRuleSeesACopyOfThePredicateAndARatioReadWithoutItsStatus() {
        String copy = "package com.morpheus.api;\nclass A {\n    boolean f(QualityReportMetrics m) {\n"
                + "        return m.totalTasks() == 0;\n    }\n}\n";
        String reversed = "package com.morpheus.api;\nclass A {\n    boolean f(QualityReportMetrics m) {\n"
                + "        return 0 == m.totalRequirements();\n    }\n}\n";
        String bareRatio = "package com.morpheus.cli;\nclass A {\n    String f(QualityReportMetrics m) {\n"
                + "        return \"coverage=\" + m.requirementCoverageRatio();\n    }\n}\n";
        String ratioWithStatus = "package com.morpheus.cli;\nclass A {\n    String f(QualityReportMetrics m) {\n"
                + "        return m.requirementCoverageStatus() + \"=\" + m.requirementCoverageRatio();\n    }\n}\n";
        String commented = "package com.morpheus.api;\nclass A {\n    // m.totalTasks() == 0 used to live here\n}\n";
        String producer = PRODUCER_PACKAGE + "\nrecord Q() {\n    double r() {\n"
                + "        return requirementCoverageRatio();\n    }\n}\n";

        assertEquals(List.of("A.java:4 compares a population with zero"), violations(Map.of("A.java", copy)));
        assertEquals(List.of("A.java:4 compares a population with zero"), violations(Map.of("A.java", reversed)));
        assertEquals(List.of("A.java reads requirementCoverageRatio() without requirementCoverageStatus()"),
                violations(Map.of("A.java", bareRatio)));
        assertEquals(List.of(), violations(Map.of("A.java", ratioWithStatus)));
        assertEquals(List.of(), violations(Map.of("A.java", commented)));
        assertEquals(List.of(), violations(Map.of("Q.java", producer)));
    }

    private static List<String> violations(Map<String, String> sources) {
        List<String> violations = new ArrayList<>();
        sources.forEach((name, raw) -> {
            String code = withoutComments(raw.replace("\r\n", "\n"));
            String shortName = name.substring(name.lastIndexOf('/') + 1);
            var comparison = COMPARED_WITH_ZERO.matcher(code);
            while (comparison.find()) {
                violations.add(shortName + ":" + line(code, comparison.start()) + " compares a population with zero");
            }
            var old = OLD_PREDICATE.matcher(code);
            while (old.find()) {
                violations.add(shortName + ":" + line(code, old.start()) + " brings back emptyRatioPopulation");
            }
            if (!code.contains(PRODUCER_PACKAGE)) {
                STATUS_OF.forEach((ratio, status) -> {
                    if (code.contains(ratio) && !code.contains(status)) {
                        violations.add(shortName + " reads " + ratio + " without " + status);
                    }
                });
            }
        });
        violations.sort(null);
        return violations;
    }

    private static String withoutComments(String source) {
        String noBlocks = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL).matcher(source)
                .replaceAll(match -> match.group().replaceAll("[^\n]", " "));
        return noBlocks.replaceAll("//[^\n]*", "");
    }

    private static int line(String code, int position) {
        return 1 + (int) code.substring(0, position).chars().filter(c -> c == '\n').count();
    }

    private static Map<String, String> mainSources() throws IOException {
        Path root = repoRoot();
        Map<String, String> sources = new TreeMap<>();
        try (Stream<Path> modules = Files.list(root)) {
            for (Path module : modules.filter(path -> path.getFileName().toString().startsWith("morpheus-")).toList()) {
                Path main = module.resolve("src/main/java");
                if (!Files.isDirectory(main)) {
                    continue;
                }
                try (Stream<Path> tree = Files.walk(main)) {
                    for (Path file : tree.filter(path -> path.toString().endsWith(".java")).toList()) {
                        sources.put(root.relativize(file).toString().replace('\\', '/'), Files.readString(file));
                    }
                }
            }
        }
        return sources;
    }

    private static Path repoRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        if (Files.isRegularFile(current.resolve("pom.xml")) && Files.isDirectory(current.resolve("distribution"))) {
            return current;
        }
        Path parent = current.getParent();
        if (parent != null && Files.isRegularFile(parent.resolve("pom.xml"))
                && Files.isDirectory(parent.resolve("distribution"))) {
            return parent;
        }
        throw new IllegalStateException("MORPHEUS repository root not found from " + current);
    }
}
