package com.morpheus.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalHttpRouteGuardsArchitectureTest {

    @Test
    void localRouteGuardsStayExtractedAndPure() throws IOException {
        String server = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusHttpServer"));
        String rootRoutes = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusRootHttpRoutes"));
        String projectSyncRoutes = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusProjectSyncHttpRoutes"));
        String guards = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusHttpRouteGuards"));
        String localHttpConsumers = server + "\n" + rootRoutes + "\n" + projectSyncRoutes;

        assertTrue(rootRoutes.contains("MorpheusHttpRouteGuards.requireMethod(method, \"GET\")"));
        assertFalse(server.contains("private void requireMethod(String actual, String expected)"));
        assertTrue(projectSyncRoutes.contains("MorpheusHttpRouteGuards.requireExactSegments(segments, 3)"));
        assertTrue(projectSyncRoutes.contains("MorpheusHttpRouteGuards.requireMethod(method, \"POST\")"));
        assertTrue(projectSyncRoutes.contains("MorpheusHttpRouteGuards.requireMethod(method, \"GET\")"));
        assertFalse(localHttpConsumers.contains("throw ApiFailure.methodNotAllowed(\"expected HTTP \""));
        assertFalse(localHttpConsumers.contains("throw ApiFailure.notFound(\"unknown API route\")"));

        assertTrue(guards.contains("ApiFailure.methodNotAllowed(\"expected HTTP \" + expected + \" but received \" + actual)"));
        assertTrue(guards.contains("ApiFailure.notFound(\"unknown API route\")"));
        assertFalse(guards.contains("HttpExchange"));
        assertFalse(guards.contains("HttpServer"));
        assertFalse(guards.contains("MorpheusApiService"));
        assertFalse(guards.contains("MorpheusRemoteRoutePolicy"));
        assertFalse(guards.contains("MorpheusRemoteRole"));
    }
}
