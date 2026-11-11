package com.morpheus.api.routes.bodiless;

import com.morpheus.api.MorpheusApiService;
import com.morpheus.api.MorpheusDiagnosticsApiService;
import com.morpheus.api.transport.MorpheusHttpQuery;
import com.morpheus.api.transport.MorpheusHttpRouteGuards;
import com.morpheus.api.transport.MorpheusHttpRouteResponse;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Owns local HTTP dispatch for project diagnostics reads. */
public final class MorpheusDiagnosticsHttpRoutes {
    private final MorpheusDiagnosticsApiService service;

    public MorpheusDiagnosticsHttpRoutes(MorpheusApiService facade) {
        this(Objects.requireNonNull(facade, "facade").diagnosticsService());
    }

    MorpheusDiagnosticsHttpRoutes(MorpheusDiagnosticsApiService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    public MorpheusHttpRouteResponse route(
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
