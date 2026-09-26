package com.morpheus.architecture.m21;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Ratchets the actionable findings from the 2026-09-07 develop audit. */
class AuditRemediationContractTest {

    @Test
    void queryBudgetsAreAppliedToCurrentRequirementsAtTheStoreBoundary() throws IOException {
        Path root = repoRoot();
        String query = read(root, "morpheus-application/src/main/java/com/morpheus/application/query/dsl/QueryExecutionService.java");
        String store = read(root, "morpheus-application/src/main/java/com/morpheus/application/store/VersionedRequirementStore.java");
        String sqlite = read(root, "morpheus-store-sqlite/src/main/java/com/morpheus/store/sqlite/SqliteVersionedRequirementStore.java");
        String memory = read(root, "morpheus-store-memory/src/main/java/com/morpheus/store/memory/MemorySpecificationKnowledgeStore.java");

        assertTrue(store.contains("listCurrentRequirementVersions("));
        assertTrue(query.contains("listCurrentRequirementVersions("));
        assertTrue(query.contains("QueryBudgets.MAX_SOURCE_ROWS + 1"),
                "the query engine must request one sentinel row beyond the hard source budget");
        assertFalse(query.contains("listRequirementVersions(snapshot.get().id())"),
                "query execution must not load historical/proposed requirement versions before enforcing its budget");
        assertTrue(sqlite.contains("temporal_state = 'CURRENT'"));
        assertTrue(sqlite.contains("LIMIT ?"),
                "SQLite must apply the CURRENT predicate and row ceiling before materializing requirement rows");
        assertTrue(memory.contains("listCurrentRequirementVersions("));
        assertTrue(memory.contains(".limit(limit)"));
    }

    @Test
    void completeMaterializationRejectsBeforeOverBudgetSortOrProjection() throws IOException {
        String query = read(repoRoot(),
                "morpheus-application/src/main/java/com/morpheus/application/query/dsl/QueryExecutionService.java");

        assertTrue(query.contains("materializeRowsBounded(query, maximumRows)"));
        assertTrue(query.contains("if (totalMatches <= maximumRows)"));
        assertTrue(query.contains("throw new QueryMaterializationLimitException(maximumRows, totalMatches)"));
        assertTrue(query.indexOf("throw new QueryMaterializationLimitException(maximumRows, totalMatches)")
                        < query.indexOf("retained.sort(rowOperations.comparator(query))"),
                "over-budget complete materialization must fail before sorting retained rows");
    }

    @Test
    void providerPluginMcpFallbackNeverRelaysArbitraryExceptionMessages() throws IOException {
        String mcp = read(repoRoot(),
                "morpheus-mcp/src/main/java/com/morpheus/mcp/MorpheusProviderPluginMcpTools.java");

        assertTrue(mcp.contains("PROVIDER_PLUGIN_DISCOVERY_FAILED"));
        assertTrue(mcp.contains("addTextContent(REMOTE_DISCOVERY_FAILURE)"));
        assertFalse(mcp.contains("addTextContent(safeMessage"));
        assertFalse(mcp.contains("failure.getMessage()"));
    }

    @Test
    void aggregateCoverageIsCanonicalForMavenDiffGateArtifactsAndSonar() throws IOException {
        Path root = repoRoot();
        String rootPom = read(root, "pom.xml");
        String coveragePom = read(root, "morpheus-coverage-report/pom.xml");
        String aggregateGate = read(root,
                "morpheus-coverage-report/src/test/java/com/morpheus/coverage/AggregateCoverageGateTest.java");
        String diffGate = read(root, "scripts/check-diff-coverage.py");
        String workflow = read(root, ".github/workflows/ci.yml");

        assertTrue(rootPom.contains("<module>morpheus-coverage-report</module>"));
        assertTrue(coveragePom.contains("<goal>report-aggregate</goal>"));
        assertTrue(coveragePom.contains("<artifactId>morpheus-architecture-tests</artifactId>"));
        assertTrue(coveragePom.contains("<scope>test</scope>"),
                "architecture execution data must be merged without treating architecture test classes as production");
        assertTrue(coveragePom.contains("AggregateCoverageGateTest.java"));
        assertTrue(aggregateGate.contains("coverageSource=jacoco-report-aggregate"));
        assertTrue(diffGate.contains("morpheus-coverage-report/target/site/jacoco-aggregate/jacoco.xml"));
        assertTrue(workflow.contains("morpheus-coverage-report/target/site/jacoco-aggregate/jacoco.xml"));
        assertTrue(workflow.contains("aggregate=\"$GITHUB_WORKSPACE/morpheus-coverage-report/target/site/jacoco-aggregate/jacoco.xml\""));
        assertTrue(workflow.contains("-Dsonar.coverage.jacoco.xmlReportPaths=\"$reports\""));
    }

    @Test
    void activeDocumentationTracksBuildVersionsAndModuleCount() throws IOException {
        Path root = repoRoot();
        String pom = read(root, "pom.xml");
        String readme = read(root, "README.md");
        String status = read(root, "docs/governance/DOCUMENTATION_STATUS.md");

        String sqlite = property(pom, "sqlite-jdbc.version");
        String dependencyCheck = property(pom, "dependency-check.maven.plugin.version");
        long modules = Pattern.compile("<module>[^<]+</module>").matcher(pom).results().count();

        assertTrue(readme.contains("SQLite JDBC            " + sqlite));
        assertTrue(readme.contains("OWASP Dependency-Check " + dependencyCheck));
        assertTrue(readme.contains("morpheus-coverage-report"));
        assertTrue(status.contains("Maven modules              " + modules));
        assertTrue(status.contains("SQLite JDBC                " + sqlite));
        assertTrue(status.contains("OWASP Dependency-Check     " + dependencyCheck));
        assertTrue(status.contains("#287"), "the active documentation must retain the external NVD secret follow-up");
        assertTrue(status.contains("#185"), "the active documentation must retain the real-release qualification follow-up");
        assertEquals(18, modules, "adding/removing a reactor module requires an explicit architecture review");
    }

    private static String property(String pom, String name) {
        Matcher matcher = Pattern.compile("<" + Pattern.quote(name) + ">([^<]+)</" + Pattern.quote(name) + ">")
                .matcher(pom);
        assertTrue(matcher.find(), "missing root POM property: " + name);
        return matcher.group(1).trim();
    }

    private static String read(Path root, String relative) throws IOException {
        return Files.readString(root.resolve(relative));
    }

    private static Path repoRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("pom.xml"))
                    && Files.isDirectory(current.resolve("morpheus-application"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("MORPHEUS repository root not found");
    }
}
