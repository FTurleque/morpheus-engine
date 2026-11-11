package com.morpheus.api.routes.shareddecoder;

import com.morpheus.api.MorpheusApiService;
import com.morpheus.api.MorpheusProjectSyncApiService;
import com.morpheus.api.routes.shareddecoder.SyncRequest;
import com.morpheus.api.transport.MorpheusHttpQuery;
import com.morpheus.api.transport.MorpheusHttpRequestDecoder;
import com.morpheus.api.transport.MorpheusHttpRouteGuards;
import com.morpheus.api.transport.MorpheusHttpRouteResponse;
import com.sun.net.httpserver.HttpExchange;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Owns local HTTP dispatch for project synchronization and synchronization status. */
public final class MorpheusProjectSyncHttpRoutes {
    private final MorpheusProjectSyncApiService service;
    private final MorpheusHttpRequestDecoder requestDecoder;

    public MorpheusProjectSyncHttpRoutes(MorpheusApiService facade, MorpheusHttpRequestDecoder requestDecoder) {
        this(Objects.requireNonNull(facade, "facade").projectSyncService(), requestDecoder);
    }

    MorpheusProjectSyncHttpRoutes(MorpheusProjectSyncApiService service, MorpheusHttpRequestDecoder requestDecoder) {
        this.service = Objects.requireNonNull(service, "service");
        this.requestDecoder = Objects.requireNonNull(requestDecoder, "requestDecoder");
    }

    public MorpheusHttpRouteResponse routeSync(
            HttpExchange exchange,
            String method,
            List<String> segments,
            MorpheusHttpQuery query,
            String projectId) {
        MorpheusHttpRouteGuards.requireExactSegments(segments, 3);
        MorpheusHttpRouteGuards.requireMethod(method, "POST");
        query.rejectUnknown(Set.of());
        SyncRequest request = requestDecoder.readOptionalJson(
                exchange,
                SyncRequest.class,
                new SyncRequest(null));
        return ok(service.sync(projectId, Optional.ofNullable(request.revision())));
    }

    public MorpheusHttpRouteResponse routeSyncStatus(
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
