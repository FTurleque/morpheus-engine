package com.morpheus.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalHttpRouteResponseArchitectureTest {

    @Test
    void localRouteResponseCarrierStaysExtractedAndPure() throws IOException {
        Path root = repositoryRoot();
        String server = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusHttpServer.java"));
        String response = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusHttpRouteResponse.java"));

        assertTrue(server.contains("MorpheusHttpRouteResponse"));
        assertFalse(server.contains("private record RouteResponse"));

        assertTrue(response.contains("record MorpheusHttpRouteResponse(int status, Object data)"));
        assertTrue(response.contains("route status must be between 200 and 599"));
        assertTrue(response.contains("Objects.requireNonNull(data, \"data\")"));
        assertFalse(response.contains("HttpExchange"));
        assertFalse(response.contains("MorpheusApiService"));
        assertFalse(response.contains("ApiFailure"));
        assertFalse(response.contains("MorpheusRemoteRoutePolicy"));
        assertFalse(response.contains("MorpheusRemoteRole"));
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
