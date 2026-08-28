package com.morpheus.api;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Owns local root, product and operability read routes. */
final class MorpheusRootHttpRoutes {
    private final MorpheusApiService service;
    private final MorpheusOperabilityApiService operabilityService;

    MorpheusRootHttpRoutes(MorpheusApiService service, MorpheusOperabilityApiService operabilityService) {
        this.service = Objects.requireNonNull(service, "service");
        this.operabilityService = Objects.requireNonNull(operabilityService, "operabilityService");
    }

    boolean handles(List<String> segments) {
        return segments.isEmpty()
                || (segments.size() == 1
                && switch (segments.getFirst()) {
                    case "health", "readiness", "metrics", "version" -> true;
                    default -> false;
                });
    }

    MorpheusHttpRouteResponse route(String method, List<String> segments, MorpheusHttpQuery query) {
        MorpheusHttpRouteGuards.requireMethod(method, "GET");
        query.rejectUnknown(Set.of());
        if (segments.isEmpty()) {
            return ok(Map.of("service", "morpheus", "apiVersion", "v1"));
        }
        return switch (segments.getFirst()) {
            case "health" -> ok(service.health());
            case "readiness" -> readiness();
            case "metrics" -> ok(operabilityService.metrics());
            case "version" -> ok(service.version());
            default -> throw ApiFailure.notFound("unknown API root route");
        };
    }

    private MorpheusHttpRouteResponse readiness() {
        MorpheusOperabilityApiService.ReadinessView readiness = operabilityService.readiness();
        return new MorpheusHttpRouteResponse("READY".equals(readiness.status()) ? 200 : 503, readiness);
    }

    private MorpheusHttpRouteResponse ok(Object data) {
        return new MorpheusHttpRouteResponse(200, data);
    }
}
