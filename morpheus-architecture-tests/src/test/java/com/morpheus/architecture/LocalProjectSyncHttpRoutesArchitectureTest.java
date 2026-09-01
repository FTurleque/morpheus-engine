package com.morpheus.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalProjectSyncHttpRoutesArchitectureTest {

    @Test
    void projectSyncRoutesStayExtractedAndReuseSharedHttpInfrastructure() throws IOException {
        Path root = repositoryRoot();
        String server = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusHttpServer.java"));
        String routes = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusProjectSyncHttpRoutes.java"));

        assertTrue(server.contains("MorpheusProjectSyncHttpRoutes projectSyncRoutes"));
        assertTrue(server.contains("new MorpheusProjectSyncHttpRoutes(this.service, this.requestDecoder)"));
        assertTrue(server.contains("projectSyncRoutes.routeSync(exchange, method, segments, query, projectId)"));
        assertTrue(server.contains("projectSyncRoutes.routeSyncStatus(method, segments, query, projectId)"));
        assertFalse(server.contains("private MorpheusHttpRouteResponse routeSync("));
        assertFalse(server.contains("private MorpheusHttpRouteResponse routeSyncStatus("));
        assertFalse(server.contains("service.sync(projectId"));
        assertFalse(server.contains("service.syncStatus(projectId"));
        assertFalse(server.contains("query.rejectUnknown(Set.of(\"maxAgeMinutes\"))"));

        assertTrue(routes.contains("MorpheusProjectSyncApiService service"));
        assertTrue(routes.contains("MorpheusApiService facade"));
        assertTrue(routes.contains("Objects.requireNonNull(facade, \"facade\").projectSyncService()"));
        assertTrue(routes.contains("MorpheusHttpRequestDecoder requestDecoder"));
        assertTrue(routes.contains("MorpheusHttpRouteGuards.requireExactSegments(segments, 3)"));
        assertTrue(routes.contains("MorpheusHttpRouteGuards.requireMethod(method, \"POST\")"));
        assertTrue(routes.contains("MorpheusHttpRouteGuards.requireMethod(method, \"GET\")"));
        assertTrue(routes.contains("query.rejectUnknown(Set.of())"));
        assertTrue(routes.contains("query.rejectUnknown(Set.of(\"maxAgeMinutes\"))"));
        assertTrue(routes.contains("requestDecoder.readOptionalJson("));
        assertTrue(routes.contains("MorpheusHttpServer.SyncRequest.class"));
        assertTrue(routes.contains("new MorpheusHttpServer.SyncRequest(null)"));
        assertTrue(routes.contains("Optional.ofNullable(request.revision())"));
        assertTrue(routes.contains("MorpheusApiService.DEFAULT_MAX_AGE_MINUTES"));
        assertTrue(routes.contains("MorpheusApiService.MAX_MAX_AGE_MINUTES"));
        assertTrue(routes.contains("service.sync(projectId"));
        assertTrue(routes.contains("service.syncStatus(projectId"));
        assertTrue(routes.contains("HttpExchange"));
        assertFalse(routes.contains("com.sun.net.httpserver.HttpServer"));
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
