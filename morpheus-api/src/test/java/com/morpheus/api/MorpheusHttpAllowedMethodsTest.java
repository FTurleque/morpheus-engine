package com.morpheus.api;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MorpheusHttpAllowedMethodsTest {
    private final MorpheusHttpAllowedMethods allowed = new MorpheusHttpAllowedMethods(
            new MorpheusHttpPathParser(MorpheusHttpServer.API_PREFIX));

    @Test
    void rejectsNullPathParser() {
        assertThrows(NullPointerException.class, () -> new MorpheusHttpAllowedMethods(null));
    }

    @Test
    void allowsNoMethodOnAPathNoRouteMatches() {
        for (String path : List.of(
                "/api/v1/unknown",
                "/api/v1/projects//sync",
                "/api/v1/provider-plugins/unknown",
                "/api/v1/portfolios/p1/projects/project-1/other",
                "/wrong-prefix/projects")) {
            assertEquals("", allowed.forPath(path), path);
        }
    }

    @Test
    void listsTheSortedMethodsOfTheMatchedRoute() {
        assertEquals("GET", allowed.forPath("/api/v1"));
        assertEquals("GET", allowed.forPath("/api/v1/"));
        assertEquals("GET", allowed.forPath("/api/v1/projects/p1/sync-status"));
        assertEquals("POST", allowed.forPath("/api/v1/projects/p1/sync"));
        assertEquals("GET, POST", allowed.forPath("/api/v1/projects"));
        assertEquals("POST", allowed.forPath("/api/v1/portfolios/p1/projects"));
        assertEquals("GET, POST", allowed.forPath("/api/v1/portfolios/p1/references"));
        assertEquals("GET, PUT", allowed.forPath("/api/v1/saved-views/view-1"));
        assertEquals("GET", allowed.forPath("/api/v1/saved-views/view-1/versions"));
        assertEquals("GET", allowed.forPath("/api/v1/policy-packs/pack-1/audit"));
        assertEquals("PUT", allowed.forPath("/api/v1/policy-packs/pack-1/overrides/rule-1"));
        assertEquals("GET", allowed.forPath("/api/v1/reasoning/adapters"));
    }

    @Test
    void agreesWithTheRouteTableOnEveryRoute() {
        MorpheusHttpRouteTable.declaredRoutes().forEach((template, methods) -> assertEquals(
                String.join(", ", methods),
                allowed.forPath(template.replaceAll("\\{[^}/]+}", "id-1")),
                template));
    }
}
