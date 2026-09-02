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
     * A schema version stated in prose on an active surface: "schema 15", "schéma V017", or the constant
     * followed by its value in words rather than by an assignment. Historical pages are never scanned with it.
     */
    private static final Pattern SCHEMA_VERSION_PROSE = Pattern.compile(
            "(?i)(?:sch[eé]ma|schema)\\s+(?:supported\\s+|support[eé]e?\\s+)?V?\\d+"
                    + "|SUPPORTED_SCHEMA_VERSION[^\\n]{0,40}?\\bV?\\d+");
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

    @Test
    void bothPlatformValidatorsConsumeSingleQualityRatchetConfiguration() throws Exception {
        Path root = repositoryRoot();
        Path ratchetFile = root.resolve("config/m21-quality-ratchets.properties");
        Map<String, String> ratchets = properties(ratchetFile);
        assertEquals("1000", ratchets.get("testsMinimum"));
        assertEquals("300", ratchets.get("architectureTestsMinimum"));
        assertEquals("0.520", ratchets.get("lineCoverageMinimum"));
        assertEquals("0.450", ratchets.get("branchCoverageMinimum"));

        String linux = Files.readString(root.resolve("scripts/validate-m21.sh"));
        String windows = Files.readString(root.resolve("scripts/validate-m21.ps1"));
        assertTrue(linux.contains("config/m21-quality-ratchets.properties"));
        assertTrue(windows.contains("config\\m21-quality-ratchets.properties"));
        assertFalse(linux.contains("line < 0.506"), "Linux validator must not retain the old embedded line ratchet");
        assertFalse(windows.contains("-lt 0.506"), "Windows validator must not retain the old embedded line ratchet");
    }

    @Test
    void activeRiskDocumentationMatchesExecutableQualityRatchetsAndResolvedAdrIndex() throws Exception {
        Path root = repositoryRoot();
        Map<String, String> ratchets = properties(root.resolve("config/m21-quality-ratchets.properties"));
        String expectedRatchets = """
                Surefire total       >= %s
                architecture         >= %s
                line coverage        >= %s
                branch coverage      >= %s
                changed-line         >= 80%%
                changed-branch       >= 70%%
                """.formatted(
                ratchets.get("testsMinimum"),
                ratchets.get("architectureTestsMinimum"),
                percentage(ratchets.get("lineCoverageMinimum")),
                percentage(ratchets.get("branchCoverageMinimum")));

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
        String line = decimalPercentage(ratchets.get("lineCoverageMinimum"));
        String branch = decimalPercentage(ratchets.get("branchCoverageMinimum"));

        assertLabelledThresholds(root, "docs/developer/BUILD_AND_TEST.md", Map.of(
                "baseline Surefire totale", tests,
                "baseline architecture", architecture,
                "JaCoCo line ratchet", line,
                "JaCoCo branch ratchet", branch));
        assertLabelledThresholds(root, "docs/developer/PRODUCTION_INTEGRITY.md", Map.of(
                "Tests ", tests,
                "Architecture ", architecture,
                "JaCoCo lines", line,
                "JaCoCo branches", branch));
        assertLabelledThresholds(root, "docs/README.md", Map.of(
                "Surefire total", tests,
                "architecture tests", architecture,
                "JaCoCo global lines", line,
                "JaCoCo global branches", branch));

        String readme = Files.readString(root.resolve("README.md"));
        String readmeClaim = "Le ratchet global est **≥ %s %% lignes / ≥ %s %% branches**, avec **≥ %s tests Surefire** et **≥ %s tests d’architecture**"
                .formatted(french(line), french(branch), tests, architecture);
        assertTrue(readme.contains(readmeClaim),
                () -> "README.md must state the normative M21 ratchets: " + readmeClaim);

        String buildAndTest = Files.readString(root.resolve("docs/developer/BUILD_AND_TEST.md"));
        String lockedBaseline = "**%s%% lignes / %s%% branches**".formatted(french(line), french(branch));
        assertTrue(buildAndTest.contains("verrouillée à " + lockedBaseline),
                () -> "BUILD_AND_TEST.md locked baseline must be " + lockedBaseline);
        assertTrue(buildAndTest.contains("une baisse sous %s%% lignes ou %s%% branches".formatted(french(line), french(branch))),
                "BUILD_AND_TEST.md regression rule must quote the normative coverage ratchets");
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
