package com.morpheus.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalHttpRequestDecoderArchitectureTest {

    @Test
    void localHttpRequestDecodingStaysExtractedStrictAndBounded() throws IOException {
        Path root = repositoryRoot();
        String server = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusHttpServer.java"));
        String projects = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusProjectsHttpRoutes.java"));
        String portfolio = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusPortfolioHttpRoutes.java"));
        String decoder = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusHttpRequestDecoder.java"));

        assertTrue(server.contains("private final MorpheusHttpRequestDecoder requestDecoder;"));
        assertTrue(server.contains("this.requestDecoder = new MorpheusHttpRequestDecoder("));
        assertTrue(server.contains("new MorpheusProjectsHttpRoutes("));
        assertTrue(server.contains("new MorpheusPortfolioHttpRoutes(portfolioService, this.requestDecoder)"));
        assertTrue(projects.contains("MorpheusHttpRequestDecoder requestDecoder"));
        assertTrue(projects.contains("requestDecoder.readRequiredJson("));
        assertTrue(projects.contains("requestDecoder.readOptionalJson("));
        assertTrue(portfolio.contains("MorpheusHttpRequestDecoder requestDecoder"));
        assertFalse(server.contains("JsonMapper"));
        assertFalse(server.contains("DeserializationFeature"));
        assertFalse(server.contains("TimedBoundedInputReader"));
        assertFalse(server.contains("requireJsonContentType("));
        assertTrue(decoder.contains("final class MorpheusHttpRequestDecoder"));
        assertTrue(decoder.contains("FAIL_ON_UNKNOWN_PROPERTIES"));
        assertTrue(decoder.contains("FAIL_ON_TRAILING_TOKENS"));
        assertTrue(decoder.contains("TimedBoundedInputReader.read("));
        assertTrue(decoder.contains("Content-Type application/json is required"));
        assertFalse(decoder.contains("MorpheusApiService"));
        assertFalse(decoder.contains("MorpheusRemoteRoutePolicy"));
    }

    private Path repositoryRoot() {
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
