package com.morpheus.api;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Owns local HTTP dispatch for project diagnostics reads. */
final class MorpheusDiagnosticsHttpRoutes {
    private final MorpheusDiagnosticsApiService service;

    MorpheusDiagnosticsHttpRoutes(MorpheusApiService facade) {
        this(Objects.requireNonNull(facade, "facade").diagnosticsService());
    }

    MorpheusDiagnosticsHttpRoutes(MorpheusDiagnosticsApiService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    MorpheusHttpRouteResponse route(
            String method,
            List<String> segments,
            MorpheusHttpQuery query,
            String projectId) {
        MorpheusHttpRouteGuards.requireExactSegments(segments, 3);
        MorpheusHttpRouteGuards.requireMethod(method, "GET");
        query.rejectUnknown(Set.of());
        return new MorpheusHttpRouteResponse(200, service.diagnostics(projectId));
    }
}
