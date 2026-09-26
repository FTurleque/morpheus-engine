package com.morpheus.architecture.m21;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * One scale of measurement, one set of thresholds, one piece of evidence that names itself.
 *
 * <p>Two gates measure coverage over different populations of lines: CoverageQualityGateTest sums each module's
 * own JaCoCo report, AggregateCoverageGateTest reads the canonical jacoco-aggregate report that also merges the
 * cross-module execution of the architecture tests. Until 09/09/2026 they read the same two ratchet keys and
 * wrote the same evidence file, so a threshold qualified over one grandeur was applied to the other and roughly
 * 6800 lines of coverage could disappear from the canonical scale without any gate reacting. The separation is
 * what this suite makes executable: a gate that reaches for the other scale's key, report or evidence file, or
 * a validator that concludes from whatever evidence it happens to find, fails here.</p>
 */
class CoverageScaleSeparationTest {
    private static final String PER_MODULE_GATE =
            "morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m21/CoverageQualityGateTest.java";
    private static final String AGGREGATE_GATE =
            "morpheus-coverage-report/src/test/java/com/morpheus/coverage/AggregateCoverageGateTest.java";
    private static final String PER_MODULE_EVIDENCE = "m21-per-module-coverage-summary.txt";
    private static final String AGGREGATE_EVIDENCE = "m21-aggregate-coverage-summary.txt";
    private static final String AGGREGATE_REPORT = "target/site/jacoco-aggregate/jacoco.xml";

    @Test
    void theRatchetConfigurationDeclaresBothScalesAndNoSharedCoverageKey() throws IOException {
        Properties ratchets = new Properties();
        try (var reader = Files.newBufferedReader(repoRoot().resolve("config/m21-quality-ratchets.properties"))) {
            ratchets.load(reader);
        }
        for (String key : List.of("testsMinimum", "architectureTestsMinimum",
                "perModuleLineCoverageMinimum", "perModuleBranchCoverageMinimum",
                "aggregateLineCoverageMinimum", "aggregateBranchCoverageMinimum")) {
            assertNotNull(ratchets.getProperty(key), () -> "missing M21 quality ratchet: " + key);
        }
        for (String legacy : List.of("lineCoverageMinimum", "branchCoverageMinimum")) {
            assertNull(ratchets.getProperty(legacy),
                    () -> "the scale-agnostic ratchet " + legacy + " is what let one threshold govern two "
                            + "grandeurs; declare perModule* and aggregate* instead");
        }
    }

    @Test
    void neitherCoverageGateReadsTheOtherScaleKeysReportOrEvidence() throws IOException {
        Path root = repoRoot();
        String perModuleGate = Files.readString(root.resolve(PER_MODULE_GATE));
        String aggregateGate = Files.readString(root.resolve(AGGREGATE_GATE));

        assertReads(perModuleGate, PER_MODULE_GATE, "perModuleLineCoverageMinimum", "perModuleBranchCoverageMinimum",
                PER_MODULE_EVIDENCE);
        assertIgnores(perModuleGate, PER_MODULE_GATE, "aggregateLineCoverageMinimum", "aggregateBranchCoverageMinimum",
                AGGREGATE_EVIDENCE, AGGREGATE_REPORT);

        assertReads(aggregateGate, AGGREGATE_GATE, "aggregateLineCoverageMinimum", "aggregateBranchCoverageMinimum",
                AGGREGATE_EVIDENCE, AGGREGATE_REPORT);
        assertIgnores(aggregateGate, AGGREGATE_GATE, "perModuleLineCoverageMinimum", "perModuleBranchCoverageMinimum",
                PER_MODULE_EVIDENCE);
    }

    @Test
    void eachGateStampsItsOwnScaleOnTheFirstLineOfItsEvidence() throws IOException {
        Path root = repoRoot();
        assertTrue(Files.readString(root.resolve(PER_MODULE_GATE)).contains("\"coverageScope=per-module%n"),
                "the per-module gate must open its evidence with the scope it measured");
        assertTrue(Files.readString(root.resolve(AGGREGATE_GATE)).contains("\"coverageScope=aggregate%n"),
                "the aggregate gate must open its evidence with the scope it measured");
    }

    @Test
    void bothM21ValidatorsCheckTheScopeBeforeReadingAnyRatio() throws IOException {
        Path root = repoRoot();
        for (Map.Entry<String, String> validator : Map.of(
                "scripts/validate-m21.sh", "lib/require-aggregate-coverage-evidence.sh",
                "scripts/validate-m21.ps1", "lib\\Require-AggregateCoverageEvidence.ps1").entrySet()) {
            String script = Files.readString(root.resolve(validator.getKey()));
            String page = validator.getKey();
            assertTrue(script.contains(AGGREGATE_EVIDENCE),
                    () -> page + " must consume the aggregate coverage evidence");
            assertFalse(script.contains(PER_MODULE_EVIDENCE),
                    () -> page + " concludes on the aggregate scale and must never read the per-module evidence");
            int guard = script.indexOf(validator.getValue());
            assertTrue(guard >= 0, () -> page + " must delegate to " + validator.getValue());
            int firstRatio = script.indexOf("lineRatio");
            assertTrue(firstRatio >= 0, () -> page + " must read the measured line ratio");
            assertTrue(guard < firstRatio,
                    () -> page + " reads a coverage ratio before checking which scale produced it");
            assertTrue(script.contains("coverageScope=aggregate"),
                    () -> page + " must record the scale it concluded on in its validation summary");
        }
    }

    /**
     * The structural checks above prove the guard is wired in; this one proves the guard actually refuses.
     * A rewrite that keeps the call but accepts any evidence passes every text assertion and fails here.
     */
    @Test
    void theScopeGuardRefusesPerModuleEvidenceAndAcceptsAggregateEvidence(@TempDir Path workspace) throws Exception {
        Path root = repoRoot();
        Path perModule = workspace.resolve("per-module-" + AGGREGATE_EVIDENCE);
        Files.writeString(perModule, "coverageScope=per-module\nlineRatio=0.625013\nbranchRatio=0.537997\n");
        Path aggregate = workspace.resolve(AGGREGATE_EVIDENCE);
        Files.writeString(aggregate, "coverageScope=aggregate\nlineRatio=0.857700\nbranchRatio=0.685500\n");
        Path unlabelled = workspace.resolve("unlabelled-" + AGGREGATE_EVIDENCE);
        Files.writeString(unlabelled, "lineRatio=0.857700\nbranchRatio=0.685500\n");
        Path absent = workspace.resolve("absent-" + AGGREGATE_EVIDENCE);

        List<Guard> guards = availableGuards(root);
        assertFalse(guards.isEmpty(), "no shell is available to execute the M21 coverage scope guard");
        for (Guard guard : guards) {
            assertNotEquals(0, guard.run(perModule),
                    () -> guard + " concluded from evidence produced on the per-module scale");
            assertNotEquals(0, guard.run(unlabelled),
                    () -> guard + " concluded from evidence that never declared its scale");
            assertNotEquals(0, guard.run(absent), () -> guard + " concluded from absent evidence");
            assertEquals(0, guard.run(aggregate), () -> guard + " rejected valid aggregate evidence");
        }
    }

    private static void assertReads(String source, String page, String... tokens) {
        for (String token : tokens) {
            assertTrue(source.contains(token), () -> page + " must read " + token);
        }
    }

    private static void assertIgnores(String source, String page, String... tokens) {
        for (String token : tokens) {
            assertFalse(source.contains(token),
                    () -> page + " reaches for " + token + ", which belongs to the other coverage scale");
        }
    }

    /**
     * Each platform executes the guard its own validator uses. A Windows run must not drive the POSIX guard:
     * the {@code bash} first on PATH there may be WSL's, which would resolve the drive-letter evidence path
     * inside another filesystem and refuse every case, including the valid one.
     */
    private static List<Guard> availableGuards(Path root) {
        Path posix = root.resolve("scripts/lib/require-aggregate-coverage-evidence.sh");
        Path windows = root.resolve("scripts/lib/Require-AggregateCoverageEvidence.ps1");
        assertTrue(Files.isRegularFile(posix), "the POSIX scope guard must exist: " + posix);
        assertTrue(Files.isRegularFile(windows), "the PowerShell scope guard must exist: " + windows);
        if (System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            return List.of(new Guard("powershell", List.of(
                    powershell(), "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", windows.toString(),
                    "-EvidencePath")));
        }
        return List.of(new Guard("bash", List.of("bash", posix.toString())));
    }

    /**
     * A build launched from a POSIX shell on Windows inherits a PATH that need not carry powershell.exe, so the
     * interpreter is addressed where Windows installs it and only falls back to PATH resolution.
     */
    private static String powershell() {
        String systemRoot = System.getenv("SystemRoot");
        if (systemRoot != null) {
            Path installed = Path.of(systemRoot, "System32", "WindowsPowerShell", "v1.0", "powershell.exe");
            if (Files.isRegularFile(installed)) {
                return installed.toString();
            }
        }
        return "powershell.exe";
    }

    private record Guard(String shell, List<String> command) {
        private int run(Path evidence) throws IOException, InterruptedException {
            List<String> invocation = new ArrayList<>(command);
            invocation.add(evidence.toString());
            Process process = new ProcessBuilder(invocation).redirectErrorStream(true).start();
            process.getInputStream().readAllBytes();
            assertTrue(process.waitFor(120, TimeUnit.SECONDS), () -> shell + " scope guard did not terminate");
            return process.exitValue();
        }

        @Override
        public String toString() {
            return "the " + shell + " scope guard";
        }
    }

    private static Path repoRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("config/m21-quality-ratchets.properties"))
                    && Files.isRegularFile(current.resolve("pom.xml"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("MORPHEUS repository root not found");
    }
}
