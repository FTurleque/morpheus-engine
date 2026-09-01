package com.morpheus.architecture.d2;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class D2RepositoryHardeningArchitectureTest {

    private static final Pattern CHECKOUT_NODE24 = Pattern.compile(
            "(?m)^\\s*(?:-\\s*)?uses: actions/checkout@[0-9a-f]{40} # v(?:[6-9]|[1-9][0-9])(?:\\.[0-9]+){0,2}\\s*$");
    private static final Pattern SETUP_JAVA_NODE24 = Pattern.compile(
            "(?m)^\\s*(?:-\\s*)?uses: actions/setup-java@[0-9a-f]{40} # v(?:[5-9]|[1-9][0-9])(?:\\.[0-9]+){0,2}\\s*$");
    private static final Pattern UPLOAD_ARTIFACT_NODE24 = Pattern.compile(
            "(?m)^\\s*(?:-\\s*)?uses: actions/upload-artifact@[0-9a-f]{40} # v(?:[6-9]|[1-9][0-9])(?:\\.[0-9]+){0,2}\\s*$");
    private static final Pattern CACHE_NODE24 = Pattern.compile(
            "(?m)^\\s*(?:-\\s*)?uses: actions/cache/(?:restore|save)@[0-9a-f]{40} # v(?:[6-9]|[1-9][0-9])(?:\\.[0-9]+){0,2}\\s*$");
    private static final Pattern CODEQL_V4 = Pattern.compile(
            "(?m)^\\s*(?:-\\s*)?uses: github/codeql-action/(?:init|analyze)@[0-9a-f]{40} # v4(?:\\.[0-9]+){1,2}\\s*$");

    @Test
    void dependencyAndQualityBaselineIsPinned() throws IOException {
        String pom = Files.readString(repoRoot().resolve("pom.xml"));
        assertTrue(pom.contains("<jackson.version>3.2.2</jackson.version>"));
        assertTrue(pom.contains("<sqlite-jdbc.version>3.53.2.0</sqlite-jdbc.version>"));
        assertTrue(pom.contains("<mcp-sdk.version>2.0.1</mcp-sdk.version>"));
        assertTrue(pom.contains("<dependency-check.maven.plugin.version>12.2.2</dependency-check.maven.plugin.version>"));
        assertTrue(pom.contains("<failOnWarning>true</failOnWarning>"));
        assertTrue(pom.contains("<id>d2-security</id>"));
        assertTrue(pom.contains("<failBuildOnCVSS>7.0</failBuildOnCVSS>"));
    }

    @Test
    void activeMcpDocumentationMatchesPinnedSdkVersion() throws IOException {
        String mcp = Files.readString(repoRoot().resolve("docs/developer/MCP.md"));
        assertTrue(mcp.contains("Java MCP SDK 2.0.1"));
        assertFalse(mcp.contains("Java MCP SDK 2.0.0"));
    }

    @Test
    void coverageRatchetCannotSilentlyReturnToTheD2Floor() throws IOException {
        Path root = repoRoot();
        Properties ratchets = m21Ratchets(root);
        double line = Double.parseDouble(ratchets.getProperty("lineCoverageMinimum"));
        double branch = Double.parseDouble(ratchets.getProperty("branchCoverageMinimum"));
        assertTrue(line > 0.40d, "M21 line ratchet must remain stricter than the D2 floor");
        assertTrue(branch > 0.35d, "M21 branch ratchet must remain stricter than the D2 floor");

        String coverage = Files.readString(root.resolve(
                "morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m21/CoverageQualityGateTest.java"));
        assertTrue(coverage.contains("config/m21-quality-ratchets.properties"),
                "M21 coverage gate must consume the centralized ratchet configuration");
        assertFalse(coverage.contains("LINE_RATCHET = 0.40d"));
        assertFalse(coverage.contains("BRANCH_RATCHET = 0.35d"));
    }

    @Test
    void durableM21ScriptsKeepQualifiedPresenceAndCoverageRatchets() throws IOException {
        Path root = repoRoot();
        Properties ratchets = m21Ratchets(root);
        assertTrue(Integer.parseInt(ratchets.getProperty("testsMinimum")) >= 860);
        assertTrue(Integer.parseInt(ratchets.getProperty("architectureTestsMinimum")) >= 265);
        assertTrue(Double.parseDouble(ratchets.getProperty("lineCoverageMinimum")) >= 0.510d);
        assertTrue(Double.parseDouble(ratchets.getProperty("branchCoverageMinimum")) >= 0.435d);

        String linux = Files.readString(root.resolve("scripts/validate-m21.sh"));
        String windows = Files.readString(root.resolve("scripts/validate-m21.ps1"));
        assertTrue(linux.contains("config/m21-quality-ratchets.properties"));
        assertTrue(windows.contains("config\\m21-quality-ratchets.properties"));
        assertTrue(linux.contains("1.2.1"));
        assertTrue(windows.contains("1.2.1"));
    }

    @Test
    void d2ScriptsKeepCurrentPresenceRatchets() throws IOException {
        Path root = repoRoot();
        String linux = Files.readString(root.resolve("scripts/validate-d2.sh"));
        String windows = Files.readString(root.resolve("scripts/validate-d2.ps1"));
        for (String script : java.util.List.of(linux, windows)) {
            assertTrue(script.contains("820"));
            assertTrue(script.contains("258"));
            assertTrue(script.contains("1.2.1"));
        }
    }

    @Test
    void activeWorkflowsUsePinnedNode24GenerationActions() throws IOException {
        Path root = repoRoot();
        for (String workflow : java.util.List.of("ci.yml", "security.yml", "codeql.yml")) {
            String text = Files.readString(root.resolve(".github/workflows").resolve(workflow));
            assertPinnedNode24(text, CHECKOUT_NODE24, "checkout", workflow);
            assertPinnedNode24(text, SETUP_JAVA_NODE24, "setup-java", workflow);
        }
        for (String workflow : java.util.List.of("ci.yml", "security.yml")) {
            String text = Files.readString(root.resolve(".github/workflows").resolve(workflow));
            assertPinnedNode24(text, UPLOAD_ARTIFACT_NODE24, "upload-artifact", workflow);
        }
        String security = Files.readString(root.resolve(".github/workflows/security.yml"));
        assertTrue(CACHE_NODE24.matcher(security).results().count() >= 2,
                "security.yml must pin Node 24 cache restore/save actions by immutable SHA");
        assertFalse(security.contains("uses: actions/cache/restore@v"));
        assertFalse(security.contains("uses: actions/cache/save@v"));

        String codeql = Files.readString(root.resolve(".github/workflows/codeql.yml"));
        assertTrue(CODEQL_V4.matcher(codeql).results().count() >= 2,
                "codeql.yml must pin CodeQL init/analyze actions by immutable SHA");
        assertFalse(codeql.contains("uses: github/codeql-action/init@v"));
        assertFalse(codeql.contains("uses: github/codeql-action/analyze@v"));
    }

    @Test
    void historicalPreflightsAvoidDeprecatedSetupJavaV4() throws IOException {
        Path root = repoRoot().resolve(".github/workflows");
        for (String workflow : java.util.List.of("m10-preflight.yml", "m11-preflight.yml", "m12-preflight.yml")) {
            String text = Files.readString(root.resolve(workflow));
            assertPinnedNode24(text, CHECKOUT_NODE24, "checkout", workflow);
            assertPinnedNode24(text, SETUP_JAVA_NODE24, "setup-java", workflow);
            assertFalse(text.contains("cf277c60eb25467037889841efdb72551f06f6c3"));
        }
    }

    @Test
    void dependencySecurityGateCoversPromotionAndDevelopUpdatesWithoutPrUpdatesOrCacheWrites() throws IOException {
        Path root = repoRoot();
        String security = Files.readString(root.resolve(".github/workflows/security.yml"));
        String dependabot = Files.readString(root.resolve(".github/dependabot.yml"));

        assertTrue(security.contains("branches: [main, develop]"));
        assertTrue(security.contains("timeout-minutes: 90"));
        assertTrue(security.contains("dependency-check-maven:12.2.2:update-only"));
        assertTrue(security.contains("dependency-check-maven:12.2.2:aggregate"));
        assertTrue(security.contains("-DautoUpdate=false"));
        assertTrue(security.contains("target/dependency-check-data"));
        assertTrue(security.contains("dependency-check-v12-trusted-${{ runner.os }}-"));
        assertFalse(security.contains("dependency-check-v12-${{ runner.os }}-32587778460"));
        assertFalse(security.contains("dependency-check-v12-${{ runner.os }}-32690353897"));
        assertTrue(security.contains("Verify restored Dependency-Check database freshness"));
        assertTrue(security.contains("if: github.event_name == 'pull_request'"));
        assertTrue(security.contains("DEPENDENCY_CHECK_MAX_CACHE_AGE_HOURS: '72'"));
        assertTrue(security.contains("max_age_seconds=\"$((DEPENDENCY_CHECK_MAX_CACHE_AGE_HOURS * 60 * 60))\""));
        assertTrue(security.contains("- cron: '17 4 * * *'"));
        assertFalse(security.contains("- cron: '17 4 * * 1'"));
        assertTrue(security.contains("No trusted Dependency-Check database was restored"));
        assertTrue(security.contains("if: github.event_name != 'pull_request'"));
        assertTrue(security.contains("NVD_API_KEY: ${{ secrets.NVD_API_KEY }}"));
        assertTrue(security.contains("-DnvdApiKeyEnvironmentVariable=NVD_API_KEY"));
        assertFalse(security.contains("-DnvdApiKey=${NVD_API_KEY}"));
        assertFalse(security.contains("Update Dependency-Check vulnerability database (pull request, no secrets)"));
        assertFalse(security.contains("pull request, no secrets"));
        assertTrue(security.contains("Remove stale Dependency-Check update lock"));
        assertTrue(security.contains("odc.update.lock"));
        assertTrue(security.contains("rm -f -- \"${lock_file}\""));

        int restoreIndex = security.indexOf("- name: Restore Dependency-Check database");
        int freshnessIndex = security.indexOf("- name: Verify restored Dependency-Check database freshness");
        int staleLockIndex = security.indexOf("- name: Remove stale Dependency-Check update lock");
        int trustedUpdateIndex = security.indexOf("- name: Update Dependency-Check vulnerability database (trusted events)");
        int saveIndex = security.indexOf("- name: Save trusted Dependency-Check database");
        int scanIndex = security.indexOf("- name: Run OWASP Dependency-Check scan");
        assertTrue(restoreIndex >= 0 && freshnessIndex > restoreIndex && staleLockIndex > freshnessIndex
                        && trustedUpdateIndex > staleLockIndex,
                "trusted Dependency-Check preparation must verify freshness before scanning or updating");
        assertTrue(saveIndex > trustedUpdateIndex,
                "trusted cache save must follow the trusted Dependency-Check update");
        assertTrue(scanIndex > saveIndex, "aggregate scan must run after cache/update preparation");

        String trustedUpdateStep = security.substring(trustedUpdateIndex, saveIndex);
        assertTrue(trustedUpdateStep.contains("if: github.event_name != 'pull_request'"),
                "Dependency-Check database updates must be restricted to trusted events");
        assertTrue(trustedUpdateStep.contains("${{ secrets.NVD_API_KEY }}"),
                "trusted Dependency-Check updates must use the configured NVD API key when available");
        int firstUpdateOnly = security.indexOf("dependency-check-maven:12.2.2:update-only");
        assertTrue(firstUpdateOnly >= trustedUpdateIndex && firstUpdateOnly < saveIndex,
                "Dependency-Check update-only must remain inside the trusted-event update step");
        assertTrue(security.indexOf("dependency-check-maven:12.2.2:update-only", firstUpdateOnly + 1) < 0,
                "pull requests must consume the trusted cache and must not run a second anonymous NVD update");

        String saveStep = security.substring(saveIndex, scanIndex);
        assertTrue(saveStep.contains("if: github.event_name != 'pull_request'"),
                "Dependency-Check cache writes must be restricted to trusted events");

        assertTrue(dependabot.contains("package-ecosystem: maven"));
        assertTrue(dependabot.contains("package-ecosystem: github-actions"));
        assertTrue(dependabot.contains("target-branch: develop"));
    }

    @Test
    void codeQlSastIsVersionedAndUsesExtendedJavaSecurityQueries() throws IOException {
        String codeql = Files.readString(repoRoot().resolve(".github/workflows/codeql.yml"));
        assertTrue(codeql.contains("branches: [main, develop]"));
        assertTrue(codeql.contains("security-events: write"));
        assertTrue(codeql.contains("languages: java-kotlin"));
        assertTrue(codeql.contains("build-mode: manual"));
        assertTrue(codeql.contains("queries: security-extended"));
        assertTrue(codeql.contains("./mvnw -DskipTests -Djacoco.skip=true clean package"));
    }

    @Test
    void untrustedJsonRemainsStrictAndDefaultTypingIsNotActivated() throws IOException {
        Path root = repoRoot();
        String server = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusHttpServer.java"));
        String requestDecoder = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusHttpRequestDecoder.java"));
        assertTrue(server.contains("MAX_REQUEST_BODY_BYTES = 65_536"));
        assertTrue(server.contains("private final MorpheusHttpRequestDecoder requestDecoder;"));
        assertTrue(requestDecoder.contains("FAIL_ON_UNKNOWN_PROPERTIES"));
        assertTrue(requestDecoder.contains("FAIL_ON_TRAILING_TOKENS"));
        assertTrue(requestDecoder.contains("TimedBoundedInputReader.read("));
        assertTrue(Files.isRegularFile(root.resolve(
                "morpheus-api/src/test/java/com/morpheus/api/JacksonSecurityRegressionTest.java")));

        try (var files = Files.walk(root, 16)) {
            for (Path source : files.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> path.toString().contains("src" + java.io.File.separator + "main" + java.io.File.separator + "java"))
                    .toList()) {
                String text = Files.readString(source);
                assertFalse(text.contains("activateDefaultTyping("), "default typing activation forbidden: " + source);
                assertFalse(text.contains("enableDefaultTyping("), "default typing activation forbidden: " + source);
            }
        }
    }

    @Test
    void d2IsLocalOnlyAndHasDualPlatformValidationArtifacts() {
        Path root = repoRoot();
        assertTrue(Files.isRegularFile(root.resolve("scripts/validate-d2.ps1")));
        assertTrue(Files.isRegularFile(root.resolve("scripts/validate-d2.sh")));
        assertTrue(Files.isRegularFile(root.resolve("docs/roadmap/D2_EXECUTION.md")));
        assertTrue(Files.isRegularFile(root.resolve("docs/validation/VALIDATION_D2.md")));
    }

    private Properties m21Ratchets(Path root) throws IOException {
        Properties properties = new Properties();
        try (var reader = Files.newBufferedReader(root.resolve("config/m21-quality-ratchets.properties"))) {
            properties.load(reader);
        }
        return properties;
    }

    private void assertPinnedNode24(String workflow, Pattern pattern, String action, String file) {
        assertTrue(pattern.matcher(workflow).find(),
                () -> file + " must pin actions/" + action + " to a 40-char SHA from the Node 24 generation or newer");
        assertFalse(workflow.contains("uses: actions/" + action + "@v"),
                () -> file + " must not use a mutable tag for actions/" + action);
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
}