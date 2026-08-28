package com.morpheus.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalHttpDispatchersArchitectureTest {

    @Test
    void localServerStaysAThinTopLevelDispatcher() throws IOException {
        Path root = repositoryRoot();
        String server = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusHttpServer.java"));
        String projects = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusProjectsHttpRoutes.java"));
        String rootRoutes = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusRootHttpRoutes.java"));

        assertTrue(server.contains("MorpheusProjectsHttpRoutes projectsRoutes"));
        assertTrue(server.contains("MorpheusRootHttpRoutes rootRoutes"));
        assertTrue(server.contains("projectsRoutes.route(exchange, method, segments, query)"));
        assertTrue(server.contains("rootRoutes.route(method, segments, query)"));
        assertFalse(server.contains("private MorpheusHttpRouteResponse routeSync("));
        assertFalse(server.contains("private MorpheusHttpRouteResponse routeSyncStatus("));
        assertFalse(server.contains("projects supports GET and POST"));
        assertFalse(server.contains("service.listProjects()"));
        assertFalse(server.contains("service.health()"));
        assertFalse(server.contains("operabilityService.readiness()"));
        assertFalse(server.contains("MorpheusChangesHttpRoutes changesRoutes"));
        assertFalse(server.contains("MorpheusRequirementsHttpRoutes requirementsRoutes"));

        assertTrue(projects.contains("MorpheusHttpRequestDecoder requestDecoder"));
        assertTrue(projects.contains("MorpheusCompositionHttpRoutes compositionRoutes"));
        assertTrue(projects.contains("MorpheusRequirementsHttpRoutes requirementsRoutes"));
        assertTrue(projects.contains("MorpheusChangesHttpRoutes changesRoutes"));
        assertTrue(projects.contains("case \"sync\""));
        assertTrue(projects.contains("case \"sync-status\""));
        assertTrue(projects.contains("projects supports GET and POST"));
        assertFalse(projects.contains("HttpServer"));
        assertFalse(projects.contains("MorpheusHttpResponseWriter"));
        assertFalse(projects.contains("MorpheusRemoteRoutePolicy"));
        assertFalse(projects.contains("MorpheusRemoteRole"));

        assertTrue(rootRoutes.contains("case \"health\""));
        assertTrue(rootRoutes.contains("case \"readiness\""));
        assertTrue(rootRoutes.contains("case \"metrics\""));
        assertTrue(rootRoutes.contains("case \"version\""));
        assertFalse(rootRoutes.contains("HttpExchange"));
        assertFalse(rootRoutes.contains("MorpheusHttpRequestDecoder"));
        assertFalse(rootRoutes.contains("MorpheusRemoteRoutePolicy"));
        assertFalse(rootRoutes.contains("MorpheusRemoteRole"));
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
