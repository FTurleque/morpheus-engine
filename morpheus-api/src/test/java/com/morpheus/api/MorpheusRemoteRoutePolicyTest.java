package com.morpheus.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MorpheusRemoteRoutePolicyTest {

    @Test
    void onlyExplicitPostRoutesReceiveReadRole() {
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
                MorpheusRemoteRoutePolicy.requiredRole(
                        "POST", "/api/v1/projects/project-1/changes/change-1/lifecycle-transitions"));
        assertEquals(MorpheusRemoteRole.WRITE,
                MorpheusRemoteRoutePolicy.requiredRole("POST", "/api/v1/future/augmented-context"));
        assertEquals(MorpheusRemoteRole.WRITE,
                MorpheusRemoteRoutePolicy.requiredRole("POST", "/api/v1/nested/saved-views/view-1/execute"));
    }

    @Test
    void timeoutPolicyUsesTheSameExplicitReadOnlyClassification() {
        assertTrue(MorpheusRemoteRoutePolicy.usesBoundedUpstreamTimeout(
                "POST", "/api/v1/projects/project-1/changes/change-1/transition-check"));
        assertFalse(MorpheusRemoteRoutePolicy.usesBoundedUpstreamTimeout(
                "POST", "/api/v1/projects/project-1/changes/change-1/lifecycle-transitions"));
        assertFalse(MorpheusRemoteRoutePolicy.usesBoundedUpstreamTimeout(
                "POST", "/api/v1/future/augmented-context"));
    }

    @Test
    void preservesAdministrativeRoutesAndMethodConstraints() {
        assertEquals(MorpheusRemoteRole.ADMIN,
                MorpheusRemoteRoutePolicy.requiredRole("GET", "/api/v1/metrics"));
        assertEquals(MorpheusRemoteRole.ADMIN,
                MorpheusRemoteRoutePolicy.requiredRole("POST", "/api/v1/provider-plugins/probe"));

        MorpheusRemoteRoutePolicy.RoutePolicyException failure = assertThrows(
                MorpheusRemoteRoutePolicy.RoutePolicyException.class,
                () -> MorpheusRemoteRoutePolicy.requiredRole("GET", "/api/v1/provider-plugins/probe"));
        assertEquals(405, failure.status());
        assertEquals("METHOD_NOT_ALLOWED", failure.code());
    }
}
