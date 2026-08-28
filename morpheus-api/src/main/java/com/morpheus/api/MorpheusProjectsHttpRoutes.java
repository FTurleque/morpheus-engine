package com.morpheus.api;

import com.sun.net.httpserver.HttpExchange;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Owns local HTTP dispatch below the /projects resource. */
final class MorpheusProjectsHttpRoutes {
    private final MorpheusApiService service;
    private final MorpheusHttpRequestDecoder requestDecoder;
    private final MorpheusCompositionHttpRoutes compositionRoutes;
    private final MorpheusSpecificationsHttpRoutes specificationsRoutes;
    private final MorpheusRequirementsHttpRoutes requirementsRoutes;
    private final MorpheusChangesHttpRoutes changesRoutes;
    private final MorpheusVersionsHttpRoutes versionsRoutes;
    private final MorpheusDiagnosticsHttpRoutes diagnosticsRoutes;
    private final MorpheusExternalReferenceHttpRoutes externalReferenceRoutes;

    MorpheusProjectsHttpRoutes(
            MorpheusApiService service,
            MorpheusExternalReferenceApiService externalReferenceService,
            MorpheusAugmentedContextApiService augmentedContextService,
            MorpheusJarvisOrchestrationApiService jarvisOrchestrationService,
            MorpheusControlledLifecycleApiService controlledLifecycleService,
            MorpheusCompositionApiService compositionService,
            MorpheusHttpRequestDecoder requestDecoder) {
        this.service = Objects.requireNonNull(service, "service");
        this.requestDecoder = Objects.requireNonNull(requestDecoder, "requestDecoder");
        this.compositionRoutes = new MorpheusCompositionHttpRoutes(
                Objects.requireNonNull(compositionService, "compositionService"));
        this.specificationsRoutes = new MorpheusSpecificationsHttpRoutes(this.service);
        this.requirementsRoutes = new MorpheusRequirementsHttpRoutes(
                this.service,
                Objects.requireNonNull(augmentedContextService, "augmentedContextService"),
                this.requestDecoder);
        this.changesRoutes = new MorpheusChangesHttpRoutes(
                this.service,
                augmentedContextService,
                Objects.requireNonNull(jarvisOrchestrationService, "jarvisOrchestrationService"),
                Objects.requireNonNull(controlledLifecycleService, "controlledLifecycleService"),
                this.requestDecoder);
        this.versionsRoutes = new MorpheusVersionsHttpRoutes(this.service);
        this.diagnosticsRoutes = new MorpheusDiagnosticsHttpRoutes(this.service);
        this.externalReferenceRoutes = new MorpheusExternalReferenceHttpRoutes(
                Objects.requireNonNull(externalReferenceService, "externalReferenceService"));
    }

    MorpheusHttpRouteResponse route(
            HttpExchange exchange,
            String method,
            List<String> segments,
            MorpheusHttpQuery query) {
        if (segments.size() == 1) {
            query.rejectUnknown(Set.of());
            if (method.equals("GET")) {
                return ok(service.listProjects());
            }
            if (method.equals("POST")) {
                MorpheusHttpServer.ProjectRegistrationRequest request = requestDecoder.readRequiredJson(
                        exchange, MorpheusHttpServer.ProjectRegistrationRequest.class);
                MorpheusApiService.RegistrationResult result = service.registerProject(request.workspace());
                return new MorpheusHttpRouteResponse(result.created() ? 201 : 200, result.project());
            }
            throw ApiFailure.methodNotAllowed("projects supports GET and POST");
        }

        String projectId = segments.get(1);
        if (segments.size() == 2) {
            MorpheusHttpRouteGuards.requireMethod(method, "GET");
            query.rejectUnknown(Set.of());
            return ok(service.project(projectId));
        }

        String resource = segments.get(2);
        return switch (resource) {
            case "sync" -> routeSync(exchange, method, segments, query, projectId);
            case "sync-status" -> routeSyncStatus(method, segments, query, projectId);
            case "composition" -> compositionRoutes.route(method, segments, query, projectId);
            case "specifications" -> specificationsRoutes.route(method, segments, query, projectId);
            case "requirements" -> requirementsRoutes.route(exchange, method, segments, query, projectId);
            case "changes" -> changesRoutes.route(exchange, method, segments, query, projectId);
            case "versions" -> versionsRoutes.route(method, segments, query, projectId);
            case "diagnostics" -> diagnosticsRoutes.route(method, segments, query, projectId);
            case "external-references" -> externalReferenceRoutes.route(method, segments, query, projectId);
            default -> throw ApiFailure.notFound("unknown project API resource: " + resource);
        };
    }

    private MorpheusHttpRouteResponse routeSync(
            HttpExchange exchange,
            String method,
            List<String> segments,
            MorpheusHttpQuery query,
            String projectId) {
        MorpheusHttpRouteGuards.requireExactSegments(segments, 3);
        MorpheusHttpRouteGuards.requireMethod(method, "POST");
        query.rejectUnknown(Set.of());
        MorpheusHttpServer.SyncRequest request = requestDecoder.readOptionalJson(
                exchange, MorpheusHttpServer.SyncRequest.class, new MorpheusHttpServer.SyncRequest(null));
        return ok(service.sync(projectId, Optional.ofNullable(request.revision())));
    }

    private MorpheusHttpRouteResponse routeSyncStatus(
            String method,
            List<String> segments,
            MorpheusHttpQuery query,
            String projectId) {
        MorpheusHttpRouteGuards.requireExactSegments(segments, 3);
        MorpheusHttpRouteGuards.requireMethod(method, "GET");
        query.rejectUnknown(Set.of("maxAgeMinutes"));
        long maxAge = query.longValue(
                "maxAgeMinutes",
                MorpheusApiService.DEFAULT_MAX_AGE_MINUTES,
                1,
                MorpheusApiService.MAX_MAX_AGE_MINUTES);
        return ok(service.syncStatus(projectId, maxAge));
    }

    private MorpheusHttpRouteResponse ok(Object data) {
        return new MorpheusHttpRouteResponse(200, data);
    }
}
