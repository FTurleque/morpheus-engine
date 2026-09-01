package com.morpheus.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalPortfolioHttpRoutesArchitectureTest {

    @Test
    void portfolioRoutesStayExtractedAndReuseSharedHttpComponents() throws IOException {
        Path root = repositoryRoot();
        String server = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusHttpServer.java"));
        String routes = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusPortfolioHttpRoutes.java"));

        assertTrue(server.contains("MorpheusPortfolioHttpRoutes portfolioRoutes"));
        assertTrue(server.contains("portfolioRoutes.route(exchange, method, segments, query)"));
        assertFalse(server.contains("private MorpheusHttpRouteResponse routePortfolios("));
        assertFalse(server.contains("PortfolioQueryService.MAX_PAGE_SIZE"));
        assertFalse(server.contains("unknown portfolio projects route"));
        assertFalse(server.contains("unknown portfolio API resource:"));

        assertTrue(routes.contains("MorpheusHttpRequestDecoder requestDecoder"));
        assertTrue(routes.contains("MorpheusHttpRouteGuards.requireMethod("));
        assertTrue(routes.contains("MorpheusHttpRouteGuards.requireExactSegments("));
        assertTrue(routes.contains("MorpheusHttpRouteResponse route("));
        assertTrue(routes.contains("MorpheusHttpQuery query"));
        assertFalse(routes.contains("JsonMapper"));
        assertFalse(routes.contains("CanonicalJsonSerializer"));
        assertFalse(routes.contains("MorpheusHttpResponseWriter"));
        assertFalse(routes.contains("MorpheusHttpPathParser"));
        assertFalse(routes.contains("MorpheusRemoteRoutePolicy"));
        assertFalse(routes.contains("MorpheusRemoteRole"));
        assertFalse(routes.contains("HttpServer"));
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
