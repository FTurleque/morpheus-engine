package com.morpheus.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalRequirementsHttpRoutesArchitectureTest {

    @Test
    void requirementsRoutesStayExtractedAndReuseBoundedDecoder() throws IOException {
        Path root = repositoryRoot();
        String server = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusHttpServer.java"));
        String routes = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusRequirementsHttpRoutes.java"));

        assertTrue(server.contains("MorpheusRequirementsHttpRoutes requirementsRoutes"));
        assertTrue(server.contains("new MorpheusRequirementsHttpRoutes("));
        assertTrue(server.contains("this.service, this.augmentedContextService, this.requestDecoder"));
        assertTrue(server.contains("requirementsRoutes.route(exchange, method, segments, query, projectId)"));
        assertFalse(server.contains("private MorpheusHttpRouteResponse routeRequirements("));
        assertFalse(server.contains("unknown requirements route"));

        assertTrue(routes.contains("private final MorpheusRequirementQueryApiService service;"));
        assertTrue(routes.contains("MorpheusRequirementsHttpRoutes("));
        assertTrue(routes.contains("MorpheusApiService facade,"));
        assertTrue(routes.contains("this(Objects.requireNonNull(facade, \"facade\").requirementQueryService(), augmentedContextService, requestDecoder);"));
        assertTrue(routes.contains("MorpheusAugmentedContextApiService augmentedContextService"));
        assertTrue(routes.contains("MorpheusHttpRequestDecoder requestDecoder"));
        assertTrue(routes.contains("requestDecoder.readRequiredJson(exchange, AugmentedContextRequest.class)"));
        assertTrue(routes.contains("MorpheusHttpRouteGuards.requireMethod(method, \"POST\")"));
        assertTrue(routes.contains("MorpheusHttpRouteGuards.requireMethod(method, \"GET\")"));
        assertTrue(routes.contains("service.requirements(projectId, query.string(\"query\").orElse(\"\"), page)"));
        assertTrue(routes.contains("service.requirement(projectId, segments.get(3))"));
        assertTrue(routes.contains("service.traceRequirement(projectId, segments.get(3), depth)"));
        assertTrue(routes.contains("unknown requirements route"));
        assertFalse(routes.contains("private final MorpheusApiService service;"));
        assertTrue(routes.contains("HttpExchange"));
        assertFalse(routes.contains("HttpServer"));
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
