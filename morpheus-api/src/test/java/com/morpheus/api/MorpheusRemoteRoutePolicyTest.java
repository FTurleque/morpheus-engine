package com.morpheus.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MorpheusRemoteRoutePolicyTest {

    @Test
    void explicitRegistryClassifiesReadWriteAndAdminRoutes() {
        assertEquals(MorpheusRemoteRole.READ,
                MorpheusRemoteRoutePolicy.requiredRole("GET", "/api/v1/projects/project-1"));
        assertEquals(MorpheusRemoteRole.READ,
                MorpheusRemoteRoutePolicy.requiredRole("POST", "/api/v1/queries/execute"));
        assertEquals(MorpheusRemoteRole.READ,
                MorpheusRemoteRoutePolicy.requiredRole("POST", "/api/v1/reasoning/analyze"));
        assertEquals(MorpheusRemoteRole.READ,
                MorpheusRemoteRoutePolicy.requiredRole("POST", "/api/v1/saved-views/view-1/execute"));
        assertEquals(MorpheusRemoteRole.READ,
                MorpheusRemoteRoutePolicy.requiredRole(
                        "POST", "/api/v1/projects/project-1/changes/change-1/transition-check"));
        assertEquals(MorpheusRemoteRole.READ,
                MorpheusRemoteRoutePolicy.requiredRole(
                        "POST", "/api/v1/projects/project-1/requirements/req-1/augmented-context"));
        assertEquals(MorpheusRemoteRole.WRITE,
                MorpheusRemoteRoutePolicy.requiredRole("POST", "/api/v1/projects"));
        assertEquals(MorpheusRemoteRole.WRITE,
                MorpheusRemoteRoutePolicy.requiredRole(
                        "POST", "/api/v1/projects/project-1/changes/change-1/lifecycle"));
        assertEquals(MorpheusRemoteRole.ADMIN,
                MorpheusRemoteRoutePolicy.requiredRole("GET", "/api/v1/metrics"));
        assertEquals(MorpheusRemoteRole.ADMIN,
                MorpheusRemoteRoutePolicy.requiredRole("POST", "/api/v1/server/backups"));
        assertEquals(MorpheusRemoteRole.ADMIN,
                MorpheusRemoteRoutePolicy.requiredRole("POST", "/api/v1/provider-plugins/probe"));
    }

    @Test
    void unknownGetAndPostRoutesFailClosedInsteadOfInheritingVerbRoles() {
        MorpheusRemoteRoutePolicy.RoutePolicyException unknownGet = assertThrows(
                MorpheusRemoteRoutePolicy.RoutePolicyException.class,
                () -> MorpheusRemoteRoutePolicy.requiredRole("GET", "/api/v1/future/read-model"));
        assertEquals(404, unknownGet.status());
        assertEquals("NOT_FOUND", unknownGet.code());

        MorpheusRemoteRoutePolicy.RoutePolicyException unknownPost = assertThrows(
                MorpheusRemoteRoutePolicy.RoutePolicyException.class,
                () -> MorpheusRemoteRoutePolicy.requiredRole("POST", "/api/v1/future/augmented-context"));
        assertEquals(404, unknownPost.status());
        assertEquals("NOT_FOUND", unknownPost.code());

        assertThrows(
                MorpheusRemoteRoutePolicy.RoutePolicyException.class,
                () -> MorpheusRemoteRoutePolicy.usesBoundedUpstreamTimeout("GET", "/api/v1/future/read-model"));
    }

    @Test
    void knownRouteWithWrongMethodFailsWith405() {
        MorpheusRemoteRoutePolicy.RoutePolicyException failure = assertThrows(
                MorpheusRemoteRoutePolicy.RoutePolicyException.class,
                () -> MorpheusRemoteRoutePolicy.requiredRole("GET", "/api/v1/provider-plugins/probe"));
        assertEquals(405, failure.status());
        assertEquals("METHOD_NOT_ALLOWED", failure.code());

        MorpheusRemoteRoutePolicy.RoutePolicyException patch = assertThrows(
                MorpheusRemoteRoutePolicy.RoutePolicyException.class,
                () -> MorpheusRemoteRoutePolicy.requiredRole("PATCH", "/api/v1/projects/project-1"));
        assertEquals(405, patch.status());
    }

    @Test
    void timeoutPolicyFollowsTheAuthorizedReadClassification() {
        assertTrue(MorpheusRemoteRoutePolicy.usesBoundedUpstreamTimeout(
                "POST", "/api/v1/projects/project-1/changes/change-1/transition-check"));
        assertTrue(MorpheusRemoteRoutePolicy.usesBoundedUpstreamTimeout(
                "GET", "/api/v1/projects/project-1/provider-baselines"));
        assertFalse(MorpheusRemoteRoutePolicy.usesBoundedUpstreamTimeout(
                "POST", "/api/v1/projects/project-1/changes/change-1/lifecycle"));
        assertFalse(MorpheusRemoteRoutePolicy.usesBoundedUpstreamTimeout(
                "POST", "/api/v1/provider-plugins/probe"));
    }

    @Test
    void pathsMustRemainNormalizedInsideApiPrefix() {
        MorpheusRemoteRoutePolicy.RoutePolicyException outside = assertThrows(
                MorpheusRemoteRoutePolicy.RoutePolicyException.class,
                () -> MorpheusRemoteRoutePolicy.requiredRole("GET", "/other/projects"));
        assertEquals(404, outside.status());

        MorpheusRemoteRoutePolicy.RoutePolicyException emptySegment = assertThrows(
                MorpheusRemoteRoutePolicy.RoutePolicyException.class,
                () -> MorpheusRemoteRoutePolicy.requiredRole("GET", "/api/v1/projects//health"));
        assertEquals(404, emptySegment.status());
    }
}
