package com.morpheus.api;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MorpheusRemoteRoutePolicyTest {

    @Test
    void explicitRegistryClassifiesCoreReadWriteAndAdminRoutes() {
        assertEquals(MorpheusRemoteRole.READ,
                MorpheusRemoteRoutePolicy.requiredRole("GET", "/api/v1"));
        assertEquals(MorpheusRemoteRole.READ,
                MorpheusRemoteRoutePolicy.requiredRole("GET", "/api/v1/health"));
        assertEquals(MorpheusRemoteRole.READ,
                MorpheusRemoteRoutePolicy.requiredRole("GET", "/api/v1/readiness"));
        assertEquals(MorpheusRemoteRole.READ,
                MorpheusRemoteRoutePolicy.requiredRole("GET", "/api/v1/projects/project-1/sync-status"));
        assertEquals(MorpheusRemoteRole.WRITE,
                MorpheusRemoteRoutePolicy.requiredRole("POST", "/api/v1/projects/project-1/sync"));
        assertEquals(MorpheusRemoteRole.WRITE,
                MorpheusRemoteRoutePolicy.requiredRole("POST", "/api/v1/portfolios/portfolio-1/traverse"));
        assertEquals(MorpheusRemoteRole.ADMIN,
                MorpheusRemoteRoutePolicy.requiredRole("GET", "/api/v1/metrics"));
        assertEquals(MorpheusRemoteRole.ADMIN,
                MorpheusRemoteRoutePolicy.requiredRole("POST", "/api/v1/server/backups"));
        assertEquals(MorpheusRemoteRole.ADMIN,
                MorpheusRemoteRoutePolicy.requiredRole("POST", "/api/v1/provider-plugins/probe"));
    }

    @Test
    void explicitRegistryPreservesReadOnlyPostExceptions() {
        assertEquals(MorpheusRemoteRole.READ,
                MorpheusRemoteRoutePolicy.requiredRole("POST", "/api/v1/queries/execute"));
        assertEquals(MorpheusRemoteRole.READ,
                MorpheusRemoteRoutePolicy.requiredRole("POST", "/api/v1/exports"));
        assertEquals(MorpheusRemoteRole.READ,
                MorpheusRemoteRoutePolicy.requiredRole("POST", "/api/v1/reasoning/analyze"));
        assertEquals(MorpheusRemoteRole.READ,
                MorpheusRemoteRoutePolicy.requiredRole("POST", "/api/v1/saved-views/view-1/execute"));
        assertEquals(MorpheusRemoteRole.READ,
                MorpheusRemoteRoutePolicy.requiredRole("POST", "/api/v1/saved-views/view-1/export"));
        assertEquals(MorpheusRemoteRole.READ,
                MorpheusRemoteRoutePolicy.requiredRole("POST", "/api/v1/policies/evaluate"));
        assertEquals(MorpheusRemoteRole.READ,
                MorpheusRemoteRoutePolicy.requiredRole("POST", "/api/v1/policies/dry-run"));
        assertEquals(MorpheusRemoteRole.READ,
                MorpheusRemoteRoutePolicy.requiredRole(
                        "POST", "/api/v1/projects/project-1/changes/change-1/transition-check"));
        assertEquals(MorpheusRemoteRole.READ,
                MorpheusRemoteRoutePolicy.requiredRole(
                        "POST", "/api/v1/projects/project-1/requirements/req-1/augmented-context"));
        assertEquals(MorpheusRemoteRole.WRITE,
                MorpheusRemoteRoutePolicy.requiredRole(
                        "POST", "/api/v1/projects/project-1/changes/change-1/lifecycle-transitions"));
    }

    @Test
    void registryCoversQueryPolicyReasoningAndPortfolioExtensions() {
        assertEquals(MorpheusRemoteRole.READ,
                MorpheusRemoteRoutePolicy.requiredRole("GET", "/api/v1/saved-views/view-1/versions"));
        assertEquals(MorpheusRemoteRole.WRITE,
                MorpheusRemoteRoutePolicy.requiredRole("POST", "/api/v1/saved-views/view-1/archive"));
        assertEquals(MorpheusRemoteRole.READ,
                MorpheusRemoteRoutePolicy.requiredRole("GET", "/api/v1/policy-packs/pack-1/audit"));
        assertEquals(MorpheusRemoteRole.WRITE,
                MorpheusRemoteRoutePolicy.requiredRole("PUT", "/api/v1/policy-packs/pack-1/overrides/rule-1"));
        assertEquals(MorpheusRemoteRole.READ,
                MorpheusRemoteRoutePolicy.requiredRole("GET", "/api/v1/policy-activations"));
        assertEquals(MorpheusRemoteRole.WRITE,
                MorpheusRemoteRoutePolicy.requiredRole("POST", "/api/v1/policy-overrides/remove"));
        assertEquals(MorpheusRemoteRole.READ,
                MorpheusRemoteRoutePolicy.requiredRole("GET", "/api/v1/reasoning/adapters"));
        assertEquals(MorpheusRemoteRole.READ,
                MorpheusRemoteRoutePolicy.requiredRole("GET", "/api/v1/portfolios/portfolio-1/members"));
        assertEquals(MorpheusRemoteRole.WRITE,
                MorpheusRemoteRoutePolicy.requiredRole("POST", "/api/v1/portfolios/portfolio-1/projects/project-1/freshness"));
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

        assertFalse(MorpheusRemoteRoutePolicy.usesBoundedUpstreamTimeout(
                "GET", "/api/v1/future/read-model"));
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
    void timeoutPolicyFollowsAuthorizedReadClassificationAndStaysConservativeForUnknownPaths() {
        assertTrue(MorpheusRemoteRoutePolicy.usesBoundedUpstreamTimeout("GET", "/api/v1/health"));
        assertTrue(MorpheusRemoteRoutePolicy.usesBoundedUpstreamTimeout(
                "POST", "/api/v1/projects/project-1/changes/change-1/transition-check"));
        assertFalse(MorpheusRemoteRoutePolicy.usesBoundedUpstreamTimeout(
                "POST", "/api/v1/projects/project-1/changes/change-1/lifecycle-transitions"));
        assertFalse(MorpheusRemoteRoutePolicy.usesBoundedUpstreamTimeout(
                "POST", "/api/v1/provider-plugins/probe"));
        assertFalse(MorpheusRemoteRoutePolicy.usesBoundedUpstreamTimeout("PUT", "/api/v1/anything"));
        assertFalse(MorpheusRemoteRoutePolicy.usesBoundedUpstreamTimeout("DELETE", "/api/v1/anything"));
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
