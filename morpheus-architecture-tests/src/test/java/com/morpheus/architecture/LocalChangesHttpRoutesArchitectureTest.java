package com.morpheus.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalChangesHttpRoutesArchitectureTest {

    @Test
    void changesRoutesStayExtractedAndReuseSharedDecoder() throws IOException {
        Path root = repositoryRoot();
        String server = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusHttpServer.java"));
        String routes = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusChangesHttpRoutes.java"));

        assertTrue(server.contains("MorpheusChangesHttpRoutes changesRoutes"));
        assertTrue(server.contains("new MorpheusChangesHttpRoutes("));
        assertTrue(server.contains("changesRoutes.route(exchange, method, segments, query, projectId)"));
        assertFalse(server.contains("private MorpheusHttpRouteResponse routeChanges("));
        assertFalse(server.contains("unknown change subresource"));

        assertTrue(routes.contains("MorpheusApiService service"));
        assertTrue(routes.contains("MorpheusAugmentedContextApiService augmentedContextService"));
        assertTrue(routes.contains("MorpheusJarvisOrchestrationApiService jarvisOrchestrationService"));
        assertTrue(routes.contains("MorpheusControlledLifecycleApiService controlledLifecycleService"));
        assertTrue(routes.contains("MorpheusHttpRequestDecoder requestDecoder"));
        assertTrue(routes.contains("requestDecoder.readRequiredJson(exchange, AugmentedContextRequest.class)"));
        assertTrue(routes.contains("requestDecoder.readRequiredJson(exchange, TransitionCheckRequest.class)"));
        assertTrue(routes.contains("requestDecoder.readRequiredJson(exchange, LifecycleMutationRequest.class)"));
        assertTrue(routes.contains("MorpheusHttpRouteGuards.requireMethod(method, \"POST\")"));
        assertTrue(routes.contains("MorpheusHttpRouteGuards.requireMethod(method, \"GET\")"));
        assertTrue(routes.contains("unknown change subresource"));
        assertTrue(routes.contains("HttpExchange"));
        assertFalse(routes.contains("HttpServer"));
        assertFalse(routes.contains("JsonMapper"));
        assertFalse(routes.contains("MorpheusHttpResponseWriter"));
        assertFalse(routes.contains("MorpheusHttpPathParser"));
        assertFalse(routes.contains("MorpheusRemoteRoutePolicy"));
        assertFalse(routes.contains("MorpheusRemoteRole"));
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
