package com.morpheus.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalExternalReferenceHttpRoutesArchitectureTest {

    @Test
    void externalReferenceRoutesStayExtractedAndTransportFree() throws IOException {
        Path root = repositoryRoot();
        String server = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusHttpServer.java"));
        String projects = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusProjectsHttpRoutes.java"));
        String routes = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusExternalReferenceHttpRoutes.java"));

        assertFalse(server.contains("MorpheusExternalReferenceHttpRoutes externalReferenceRoutes"));
        assertTrue(projects.contains("MorpheusExternalReferenceHttpRoutes externalReferenceRoutes"));
        assertTrue(projects.contains("new MorpheusExternalReferenceHttpRoutes("));
        assertTrue(projects.contains("externalReferenceRoutes.route(method, segments, query, projectId)"));
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
