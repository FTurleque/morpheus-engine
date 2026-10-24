package com.morpheus.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalPortfolioHttpRoutesArchitectureTest {

    @Test
    void portfolioRoutesStayExtractedAndReuseSharedHttpComponents() throws IOException {
        String server = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusHttpServer"));
        String routes = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusPortfolioHttpRoutes"));

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
}
