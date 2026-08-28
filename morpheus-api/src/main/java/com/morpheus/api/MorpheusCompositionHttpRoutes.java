package com.morpheus.api;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Routes persisted composition reads without owning HTTP transport or top-level project dispatch. */
final class MorpheusCompositionHttpRoutes {
    private final MorpheusCompositionApiService compositionService;

    MorpheusCompositionHttpRoutes(MorpheusCompositionApiService compositionService) {
        this.compositionService = Objects.requireNonNull(compositionService, "compositionService");
    }

    MorpheusHttpRouteResponse route(
            String method,
            List<String> segments,
            MorpheusHttpQuery query,
            String projectId) {
        MorpheusHttpRouteGuards.requireMethod(method, "GET");
        if (segments.size() == 3) {
            query.rejectUnknown(Set.of());
            return ok(compositionService.status(projectId));
        }
        if (segments.size() == 4 && segments.get(3).equals("conflicts")) {
            query.rejectUnknown(Set.of("offset", "limit"));
            int offset = query.intValue("offset", 0, 0, Integer.MAX_VALUE);
            int limit = query.intValue(
                    "limit",
                    MorpheusCompositionApiService.DEFAULT_LIMIT,
                    1,
                    MorpheusCompositionApiService.MAX_LIMIT);
            return ok(compositionService.conflicts(projectId, offset, limit));
        }
        throw ApiFailure.notFound("unknown composition route");
    }

    private MorpheusHttpRouteResponse ok(Object data) {
        return new MorpheusHttpRouteResponse(200, data);
    }
}
