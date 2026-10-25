package com.morpheus.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalRootHttpRoutesArchitectureTest {

    @Test
    void localRootProductAndOperabilityRoutingStaysExtracted() throws IOException {
        String server = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusHttpServer"));
        String routes = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusRootHttpRoutes"));

        assertServerDelegatesToRootRoutes(server);
        assertRootRoutesImplementRouting(routes);
        assertRootRoutesDoNotLeakOtherHttpConcerns(routes);
    }

    private void assertServerDelegatesToRootRoutes(String server) {
        assertTrue(server.contains("private final MorpheusRootHttpRoutes rootRoutes;"));
        assertTrue(server.contains("rootRoutes.handles(segments)"));
        assertTrue(server.contains("rootRoutes.route(method, segments, query)"));
        assertFalse(server.contains("private final MorpheusOperabilityApiService operabilityService;"));
        assertFalse(server.contains("return ok(service.health())"));
        assertFalse(server.contains("operabilityService.readiness()"));
        assertFalse(server.contains("operabilityService.metrics()"));
        assertFalse(server.contains("return ok(service.version())"));
    }

    private void assertRootRoutesImplementRouting(String routes) {
        assertTrue(routes.contains("final class MorpheusRootHttpRoutes"));
        assertTrue(routes.contains("MorpheusApiService service"));
        assertTrue(routes.contains("MorpheusOperabilityApiService operabilityService"));
        assertTrue(routes.contains("case \"health\", \"readiness\", \"metrics\", \"version\" -> true"));
        assertTrue(routes.contains("MorpheusHttpRouteGuards.requireMethod(method, \"GET\")"));
        assertTrue(routes.contains("query.rejectUnknown(Set.of())"));
        assertTrue(routes.contains("case \"health\" -> ok(service.health())"));
        assertTrue(routes.contains("case \"readiness\" -> readiness()"));
        assertTrue(routes.contains("case \"metrics\" -> ok(operabilityService.metrics())"));
        assertTrue(routes.contains("case \"version\" -> ok(service.version())"));
        assertTrue(routes.contains("\"READY\".equals(readiness.status()) ? 200 : 503"));
    }

    private void assertRootRoutesDoNotLeakOtherHttpConcerns(String routes) {
        assertFalse(routes.contains("com.sun.net.httpserver"));
        assertFalse(routes.contains("MorpheusHttpRequestDecoder"));
        assertFalse(routes.contains("MorpheusHttpResponseWriter"));
        assertFalse(routes.contains("MorpheusRemoteRoutePolicy"));
        assertFalse(routes.contains("MorpheusRemoteRole"));
        assertFalse(routes.contains("MorpheusProjectRootHttpRoutes"));
        assertFalse(routes.contains("MorpheusProjectSyncHttpRoutes"));
    }
}
