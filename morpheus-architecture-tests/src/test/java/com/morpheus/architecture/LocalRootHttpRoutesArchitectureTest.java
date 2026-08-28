package com.morpheus.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalRootHttpRoutesArchitectureTest {

    @Test
    void localRootProductAndOperabilityRoutingStaysExtracted() throws IOException {
        Path root = repositoryRoot();
        String server = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusHttpServer.java"));
        String routes = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusRootHttpRoutes.java"));

        assertTrue(server.contains("private final MorpheusRootHttpRoutes rootRoutes;"));
        assertTrue(server.contains("rootRoutes.handles(segments)"));
        assertTrue(server.contains("rootRoutes.route(method, segments, query)"));
        assertFalse(server.contains("private final MorpheusOperabilityApiService operabilityService;"));
        assertFalse(server.contains("return ok(service.health())"));
        assertFalse(server.contains("operabilityService.readiness()"));
        assertFalse(server.contains("operabilityService.metrics()"));
        assertFalse(server.contains("return ok(service.version())"));

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
        assertFalse(routes.contains("com.sun.net.httpserver"));
        assertFalse(routes.contains("MorpheusHttpRequestDecoder"));
        assertFalse(routes.contains("MorpheusHttpResponseWriter"));
        assertFalse(routes.contains("MorpheusRemoteRoutePolicy"));
        assertFalse(routes.contains("MorpheusRemoteRole"));
        assertFalse(routes.contains("MorpheusProjectRootHttpRoutes"));
        assertFalse(routes.contains("MorpheusProjectSyncHttpRoutes"));
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
