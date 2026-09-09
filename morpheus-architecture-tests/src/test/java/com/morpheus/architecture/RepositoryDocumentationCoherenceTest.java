package com.morpheus.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards active repository documentation and validation contracts against drifting from repository facts. */
class RepositoryDocumentationCoherenceTest {
    private static final Pattern PROJECT_VERSION = Pattern.compile("<version>([^<]+)</version>");
    private static final Pattern MCP_VERSION = Pattern.compile("<mcp-sdk\\.version>([^<]+)</mcp-sdk\\.version>");
    private static final Pattern MODULE_BLOCK = Pattern.compile("<modules>(.*?)</modules>", Pattern.DOTALL);
    private static final Pattern SCHEMA_VERSION_DECLARATION =
            Pattern.compile("SUPPORTED_SCHEMA_VERSION\\s*=\\s*(\\d+)");
    private static final Pattern SCHEMA_VERSION_LITERAL =
            Pattern.compile("SUPPORTED_SCHEMA_VERSION\\s*=\\s*\\d+");
    /**
     * A total that grows with the repository, stated on a page that describes the current contract:
     * "98 fichiers" ADR, "17 modules". The count belongs in a glob at answer time, not on the page.
     */
    private static final Pattern ADR_OR_MODULE_TOTAL = Pattern.compile(
            "(?i)\\b\\d{1,4}\\s+(?:fichiers?|ADRs?|modules?)\\b");
    /**
     * A schema version stated in prose on an active surface: "schema 15", "schéma V017", or the constant
     * followed by its value in words rather than by an assignment. Historical pages are never scanned with it.
     */
    private static final Pattern SCHEMA_VERSION_PROSE = Pattern.compile(
            "(?i)(?:sch[eé]ma|schema)\\s+(?:supported\\s+|support[eé]e?\\s+)?V?\\d+"
                    + "|SUPPORTED_SCHEMA_VERSION[^\\n]{0,40}?\\bV?\\d+");
    /**
     * A dependency version stated next to the name of the dependency it belongs to.
     *
     * <p>The window is short and digit-free on purpose: it catches the table cell, the aligned block and the
     * Maven coordinate an active page actually uses, without reaching across a sentence into an unrelated
     * number.</p>
     */
    private static final Pattern SQLITE_JDBC_MENTION =
            Pattern.compile("(?i)sqlite[- ]jdbc[^0-9\\n]{0,24}(\\d+(?:\\.\\d+){2,3})");
    private static final Pattern DEPENDENCY_CHECK_MENTION =
            Pattern.compile("(?i)dependency-check[^0-9\\n]{0,24}(\\d+(?:\\.\\d+){2})");
    private static final Pattern JACKSON_MENTION =
            Pattern.compile("(?i)jackson(?: bom)?[^0-9\\n]{0,24}(\\d+(?:\\.\\d+){2})");

    /**
     * Surfaces whose stack statement is about the current baseline rather than a dated one.
     *
     * <p>Deliberately excludes the D2 evidence blocks in {@code DOCUMENTATION_STATUS.md}, {@code ROADMAP.md} and
     * {@code validation/README.md}: those describe what was integrated at SHA {@code fa54b3d6}, and updating them
     * would falsify a record rather than refresh a claim.</p>
     */
    private static final List<String> CURRENT_STACK_SURFACES = List.of(
            "README.md",
            "docs/README.md",
            "docs/developer/README.md",
            "docs/developer/BUILD_AND_TEST.md",
            "docs/architecture/arc42/02-contraintes.md",
            "docs/architecture/arc42/04-strategie-solution.md",
            "docs/architecture/arc42/05-vue-blocs.md",
            "docs/architecture/arc42/08-concepts-transverses.md");

    private static final Pattern MODULE = Pattern.compile("<module>([^<]+)</module>");
    private static final Pattern DOCUMENTED_THRESHOLD = Pattern.compile(">=\\s*([0-9]+(?:[.,][0-9]+)?)");

    @Test
    void rootReadmeMatchesPomVersionMcpSdkAndModuleList() throws Exception {
        Path root = repositoryRoot();
        String pom = Files.readString(root.resolve("pom.xml"));
        String readme = Files.readString(root.resolve("README.md"));

        String version = firstGroup(PROJECT_VERSION, pom, "project version");
        String mcpVersion = firstGroup(MCP_VERSION, pom, "MCP SDK version");
        List<String> pomModules = pomModules(pom);

        assertTrue(readme.contains("Baseline développement " + version),
                () -> "README development baseline must match pom.xml version " + version);
        assertTrue(readme.contains("Java MCP SDK " + mcpVersion),
                () -> "README MCP SDK must match pom.xml version " + mcpVersion);
        assertEquals(pomModules, readmeModules(readme),
                "README Maven module list must exactly match root pom.xml <modules>");
    }

    @Test
    void activeDocumentationDeclaresCurrentDevelopmentBaseline() throws Exception {
        Path root = repositoryRoot();
        String version = firstGroup(PROJECT_VERSION, Files.readString(root.resolve("pom.xml")), "project version");
        for (Path page : activeStatusPages(root)) {
            String content = Files.readString(page);
            assertTrue(content.contains(version),
                    () -> root.relativize(page) + " must mention current development baseline " + version);
        }
    }

    @Test
    void activeStatusPagesDoNotAdvertiseCompletedD2AsPending() throws Exception {
        Path root = repositoryRoot();
        List<String> obsoleteMarkers = List.of(
                "D2 EN COURS",
                "D2 — Repository Hardening en cours",
                "PENDING LOCAL QUALIFICATION",
                "LOCAL QUALIFICATION PENDING",
                "#120 OPEN");

        for (Path page : activeStatusPages(root)) {
            String content = Files.readString(page);
            for (String obsolete : obsoleteMarkers) {
                assertFalse(content.contains(obsolete),
                        () -> root.relativize(page) + " still contains obsolete D2 marker: " + obsolete);
            }
        }
    }

    /**
     * Surfaces that state the current stack must state the version the root POM declares.
     *
     * <p>They had drifted: the POM carried {@code sqlite-jdbc 3.53.4.0} and Dependency-Check {@code 13.0.0} while
     * both D2 validators still asserted {@code 3.53.2.0} and {@code 12.2.2}. Those two are executable, so the
     * drift was not merely misleading -- {@code validate-d2} could not pass on {@code develop} at all, because it
     * required a token the POM no longer contains. The expected values are read from the POM rather than pinned
     * here, so this test cannot itself become the next stale copy.</p>
     *
     * <p><strong>A page is not the unit of currency; a block is.</strong> The first version of this rule scanned
     * whole files, and the sweep that satisfied it rewrote dated evidence: three D2 records tied to SHA
     * {@code fa54b3d6} were given today's versions, and two arc42 tables labelled "baseline 1.2.0" came out half
     * updated -- sqlite and Dependency-Check current, Jackson and the MCP SDK not -- which is worse than either
     * being stale. Only surfaces whose stack statement is unambiguously about now are scanned.</p>
     */
    @Test
    void activeStackDocumentationAndD2ValidatorsFollowTheRootPomDependencyVersions() throws Exception {
        Path root = repositoryRoot();
        String pom = Files.readString(root.resolve("pom.xml"));
        String sqlite = pomProperty(pom, "sqlite-jdbc.version");
        String dependencyCheck = pomProperty(pom, "dependency-check.maven.plugin.version");

        for (String validator : List.of("scripts/validate-d2.sh", "scripts/validate-d2.ps1")) {
            String script = Files.readString(root.resolve(validator));
            assertTrue(script.contains("<sqlite-jdbc.version>" + sqlite + "</sqlite-jdbc.version>"),
                    () -> validator + " asserts a sqlite-jdbc version the root POM no longer declares");
            assertTrue(script.contains(
                            "<dependency-check.maven.plugin.version>" + dependencyCheck
                                    + "</dependency-check.maven.plugin.version>"),
                    () -> validator + " asserts a Dependency-Check version the root POM no longer declares");
            assertTrue(script.contains("dependency-check-maven:" + dependencyCheck + ":aggregate"),
                    () -> validator + " invokes a Dependency-Check version the root POM no longer declares");
        }

        String jackson = pomProperty(pom, "jackson.version");
        for (String page : CURRENT_STACK_SURFACES) {
            String content = Files.readString(root.resolve(page));
            assertStatedVersion(page, content, SQLITE_JDBC_MENTION, sqlite, "sqlite-jdbc");
            assertStatedVersion(page, content, DEPENDENCY_CHECK_MENTION, dependencyCheck, "Dependency-Check");
            assertStatedVersion(page, content, JACKSON_MENTION, jackson, "Jackson");
        }
    }

    /**
     * A dated snapshot has to agree with itself.
     *
     * <p>The arc42 stack tables defer authority to {@code pom.xml} and describe "la baseline au moment de la
     * réconciliation documentaire", so reconciling them means moving every row at once. Half a reconciliation
     * produces a table that is true about two dependencies and false about five, and nothing in it says which
     * is which -- which is how this repository's documentation actually broke.</p>
     */
    @Test
    void theArc42StackTablesAreReconciledAsAWholeRatherThanRowByRow() throws Exception {
        Path root = repositoryRoot();
        String pom = Files.readString(root.resolve("pom.xml"));
        Map<String, String> reconciled = new HashMap<>();
        for (String property : List.of(
                "junit.version", "archunit.version", "mcp-sdk.version", "cyclonedx.maven.plugin.version")) {
            reconciled.put(property, pomProperty(pom, property));
        }

        for (String page : List.of(
                "docs/architecture/arc42/04-strategie-solution.md",
                "docs/architecture/arc42/08-concepts-transverses.md")) {
            String content = Files.readString(root.resolve(page));
            for (Map.Entry<String, String> row : reconciled.entrySet()) {
                assertTrue(content.contains(row.getValue()),
                        () -> page + " omits the reconciled " + row.getKey() + " " + row.getValue()
                                + "; the stack table moves as a whole or not at all");
            }
        }
    }

    private static void assertStatedVersion(
            String page, String content, Pattern mention, String expected, String label) {
        Matcher matcher = mention.matcher(content);
        while (matcher.find()) {
            String stated = matcher.group(1);
            assertEquals(expected, stated,
                    () -> page + " states " + label + " " + stated + " while the root POM declares " + expected);
        }
    }

    private static String pomProperty(String pom, String name) {
        Matcher matcher = Pattern.compile("<" + Pattern.quote(name) + ">([^<]+)</" + Pattern.quote(name) + ">")
                .matcher(pom);
        assertTrue(matcher.find(), () -> "the root POM must declare " + name);
        return matcher.group(1).trim();
    }

    @Test
    void bothPlatformValidatorsConsumeSingleQualityRatchetConfiguration() throws Exception {
        Path root = repositoryRoot();
        Path ratchetFile = root.resolve("config/m21-quality-ratchets.properties");
        Map<String, String> ratchets = properties(ratchetFile);
        // Pinned so that moving a ratchet is a deliberate act with evidence, never a side effect. The presence
        // ratchets were raised on 08/09/2026 from 1300/335 against exact-head measurements taken on BOTH
        // platforms at fix/audit-hardening-2026-09-08, 1560+ tests of which 390+ are architecture tests.
        //
        // The coverage ratchets are two pairs because two gates measure two different grandeurs, each with its
        // own qualified cap, and until 09/09/2026 they shared one pair of keys:
        //     per-module (CoverageQualityGateTest, sum of each module's own report)
        //         Windows  62.5328% / 62.5432% lines,  53.8092% / 53.8188% branches
        //         Linux    62.5083% / 62.5013% lines,  53.7997% / 53.7997% branches
        //     aggregate  (AggregateCoverageGateTest, canonical jacoco-aggregate report)
        //         cited in AggregateCoverageGateTest, which is where that cap lives
        // Each pair stays below its own cap rather than at it: two runs of one commit differed by two covered
        // lines, so pinning a ratchet to the measurement would make ordinary variation fail the build.
        assertEquals("1550", ratchets.get("testsMinimum"));
        assertEquals("385", ratchets.get("architectureTestsMinimum"));
        assertEquals("0.620", ratchets.get("perModuleLineCoverageMinimum"));
        assertEquals("0.535", ratchets.get("perModuleBranchCoverageMinimum"));
        assertEquals("0.620", ratchets.get("aggregateLineCoverageMinimum"));
        assertEquals("0.535", ratchets.get("aggregateBranchCoverageMinimum"));

        String linux = Files.readString(root.resolve("scripts/validate-m21.sh"));
        String windows = Files.readString(root.resolve("scripts/validate-m21.ps1"));
        assertTrue(linux.contains("config/m21-quality-ratchets.properties"));
        assertTrue(windows.contains("config\\m21-quality-ratchets.properties"));
        // Both validators conclude on the aggregate scale, so both must read the aggregate keys and neither
        // may fall back to a scale-agnostic one.
        for (String script : List.of(linux, windows)) {
            assertTrue(script.contains("aggregateLineCoverageMinimum"));
            assertTrue(script.contains("aggregateBranchCoverageMinimum"));
            assertFalse(script.contains("read_ratchet lineCoverageMinimum"));
            assertFalse(script.contains("$values.lineCoverageMinimum"));
        }
        assertFalse(linux.contains("line < 0.506"), "Linux validator must not retain the old embedded line ratchet");
        assertFalse(windows.contains("-lt 0.506"), "Windows validator must not retain the old embedded line ratchet");
    }

    @Test
    void activeRiskDocumentationMatchesExecutableQualityRatchetsAndResolvedAdrIndex() throws Exception {
        Path root = repositoryRoot();
        Map<String, String> ratchets = properties(root.resolve("config/m21-quality-ratchets.properties"));
        String expectedRatchets = """
                Surefire total          >= %s
                architecture            >= %s
                aggregate line          >= %s
                aggregate branch        >= %s
                per-module line         >= %s
                per-module branch       >= %s
                changed-line            >= 80%%
                changed-branch          >= 70%%
                """.formatted(
                ratchets.get("testsMinimum"),
                ratchets.get("architectureTestsMinimum"),
                percentage(ratchets.get("aggregateLineCoverageMinimum")),
                percentage(ratchets.get("aggregateBranchCoverageMinimum")),
                percentage(ratchets.get("perModuleLineCoverageMinimum")),
                percentage(ratchets.get("perModuleBranchCoverageMinimum")));

        for (Path page : List.of(
                root.resolve("docs/architecture/arc42/11-risques-dette.md"),
                root.resolve("docs/architecture/risks/register.md"))) {
            String content = Files.readString(page).replace("\r\n", "\n");
            assertTrue(content.contains(expectedRatchets),
                    () -> root.relativize(page) + " must mirror executable M21 quality ratchets");
        }

        String arc42 = Files.readString(root.resolve("docs/architecture/arc42/11-risques-dette.md"));
        String adrIndex = Files.readString(root.resolve("docs/adr/README.md"));
        assertTrue(adrIndex.contains("[ADR-0096](0096-conservative-native-mcp-client"),
                "ADR index must retain the accepted ADR-0096 entry");
        assertFalse(arc42.contains("| DT-02 |"),
                "active debt register must not advertise the already-resolved ADR-0096 index drift");
    }

    @Test
    void operatorFacingQualityGateDocumentationMirrorsTheNormativeRatchets() throws Exception {
        Path root = repositoryRoot();
        Map<String, String> ratchets = properties(root.resolve("config/m21-quality-ratchets.properties"));
        String tests = ratchets.get("testsMinimum");
        String architecture = ratchets.get("architectureTestsMinimum");
        String aggregateLine = decimalPercentage(ratchets.get("aggregateLineCoverageMinimum"));
        String aggregateBranch = decimalPercentage(ratchets.get("aggregateBranchCoverageMinimum"));
        String perModuleLine = decimalPercentage(ratchets.get("perModuleLineCoverageMinimum"));
        String perModuleBranch = decimalPercentage(ratchets.get("perModuleBranchCoverageMinimum"));

        assertLabelledThresholds(root, "docs/developer/BUILD_AND_TEST.md", Map.of(
                "baseline Surefire totale", tests,
                "baseline architecture", architecture,
                "JaCoCo aggregate line ratchet", aggregateLine,
                "JaCoCo aggregate branch ratchet", aggregateBranch,
                "JaCoCo per-module line ratchet", perModuleLine,
                "JaCoCo per-module branch ratchet", perModuleBranch));
        assertLabelledThresholds(root, "docs/developer/PRODUCTION_INTEGRITY.md", Map.of(
                "Tests ", tests,
                "Architecture ", architecture,
                "JaCoCo aggregate lines", aggregateLine,
                "JaCoCo aggregate branches", aggregateBranch,
                "JaCoCo per-module lines", perModuleLine,
                "JaCoCo per-module branches", perModuleBranch));
        assertLabelledThresholds(root, "docs/README.md", Map.of(
                "Surefire total", tests,
                "architecture tests", architecture,
                "JaCoCo aggregate lines", aggregateLine,
                "JaCoCo aggregate branches", aggregateBranch,
                "JaCoCo per-module lines", perModuleLine,
                "JaCoCo per-module branches", perModuleBranch));
        // The operator surfaces an engineer actually opens before running a gate. Each of these still announced
        // the pre-1.2.1 ratchets, so four different numbers were in circulation for one executable threshold.
        // Each now has to name the scale as well as the number: one figure standing alone was exactly how a
        // per-module threshold came to be read as governing the canonical measurement.
        assertLabelledThresholds(root, "scripts/README.md", Map.of(
                "Surefire total", tests,
                "architecture ", architecture,
                "aggregate line coverage", aggregateLine,
                "aggregate branch coverage", aggregateBranch,
                "per-module line coverage", perModuleLine,
                "per-module branch coverage", perModuleBranch));
        assertLabelledThresholds(root, "distribution/README.md", Map.of(
                "Surefire total", tests,
                "architecture ", architecture,
                "aggregate line coverage", aggregateLine,
                "aggregate branch coverage", aggregateBranch,
                "per-module line coverage", perModuleLine,
                "per-module branch coverage", perModuleBranch));
        assertLabelledThresholds(root, "docs/developer/README.md", Map.of(
                "Surefire floor", tests,
                "Architecture floor", architecture,
                "JaCoCo aggregate line ratchet", aggregateLine,
                "JaCoCo aggregate branch ratchet", aggregateBranch,
                "JaCoCo per-module line ratchet", perModuleLine,
                "JaCoCo per-module branch ratchet", perModuleBranch));
        assertLabelledThresholds(root, "docs/governance/DOCUMENTATION_STATUS.md", Map.of(
                "Surefire ratchet", tests,
                "Architecture ratchet", architecture,
                "Aggregate line ratchet", aggregateLine,
                "Aggregate branch ratchet", aggregateBranch,
                "Per-module line ratchet", perModuleLine,
                "Per-module branch ratchet", perModuleBranch));
        assertLabelledThresholds(root, "docs/governance/ROADMAP.md", Map.of(
                "Surefire ratchet", tests,
                "architecture ratchet", architecture,
                "aggregate line ratchet", aggregateLine,
                "aggregate branch ratchet", aggregateBranch,
                "per-module line ratchet", perModuleLine,
                "per-module branch ratchet", perModuleBranch));

        String readme = Files.readString(root.resolve("README.md"));
        String readmeClaim = ("Le ratchet agrégé est **≥ %s %% lignes / ≥ %s %% branches**, le ratchet par module"
                + " **≥ %s %% lignes / ≥ %s %% branches**, avec **≥ %s tests Surefire** et"
                + " **≥ %s tests d’architecture**")
                .formatted(french(aggregateLine), french(aggregateBranch),
                        french(perModuleLine), french(perModuleBranch), tests, architecture);
        assertTrue(readme.contains(readmeClaim),
                () -> "README.md must state the normative M21 ratchets: " + readmeClaim);

        String buildAndTest = Files.readString(root.resolve("docs/developer/BUILD_AND_TEST.md"));
        String lockedBaseline = "**%s%% lignes / %s%% branches** sur l'échelle agrégée et **%s%% lignes / %s%% branches** par module"
                .formatted(french(aggregateLine), french(aggregateBranch), french(perModuleLine), french(perModuleBranch));
        assertTrue(buildAndTest.contains("verrouillée à " + lockedBaseline),
                () -> "BUILD_AND_TEST.md locked baseline must be " + lockedBaseline);
        assertTrue(buildAndTest.contains("une baisse sous %s%% lignes ou %s%% branches agrégées, ou sous %s%% lignes ou %s%% branches par module"
                        .formatted(french(aggregateLine), french(aggregateBranch), french(perModuleLine), french(perModuleBranch))),
                "BUILD_AND_TEST.md regression rule must quote the normative coverage ratchets of both scales");
    }

    /**
     * The D2 half of the operator guide has no properties file behind it: both validators carry the baseline as
     * a literal, and D2RepositoryHardeningArchitectureTest pins those literals. The guide announced the M21
     * ratchets instead, so an operator reading it expected D2 to refuse a build that D2 accepts.
     */
    @Test
    void operatorFacingD2GateDocumentationMirrorsWhatTheD2ValidatorsEnforce() throws Exception {
        Path root = repositoryRoot();
        String linux = Files.readString(root.resolve("scripts/validate-d2.sh"));
        String windows = Files.readString(root.resolve("scripts/validate-d2.ps1"));

        String tests = onlyGroup(Pattern.compile("\\(\\( TESTS < (\\d+) \\)\\)"), linux, "D2 Surefire baseline");
        String architecture = onlyGroup(Pattern.compile("\\(\\( ARCH_TESTS < (\\d+) \\)\\)"), linux, "D2 architecture baseline");
        String lineFloor = onlyGroup(Pattern.compile("if line < (0\\.\\d+):"), linux, "D2 line coverage floor");
        String branchFloor = onlyGroup(Pattern.compile("if branch < (0\\.\\d+):"), linux, "D2 branch coverage floor");

        assertTrue(windows.contains("$tests -lt " + tests),
                "the Windows D2 validator must enforce the same Surefire baseline as the Linux one");
        assertTrue(windows.contains("$architectureTests -lt " + architecture),
                "the Windows D2 validator must enforce the same architecture baseline as the Linux one");
        assertTrue(windows.contains("$lineCoverage -lt " + lineFloor),
                "the Windows D2 validator must enforce the same line coverage floor as the Linux one");
        assertTrue(windows.contains("$branchCoverage -lt " + branchFloor),
                "the Windows D2 validator must enforce the same branch coverage floor as the Linux one");

        assertLabelledThresholds(root, "scripts/README.md", Map.of(
                "baseline Surefire", tests,
                "baseline architecture", architecture,
                "absolute line floor", decimalPercentage(lineFloor),
                "absolute branch floor", decimalPercentage(branchFloor)));
    }

    @Test
    void activeGovernanceDocumentationNeverPinsTheSqliteSchemaVersion() throws Exception {
        Path root = repositoryRoot();
        String manager = Files.readString(root.resolve(
                "morpheus-store-sqlite/src/main/java/com/morpheus/store/sqlite/SqliteSchemaManager.java"));
        Matcher declaration = SCHEMA_VERSION_DECLARATION.matcher(manager);
        assertTrue(declaration.find(), "SqliteSchemaManager must declare SUPPORTED_SCHEMA_VERSION");

        String maintenance = Files.readString(root.resolve(
                "morpheus-store-sqlite/src/main/java/com/morpheus/store/sqlite/SqliteServerMaintenance.java"));
        assertTrue(maintenance.contains("SqliteSchemaManager.SUPPORTED_SCHEMA_VERSION"),
                "maintenance must consume the declared constant rather than restate the version");

        // Active governance surfaces describe the *current* contract, so a literal here silently rots.
        // Historical snapshots under docs/validation and docs/roadmap legitimately record past versions.
        for (Path page : activeGovernanceSurfaces(root)) {
            if (!Files.isRegularFile(page)) {
                continue;
            }
            String content = Files.readString(page);
            Matcher pinned = SCHEMA_VERSION_LITERAL.matcher(content);
            assertFalse(pinned.find(),
                    () -> root.relativize(page) + " pins SUPPORTED_SCHEMA_VERSION to a literal; derive it from "
                            + "SqliteSchemaManager instead so the check cannot describe a stale contract");

            // The assignment form is only one way to state the version. An active surface that *describes* the
            // current schema in prose - "schema 15" in a report template, "constate a 17" in a rule - rots the
            // same way while sailing past a check that only looks for the constant.
            Matcher prose = SCHEMA_VERSION_PROSE.matcher(content);
            assertFalse(prose.find(),
                    () -> root.relativize(page) + " states a current SQLite schema version in prose ("
                            + describeFirstMatch(SCHEMA_VERSION_PROSE, content)
                            + "); active surfaces must read SqliteSchemaManager.SUPPORTED_SCHEMA_VERSION instead");
        }
    }

    /**
     * Active governance surfaces must not state a total that changes when the repository grows.
     *
     * <p>{@code rules/meta.md} says never to recopy a perishable total, yet the ADR rule recopied one, and it had
     * rotted into an ambiguity: "98 fichiers" counted 97 numbered ADRs plus the directory's own README, so a
     * reader following it would have cited 98 ADRs. The instruction to count is what belongs on these pages.</p>
     */
    @Test
    void activeGovernanceSurfacesDoNotRestateAPerishableAdrOrModuleTotal() throws Exception {
        Path root = repositoryRoot();
        long numberedAdrs;
        try (var files = Files.list(root.resolve("docs/adr"))) {
            numberedAdrs = files.filter(path -> path.getFileName().toString().matches("\\d{4}-.*\\.md")).count();
        }
        assertTrue(numberedAdrs > 0, "the ADR directory must contain numbered decision records");

        for (Path page : activeGovernanceSurfaces(root)) {
            if (!Files.isRegularFile(page)) {
                continue;
            }
            String content = Files.readString(page);
            assertFalse(ADR_OR_MODULE_TOTAL.matcher(content).find(),
                    () -> root.relativize(page) + " restates a perishable total (\""
                            + describeFirstMatch(ADR_OR_MODULE_TOTAL, content)
                            + "\"); count docs/adr/0*.md or pom.xml <module> entries instead");
        }
    }

    /**
     * The developer platform guide describes the current contract, so it must not pin the runtime's maximum
     * schema version. It said the maximum was V016 and that anything above 16 is refused, while the manager had
     * moved to 17 -- a reader following it would have concluded a valid database was rejected.
     */
    @Test
    void theRemotePlatformGuideDerivesTheSchemaCeilingFromTheDeclaringConstant() throws Exception {
        Path root = repositoryRoot();
        String guide = Files.readString(root.resolve("docs/developer/REMOTE_SERVER_PLATFORM.md"));

        int section = guide.indexOf("## Schéma SQLite");
        assertTrue(section >= 0, "the guide must keep its SQLite schema section");
        String schemaSection = guide.substring(section, Math.min(guide.length(), section + 2000));

        assertTrue(schemaSection.contains("SqliteSchemaManager.SUPPORTED_SCHEMA_VERSION"),
                "the documented ceiling must point at the constant that declares it");
        assertFalse(Pattern.compile("version maximale supportée par le runtime\\s*:\\s*\\*\\*V?\\d+")
                        .matcher(schemaSection).find(),
                "the guide must not restate the runtime's maximum schema version as a literal");
    }

    /**
     * The guide must not describe a failure type that no longer exists.
     *
     * <p>It documented the transaction runner as throwing {@code SqliteCommittedTransactionException} on a
     * post-commit cleanup failure. Nothing threw it -- the runner retries, recovers the scope or quarantines the
     * connection, and reports the mutation as the success it durably is. A reader would have written a catch
     * block for a case that never arrives, and missed the one that does: the next transaction being refused.</p>
     */
    @Test
    void theRemotePlatformGuideOnlyNamesFailureTypesThatExist() throws Exception {
        Path root = repositoryRoot();
        String guide = Files.readString(root.resolve("docs/developer/REMOTE_SERVER_PLATFORM.md"));
        Path sqliteSources = root.resolve("morpheus-store-sqlite/src/main/java/com/morpheus/store/sqlite");

        Matcher named = Pattern.compile("`(Sqlite[A-Za-z]*Exception)`").matcher(guide);
        while (named.find()) {
            String type = named.group(1);
            assertTrue(Files.isRegularFile(sqliteSources.resolve(type + ".java")),
                    () -> "REMOTE_SERVER_PLATFORM.md documents " + type + ", which no longer exists");
        }
    }

    /**
     * The audit command must tell its reader where the normative value lives, not just avoid restating it.
     * A template with an empty placeholder and no instruction invites the next reader to fill in a remembered
     * number, which is how the stale "schema 15" line survived the previous correction.
     */
    @Test
    void theSecurityAuditCommandDerivesTheSqliteSchemaVersionFromTheDeclaringConstant() throws Exception {
        Path root = repositoryRoot();
        String command = Files.readString(root.resolve(".claude/commands/security-audit.md"));

        assertTrue(command.contains("SUPPORTED_SCHEMA_VERSION"),
                "the audit command must point at the declaring constant");
        assertTrue(command.contains("SqliteSchemaManager.java"),
                "the audit command must name the file that declares the normative version");
        assertTrue(command.contains("SqliteSchemaManager>"),
                "the audit report template must carry a placeholder rather than a version number");
    }

    @Test
    void rootBuildEnforcerPinsQualifiedJavaAndDependencyConvergence() throws Exception {
        String pom = Files.readString(repositoryRoot().resolve("pom.xml")).replace("\r\n", "\n");
        assertTrue(pom.contains("<requireJavaVersion>\n                                    <version>[21,22)</version>\n                                </requireJavaVersion>"),
                "root Maven enforcer must reject JDKs newer than the qualified Java 21 line");
        assertTrue(pom.contains("<dependencyConvergence/>"),
                "root Maven enforcer must reject divergent transitive dependency versions");
    }

    @Test
    void httpExtensionRoutesUseSharedTimedRequestBodyBoundary() throws Exception {
        Path root = repositoryRoot();
        Path api = root.resolve("morpheus-api/src/main/java/com/morpheus/api");
        List<String> routes = List.of(
                "MorpheusQueryHttpRoutes.java",
                "MorpheusPolicyHttpRoutes.java",
                "MorpheusPolicyManagementHttpRoutes.java",
                "MorpheusReasoningHttpRoutes.java");

        for (String route : routes) {
            String content = Files.readString(api.resolve(route));
            assertTrue(content.contains("HttpRequestBodyReader.read(exchange)"),
                    () -> route + " must use the shared timed request-body boundary");
            assertFalse(content.contains("getRequestBody().readNBytes("),
                    () -> route + " must not perform direct wall-clock-unbounded request-body reads");
        }

        String reader = Files.readString(api.resolve("HttpRequestBodyReader.java"));
        assertTrue(reader.contains("TimedBoundedInputReader.read("),
                "shared extension-route body reader must delegate to the deadline-aware primitive");
    }

    @Test
    void repositoryPublishesSecurityPolicyAndSensitiveCodeOwnership() throws Exception {
        Path root = repositoryRoot();
        String security = Files.readString(root.resolve("SECURITY.md"));
        String codeowners = Files.readString(root.resolve(".github/CODEOWNERS"));

        assertTrue(security.contains("Reporting a vulnerability"));
        assertTrue(security.contains("not an operating-system sandbox"));
        assertTrue(security.contains("Unknown remote routes are denied"));
        assertTrue(codeowners.contains("* @FTurleque"));
        assertTrue(codeowners.contains("/morpheus-api/ @FTurleque"));
        assertTrue(codeowners.contains("/morpheus-provider-sdk/ @FTurleque"));
        assertTrue(codeowners.contains("/morpheus-mcp-transport/ @FTurleque"));
        assertTrue(codeowners.contains("/morpheus-store-sqlite/ @FTurleque"));
    }

    @Test
    void remoteAuthorizationAndCredentialDocumentationStayFailClosedAndExpiryAware() throws Exception {
        Path root = repositoryRoot();
        String routePolicy = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusRemoteRoutePolicy.java"));
        String remoteServer = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusRemoteHttpServer.java"));
        String userGuide = Files.readString(root.resolve("docs/user/TEAM_REMOTE_SERVER.md"));
        String developerGuide = Files.readString(root.resolve("docs/developer/REMOTE_SERVER_PLATFORM.md"));

        assertTrue(routePolicy.contains("private static final List<RouteRule> ROUTES"));
        assertTrue(routePolicy.contains("unknown remote API path"));
        assertFalse(routePolicy.contains("method.equals(\"GET\") || method.equals(\"HEAD\")"),
                "remote authorization must not infer READ authority from GET/HEAD");
        assertTrue(remoteServer.contains("at least one active ADMIN identity"));
        assertTrue(remoteServer.contains("identity.isActiveAt(now)"));

        assertTrue(userGuide.contains("table exhaustive `(méthode HTTP, route) -> rôle minimum`"));
        assertTrue(userGuide.contains("principal|role|sha256(token)[|expiresAt]"));
        assertTrue(userGuide.contains("--expires-at never"));

        assertTrue(developerGuide.contains("table exhaustive `(méthode HTTP, route) -> rôle minimum`"));
        assertTrue(developerGuide.contains("principal|role|sha256(token)[|expiresAt]"));
        assertTrue(developerGuide.contains("ADMIN` **active à l'instant du démarrage**"));
        assertTrue(developerGuide.contains("70 % des branches modifiées"));
        assertTrue(developerGuide.contains("ne constituent pas une sandbox du système d'exploitation"));
        assertFalse(developerGuide.contains("`GET`/`HEAD` : READ"),
                "developer guide must not reintroduce the obsolete verb-derived RBAC contract");
    }

    /** Reads a threshold a validator states exactly once, so a second statement of it cannot go unnoticed. */
    private static String onlyGroup(Pattern pattern, String script, String label) {
        Matcher matcher = pattern.matcher(script);
        assertTrue(matcher.find(), () -> "cannot find " + label + " in the D2 validator");
        String value = matcher.group(1);
        assertFalse(matcher.find(), () -> label + " is stated more than once in the D2 validator");
        return value;
    }

    /** Quotes the offending text so a failure names what to remove instead of only where to look. */
    private static String describeFirstMatch(Pattern pattern, String content) {
        Matcher matcher = pattern.matcher(content);
        return matcher.find() ? matcher.group().strip() : "";
    }

    private static void assertLabelledThresholds(Path root, String page, Map<String, String> expected) throws IOException {
        String content = Files.readString(root.resolve(page)).replace("\r\n", "\n");
        for (Map.Entry<String, String> entry : expected.entrySet()) {
            List<String> observed = labelledThresholds(content, entry.getKey());
            assertFalse(observed.isEmpty(),
                    () -> page + " must document the \"" + entry.getKey().strip() + "\" quality ratchet");
            for (String value : observed) {
                assertEquals(entry.getValue(), value,
                        () -> page + " documents a stale \"" + entry.getKey().strip()
                                + "\" ratchet; config/m21-quality-ratchets.properties is normative");
            }
        }
    }

    private static List<String> labelledThresholds(String content, String label) {
        List<String> values = new ArrayList<>();
        for (String raw : content.split("\n")) {
            String candidate = raw.strip();
            if (!candidate.startsWith(label.strip()) || !candidate.contains(">=")) {
                continue;
            }
            Matcher matcher = DOCUMENTED_THRESHOLD.matcher(candidate);
            if (matcher.find()) {
                values.add(matcher.group(1).replace(',', '.'));
            }
        }
        return values;
    }

    /** Renders a ratchet ratio the way the gate blocks do, e.g. 0.520 -> "52.0". */
    private static String decimalPercentage(String decimal) {
        return String.format(Locale.ROOT, "%.1f", Double.parseDouble(decimal) * 100.0d);
    }

    private static String french(String decimal) {
        return decimal.replace('.', ',');
    }

    private static List<Path> activeGovernanceSurfaces(Path root) throws IOException {
        List<Path> surfaces = new ArrayList<>();
        // The two always-loaded entry points belong here too: they are the surfaces an agent reads first, so a
        // stale fact on them propagates furthest.
        for (String page : List.of(".claude/CLAUDE.md", ".github/copilot-instructions.md")) {
            Path file = root.resolve(page);
            if (Files.isRegularFile(file)) {
                surfaces.add(file);
            }
        }
        for (String directory : List.of(".claude/commands", ".claude/agents", ".claude/rules", ".github/prompts",
                ".github/instructions")) {
            Path base = root.resolve(directory);
            if (!Files.isDirectory(base)) {
                continue;
            }
            try (var entries = Files.list(base)) {
                entries.filter(path -> path.toString().endsWith(".md")).sorted().forEach(surfaces::add);
            }
        }
        return surfaces;
    }

    private static List<Path> activeStatusPages(Path root) {
        return List.of(
                root.resolve("README.md"),
                root.resolve("docs/README.md"),
                root.resolve("docs/user/README.md"),
                root.resolve("docs/developer/README.md"),
                root.resolve("docs/developer/BUILD_AND_TEST.md"),
                root.resolve("docs/governance/ROADMAP.md"),
                root.resolve("docs/governance/DOCUMENTATION_STATUS.md"),
                root.resolve("docs/validation/README.md"));
    }

    private static String percentage(String decimal) {
        return String.format(Locale.ROOT, "%.1f%%", Double.parseDouble(decimal) * 100.0d);
    }

    private static Map<String, String> properties(Path path) throws IOException {
        Map<String, String> result = new HashMap<>();
        for (String raw : Files.readAllLines(path)) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) continue;
            int separator = line.indexOf('=');
            if (separator <= 0 || separator == line.length() - 1) {
                throw new IllegalArgumentException("invalid property in " + path + ": " + line);
            }
            result.put(line.substring(0, separator).trim(), line.substring(separator + 1).trim());
        }
        return Map.copyOf(result);
    }

    private static Path repositoryRoot() throws IOException {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            Path pom = current.resolve("pom.xml");
            if (Files.isRegularFile(pom)) {
                String content = Files.readString(pom);
                if (content.contains("<artifactId>morpheus-engine</artifactId>")
                        && content.contains("<modules>")) {
                    return current;
                }
            }
            current = current.getParent();
        }
        throw new IOException("cannot locate MORPHEUS repository root from " + Path.of("").toAbsolutePath());
    }

    private static String firstGroup(Pattern pattern, String text, String label) {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            throw new IllegalArgumentException("cannot find " + label + " in root pom.xml");
        }
        return matcher.group(1).trim();
    }

    private static List<String> pomModules(String pom) {
        Matcher block = MODULE_BLOCK.matcher(pom);
        if (!block.find()) {
            throw new IllegalArgumentException("root pom.xml has no <modules> block");
        }
        Matcher module = MODULE.matcher(block.group(1));
        java.util.ArrayList<String> result = new java.util.ArrayList<>();
        while (module.find()) {
            result.add(module.group(1).trim());
        }
        return List.copyOf(result);
    }

    private static List<String> readmeModules(String readme) {
        int marker = readme.indexOf("Modules Maven :");
        if (marker < 0) throw new IllegalArgumentException("README has no 'Modules Maven :' section");
        int fence = readme.indexOf("```text", marker);
        if (fence < 0) throw new IllegalArgumentException("README module section has no text fence");
        int start = readme.indexOf('\n', fence);
        int end = readme.indexOf("```", start + 1);
        if (start < 0 || end < 0) throw new IllegalArgumentException("README module fence is incomplete");
        return readme.substring(start + 1, end).lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .toList();
    }
}
