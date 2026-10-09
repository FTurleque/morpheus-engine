package com.morpheus.api;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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

    @Test
    void everyRouteOfTheTableCarriesARoleForExactlyItsMethods() {
        MorpheusHttpRouteTable.templates().forEach((template, methods) -> {
            String path = MorpheusHttpServer.API_PREFIX + (template.isEmpty() ? "" : "/" + template);
            for (String method : methods) {
                assertDoesNotThrow(() -> MorpheusRemoteRoutePolicy.requiredRole(method, path), method + " " + path);
            }
        });
    }

    @Test
    void allowedMethodsListTheMatchedRouteAndNothingForAnUnroutedPath() {
        assertEquals("GET, PUT", MorpheusRemoteRoutePolicy.allowedMethods("/api/v1/saved-views/view-1"));
        assertEquals("PUT", MorpheusRemoteRoutePolicy.allowedMethods("/api/v1/policy-packs/pack-1/overrides/rule-1"));
        assertEquals("POST", MorpheusRemoteRoutePolicy.allowedMethods("/api/v1/server/backups"));
        assertEquals("", MorpheusRemoteRoutePolicy.allowedMethods("/api/v1/future/read-model"));
        assertEquals("", MorpheusRemoteRoutePolicy.allowedMethods("/api/v1/projects//health"));
        assertEquals("", MorpheusRemoteRoutePolicy.allowedMethods("/other/projects"));
    }

    @Test
    void theRolesMustCoverTheRouteTableExactlyAndNameEachDefect() {
        Map<String, Set<String>> surface = Map.of("alpha", Set.of("GET"), "beta", Set.of("GET", "POST"));

        assertEquals(Set.of("alpha", "beta"), MorpheusRemoteRoutePolicy.requireExactCover(surface, List.of(
                MorpheusRemoteRoutePolicy.route("alpha", Map.of("GET", MorpheusRemoteRole.READ)),
                MorpheusRemoteRoutePolicy.route("beta",
                        Map.of("GET", MorpheusRemoteRole.READ, "POST", MorpheusRemoteRole.WRITE)))).keySet());

        IllegalStateException defects = assertThrows(IllegalStateException.class,
                () -> MorpheusRemoteRoutePolicy.requireExactCover(surface, List.of(
                        MorpheusRemoteRoutePolicy.route("beta", Map.of("GET", MorpheusRemoteRole.READ)),
                        MorpheusRemoteRoutePolicy.route("beta", Map.of("GET", MorpheusRemoteRole.READ)),
                        MorpheusRemoteRoutePolicy.route("gamma", Map.of("GET", MorpheusRemoteRole.READ)))));
        String message = defects.getMessage();
        assertTrue(message.contains("duplicate entry for /beta"), message);
        assertTrue(message.contains("entry for undeclared route /gamma"), message);
        assertTrue(message.contains("no entry for /alpha"), message);
        assertTrue(message.contains("/beta assigns roles to [GET] but accepts [GET, POST]"), message);
    }
}
