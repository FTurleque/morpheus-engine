package com.morpheus.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalExternalReferenceHttpRoutesArchitectureTest {

    @Test
    void externalReferenceRoutesStayExtractedAndTransportFree() throws IOException {
        String server = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusHttpServer"));
        String routes = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusExternalReferenceHttpRoutes"));

        assertTrue(server.contains("MorpheusExternalReferenceHttpRoutes externalReferenceRoutes"));
        assertTrue(server.contains("new MorpheusExternalReferenceHttpRoutes(this.externalReferenceService)"));
        assertTrue(server.contains("externalReferenceRoutes.route(method, segments, query, projectId)"));
        assertFalse(server.contains("private MorpheusHttpRouteResponse routeExternalReferences("));

        assertTrue(routes.contains("MorpheusExternalReferenceApiService service"));
        assertTrue(routes.contains("MorpheusHttpRouteGuards.requireMethod(method, \"GET\")"));
        assertTrue(routes.contains("query.rejectUnknown(Set.of(\"ownerId\"))"));
        assertTrue(routes.contains("query.required(\"ownerId\")"));
        assertTrue(routes.contains("service.resolve(projectId, segments.get(3))"));
        assertTrue(routes.contains("unknown external-references route"));
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
