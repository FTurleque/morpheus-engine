package com.morpheus.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
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
    private static final Pattern MODULE = Pattern.compile("<module>([^<]+)</module>");

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
        assertEquals("860", ratchets.get("testsMinimum"));
        assertEquals("265", ratchets.get("architectureTestsMinimum"));
        assertEquals("0.510", ratchets.get("lineCoverageMinimum"));
        assertEquals("0.435", ratchets.get("branchCoverageMinimum"));

        String linux = Files.readString(root.resolve("scripts/validate-m21.sh"));
        String windows = Files.readString(root.resolve("scripts/validate-m21.ps1"));
        assertTrue(linux.contains("config/m21-quality-ratchets.properties"));
        assertTrue(windows.contains("config\\m21-quality-ratchets.properties"));
        assertFalse(linux.contains("line < 0.506"), "Linux validator must not retain the old embedded line ratchet");
        assertFalse(windows.contains("-lt 0.506"), "Windows validator must not retain the old embedded line ratchet");
    }

    @Test
    void rootBuildEnforcerPinsQualifiedJavaAndDependencyConvergence() throws Exception {
        String pom = Files.readString(repositoryRoot().resolve("pom.xml"));
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
