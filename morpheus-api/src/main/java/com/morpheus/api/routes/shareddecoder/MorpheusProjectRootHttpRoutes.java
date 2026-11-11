package com.morpheus.api.routes.shareddecoder;

import com.morpheus.api.MorpheusApiService;
import com.morpheus.api.MorpheusProjectRegistryApiService;
import com.morpheus.api.routes.shareddecoder.ProjectRegistrationRequest;
import com.morpheus.api.transport.ApiFailure;
import com.morpheus.api.transport.MorpheusHttpQuery;
import com.morpheus.api.transport.MorpheusHttpRequestDecoder;
import com.morpheus.api.transport.MorpheusHttpRouteGuards;
import com.morpheus.api.transport.MorpheusHttpRouteResponse;
import com.sun.net.httpserver.HttpExchange;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Owns local HTTP dispatch for the project collection and project detail routes. */
public final class MorpheusProjectRootHttpRoutes {
    private final MorpheusProjectRegistryApiService service;
    private final MorpheusHttpRequestDecoder requestDecoder;

    public MorpheusProjectRootHttpRoutes(MorpheusApiService service, MorpheusHttpRequestDecoder requestDecoder) {
        this.service = Objects.requireNonNull(service, "service").projectRegistryService();
        this.requestDecoder = Objects.requireNonNull(requestDecoder, "requestDecoder");
    }

    public MorpheusHttpRouteResponse route(
            HttpExchange exchange,
            String method,
            List<String> segments,
            MorpheusHttpQuery query) {
        if (segments.size() == 1) {
            return routeCollection(exchange, method, query);
        }
        return routeProject(method, query, segments.get(1));
    }

    private MorpheusHttpRouteResponse routeCollection(
            HttpExchange exchange,
            String method,
            MorpheusHttpQuery query) {
        query.rejectUnknown(Set.of());
        if (method.equals("GET")) {
            return ok(service.listProjects());
        }
        if (method.equals("POST")) {
            ProjectRegistrationRequest request = requestDecoder.readRequiredJson(
                    exchange, ProjectRegistrationRequest.class);
            MorpheusProjectRegistryApiService.RegistrationResult result = service.registerProject(request.workspace());
            return new MorpheusHttpRouteResponse(result.created() ? 201 : 200, result.project());
        }
        throw ApiFailure.methodNotAllowed("projects supports GET and POST");
    }

    private MorpheusHttpRouteResponse routeProject(
            String method,
            MorpheusHttpQuery query,
            String projectId) {
        MorpheusHttpRouteGuards.requireMethod(method, "GET");
        query.rejectUnknown(Set.of());
        return ok(service.project(projectId));
    }

    private MorpheusHttpRouteResponse ok(Object data) {
        return new MorpheusHttpRouteResponse(200, data);
    }
}
