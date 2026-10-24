package com.morpheus.api.remote;

import com.morpheus.api.MorpheusHttpRouteTable;
import com.morpheus.api.MorpheusHttpServer;
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
                requiredRole("GET", "/api/v1"));
        assertEquals(MorpheusRemoteRole.READ,
                requiredRole("GET", "/api/v1/health"));
        assertEquals(MorpheusRemoteRole.READ,
                requiredRole("GET", "/api/v1/readiness"));
        assertEquals(MorpheusRemoteRole.READ,
                requiredRole("GET", "/api/v1/projects/project-1/sync-status"));
        assertEquals(MorpheusRemoteRole.WRITE,
                requiredRole("POST", "/api/v1/projects/project-1/sync"));
        assertEquals(MorpheusRemoteRole.WRITE,
                requiredRole("POST", "/api/v1/portfolios/portfolio-1/traverse"));
        assertEquals(MorpheusRemoteRole.ADMIN,
                requiredRole("GET", "/api/v1/metrics"));
        assertEquals(MorpheusRemoteRole.ADMIN,
                requiredRole("POST", "/api/v1/server/backups"));
        assertEquals(MorpheusRemoteRole.ADMIN,
                requiredRole("POST", "/api/v1/provider-plugins/probe"));
    }

    @Test
    void explicitRegistryPreservesReadOnlyPostExceptions() {
        assertEquals(MorpheusRemoteRole.READ,
                requiredRole("POST", "/api/v1/queries/execute"));
        assertEquals(MorpheusRemoteRole.READ,
                requiredRole("POST", "/api/v1/exports"));
        assertEquals(MorpheusRemoteRole.READ,
                requiredRole("POST", "/api/v1/reasoning/analyze"));
        assertEquals(MorpheusRemoteRole.READ,
                requiredRole("POST", "/api/v1/saved-views/view-1/execute"));
        assertEquals(MorpheusRemoteRole.READ,
                requiredRole("POST", "/api/v1/saved-views/view-1/export"));
        assertEquals(MorpheusRemoteRole.READ,
                requiredRole("POST", "/api/v1/policies/evaluate"));
        assertEquals(MorpheusRemoteRole.READ,
                requiredRole("POST", "/api/v1/policies/dry-run"));
        assertEquals(MorpheusRemoteRole.READ,
                requiredRole(
                        "POST", "/api/v1/projects/project-1/changes/change-1/transition-check"));
        assertEquals(MorpheusRemoteRole.READ,
                requiredRole(
                        "POST", "/api/v1/projects/project-1/requirements/req-1/augmented-context"));
        assertEquals(MorpheusRemoteRole.WRITE,
                requiredRole(
                        "POST", "/api/v1/projects/project-1/changes/change-1/lifecycle-transitions"));
    }

    @Test
    void registryCoversQueryPolicyReasoningAndPortfolioExtensions() {
        assertEquals(MorpheusRemoteRole.READ,
                requiredRole("GET", "/api/v1/saved-views/view-1/versions"));
        assertEquals(MorpheusRemoteRole.WRITE,
                requiredRole("POST", "/api/v1/saved-views/view-1/archive"));
        assertEquals(MorpheusRemoteRole.READ,
                requiredRole("GET", "/api/v1/policy-packs/pack-1/audit"));
        assertEquals(MorpheusRemoteRole.WRITE,
                requiredRole("PUT", "/api/v1/policy-packs/pack-1/overrides/rule-1"));
        assertEquals(MorpheusRemoteRole.READ,
                requiredRole("GET", "/api/v1/policy-activations"));
        assertEquals(MorpheusRemoteRole.WRITE,
                requiredRole("POST", "/api/v1/policy-overrides/remove"));
        assertEquals(MorpheusRemoteRole.READ,
                requiredRole("GET", "/api/v1/reasoning/adapters"));
        assertEquals(MorpheusRemoteRole.READ,
                requiredRole("GET", "/api/v1/portfolios/portfolio-1/members"));
        assertEquals(MorpheusRemoteRole.WRITE,
                requiredRole("POST", "/api/v1/portfolios/portfolio-1/projects/project-1/freshness"));
    }

    @Test
    void unknownGetAndPostRoutesFailClosedInsteadOfInheritingVerbRoles() {
        MorpheusRemoteRoutePolicy.RoutePolicyException unknownGet = assertThrows(
                MorpheusRemoteRoutePolicy.RoutePolicyException.class,
                () -> requiredRole("GET", "/api/v1/future/read-model"));
        assertEquals(404, unknownGet.status());
        assertEquals("NOT_FOUND", unknownGet.code());

        MorpheusRemoteRoutePolicy.RoutePolicyException unknownPost = assertThrows(
                MorpheusRemoteRoutePolicy.RoutePolicyException.class,
                () -> requiredRole("POST", "/api/v1/future/augmented-context"));
        assertEquals(404, unknownPost.status());
        assertEquals("NOT_FOUND", unknownPost.code());
    }

    @Test
    void knownRouteWithWrongMethodFailsWith405() {
        MorpheusRemoteRoutePolicy.RoutePolicyException failure = assertThrows(
                MorpheusRemoteRoutePolicy.RoutePolicyException.class,
                () -> requiredRole("GET", "/api/v1/provider-plugins/probe"));
        assertEquals(405, failure.status());
        assertEquals("METHOD_NOT_ALLOWED", failure.code());

        MorpheusRemoteRoutePolicy.RoutePolicyException patch = assertThrows(
                MorpheusRemoteRoutePolicy.RoutePolicyException.class,
                () -> requiredRole("PATCH", "/api/v1/projects/project-1"));
        assertEquals(405, patch.status());
    }

    @Test
    void onlyAnAuthorizedReadIsBoundedUpstreamAndAnUnknownPathIsNeverClassified() {
        assertTrue(MorpheusRemoteRoutePolicy.authorize("GET", "/api/v1/health").usesBoundedUpstreamTimeout());
        assertTrue(MorpheusRemoteRoutePolicy.authorize(
                "POST", "/api/v1/projects/project-1/changes/change-1/transition-check").usesBoundedUpstreamTimeout());
        assertFalse(MorpheusRemoteRoutePolicy.authorize(
                "POST", "/api/v1/projects/project-1/changes/change-1/lifecycle-transitions")
                .usesBoundedUpstreamTimeout());
        assertFalse(MorpheusRemoteRoutePolicy.authorize(
                "POST", "/api/v1/provider-plugins/probe").usesBoundedUpstreamTimeout());
        assertEquals(404, assertThrows(MorpheusRemoteRoutePolicy.RoutePolicyException.class,
                () -> MorpheusRemoteRoutePolicy.authorize("PUT", "/api/v1/anything")).status());
        assertEquals(404, assertThrows(MorpheusRemoteRoutePolicy.RoutePolicyException.class,
                () -> MorpheusRemoteRoutePolicy.authorize("DELETE", "/api/v1/anything")).status());
    }

    private static MorpheusRemoteRole requiredRole(String rawMethod, String rawPath) {
        return MorpheusRemoteRoutePolicy.authorize(rawMethod, rawPath).role();
    }

    @Test
    void pathsMustRemainNormalizedInsideApiPrefix() {
        MorpheusRemoteRoutePolicy.RoutePolicyException outside = assertThrows(
                MorpheusRemoteRoutePolicy.RoutePolicyException.class,
                () -> requiredRole("GET", "/other/projects"));
        assertEquals(404, outside.status());

        MorpheusRemoteRoutePolicy.RoutePolicyException emptySegment = assertThrows(
                MorpheusRemoteRoutePolicy.RoutePolicyException.class,
                () -> requiredRole("GET", "/api/v1/projects//health"));
        assertEquals(404, emptySegment.status());
    }

    @Test
    void aPathIsReadAsItWasSentAndEachSegmentIsDecodedOnce() {
        assertEquals(MorpheusRemoteRole.WRITE,
                requiredRole("POST", "/api/v1/saved-views/view-1/%61rchive"));
        assertEquals("GET, PUT", MorpheusRemoteRoutePolicy.allowedMethods("/api/v1/saved-views/a%2Fb"));
        assertEquals("", MorpheusRemoteRoutePolicy.allowedMethods("/api/v1/saved-views/view-1/%2561rchive"));
        assertEquals("", MorpheusRemoteRoutePolicy.allowedMethods("/api/v1/provider-plugins%2Fprobe"));
        assertEquals("", MorpheusRemoteRoutePolicy.allowedMethods("/api/v1/health//"));
        assertEquals("", MorpheusRemoteRoutePolicy.allowedMethods("/api/v1health"));
        assertEquals("", MorpheusRemoteRoutePolicy.allowedMethods("/api/v1/%zz"));
    }

    @Test
    void aRouteIsNamedByThePathTheAuthorizationRead() {
        assertTrue(MorpheusRemoteRoutePolicy.authorize("GET", "/api/v1/server/status").names("server/status"));
        assertTrue(MorpheusRemoteRoutePolicy.authorize("GET", "/api/v1/server/%73tatus/").names("server/status"));
        assertFalse(MorpheusRemoteRoutePolicy.authorize("POST", "/api/v1/server/backups").names("server/status"));

        MorpheusRemoteRoutePolicy.Authorization view =
                MorpheusRemoteRoutePolicy.authorize("PUT", "/api/v1/saved-views/view-1");
        assertTrue(view.names("saved-views/{viewId}"));
        assertEquals(MorpheusRemoteRole.WRITE, view.role());
        assertFalse(view.usesBoundedUpstreamTimeout());
        assertTrue(MorpheusRemoteRoutePolicy.authorize("GET", "/api/v1/saved-views/view-1")
                .usesBoundedUpstreamTimeout());

        assertEquals(404, assertThrows(MorpheusRemoteRoutePolicy.RoutePolicyException.class,
                () -> MorpheusRemoteRoutePolicy.authorize("GET", "/api/v1/server%2Fstatus")).status());
    }

    @Test
    void everyRouteOfTheTableCarriesARoleForExactlyItsMethods() {
        MorpheusHttpRouteTable.templates().forEach((template, methods) -> {
            String path = MorpheusHttpServer.API_PREFIX + (template.isEmpty() ? "" : "/" + template);
            for (String method : methods) {
                assertDoesNotThrow(() -> requiredRole(method, path), method + " " + path);
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
