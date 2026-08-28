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
        String projectSyncRoutes = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusProjectSyncHttpRoutes.java"));
        String decoder = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusHttpRequestDecoder.java"));
        String localHttpConsumers = server + "\n" + projectSyncRoutes;

        assertTrue(server.contains("private final MorpheusHttpRequestDecoder requestDecoder;"));
        assertTrue(server.contains("requestDecoder.readRequiredJson(exchange, type)"));
        assertTrue(projectSyncRoutes.contains("MorpheusHttpRequestDecoder requestDecoder"));
        assertTrue(projectSyncRoutes.contains("requestDecoder.readOptionalJson("));
        assertFalse(localHttpConsumers.contains("JsonMapper"));
        assertFalse(localHttpConsumers.contains("DeserializationFeature"));
        assertFalse(localHttpConsumers.contains("TimedBoundedInputReader"));
        assertFalse(localHttpConsumers.contains("requireJsonContentType("));
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
