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
    void defaultsToGetForRootMalformedAndReadOnlyRoutes() {
        for (String path : List.of(
                "/api/v1",
                "/api/v1/",
                "/api/v1/health",
                "/api/v1/projects/p1",
                "/api/v1/portfolios/p1",
                "/api/v1/unknown",
                "/api/v1/projects//sync",
                "/wrong-prefix/projects")) {
            assertEquals("GET", allowed.forPath(path), path);
        }
    }

    @Test
    void mapsProviderPluginMethodsExactly() {
        assertEquals("GET", allowed.forPath("/api/v1/provider-plugins/discover"));
        assertEquals("POST", allowed.forPath("/api/v1/provider-plugins/probe"));
        assertEquals("GET", allowed.forPath("/api/v1/provider-plugins/unknown"));
    }

    @Test
    void mapsCollectionAndPortfolioMutationMethodsExactly() {
        assertEquals("GET, POST", allowed.forPath("/api/v1/projects"));
        assertEquals("GET, POST", allowed.forPath("/api/v1/portfolios"));
        assertEquals("GET, POST", allowed.forPath("/api/v1/portfolios/p1/projects"));
        assertEquals("GET, POST", allowed.forPath("/api/v1/portfolios/p1/references"));
        assertEquals("POST", allowed.forPath("/api/v1/portfolios/p1/traverse"));
        assertEquals("POST", allowed.forPath("/api/v1/portfolios/p1/projects/project-1/missing"));
        assertEquals("POST", allowed.forPath("/api/v1/portfolios/p1/projects/project-1/freshness"));
        assertEquals("GET", allowed.forPath("/api/v1/portfolios/p1/projects/project-1/other"));
        assertEquals("GET", allowed.forPath("/api/v1/portfolios/p1/conflicts"));
    }

    @Test
    void mapsProjectMutationMethodsExactly() {
        assertEquals("POST", allowed.forPath("/api/v1/projects/p1/sync"));
        for (String resource : List.of("requirements", "changes")) {
            for (String child : List.of("augmented-context", "transition-check", "lifecycle-transitions")) {
                assertEquals("POST", allowed.forPath(
                        "/api/v1/projects/p1/" + resource + "/item-1/" + child), resource + "/" + child);
            }
        }
        assertEquals("GET", allowed.forPath("/api/v1/projects/p1/sync-status"));
        assertEquals("GET", allowed.forPath("/api/v1/projects/p1/changes/item-1/status"));
        assertEquals("GET", allowed.forPath("/api/v1/projects/p1/requirements/item-1/trace"));
    }
}
