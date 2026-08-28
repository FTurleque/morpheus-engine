package com.morpheus.api;

import com.morpheus.application.query.PageRequest;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Owns local HTTP dispatch for specification reads. */
final class MorpheusSpecificationsHttpRoutes {
    private final MorpheusApiService service;

    MorpheusSpecificationsHttpRoutes(MorpheusApiService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    MorpheusHttpRouteResponse route(
            String method,
            List<String> segments,
            MorpheusHttpQuery query,
            String projectId) {
        MorpheusHttpRouteGuards.requireMethod(method, "GET");
        if (segments.size() == 3) {
            return ok(service.listSpecifications(projectId, page(query)));
        }
        if (segments.size() == 4) {
            query.rejectUnknown(Set.of());
            return ok(service.specification(projectId, segments.get(3)));
        }
        if (segments.size() == 5 && segments.get(4).equals("context")) {
            return ok(service.specificationContext(projectId, segments.get(3), page(query)));
        }
        throw ApiFailure.notFound("unknown specifications route");
    }

    private PageRequest page(MorpheusHttpQuery query) {
        query.rejectUnknown(Set.of("offset", "limit"));
        return new PageRequest(
                query.intValue("offset", 0, 0, Integer.MAX_VALUE),
                query.intValue("limit", MorpheusApiService.DEFAULT_LIMIT, 1, MorpheusApiService.MAX_LIMIT));
    }

    private MorpheusHttpRouteResponse ok(Object data) {
        return new MorpheusHttpRouteResponse(200, data);
    }
}
