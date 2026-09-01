package com.morpheus.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalProviderPluginHttpRoutesArchitectureTest {

    @Test
    void providerPluginRoutesStayExtractedAndSecurityBounded() throws IOException {
        Path root = repositoryRoot();
        String server = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusHttpServer.java"));
        String routes = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusProviderPluginHttpRoutes.java"));

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

    private Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("contracts/public-surfaces.tsv"))
                    && Files.isRegularFile(current.resolve("pom.xml"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("cannot locate MORPHEUS repository root");
    }
}
