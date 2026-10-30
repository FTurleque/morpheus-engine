package com.morpheus.architecture;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
            "CONTRIBUTING.md",
            "docs/README.md",
            "docs/developer/README.md",
            "docs/developer/BUILD_AND_TEST.md",
            "docs/architecture/arc42/02-contraintes.md",
            "docs/architecture/arc42/04-strategie-solution.md",
            "docs/architecture/arc42/05-vue-blocs.md",
            "docs/architecture/arc42/08-concepts-transverses.md");

    private static final Pattern MODULE = Pattern.compile("<module>([^<]+)</module>");
    private static final Pattern DOCUMENTED_THRESHOLD = Pattern.compile(">=\\s*([0-9]+(?:[.,][0-9]+)?)");

    /** An inline Markdown link or image, {@code [text](target)} or {@code [text](target "title")}. */
    private static final Pattern MARKDOWN_LINK = Pattern.compile("\\[[^\\]]*\\]\\(([^)\\s]+)(?:\\s+\"[^\"]*\")?\\)");
    private static final Pattern FENCED_CODE = Pattern.compile("```.*?```", Pattern.DOTALL);
    private static final Pattern INLINE_CODE = Pattern.compile("`[^`\\n]*`");
    private static final Pattern URI_SCHEME = Pattern.compile("^[A-Za-z][A-Za-z0-9+.-]*:");
    /** Trees the build and the validators generate, which .gitignore keeps out of the repository. */
    private static final Set<String> GENERATED_TREES = Set.of(".git", "target", "dist", "validation-output");

    /**
     * Every relative Markdown link resolves, on a current guide and on a dated record alike.
     *
     * <p>This is the one property that holds for the whole corpus without sorting it into live and frozen pages:
     * it asks no page to be current and freezes nothing, and a dated validation record whose evidence link no
     * longer resolves has lost that evidence as surely as a guide has. A link is resolved from the directory of
     * the page that holds it; resolving from the repository root turns every {@code ../} into a false positive.</p>
     */
    @Test
    void everyRelativeMarkdownLinkResolvesFromThePageThatHoldsIt() throws Exception {
        List<String> broken = brokenMarkdownLinks(RepositoryRoot.locate());
        assertTrue(broken.isEmpty(), () -> broken.size() + " relative Markdown link(s) do not resolve from their page:"
                + System.lineSeparator() + String.join(System.lineSeparator(), broken));
    }

    /**
     * An empty corpus would satisfy the rule above as well. This proves it refuses a moved target, names the
     * page and the target, and leaves alone what is not a link to a file.
     */
    @Test
    void theLinkRuleRefusesAMovedTargetAndIgnoresWhatIsNotAFileLink(@TempDir Path corpus) throws Exception {
        Files.createDirectories(corpus.resolve("docs/guide"));
        Files.createDirectories(corpus.resolve("docs/validation"));
        Files.writeString(corpus.resolve("docs/validation/VALIDATION_M1.md"), "# M1\n");
        Files.writeString(corpus.resolve("docs/guide/page.md"), """
                [resolves from its page, not from the root](../validation/VALIDATION_M1.md#preuve)
                ![image with a title](../validation/VALIDATION_M1.md "M1")
                [web](https://example.test/missing.md) [mail](mailto:someone@example.test) [anchor](#section)
                [prose shorthand](morpheus-api/.../Missing.java)
                `[inline code](missing-inline.md)`
                ```text
                [fenced](missing-fenced.md)
                ```
                [moved into a subdirectory](../VALIDATION_M1.md)
                """);
        Files.createDirectories(corpus.resolve("module/target"));
        Files.writeString(corpus.resolve("module/target/generated.md"), "[generated](nowhere.md)\n");

        assertEquals(List.of("docs/guide/page.md -> ../VALIDATION_M1.md"), brokenMarkdownLinks(corpus),
                "only the moved target may be refused, named by its page and its target as written");
    }

    private static List<String> brokenMarkdownLinks(Path root) throws IOException {
        List<Path> pages = new ArrayList<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                return !directory.equals(root) && GENERATED_TREES.contains(directory.getFileName().toString())
                        ? FileVisitResult.SKIP_SUBTREE
                        : FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                if (attributes.isRegularFile() && file.getFileName().toString().endsWith(".md")) {
                    pages.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });
        pages.sort(null);

        List<String> broken = new ArrayList<>();
        for (Path page : pages) {
            String prose = INLINE_CODE.matcher(FENCED_CODE.matcher(Files.readString(page)).replaceAll("")).replaceAll("");
            Matcher link = MARKDOWN_LINK.matcher(prose);
            while (link.find()) {
                String target = link.group(1);
                if (URI_SCHEME.matcher(target).find() || target.startsWith("#") || target.contains("...")) {
                    continue;
                }
                String file = target.replaceFirst("[#?].*$", "");
                if (!file.isEmpty() && !resolves(root, page, file)) {
                    broken.add(root.relativize(page).toString().replace('\\', '/') + " -> " + target);
                }
            }
        }
        return broken;
    }

    private static boolean resolves(Path root, Path page, String file) {
        try {
            Path base = file.startsWith("/") ? root : page.getParent();
            return Files.exists(base.resolve(file.startsWith("/") ? file.substring(1) : file).normalize());
        } catch (InvalidPathException unrepresentable) {
            return false;
        }
    }

    @Test
    void contributingGuideMatchesPomVersionMcpSdkAndModuleList() throws Exception {
        Path root = RepositoryRoot.locate();
        String pom = Files.readString(root.resolve("pom.xml"));
        String readme = Files.readString(root.resolve("CONTRIBUTING.md"));

        String version = firstGroup(PROJECT_VERSION, pom, "project version");
        String mcpVersion = firstGroup(MCP_VERSION, pom, "MCP SDK version");
        List<String> pomModules = pomModules(pom);

        assertTrue(readme.contains("Baseline développement " + version),
                () -> "CONTRIBUTING.md development baseline must match pom.xml version " + version);
        assertTrue(readme.contains("Java MCP SDK " + mcpVersion),
                () -> "CONTRIBUTING.md MCP SDK must match pom.xml version " + mcpVersion);
        assertEquals(pomModules, readmeModules(readme),
                "CONTRIBUTING.md Maven module list must exactly match root pom.xml <modules>");
    }

    @Test
    void activeDocumentationDeclaresCurrentDevelopmentBaseline() throws Exception {
        Path root = RepositoryRoot.locate();
        String version = firstGroup(PROJECT_VERSION, Files.readString(root.resolve("pom.xml")), "project version");
        for (Path page : activeStatusPages(root)) {
            String content = Files.readString(page);
            assertTrue(content.contains(version),
                    () -> root.relativize(page) + " must mention current development baseline " + version);
        }
    }

    @Test
    void activeStatusPagesDoNotAdvertiseCompletedD2AsPending() throws Exception {
        Path root = RepositoryRoot.locate();
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
        Path root = RepositoryRoot.locate();
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
        Path root = RepositoryRoot.locate();
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
        Path root = RepositoryRoot.locate();
        Path ratchetFile = root.resolve("config/m21-quality-ratchets.properties");
        Map<String, String> ratchets = properties(ratchetFile);
        // Pinned so that moving a ratchet is a deliberate act with evidence, never a side effect. A presence key
        // sits ten executions under a measurement taken on both platforms, stated with its commit beside the key in
        // config/m21-quality-ratchets.properties; a coverage key sits under the cap that CoverageQualityGateTest or
        // AggregateCoverageGateTest qualifies, with its measurements in config/coverage-qualification/.
        assertEquals("2876", ratchets.get("testsMinimum"));
        assertEquals("1819", ratchets.get("generatedTestsMinimum"));
        assertEquals("836", ratchets.get("architectureTestsMinimum"));
        assertEquals("0.899", ratchets.get("perModuleLineCoverageMinimum"));
        assertEquals("0.771", ratchets.get("perModuleBranchCoverageMinimum"));
        assertEquals("0.912", ratchets.get("aggregateLineCoverageMinimum"));
        assertEquals("0.773", ratchets.get("aggregateBranchCoverageMinimum"));

        assertBothValidatorsReadTheAggregateKeysOf(root);
    }

    /** Both validators conclude on the aggregate scale, so both read its keys and neither falls back to another. */
    private static void assertBothValidatorsReadTheAggregateKeysOf(Path root) throws IOException {
        String linux = Files.readString(root.resolve("scripts/validate-m21.sh"));
        String windows = Files.readString(root.resolve("scripts/validate-m21.ps1"));
        assertTrue(linux.contains("config/m21-quality-ratchets.properties"));
        assertTrue(windows.contains("config\\m21-quality-ratchets.properties"));
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
        Path root = RepositoryRoot.locate();
        Map<String, String> ratchets = properties(root.resolve("config/m21-quality-ratchets.properties"));
        String expectedRatchets = """
                Surefire written        >= %s
                Surefire generated      >= %s
                architecture            >= %s
                aggregate line          >= %s
                aggregate branch        >= %s
                per-module line         >= %s
                per-module branch       >= %s
                changed-line            >= 80%%
                changed-branch          >= 70%%
                """.formatted(
                ratchets.get("testsMinimum"),
                ratchets.get("generatedTestsMinimum"),
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
        String adrIndex = Files.readString(root.resolve("docs/architecture/adr/README.md"));
        assertTrue(adrIndex.contains("[ADR-0096](GEN-0096-conservative-native-mcp-client"),
                "ADR index must retain the accepted ADR-0096 entry");
        assertFalse(arc42.contains("| DT-02 |"),
                "active debt register must not advertise the already-resolved ADR-0096 index drift");
    }

    @Test
    void operatorFacingQualityGateDocumentationMirrorsTheNormativeRatchets() throws Exception {
        Path root = RepositoryRoot.locate();
        Map<String, String> ratchets = properties(root.resolve("config/m21-quality-ratchets.properties"));
        String tests = ratchets.get("testsMinimum");
        String generated = ratchets.get("generatedTestsMinimum");
        String architecture = ratchets.get("architectureTestsMinimum");
        String aggregateLine = decimalPercentage(ratchets.get("aggregateLineCoverageMinimum"));
        String aggregateBranch = decimalPercentage(ratchets.get("aggregateBranchCoverageMinimum"));
        String perModuleLine = decimalPercentage(ratchets.get("perModuleLineCoverageMinimum"));
        String perModuleBranch = decimalPercentage(ratchets.get("perModuleBranchCoverageMinimum"));

        assertLabelledThresholds(root, "docs/developer/BUILD_AND_TEST.md", Map.of(
                "baseline Surefire écrits", tests,
                "baseline Surefire générés", generated,
                "baseline architecture", architecture,
                "JaCoCo aggregate line ratchet", aggregateLine,
                "JaCoCo aggregate branch ratchet", aggregateBranch,
                "JaCoCo per-module line ratchet", perModuleLine,
                "JaCoCo per-module branch ratchet", perModuleBranch));
        assertLabelledThresholds(root, "docs/developer/PRODUCTION_INTEGRITY.md", Map.of(
                "Tests written", tests,
                "Tests generated", generated,
                "Architecture ", architecture,
                "JaCoCo aggregate lines", aggregateLine,
                "JaCoCo aggregate branches", aggregateBranch,
                "JaCoCo per-module lines", perModuleLine,
                "JaCoCo per-module branches", perModuleBranch));
        // The operator surfaces an engineer actually opens before running a gate. Each of these still announced
        // the pre-1.2.1 ratchets, so four different numbers were in circulation for one executable threshold.
        // Each now has to name the scale as well as the number: one figure standing alone was exactly how a
        // per-module threshold came to be read as governing the canonical measurement.
        assertLabelledThresholds(root, "scripts/README.md", Map.of(
                "Surefire written", tests,
                "Surefire generated", generated,
                "architecture ", architecture,
                "aggregate line coverage", aggregateLine,
                "aggregate branch coverage", aggregateBranch,
                "per-module line coverage", perModuleLine,
                "per-module branch coverage", perModuleBranch));
        assertLabelledThresholds(root, "distribution/README.md", Map.of(
                "Surefire written", tests,
                "Surefire generated", generated,
                "architecture ", architecture,
                "aggregate line coverage", aggregateLine,
                "aggregate branch coverage", aggregateBranch,
                "per-module line coverage", perModuleLine,
                "per-module branch coverage", perModuleBranch));
        assertLabelledThresholds(root, "docs/developer/README.md", Map.of(
                "Surefire written floor", tests,
                "Surefire generated floor", generated,
                "Architecture floor", architecture,
                "JaCoCo aggregate line ratchet", aggregateLine,
                "JaCoCo aggregate branch ratchet", aggregateBranch,
                "JaCoCo per-module line ratchet", perModuleLine,
                "JaCoCo per-module branch ratchet", perModuleBranch));
        assertLabelledThresholds(root, "docs/project/governance/DOCUMENTATION_STATUS.md", Map.of(
                "Surefire written ratchet", tests,
                "Surefire generated ratchet", generated,
                "Architecture ratchet", architecture,
                "Aggregate line ratchet", aggregateLine,
                "Aggregate branch ratchet", aggregateBranch,
                "Per-module line ratchet", perModuleLine,
                "Per-module branch ratchet", perModuleBranch));
        assertLabelledThresholds(root, "docs/project/governance/ROADMAP.md", Map.of(
                "Surefire written ratchet", tests,
                "Surefire generated ratchet", generated,
                "architecture ratchet", architecture,
                "aggregate line ratchet", aggregateLine,
                "aggregate branch ratchet", aggregateBranch,
                "per-module line ratchet", perModuleLine,
                "per-module branch ratchet", perModuleBranch));

        String readme = Files.readString(root.resolve("CONTRIBUTING.md"));
        String readmeClaim = ("Le ratchet agrégé est **≥ %s %% lignes / ≥ %s %% branches**, le ratchet par module"
                + " **≥ %s %% lignes / ≥ %s %% branches**, avec **≥ %s tests Surefire écrits**,"
                + " **≥ %s tests Surefire générés** et"
                + " **≥ %s tests d’architecture**")
                .formatted(french(aggregateLine), french(aggregateBranch),
                        french(perModuleLine), french(perModuleBranch), tests, generated, architecture);
        assertTrue(readme.contains(readmeClaim),
                () -> "CONTRIBUTING.md must state the normative M21 ratchets: " + readmeClaim);

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
     * The D2 guide states what the D2 validators enforce. The presence minimums are not restated: they are the living
     * ratchets, which the validators read from the properties file, and a number written in the guide would be a copy
     * that could drift (D2RepositoryHardeningArchitectureTest forbids a literal in the scripts). What remains literal
     * on purpose is the pair of absolute coverage floors, so those are the only values mirrored here.
     */
    @Test
    void operatorFacingD2GateDocumentationMirrorsWhatTheD2ValidatorsEnforce() throws Exception {
        Path root = RepositoryRoot.locate();
        String linux = Files.readString(root.resolve("scripts/validate-d2.sh"));
        String windows = Files.readString(root.resolve("scripts/validate-d2.ps1"));

        String lineFloor = onlyGroup(Pattern.compile("if line < (0\\.\\d+):"), linux, "D2 line coverage floor");
        String branchFloor = onlyGroup(Pattern.compile("if branch < (0\\.\\d+):"), linux, "D2 branch coverage floor");

        assertTrue(windows.contains("$lineCoverage -lt " + lineFloor),
                "the Windows D2 validator must enforce the same line coverage floor as the Linux one");
        assertTrue(windows.contains("$branchCoverage -lt " + branchFloor),
                "the Windows D2 validator must enforce the same branch coverage floor as the Linux one");

        assertLabelledThresholds(root, "scripts/README.md", Map.of(
                "absolute line floor", decimalPercentage(lineFloor),
                "absolute branch floor", decimalPercentage(branchFloor)));
        assertTrue(Files.readString(root.resolve("scripts/README.md")).contains("testsMinimum"),
                "the D2 guide must say that the presence minimums are the ratchet file's, not a number of its own");
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
        Path root = RepositoryRoot.locate();
        String guide = Files.readString(root.resolve("docs/developer/REMOTE_SERVER_PLATFORM.md"));
        Path sqliteSources = root.resolve("morpheus-store-sqlite/src/main/java/com/morpheus/store/sqlite");

        Matcher named = Pattern.compile("`(Sqlite[A-Za-z]*Exception)`").matcher(guide);
        while (named.find()) {
            String type = named.group(1);
            assertTrue(Files.isRegularFile(sqliteSources.resolve("adapter").resolve(type + ".java"))
                            || Files.isRegularFile(sqliteSources.resolve("runtime").resolve(type + ".java")),
                    () -> "REMOTE_SERVER_PLATFORM.md documents " + type + ", which no longer exists");
        }
    }


    @Test
    void rootBuildEnforcerPinsQualifiedJavaAndDependencyConvergence() throws Exception {
        String pom = Files.readString(RepositoryRoot.locate().resolve("pom.xml")).replace("\r\n", "\n");
        assertTrue(pom.contains("<requireJavaVersion>\n                                    <version>[21,22)</version>\n                                </requireJavaVersion>"),
                "root Maven enforcer must reject JDKs newer than the qualified Java 21 line");
        assertTrue(pom.contains("<dependencyConvergence/>"),
                "root Maven enforcer must reject divergent transitive dependency versions");
    }

    @Test
    void httpExtensionRoutesUseSharedTimedRequestBodyBoundary() throws Exception {
        Path root = RepositoryRoot.locate();
        Path api = root.resolve("morpheus-api/src/main/java");
        List<Path> routers;
        try (var files = Files.walk(api)) {
            routers = files.filter(path -> path.getFileName().toString().endsWith("HttpRoutes.java")).sorted().toList();
        }
        assertFalse(routers.isEmpty(), "no *HttpRoutes.java found under " + api);
        for (Path router : routers) {
            String route = router.getFileName().toString();
            String content = Files.readString(router);
            assertFalse(content.contains("getRequestBody()"),
                    () -> route + " must leave reading the request body to MorpheusHttpRequestDecoder");
            assertFalse(content.contains("HttpRequestBodyReader"),
                    () -> route + " must read its body through MorpheusHttpRequestDecoder, not a reader of its own");
        }

        String decoder = ProductionSources.readMain("morpheus-api", "MorpheusHttpRequestDecoder");
        assertTrue(decoder.contains("HttpRequestBodyReader.read("),
                "the request decoder must read bodies through the shared bounded reader");
        String reader = ProductionSources.readMain("morpheus-api", "HttpRequestBodyReader");
        assertTrue(reader.contains("TimedBoundedInputReader.read("),
                "shared request-body reader must delegate to the deadline-aware primitive");
    }

    @Test
    void repositoryPublishesSecurityPolicyAndSensitiveCodeOwnership() throws Exception {
        Path root = RepositoryRoot.locate();
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
        Path root = RepositoryRoot.locate();
        String routePolicy = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusRemoteRoutePolicy"));
        String remoteServer = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusRemoteHttpServer"));
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


    private static List<Path> activeStatusPages(Path root) {
        return List.of(
                root.resolve("README.md"),
                root.resolve("CONTRIBUTING.md"),
                root.resolve("docs/README.md"),
                root.resolve("docs/user/README.md"),
                root.resolve("docs/developer/README.md"),
                root.resolve("docs/developer/BUILD_AND_TEST.md"),
                root.resolve("docs/project/governance/ROADMAP.md"),
                root.resolve("docs/project/governance/DOCUMENTATION_STATUS.md"),
                root.resolve("docs/project/validation/README.md"));
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
        if (marker < 0) throw new IllegalArgumentException("CONTRIBUTING.md has no 'Modules Maven :' section");
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
