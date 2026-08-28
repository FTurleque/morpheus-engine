package com.morpheus.api;

import com.morpheus.application.query.PageRequest;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Owns local HTTP dispatch for project version-history reads. */
final class MorpheusVersionsHttpRoutes {
    private final MorpheusApiService service;

    MorpheusVersionsHttpRoutes(MorpheusApiService service) {
        this.service = Objects.requireNonNull(service, "service");
    }

    MorpheusHttpRouteResponse route(
            String method,
            List<String> segments,
            MorpheusHttpQuery query,
            String projectId) {
        MorpheusHttpRouteGuards.requireMethod(method, "GET");
        if (segments.size() == 3) {
            query.rejectUnknown(Set.of());
            return ok(service.versions(projectId));
        }
        if (segments.size() == 4 && segments.get(3).equals("compare")) {
            query.rejectUnknown(Set.of("fromSnapshotId", "toSnapshotId"));
            return ok(service.compareVersions(
                    projectId,
                    query.required("fromSnapshotId"),
                    query.required("toSnapshotId")));
        }
        if (segments.size() == 5 && segments.get(4).equals("requirements")) {
            return ok(service.historicalRequirements(projectId, segments.get(3), page(query)));
        }
        throw ApiFailure.notFound("unknown versions route");
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
