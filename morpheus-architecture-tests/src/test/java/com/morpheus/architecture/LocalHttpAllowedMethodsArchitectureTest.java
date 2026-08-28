package com.morpheus.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalHttpAllowedMethodsArchitectureTest {

    @Test
    void localAllowHeaderMappingStaysExtractedAndRouteFailuresRemainInFacade() throws IOException {
        Path root = repositoryRoot();
        String server = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusHttpServer.java"));
        String allowed = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusHttpAllowedMethods.java"));

        assertTrue(server.contains("private final MorpheusHttpAllowedMethods allowedMethods"));
        assertTrue(server.contains("allowedMethods.forPath(exchange.getRequestURI().getPath())"));
        assertTrue(server.contains("private void requireMethod(String actual, String expected)"));
        assertTrue(server.contains("ApiFailure.methodNotAllowed"));
        assertFalse(server.contains("private String allowedMethods(String path)"));

        assertTrue(allowed.contains("final class MorpheusHttpAllowedMethods"));
        assertTrue(allowed.contains("String forPath(String path)"));
        assertTrue(allowed.contains("MorpheusHttpPathParser"));
        assertTrue(allowed.contains("GET, POST"));
        assertTrue(allowed.contains("provider-plugins"));
        assertFalse(allowed.contains("HttpExchange"));
        assertFalse(allowed.contains("ApiFailure"));
        assertFalse(allowed.contains("MorpheusApiService"));
        assertFalse(allowed.contains("MorpheusRemoteRoutePolicy"));
        assertFalse(allowed.contains("MorpheusRemoteRole"));
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
