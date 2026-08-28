package com.morpheus.api;

import com.morpheus.application.query.PageRequest;
import com.sun.net.httpserver.HttpExchange;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Owns local HTTP dispatch for project requirement reads and augmented context. */
final class MorpheusRequirementsHttpRoutes {
    private final MorpheusApiService service;
    private final MorpheusAugmentedContextApiService augmentedContextService;
    private final MorpheusHttpRequestDecoder requestDecoder;

    MorpheusRequirementsHttpRoutes(
            MorpheusApiService service,
            MorpheusAugmentedContextApiService augmentedContextService,
            MorpheusHttpRequestDecoder requestDecoder) {
        this.service = Objects.requireNonNull(service, "service");
        this.augmentedContextService = Objects.requireNonNull(augmentedContextService, "augmentedContextService");
        this.requestDecoder = Objects.requireNonNull(requestDecoder, "requestDecoder");
    }

    MorpheusHttpRouteResponse route(
            HttpExchange exchange,
            String method,
            List<String> segments,
            MorpheusHttpQuery query,
            String projectId) {
        if (segments.size() == 5 && segments.get(4).equals("augmented-context")) {
            MorpheusHttpRouteGuards.requireMethod(method, "POST");
            query.rejectUnknown(Set.of());
            return ok(augmentedContextService.requirement(
                    projectId,
                    segments.get(3),
                    requestDecoder.readRequiredJson(exchange, AugmentedContextRequest.class)));
        }
        MorpheusHttpRouteGuards.requireMethod(method, "GET");
        if (segments.size() == 3) {
            query.rejectUnknown(Set.of("query", "offset", "limit"));
            PageRequest page = new PageRequest(
                    query.intValue("offset", 0, 0, Integer.MAX_VALUE),
                    query.intValue("limit", MorpheusApiService.DEFAULT_LIMIT, 1, MorpheusApiService.MAX_LIMIT));
            return ok(service.requirements(projectId, query.string("query").orElse(""), page));
        }
        if (segments.size() == 4) {
            query.rejectUnknown(Set.of());
            return ok(service.requirement(projectId, segments.get(3)));
        }
        if (segments.size() == 5 && segments.get(4).equals("trace")) {
            query.rejectUnknown(Set.of("depth"));
            int depth = query.intValue(
                    "depth", MorpheusApiService.DEFAULT_DEPTH, 1, MorpheusApiService.MAX_DEPTH);
            return ok(service.traceRequirement(projectId, segments.get(3), depth));
        }
        throw ApiFailure.notFound("unknown requirements route");
    }

    private MorpheusHttpRouteResponse ok(Object data) {
        return new MorpheusHttpRouteResponse(200, data);
    }
}
