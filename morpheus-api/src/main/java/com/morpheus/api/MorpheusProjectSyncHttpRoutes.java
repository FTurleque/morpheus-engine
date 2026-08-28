package com.morpheus.api;

import com.sun.net.httpserver.HttpExchange;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Owns local HTTP dispatch for project synchronization and synchronization status. */
final class MorpheusProjectSyncHttpRoutes {
    private final MorpheusApiService service;
    private final MorpheusHttpRequestDecoder requestDecoder;

    MorpheusProjectSyncHttpRoutes(MorpheusApiService service, MorpheusHttpRequestDecoder requestDecoder) {
        this.service = Objects.requireNonNull(service, "service");
        this.requestDecoder = Objects.requireNonNull(requestDecoder, "requestDecoder");
    }

    MorpheusHttpRouteResponse routeSync(
            HttpExchange exchange,
            String method,
            List<String> segments,
            MorpheusHttpQuery query,
            String projectId) {
        MorpheusHttpRouteGuards.requireExactSegments(segments, 3);
        MorpheusHttpRouteGuards.requireMethod(method, "POST");
        query.rejectUnknown(Set.of());
        MorpheusHttpServer.SyncRequest request = requestDecoder.readOptionalJson(
                exchange,
                MorpheusHttpServer.SyncRequest.class,
                new MorpheusHttpServer.SyncRequest(null));
        return ok(service.sync(projectId, Optional.ofNullable(request.revision())));
    }

    MorpheusHttpRouteResponse routeSyncStatus(
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
