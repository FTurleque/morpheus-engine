package com.morpheus.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalHttpRouteResponseArchitectureTest {

    @Test
    void localRouteResponseCarrierStaysExtractedAndPure() throws IOException {
        String server = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusHttpServer"));
        String response = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusHttpRouteResponse"));

        assertTrue(server.contains("MorpheusHttpRouteResponse"));
        assertFalse(server.contains("private record RouteResponse"));

        assertTrue(response.contains("record MorpheusHttpRouteResponse(int status, Object data)"));
        assertTrue(response.contains("route status must be between 200 and 599"));
        assertTrue(response.contains("Objects.requireNonNull(data, \"data\")"));
        assertFalse(response.contains("HttpExchange"));
        assertFalse(response.contains("MorpheusApiService"));
        assertFalse(response.contains("ApiFailure"));
        assertFalse(response.contains("MorpheusRemoteRoutePolicy"));
        assertFalse(response.contains("MorpheusRemoteRole"));
    }
}
