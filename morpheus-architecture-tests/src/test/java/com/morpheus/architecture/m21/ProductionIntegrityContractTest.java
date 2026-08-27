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
        String compositionMcp = readJavaTree(root.resolve("morpheus-mcp/src/main/java"));
        String lifecycleCli = readJavaTree(root.resolve("morpheus-cli/src/main/java"));

        assertTrue(main.contains("Product integrity (M21)"));
        assertTrue(productCli.contains("update-check"));
        assertTrue(mcp.contains("get_product_info"));
        assertTrue(mcp.contains("check_product_update"));
        assertTrue(http.contains("segments.getFirst().equals(\"version\")"));
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
        assertTrue(integrity.contains("820 PASS"));
        assertTrue(integrity.contains("258 PASS"));
        assertTrue(integrity.contains("50 % aggregate"));
        assertTrue(integrity.contains("42 % aggregate"));
        assertTrue(integrity.contains("Changed lines       >= 80 %"));
        assertTrue(integrity.contains("CycloneDX"));
        assertTrue(integrity.contains("update discovery != automatic update"));
        assertTrue(integrity.contains("checksum != signature"));
        assertTrue(integrity.contains("`file:` et `https:`"));
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
