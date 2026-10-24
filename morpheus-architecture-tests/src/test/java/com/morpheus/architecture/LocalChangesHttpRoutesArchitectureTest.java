package com.morpheus.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalChangesHttpRoutesArchitectureTest {

    @Test
    void changesRoutesStayExtractedAndReuseSharedDecoder() throws IOException {
        String server = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusHttpServer"));
        String routes = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusChangesHttpRoutes"));

        assertTrue(server.contains("MorpheusChangesHttpRoutes changesRoutes"));
        assertTrue(server.contains("new MorpheusChangesHttpRoutes("));
        assertTrue(server.contains("changesRoutes.route(exchange, method, segments, query, projectId)"));
        assertFalse(server.contains("private MorpheusHttpRouteResponse routeChanges("));
        assertFalse(server.contains("unknown change subresource"));

        assertTrue(routes.contains("private final MorpheusChangeQueryApiService service;"));
        assertTrue(routes.contains("private final MorpheusDiagnosticsApiService diagnosticsService;"));
        assertTrue(routes.contains("MorpheusChangesHttpRoutes("));
        assertTrue(routes.contains("MorpheusApiService facade"));
        assertTrue(routes.contains("changeQueryService()"));
        assertTrue(routes.contains("diagnosticsService()"));
        assertTrue(routes.contains("MorpheusAugmentedContextApiService augmentedContextService"));
        assertTrue(routes.contains("MorpheusJarvisOrchestrationApiService jarvisOrchestrationService"));
        assertTrue(routes.contains("MorpheusControlledLifecycleApiService controlledLifecycleService"));
        assertTrue(routes.contains("MorpheusHttpRequestDecoder requestDecoder"));
        assertTrue(routes.contains("requestDecoder.readRequiredJson(exchange, AugmentedContextRequest.class)"));
        assertTrue(routes.contains("requestDecoder.readRequiredJson(exchange, TransitionCheckRequest.class)"));
        assertTrue(routes.contains("requestDecoder.readRequiredJson(exchange, LifecycleMutationRequest.class)"));
        assertTrue(routes.contains("MorpheusHttpRouteGuards.requireMethod(method, \"POST\")"));
        assertTrue(routes.contains("MorpheusHttpRouteGuards.requireMethod(method, \"GET\")"));
        assertTrue(routes.contains("service.listChanges(projectId, page(query))"));
        assertTrue(routes.contains("service.change(projectId, segments.get(3))"));
        assertTrue(routes.contains("service.constraints(projectId, changeId, page(query))"));
        assertTrue(routes.contains("service.acceptanceCriteria(projectId, changeId, page(query))"));
        assertTrue(routes.contains("service.designDecisions(projectId, changeId, page(query))"));
        assertTrue(routes.contains("service.implementationTasks(projectId, changeId, page(query))"));
        assertTrue(routes.contains("service.changeContext(projectId, changeId, depth)"));
        assertTrue(routes.contains("diagnosticsService.changeStatus(projectId, changeId)"));
        assertTrue(routes.contains("diagnosticsService.blockingConditions(projectId, changeId)"));
        assertTrue(routes.contains("unknown change subresource"));
        assertFalse(routes.contains("private final MorpheusApiService service;"));
        assertTrue(routes.contains("HttpExchange"));
        assertFalse(routes.contains("HttpServer"));
        assertFalse(routes.contains("JsonMapper"));
        assertFalse(routes.contains("MorpheusHttpResponseWriter"));
        assertFalse(routes.contains("MorpheusHttpPathParser"));
        assertFalse(routes.contains("MorpheusRemoteRoutePolicy"));
        assertFalse(routes.contains("MorpheusRemoteRole"));
    }
}
