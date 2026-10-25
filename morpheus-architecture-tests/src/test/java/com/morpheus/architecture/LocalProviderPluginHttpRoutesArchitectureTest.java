package com.morpheus.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalProviderPluginHttpRoutesArchitectureTest {

    @Test
    void providerPluginRoutesStayExtractedAndSecurityBounded() throws IOException {
        String server = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusHttpServer"));
        String routes = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusProviderPluginHttpRoutes"));

        assertTrue(server.contains("MorpheusProviderPluginHttpRoutes providerPluginRoutes"));
        assertTrue(server.contains("providerPluginRoutes.route(method, segments, query)"));
        assertFalse(server.contains("MorpheusProviderPluginApiService plugins ="));
        assertFalse(server.contains("provider-plugin probe is remote-only"));
        assertFalse(server.contains("unknown provider-plugin route"));

        assertTrue(routes.contains("boolean probeEnabled"));
        assertTrue(routes.contains("MorpheusHttpRouteGuards.requireMethod(method, \"GET\")"));
        assertTrue(routes.contains("MorpheusHttpRouteGuards.requireMethod(method, \"POST\")"));
        assertTrue(routes.contains("query.rejectUnknown(Set.of(\"directory\", \"pluginId\", \"workspace\", \"sha256\"))"));
        assertTrue(routes.contains("provider-plugin probe is remote-only"));
        assertFalse(routes.contains("HttpExchange"));
        assertFalse(routes.contains("HttpServer"));
        assertFalse(routes.contains("JsonMapper"));
        assertFalse(routes.contains("MorpheusHttpResponseWriter"));
        assertFalse(routes.contains("MorpheusRemoteRoutePolicy"));
        assertFalse(routes.contains("MorpheusRemoteRole"));
    }
}
