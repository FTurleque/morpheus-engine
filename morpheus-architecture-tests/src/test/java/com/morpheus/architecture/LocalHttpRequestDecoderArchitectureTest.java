package com.morpheus.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalHttpRequestDecoderArchitectureTest {

    @Test
    void localHttpRequestDecodingStaysExtractedStrictAndBounded() throws IOException {
        String server = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusHttpServer"));
        String projectRootRoutes = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusProjectRootHttpRoutes"));
        String projectSyncRoutes = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusProjectSyncHttpRoutes"));
        String decoder = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusHttpRequestDecoder"));
        String localHttpConsumers = server + "\n" + projectRootRoutes + "\n" + projectSyncRoutes;

        assertTrue(server.contains("private final MorpheusHttpRequestDecoder requestDecoder;"));
        assertTrue(server.contains("new MorpheusProjectRootHttpRoutes(this.service, this.requestDecoder)"));
        assertTrue(projectRootRoutes.contains("MorpheusHttpRequestDecoder requestDecoder"));
        assertTrue(projectRootRoutes.contains("requestDecoder.readRequiredJson("));
        assertTrue(projectSyncRoutes.contains("MorpheusHttpRequestDecoder requestDecoder"));
        assertTrue(projectSyncRoutes.contains("requestDecoder.readOptionalJson("));
        assertFalse(server.contains("requestDecoder.readRequiredJson("));
        assertFalse(localHttpConsumers.contains("JsonMapper"));
        assertFalse(localHttpConsumers.contains("DeserializationFeature"));
        assertFalse(localHttpConsumers.contains("TimedBoundedInputReader"));
        assertFalse(localHttpConsumers.contains("requireJsonContentType("));
        assertTrue(decoder.contains("final class MorpheusHttpRequestDecoder"));
        assertTrue(decoder.contains("FAIL_ON_UNKNOWN_PROPERTIES"));
        assertTrue(decoder.contains("FAIL_ON_TRAILING_TOKENS"));
        assertTrue(decoder.contains("HttpRequestBodyReader.read("));
        assertTrue(Files.readString(ProductionSources.mainSource("morpheus-api", "HttpRequestBodyReader"))
                .contains("TimedBoundedInputReader.read("));
        assertTrue(decoder.contains("Content-Type application/json is required"));
        assertFalse(decoder.contains("MorpheusApiService"));
        assertFalse(decoder.contains("MorpheusRemoteRoutePolicy"));
    }
}
