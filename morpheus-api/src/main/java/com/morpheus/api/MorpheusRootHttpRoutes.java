package com.morpheus.api;

import com.morpheus.api.transport.ApiFailure;
import com.morpheus.api.transport.MorpheusHttpQuery;
import com.morpheus.api.transport.MorpheusHttpRouteGuards;
import com.morpheus.api.transport.MorpheusHttpRouteResponse;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Owns local root, product and operability read routes. */
public final class MorpheusRootHttpRoutes {
    private final MorpheusApiService service;
    private final MorpheusOperabilityApiService operabilityService;

    public MorpheusRootHttpRoutes(MorpheusApiService service, MorpheusOperabilityApiService operabilityService) {
        this.service = Objects.requireNonNull(service, "service");
        this.operabilityService = Objects.requireNonNull(operabilityService, "operabilityService");
    }

    /**
     * The root routes that answer from the process alone. They are served without a database worker, so that a
     * database every worker is waiting on does not silence them; {@code readiness} is not one of them, it asks the
     * database.
     */
    public static final List<String> ANSWERED_WITHOUT_THE_DATABASE = List.of("health", "metrics", "version");

    public boolean handles(List<String> segments) {
        return segments.isEmpty()
                || (segments.size() == 1
                && switch (segments.getFirst()) {
                    case "health", "readiness", "metrics", "version" -> true;
                    default -> false;
                });
    }

    public MorpheusHttpRouteResponse route(String method, List<String> segments, MorpheusHttpQuery query) {
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
