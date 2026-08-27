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

        assertTrue(workflow.contains("tags:\n      - 'v*'"));
        assertTrue(workflow.contains("contents: write"));
        assertTrue(workflow.contains("id-token: write"));
        assertTrue(workflow.contains("attestations: write"));
        assertTrue(workflow.contains("git merge-base --is-ancestor HEAD origin/main"));
        assertTrue(workflow.contains("distribution/build-release.sh"));
        assertTrue(workflow.contains(
                "actions/attest@1e69f48acb82d1966a394da916b4c1698aa569d6"),
                "Release provenance action must remain SHA-pinned");
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
