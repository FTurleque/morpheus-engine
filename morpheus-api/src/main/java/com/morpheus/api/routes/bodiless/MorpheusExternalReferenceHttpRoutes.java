package com.morpheus.api.routes.bodiless;

import com.morpheus.api.MorpheusExternalReferenceApiService;
import com.morpheus.api.transport.ApiFailure;
import com.morpheus.api.transport.MorpheusHttpQuery;
import com.morpheus.api.transport.MorpheusHttpRouteGuards;
import com.morpheus.api.transport.MorpheusHttpRouteResponse;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Owns local HTTP dispatch for project external-reference reads. */
public final class MorpheusExternalReferenceHttpRoutes {
    private final MorpheusExternalReferenceApiService service;

    public MorpheusExternalReferenceHttpRoutes(MorpheusExternalReferenceApiService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    public MorpheusHttpRouteResponse route(
            String method,
            List<String> segments,
            MorpheusHttpQuery query,
            String projectId) {
        MorpheusHttpRouteGuards.requireMethod(method, "GET");
        if (segments.size() == 3) {
            query.rejectUnknown(Set.of("ownerId"));
            return ok(service.list(projectId, query.required("ownerId")));
        }
        if (segments.size() == 5 && segments.get(4).equals("resolution")) {
            query.rejectUnknown(Set.of());
            return ok(service.resolve(projectId, segments.get(3)));
        }
        throw ApiFailure.notFound("unknown external-references route");
    }

    private MorpheusHttpRouteResponse ok(Object data) {
        return new MorpheusHttpRouteResponse(200, data);
    }
}
