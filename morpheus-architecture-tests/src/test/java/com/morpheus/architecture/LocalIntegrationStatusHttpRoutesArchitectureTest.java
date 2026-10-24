package com.morpheus.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalIntegrationStatusHttpRoutesArchitectureTest {

    @Test
    void integrationStatusRoutesStayExtractedAndTransportFree() throws IOException {
        String server = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusHttpServer"));
        String routes = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusIntegrationStatusHttpRoutes"));

        assertTrue(server.contains("MorpheusIntegrationStatusHttpRoutes integrationStatusRoutes"));
        assertTrue(server.contains("new MorpheusIntegrationStatusHttpRoutes("));
        assertTrue(server.contains("this.externalReferenceService, this.augmentedContextService"));
        assertTrue(server.contains("integrationStatusRoutes.route(method, segments, query)"));
        assertFalse(server.contains("case \"minos\" -> ok(externalReferenceService.minosStatus())"));
        assertFalse(server.contains("case \"nexus\" -> ok(augmentedContextService.nexusStatus())"));
        assertFalse(server.contains("unknown integration: "));

        assertTrue(routes.contains("MorpheusExternalReferenceApiService externalReferenceService"));
        assertTrue(routes.contains("MorpheusAugmentedContextApiService augmentedContextService"));
        assertTrue(routes.contains("MorpheusHttpRouteGuards.requireMethod(method, \"GET\")"));
        assertTrue(routes.contains("query.rejectUnknown(Set.of())"));
        assertTrue(routes.contains("externalReferenceService.minosStatus()"));
        assertTrue(routes.contains("augmentedContextService.nexusStatus()"));
        assertTrue(routes.contains("unknown integration: "));
        assertFalse(routes.contains("HttpExchange"));
        assertFalse(routes.contains("HttpServer"));
        assertFalse(routes.contains("JsonMapper"));
        assertFalse(routes.contains("MorpheusHttpResponseWriter"));
        assertFalse(routes.contains("MorpheusHttpPathParser"));
        assertFalse(routes.contains("MorpheusRemoteRoutePolicy"));
        assertFalse(routes.contains("MorpheusRemoteRole"));
    }
}
