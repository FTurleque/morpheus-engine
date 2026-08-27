package com.morpheus.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Guards active repository documentation against drifting from root Maven facts. */
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
        List<Path> activeStatusPages = List.of(
                root.resolve("README.md"),
                root.resolve("docs/README.md"),
                root.resolve("docs/user/README.md"),
                root.resolve("docs/developer/README.md"),
                root.resolve("docs/developer/BUILD_AND_TEST.md"),
                root.resolve("docs/governance/ROADMAP.md"),
                root.resolve("docs/governance/DOCUMENTATION_STATUS.md"),
                root.resolve("docs/validation/README.md"));

        for (Path page : activeStatusPages) {
            String content = Files.readString(page);
            assertTrue(content.contains(version),
                    () -> root.relativize(page) + " must mention current development baseline " + version);
        }
    }

    @Test
    void activeStatusPagesDoNotAdvertiseCompletedD2AsPending() throws Exception {
        Path root = repositoryRoot();
        List<Path> statusPages = List.of(
                root.resolve("README.md"),
                root.resolve("docs/README.md"),
                root.resolve("docs/user/README.md"),
                root.resolve("docs/governance/ROADMAP.md"),
                root.resolve("docs/governance/DOCUMENTATION_STATUS.md"),
                root.resolve("docs/validation/README.md"));
        List<String> obsoleteMarkers = List.of(
                "D2 EN COURS",
                "D2 — Repository Hardening en cours",
                "PENDING LOCAL QUALIFICATION",
                "LOCAL QUALIFICATION PENDING",
                "#120 OPEN");

        for (Path page : statusPages) {
            String content = Files.readString(page);
            for (String obsolete : obsoleteMarkers) {
                assertFalse(content.contains(obsolete),
                        () -> root.relativize(page) + " still contains obsolete D2 marker: " + obsolete);
            }
        }
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
