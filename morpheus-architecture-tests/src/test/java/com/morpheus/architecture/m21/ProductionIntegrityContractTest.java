package com.morpheus.architecture.m21;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.morpheus.application.product.ProductMetadata;
import com.morpheus.mcp.MorpheusMcpServer;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class ProductionIntegrityContractTest {
    @Test
    void productVersionHasOneBuildDerivedSourceAcrossPackagedSurfaces() {
        assertEquals(System.getProperty("morpheus.project.version"), ProductMetadata.version());
        assertEquals(ProductMetadata.version(), MorpheusMcpServer.SERVER_VERSION);
    }

    @Test
    void publicSurfaceManifestIsCompleteExplicitAndBackedByAdapters() throws IOException {
        Path root = repoRoot();
        List<String> lines = Files.readAllLines(root.resolve("contracts/public-surfaces.tsv"));
        List<String[]> entries = new ArrayList<>();
        for (String line : lines) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] columns = line.split("\\t", -1);
            assertEquals(6, columns.length, "each public surface row must have six columns: " + line);
            assertTrue(columns[1].equals("READ") || columns[1].equals("WRITE"), "intent must be READ or WRITE");
            assertFalse(columns[2].isBlank(), "CLI shape must be explicit");
            assertFalse(columns[3].isBlank(), "MCP shape must be explicit");
            assertFalse(columns[4].isBlank(), "HTTP shape or explicit omission must be present");
            entries.add(columns);
        }
        assertTrue(entries.size() >= 6, "M21 convergence manifest must cover the critical public capabilities");

        String manifest = Files.readString(root.resolve("contracts/public-surfaces.tsv"));
        assertTrue(manifest.contains("product.version\tREAD\tversion\tget_product_info\tGET /api/v1/version"));
        assertTrue(manifest.contains("product.update-discovery\tREAD\tupdate-check --manifest\tcheck_product_update\tEXPLICITLY_NOT_EXPOSED"));
        assertTrue(manifest.contains("lifecycle.apply\tWRITE"));

        String main = Files.readString(root.resolve("morpheus-cli/src/main/java/com/morpheus/cli/MorpheusMain.java"));
        String productCli = Files.readString(root.resolve("morpheus-cli/src/main/java/com/morpheus/cli/MorpheusProductCli.java"));
        String mcp = Files.readString(root.resolve("morpheus-mcp/src/main/java/com/morpheus/mcp/MorpheusProductMcpTools.java"));
        String http = Files.readString(root.resolve("morpheus-api/src/main/java/com/morpheus/api/MorpheusHttpServer.java"));
        String rootHttp = Files.readString(root.resolve("morpheus-api/src/main/java/com/morpheus/api/MorpheusRootHttpRoutes.java"));
        String compositionMcp = readJavaTree(root.resolve("morpheus-mcp/src/main/java"));
        String lifecycleCli = readJavaTree(root.resolve("morpheus-cli/src/main/java"));

        assertTrue(main.contains("Product integrity (M21)"));
        assertTrue(productCli.contains("update-check"));
        assertTrue(mcp.contains("get_product_info"));
        assertTrue(mcp.contains("check_product_update"));
        assertTrue(http.contains("rootRoutes.route(method, segments, query)"));
        assertTrue(rootHttp.contains("case \"version\" -> ok(service.version())"));
        assertTrue(compositionMcp.contains("get_composition_status"));
        assertTrue(compositionMcp.contains("list_composition_conflicts"));
        assertTrue(compositionMcp.contains("apply_change_lifecycle_transition"));
        assertTrue(lifecycleCli.contains("lifecycle apply"));
    }

    @Test
    void updateDiscoveryHasNoImplicitStartupEntryPoint() throws IOException {
        Path root = repoRoot();
        Path productionRoot = root.resolve("morpheus-application/src/main/java").getParent().getParent().getParent().getParent();
        List<Path> constructionSites = new ArrayList<>();
        try (var files = Files.walk(root)) {
            for (Path file : files
                    .filter(path -> path.toString().contains("src" + java.io.File.separator + "main" + java.io.File.separator + "java"))
                    .filter(path -> path.toString().endsWith(".java"))
                    .toList()) {
                if (Files.readString(file).contains("new UpdateDiscoveryService()")) {
                    constructionSites.add(root.relativize(file));
                }
            }
        }

        assertEquals(1, constructionSites.size(), "update discovery must only be constructed by the explicit CLI operation");
        assertTrue(constructionSites.contains(Path.of("morpheus-cli/src/main/java/com/morpheus/cli/MorpheusProductCli.java")));
        assertTrue(Files.isDirectory(productionRoot), "repository production source tree must remain discoverable");
    }

    @Test
    void documentationReferencesMachineContractInsteadOfRedefiningIt() throws IOException {
        Path root = repoRoot();
        String publicSurfaces = Files.readString(root.resolve("docs/reference/PUBLIC_SURFACES.md"));
        String integrity = Files.readString(root.resolve("docs/developer/PRODUCTION_INTEGRITY.md"));
        String userIntegrity = Files.readString(root.resolve("docs/user/PRODUCT_INTEGRITY.md"));

        assertTrue(publicSurfaces.contains("../../contracts/public-surfaces.tsv"));
        assertTrue(publicSurfaces.contains("EXPLICITLY_NOT_EXPOSED"));
        Ratchets ratchets = Ratchets.load(root.resolve("config/m21-quality-ratchets.properties"));
        assertTrue(integrity.contains(ratchets.tests() + " PASS"),
                "PRODUCTION_INTEGRITY.md must quote the normative Surefire ratchet " + ratchets.tests());
        assertTrue(integrity.contains(ratchets.architectureTests() + " PASS"),
                "PRODUCTION_INTEGRITY.md must quote the normative architecture ratchet " + ratchets.architectureTests());
        assertTrue(integrity.contains(ratchets.lineCoverage() + " % aggregate"),
                "PRODUCTION_INTEGRITY.md must quote the normative line ratchet " + ratchets.lineCoverage());
        assertTrue(integrity.contains(ratchets.branchCoverage() + " % aggregate"),
                "PRODUCTION_INTEGRITY.md must quote the normative branch ratchet " + ratchets.branchCoverage());
        assertTrue(integrity.contains("Changed lines       >= 80 %"));
        assertTrue(integrity.contains("Changed branches    >= 70 %"));
        assertTrue(integrity.contains("config/m21-quality-ratchets.properties"));
        assertTrue(integrity.contains("convergence des dépendances transitives"));
        assertTrue(integrity.contains("HttpRequestBodyReader"));
        assertTrue(integrity.contains("CycloneDX"));
        assertTrue(integrity.contains("update discovery != automatic update"));
        assertTrue(integrity.contains("GitHub attestation"));
        assertTrue(integrity.contains("attestationUri=https://"));
        assertTrue(integrity.contains("Un manifeste local `file:`"));
        assertTrue(integrity.contains("Un manifeste distant `https:`"));
        assertFalse(userIntegrity.contains("\nhttp:\n"));
    }

    private String readJavaTree(Path root) throws IOException {
        StringBuilder result = new StringBuilder();
        try (var files = Files.walk(root)) {
            for (Path file : files.filter(path -> path.toString().endsWith(".java")).sorted().toList()) {
                result.append(Files.readString(file)).append('\n');
            }
        }
        return result.toString();
    }

    /** Reads the single normative source of the M21 ratchets so documentation gates can never pin stale numbers. */
    private record Ratchets(String tests, String architectureTests, String lineCoverage, String branchCoverage) {
        private static Ratchets load(Path path) throws IOException {
            Properties properties = new Properties();
            try (var reader = Files.newBufferedReader(path)) {
                properties.load(reader);
            }
            return new Ratchets(
                    properties.getProperty("testsMinimum"),
                    properties.getProperty("architectureTestsMinimum"),
                    percentage(properties.getProperty("lineCoverageMinimum")),
                    percentage(properties.getProperty("branchCoverageMinimum")));
        }

        private static String percentage(String decimal) {
            return String.format(Locale.ROOT, "%.1f", Double.parseDouble(decimal) * 100.0d);
        }
    }

    private Path repoRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("contracts/public-surfaces.tsv"))
                    && Files.isRegularFile(current.resolve("pom.xml"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("cannot locate MORPHEUS repository root");
    }
}
