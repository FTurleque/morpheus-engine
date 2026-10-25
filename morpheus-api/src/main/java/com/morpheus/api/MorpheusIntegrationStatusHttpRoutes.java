package com.morpheus.api;

import com.morpheus.api.transport.ApiFailure;
import com.morpheus.api.transport.MorpheusHttpQuery;
import com.morpheus.api.transport.MorpheusHttpRouteGuards;
import com.morpheus.api.transport.MorpheusHttpRouteResponse;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Routes local integration status reads without owning HTTP transport or path dispatch. */
public final class MorpheusIntegrationStatusHttpRoutes {
    private final MorpheusExternalReferenceApiService externalReferenceService;
    private final MorpheusAugmentedContextApiService augmentedContextService;

    public MorpheusIntegrationStatusHttpRoutes(
            MorpheusExternalReferenceApiService externalReferenceService,
            MorpheusAugmentedContextApiService augmentedContextService) {
        this.externalReferenceService = Objects.requireNonNull(externalReferenceService, "externalReferenceService");
        this.augmentedContextService = Objects.requireNonNull(augmentedContextService, "augmentedContextService");
    }

    public MorpheusHttpRouteResponse route(String method, List<String> segments, MorpheusHttpQuery query) {
        MorpheusHttpRouteGuards.requireMethod(method, "GET");
        query.rejectUnknown(Set.of());
        return switch (segments.get(1)) {
            case "minos" -> ok(externalReferenceService.minosStatus());
            case "nexus" -> ok(augmentedContextService.nexusStatus());
            default -> throw ApiFailure.notFound("unknown integration: " + segments.get(1));
        };
    }

    private MorpheusHttpRouteResponse ok(Object data) {
        return new MorpheusHttpRouteResponse(200, data);
    }
}
