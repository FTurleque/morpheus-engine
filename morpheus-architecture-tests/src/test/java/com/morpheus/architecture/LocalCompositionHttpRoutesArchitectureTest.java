package com.morpheus.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalCompositionHttpRoutesArchitectureTest {

    @Test
    void compositionRoutesStayExtractedAndTransportFree() throws IOException {
        String server = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusHttpServer"));
        String routes = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusCompositionHttpRoutes"));

        assertTrue(server.contains("MorpheusCompositionHttpRoutes compositionRoutes"));
        assertTrue(server.contains("new MorpheusCompositionHttpRoutes(this.compositionService)"));
        assertTrue(server.contains("compositionRoutes.route(method, segments, query, projectId)"));
        assertFalse(server.contains("private MorpheusHttpRouteResponse routeComposition("));
        assertFalse(server.contains("unknown composition route"));

        assertTrue(routes.contains("MorpheusCompositionApiService compositionService"));
        assertTrue(routes.contains("MorpheusHttpRouteGuards.requireMethod(method, \"GET\")"));
        assertTrue(routes.contains("compositionService.status(projectId)"));
        assertTrue(routes.contains("compositionService.conflicts(projectId, offset, limit)"));
        assertTrue(routes.contains("query.rejectUnknown(Set.of(\"offset\", \"limit\"))"));
        assertTrue(routes.contains("unknown composition route"));
        assertFalse(routes.contains("HttpExchange"));
        assertFalse(routes.contains("HttpServer"));
        assertFalse(routes.contains("JsonMapper"));
        assertFalse(routes.contains("MorpheusHttpRequestDecoder"));
        assertFalse(routes.contains("MorpheusHttpResponseWriter"));
        assertFalse(routes.contains("MorpheusHttpPathParser"));
        assertFalse(routes.contains("MorpheusRemoteRoutePolicy"));
        assertFalse(routes.contains("MorpheusRemoteRole"));
    }
}
