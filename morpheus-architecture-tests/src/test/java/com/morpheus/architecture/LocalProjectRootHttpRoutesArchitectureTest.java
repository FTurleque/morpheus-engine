package com.morpheus.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalProjectRootHttpRoutesArchitectureTest {

    @Test
    void projectRootRoutesStayExtractedAndReuseSharedHttpInfrastructure() throws IOException {
        String server = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusHttpServer"));
        String routes = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusProjectRootHttpRoutes"));

        assertServerDelegatesToProjectRootRoutes(server);
        assertProjectRootRoutesImplementRouting(routes);
        assertProjectRootRoutesDoNotLeakSharedHttpInfrastructure(routes);
    }

    private void assertServerDelegatesToProjectRootRoutes(String server) {
        assertTrue(server.contains("MorpheusProjectRootHttpRoutes projectRootRoutes"));
        assertTrue(server.contains("new MorpheusProjectRootHttpRoutes(this.service, this.requestDecoder)"));
        assertTrue(server.contains("projectRootRoutes.route(exchange, method, segments, query)"));
        assertFalse(server.contains("service.listProjects()"));
        assertFalse(server.contains("service.registerProject("));
        assertFalse(server.contains("service.project(projectId)"));
        assertFalse(server.contains("projects supports GET and POST"));
        assertFalse(server.contains("ProjectRegistrationRequest.class"));
        assertFalse(server.contains("private <T> T readRequiredJson("));
    }

    private void assertProjectRootRoutesImplementRouting(String routes) {
        assertTrue(routes.contains("MorpheusApiService service"));
        assertTrue(routes.contains("MorpheusHttpRequestDecoder requestDecoder"));
        assertTrue(routes.contains("query.rejectUnknown(Set.of())"));
        assertTrue(routes.contains("method.equals(\"GET\")"));
        assertTrue(routes.contains("method.equals(\"POST\")"));
        assertTrue(routes.contains("requestDecoder.readRequiredJson("));
        assertTrue(routes.contains("MorpheusHttpServer.ProjectRegistrationRequest.class"));
        assertTrue(routes.contains("service.listProjects()"));
        assertTrue(routes.contains("service.registerProject(request.workspace())"));
        assertTrue(routes.contains("result.created() ? 201 : 200"));
        assertTrue(routes.contains("ApiFailure.methodNotAllowed(\"projects supports GET and POST\")"));
        assertTrue(routes.contains("MorpheusHttpRouteGuards.requireMethod(method, \"GET\")"));
        assertTrue(routes.contains("service.project(projectId)"));
        assertTrue(routes.contains("HttpExchange"));
    }

    private void assertProjectRootRoutesDoNotLeakSharedHttpInfrastructure(String routes) {
        assertFalse(routes.contains("com.sun.net.httpserver.HttpServer"));
        assertFalse(routes.contains("JsonMapper"));
        assertFalse(routes.contains("MorpheusHttpResponseWriter"));
        assertFalse(routes.contains("MorpheusHttpPathParser"));
        assertFalse(routes.contains("MorpheusRemoteRoutePolicy"));
        assertFalse(routes.contains("MorpheusRemoteRole"));
        assertFalse(routes.contains("routeSync"));
        assertFalse(routes.contains("sync-status"));
    }
}
