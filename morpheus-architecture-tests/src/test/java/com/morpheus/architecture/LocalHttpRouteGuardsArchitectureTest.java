package com.morpheus.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalHttpRouteGuardsArchitectureTest {

    @Test
    void localRouteGuardsStayExtractedAndPure() throws IOException {
        Path root = repositoryRoot();
        String server = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusHttpServer.java"));
        String projectSyncRoutes = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusProjectSyncHttpRoutes.java"));
        String guards = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusHttpRouteGuards.java"));
        String localHttpConsumers = server + "\n" + projectSyncRoutes;

        assertTrue(server.contains("MorpheusHttpRouteGuards.requireMethod(actual, expected)"));
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
