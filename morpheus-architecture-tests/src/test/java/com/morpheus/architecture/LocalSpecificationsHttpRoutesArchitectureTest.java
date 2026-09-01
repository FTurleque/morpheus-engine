package com.morpheus.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalSpecificationsHttpRoutesArchitectureTest {

    @Test
    void specificationRoutesStayExtractedAndTransportFree() throws IOException {
        Path root = repositoryRoot();
        String server = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusHttpServer.java"));
        String routes = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusSpecificationsHttpRoutes.java"));

        assertTrue(server.contains("MorpheusSpecificationsHttpRoutes specificationsRoutes"));
        assertTrue(server.contains("new MorpheusSpecificationsHttpRoutes(this.service)"));
        assertTrue(server.contains("specificationsRoutes.route(method, segments, query, projectId)"));
        assertFalse(server.contains("private MorpheusHttpRouteResponse routeSpecifications("));
        assertFalse(server.contains("unknown specifications route"));

        assertTrue(routes.contains("private final MorpheusSpecificationQueryApiService service;"));
        assertTrue(routes.contains("MorpheusSpecificationsHttpRoutes(MorpheusApiService facade)"));
        assertTrue(routes.contains("specificationQueryService()"));
        assertTrue(routes.contains("MorpheusHttpRouteGuards.requireMethod(method, \"GET\")"));
        assertTrue(routes.contains("service.listSpecifications(projectId, page(query))"));
        assertTrue(routes.contains("service.specification(projectId, segments.get(3))"));
        assertTrue(routes.contains("service.specificationContext(projectId, segments.get(3), page(query))"));
        assertTrue(routes.contains("query.rejectUnknown(Set.of(\"offset\", \"limit\"))"));
        assertTrue(routes.contains("unknown specifications route"));
        assertFalse(routes.contains("private final MorpheusApiService service;"));
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
