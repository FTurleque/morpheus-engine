package com.morpheus.architecture.m21;

import com.morpheus.architecture.ProductionSources;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.morpheus.architecture.RepositoryRoot;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductVersionSourceOfTruthTest {
    @Test
    void activeRuntimeSourcesContainNoHistoricalVersionFallback() throws IOException {
        Path root = RepositoryRoot.locate();
        try (var files = Files.walk(root, 16)) {
            List<Path> offenders = files
                    .filter(Files::isRegularFile)
                    .filter(path -> path.toString().replace('\\', '/').contains("/src/main/java/"))
                    .filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> {
                        try {
                            String source = Files.readString(path);
                            return source.contains("0.1.0-SNAPSHOT") || source.contains("FALLBACK_VERSION");
                        } catch (IOException failure) {
                            throw new IllegalStateException(failure);
                        }
                    })
                    .toList();
            assertTrue(offenders.isEmpty(), "active runtime sources contain historical product-version fallback: " + offenders);
        }
    }

    @Test
    void publicProductSurfacesDelegateToCanonicalMetadata() throws IOException {
        Path root = RepositoryRoot.locate();
        assertContains(ProductionSources.mainSource("morpheus-api", "MorpheusApiService"),
                "ProductMetadata.version()");
        assertContains(ProductionSources.mainSource("morpheus-cli", "MorpheusProductCli"),
                "ProductMetadata.version()");
        assertContains(ProductionSources.mainSource("morpheus-mcp", "MorpheusProductMcpTools"),
                "ProductMetadata.current()");
        String metadata = Files.readString(root.resolve(
                "morpheus-application/src/main/java/com/morpheus/application/product/ProductMetadata.java"));
        assertTrue(metadata.contains("PROJECT_VERSION_PROPERTY"));
        assertTrue(metadata.contains("DEVELOPMENT_VERSION"));
        assertFalse(metadata.contains("0.1.0-SNAPSHOT"));
    }

    private static void assertContains(Path path, String expected) throws IOException {
        assertTrue(Files.readString(path).contains(expected), path + " must delegate product version to ProductMetadata");
    }
}
