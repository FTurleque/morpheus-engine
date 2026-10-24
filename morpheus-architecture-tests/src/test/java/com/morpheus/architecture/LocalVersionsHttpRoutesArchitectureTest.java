package com.morpheus.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalVersionsHttpRoutesArchitectureTest {

    @Test
    void versionsRoutesStayExtractedAndTransportFree() throws IOException {
        String server = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusHttpServer"));
        String routes = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusVersionsHttpRoutes"));

        assertTrue(server.contains("MorpheusVersionsHttpRoutes versionsRoutes"));
        assertTrue(server.contains("new MorpheusVersionsHttpRoutes(this.service)"));
        assertTrue(server.contains("versionsRoutes.route(method, segments, query, projectId)"));
        assertFalse(server.contains("private MorpheusHttpRouteResponse routeVersions("));
        assertFalse(server.contains("unknown versions route"));

        assertTrue(routes.contains("private final MorpheusHistoryApiService service;"));
        assertTrue(routes.contains("MorpheusVersionsHttpRoutes(MorpheusApiService facade)"));
        assertTrue(routes.contains("this(Objects.requireNonNull(facade, \"facade\").historyService());"));
        assertTrue(routes.contains("MorpheusHttpRouteGuards.requireMethod(method, \"GET\")"));
        assertTrue(routes.contains("service.versions(projectId, page(query))"));
        assertTrue(routes.contains("service.compareVersions("));
        assertTrue(routes.contains("service.historicalRequirements(projectId, segments.get(3), page(query))"));
        assertTrue(routes.contains("query.required(\"fromSnapshotId\")"));
        assertTrue(routes.contains("query.required(\"toSnapshotId\")"));
        assertTrue(routes.contains("query.rejectUnknown(Set.of(\"offset\", \"limit\"))"));
        assertTrue(routes.contains("unknown versions route"));
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
}
