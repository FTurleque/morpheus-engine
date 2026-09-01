package com.morpheus.architecture.m21;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AuditHardeningWorkflowContractTest {
    private static final Pattern CACHE_AGE = Pattern.compile(
            "DEPENDENCY_CHECK_MAX_CACHE_AGE_HOURS:\\s*'([0-9]+)'");
    private static final Pattern RELEASE_TAG_TRIGGER = Pattern.compile(
            "(?m)^\\s*tags:\\s*\\R\\s*-\\s*'v\\*'\\s*$");

    @Test
    void dependencyCheckTrustedRefreshCannotAgePastPullRequestCacheWindow() throws IOException {
        String workflow = Files.readString(repoRoot().resolve(".github/workflows/security.yml"));
        Matcher cacheAge = CACHE_AGE.matcher(workflow);

        assertTrue(cacheAge.find(), "Dependency-Check cache-age policy must be explicit");
        int maximumAgeHours = Integer.parseInt(cacheAge.group(1));
        assertTrue(maximumAgeHours > 24,
                "Daily trusted refresh must complete before the pull-request cache can become stale");
        assertTrue(workflow.contains("- cron: '17 4 * * *'"),
                "Trusted Dependency-Check refresh must run daily");
        assertFalse(workflow.contains("- cron: '17 4 * * 1'"),
                "Weekly refresh would leave a deterministic stale-cache merge window");
        assertTrue(workflow.contains("DEPENDENCY_CHECK_MAX_CACHE_AGE_HOURS * 60 * 60"),
                "Freshness enforcement must use the declared cache-age policy");
    }

    @Test
    void releaseWorkflowPublishesAttestedNonOverwritableAssetsFromMain() throws IOException {
        String workflow = Files.readString(repoRoot().resolve(".github/workflows/release.yml"));

        assertTrue(RELEASE_TAG_TRIGGER.matcher(workflow).find(),
                "Release workflow must trigger on version tags on every supported platform");
        assertTrue(workflow.contains("contents: write"));
        assertTrue(workflow.contains("id-token: write"));
        assertTrue(workflow.contains("attestations: write"));
        assertTrue(workflow.contains("git merge-base --is-ancestor HEAD origin/main"));
        assertTrue(workflow.contains("distribution/build-release.sh"));
        assertTrue(workflow.contains("distribution/build-release.ps1"));
        assertTrue(workflow.contains(
                "actions/attest@1e69f48acb82d1966a394da916b4c1698aa569d6"),
                "Release provenance action must remain SHA-pinned");
        assertTrue(workflow.contains(
                "actions/download-artifact@3e5f45b2cfb9172054b4087a40e8e0b5a5461e7c"),
                "Cross-job release asset download must remain SHA-pinned");
        assertTrue(workflow.contains("MORPHEUS-${version}-windows-x64-setup.exe"));
        assertTrue(workflow.contains("morpheus-${version}-linux-x64.tar.gz"));
        assertTrue(workflow.contains("gh release view \"${GITHUB_REF_NAME}\""));
        assertTrue(workflow.contains("refusing to overwrite published assets"));
        assertFalse(workflow.contains("--clobber"),
                "Published release assets must never be silently replaced");
    }

    @Test
    void pullRequestCoverageGateIncludesChangedBranches() throws IOException {
        Path root = repoRoot();
        String workflow = Files.readString(root.resolve(".github/workflows/ci.yml"));
        String checker = Files.readString(root.resolve("scripts/check-diff-coverage.py"));

        assertTrue(workflow.contains("--minimum 0.80 --minimum-branch 0.70"),
                "CI must gate both changed lines and branches");
        assertTrue(checker.contains("--minimum-branch"));
        assertTrue(checker.contains("covered_changed_branches"));
        assertTrue(checker.contains("changed_branches"));
        assertTrue(checker.contains("Changed-branch coverage"));
    }

    @Test
    void sonarCiAnalysisImportsExactHeadJaCoCoAndFailsClosed() throws IOException {
        String workflow = Files.readString(repoRoot().resolve(".github/workflows/ci.yml"));

        assertTrue(workflow.contains("Run SonarQube Cloud CI analysis with JaCoCo"));
        assertTrue(workflow.contains("SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}"),
                "Sonar analysis must authenticate through the repository secret");
        assertTrue(workflow.contains("github.event.pull_request.head.repo.full_name == github.repository"),
                "Untrusted fork pull requests must not receive the Sonar secret");
        assertTrue(workflow.contains("github.actor != 'dependabot[bot]'"),
                "Dependabot pull requests must not enter a Sonar step that requires unavailable repository secrets");
        assertTrue(workflow.contains("Record SonarQube Cloud skip for Dependabot"),
                "The Dependabot-only Sonar exception must remain explicit and auditable");
        assertTrue(workflow.contains("github.actor == 'dependabot[bot]'"),
                "The Sonar skip path must be scoped to the Dependabot actor");
        assertFalse(workflow.contains("pull_request_target"),
                "CI must not expose repository secrets to pull-request code through pull_request_target");
        assertTrue(workflow.contains("*/target/site/jacoco/jacoco.xml"),
                "Sonar analysis must import the XML reports produced by M21");
        assertTrue(workflow.contains("-Dsonar.coverage.jacoco.xmlReportPaths=\"$reports\""));
        assertTrue(workflow.contains("-Dsonar.projectKey=FTurleque_morpheus-engine"));
        assertTrue(workflow.contains("-Dsonar.organization=fturleque"));
        assertTrue(workflow.contains("-Dsonar.host.url=https://sonarcloud.io"));
        assertTrue(workflow.contains("-Dsonar.qualitygate.wait=true"),
                "The GitHub CI gate must fail when SonarQube Cloud rejects the analysis");
        assertTrue(workflow.contains(
                "org.sonarsource.scanner.maven:sonar-maven-plugin:5.5.0.6356:sonar"),
                "The SonarScanner for Maven must be version-pinned");
        assertTrue(workflow.indexOf("Run one-command M21 gate on Linux")
                        < workflow.indexOf("Run SonarQube Cloud CI analysis with JaCoCo"),
                "Coverage-producing M21 validation must complete before Sonar analysis");
        assertFalse(workflow.contains("continue-on-error: true"),
                "Sonar analysis must never be made advisory through continue-on-error");
    }

    @Test
    void mcpTransportDoesNotLogRawPeerControlledThrowables() throws IOException {
        Path root = repoRoot();
        String client = Files.readString(root.resolve(
                "morpheus-mcp-transport/src/main/java/com/morpheus/integration/mcp/BoundedStdioClientTransport.java"));
        String server = Files.readString(root.resolve(
                "morpheus-mcp-transport/src/main/java/com/morpheus/integration/mcp/BoundedStdioServerTransportProvider.java"));
        String redactor = Files.readString(root.resolve(
                "morpheus-mcp-transport/src/main/java/com/morpheus/integration/mcp/McpDiagnosticRedactor.java"));

        assertTrue(client.contains("McpDiagnosticRedactor.describe(failure)"));
        assertTrue(server.contains("McpDiagnosticRedactor.describe(failure)"));
        assertFalse(client.contains("failure.getMessage()"));
        assertFalse(server.contains("failure.getMessage()"));
        assertFalse(client.contains("\"MCP inbound processing failed\", failure"));
        assertFalse(client.contains("\"MCP outbound processing failed\", failure"));
        assertTrue(redactor.contains("JSON_OR_NAMED_SECRET"));
    }

    @Test
    void futureRemoteUpdaterCannotRegressToChecksumOnlyTrust() throws IOException {
        Path root = repoRoot();
        String manifest = Files.readString(root.resolve(
                "morpheus-application/src/main/java/com/morpheus/application/product/UpdateManifest.java"));
        String discovery = Files.readString(root.resolve(
                "morpheus-application/src/main/java/com/morpheus/application/product/UpdateDiscoveryService.java"));

        assertTrue(manifest.contains("remote update manifest must declare attestationUri"));
        assertTrue(manifest.contains("remote update artifactUri must use https"));
        assertTrue(manifest.contains("remote update attestationUri must use https"));
        assertTrue(discovery.contains("optionalUri(properties, \"attestationUri\")"));
        assertTrue(discovery.contains("manifest.requireRemoteTrust(manifestUri)"));
    }

    @Test
    void policyAdaptersCannotBypassBoundedQueryDefinitionCodec() throws IOException {
        Path root = repoRoot();
        String codec = Files.readString(root.resolve(
                "morpheus-application/src/main/java/com/morpheus/application/query/dsl/QueryDefinitionCodec.java"));
        String validator = Files.readString(root.resolve(
                "morpheus-application/src/main/java/com/morpheus/application/query/dsl/QueryValidator.java"));

        assertTrue(codec.contains("requireEncodedSize(encoded)"),
                "QueryDefinitionCodec must reject oversized encoded input before Base64 decoding");
        assertTrue(codec.contains("budget.enterNode(depth)"),
                "QueryDefinitionCodec must account for every AST node before recursive decoding");
        assertTrue(codec.contains("budget.enterPredicate()"),
                "QueryDefinitionCodec must enforce the global predicate budget during decoding");
        assertTrue(codec.contains("new QueryValidator().requireValid(query, encoded.length())"),
                "Decoded queries must pass the shared semantic and size validator before leaving the codec");
        assertTrue(validator.contains("counters.exhausted"),
                "QueryValidator must stop traversal after a structural budget is exceeded");

        assertUsesBoundedQueryCodec(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusPolicyApiService.java"));
        assertUsesBoundedQueryCodec(root.resolve(
                "morpheus-mcp/src/main/java/com/morpheus/mcp/MorpheusPolicyMcpTools.java"));
        assertUsesBoundedQueryCodec(root.resolve(
                "morpheus-cli/src/main/java/com/morpheus/cli/MorpheusPolicyCli.java"));
    }

    private void assertUsesBoundedQueryCodec(Path adapter) throws IOException {
        String source = Files.readString(adapter);
        assertTrue(source.contains("QueryDefinitionCodec"),
                adapter + " must depend on the shared bounded query codec");
        assertTrue(source.contains("queryCodec.decode("),
                adapter + " must decode persisted QUERY_ASSERTION payloads through QueryDefinitionCodec");
        assertFalse(source.contains("Base64.getUrlDecoder()"),
                adapter + " must not implement an unbounded parallel query decoder");
    }

    private Path repoRoot() {
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
