package com.morpheus.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalHttpAllowedMethodsArchitectureTest {

    @Test
    void localAllowHeaderMappingStaysExtractedAndRouteFailuresStayInLocalRouteLayer() throws IOException {
        Path root = repositoryRoot();
        String server = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusHttpServer.java"));
        String rootRoutes = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusRootHttpRoutes.java"));
        String projectRootRoutes = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusProjectRootHttpRoutes.java"));
        String allowed = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusHttpAllowedMethods.java"));

        assertTrue(server.contains("private final MorpheusHttpAllowedMethods allowedMethods"));
        assertTrue(server.contains("allowedMethods.forPath(exchange.getRequestURI().getPath())"));
        assertFalse(server.contains("private void requireMethod(String actual, String expected)"));
        assertTrue(server.contains("projectRootRoutes.route(exchange, method, segments, query)"));
        assertFalse(server.contains("ApiFailure.methodNotAllowed"));
        assertFalse(server.contains("projects supports GET and POST"));
        assertFalse(server.contains("private String allowedMethods(String path)"));

        assertTrue(rootRoutes.contains("MorpheusHttpRouteGuards.requireMethod(method, \"GET\")"));
        assertFalse(rootRoutes.contains("MorpheusHttpAllowedMethods"));
        assertFalse(rootRoutes.contains("getResponseHeaders().set(\"Allow\""));

        assertTrue(projectRootRoutes.contains("ApiFailure.methodNotAllowed(\"projects supports GET and POST\")"));
        assertFalse(projectRootRoutes.contains("MorpheusHttpAllowedMethods"));
        assertFalse(projectRootRoutes.contains("getResponseHeaders().set(\"Allow\""));

        assertTrue(allowed.contains("final class MorpheusHttpAllowedMethods"));
        assertTrue(allowed.contains("String forPath(String path)"));
        assertTrue(allowed.contains("MorpheusHttpPathParser"));
        assertTrue(allowed.contains("MorpheusHttpRouteTable.methodsOf(segments)"));
        assertFalse(allowed.contains(".equals(\""), "the Allow header is read from the route table, not matched by hand");
        assertFalse(allowed.contains("HttpExchange"));
        assertFalse(allowed.contains("ApiFailure"));
        assertFalse(allowed.contains("MorpheusApiService"));
        assertFalse(allowed.contains("MorpheusRemoteRoutePolicy"));
        assertFalse(allowed.contains("MorpheusRemoteRole"));
    }

    /**
     * The four routers that register their own context set {@code Allow} on their own 405s; until 8 October 2026 each
     * computed it by hand and five routes answered a wrong one. They now read the server's
     * {@code MorpheusHttpAllowedMethods}, received at {@code register(...)} like the decoder and the writer.
     */
    @Test
    void routersRegisteringTheirOwnContextReadTheAllowHeaderFromTheRouteTable() throws IOException {
        Path api = repositoryRoot().resolve("morpheus-api/src/main/java/com/morpheus/api");
        for (String router : List.of("MorpheusPolicyHttpRoutes", "MorpheusPolicyManagementHttpRoutes",
                "MorpheusQueryHttpRoutes", "MorpheusReasoningHttpRoutes")) {
            String source = Files.readString(api.resolve(router + ".java"));
            assertTrue(source.contains("exchange.getResponseHeaders().set(\"Allow\", allowedMethods.forPath("), router);
            assertFalse(source.contains("String allowed("), router);
        }
    }

    /** The table states routes and methods only: roles stay in the remote policy, which reads the table. */
    @Test
    void theRouteTableKnowsNothingOfTheRemoteAuthorizationModel() throws IOException {
        Path api = repositoryRoot().resolve("morpheus-api/src/main/java/com/morpheus/api");
        String table = Files.readString(api.resolve("MorpheusHttpRouteTable.java"));
        String policy = Files.readString(api.resolve("MorpheusRemoteRoutePolicy.java"));

        assertFalse(table.contains("MorpheusRemote"));
        assertTrue(policy.contains("requireExactCover(MorpheusHttpRouteTable.templates(), ROUTES)"));
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
