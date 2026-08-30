package com.morpheus.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalHttpServerBootstrapArchitectureTest {

    @Test
    void localServerKeepsDispatchWhileBootstrapOwnsRuntimeStartup() throws IOException {
        Path root = repositoryRoot();
        String server = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusHttpServer.java"));
        String bootstrap = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusLocalHttpServerBootstrap.java"));

        assertTrue(server.contains("return MorpheusLocalHttpServerBootstrap.start(databasePath, host, port);"));
        assertTrue(server.contains("return MorpheusLocalHttpServerBootstrap.startRemote("));
        assertFalse(server.contains("RuntimeSnapshotRecovery"));
        assertFalse(server.contains("SqliteSpecificationKnowledgeStore"));
        assertFalse(server.contains("InetSocketAddress"));
        assertFalse(server.contains("HttpServer.create("));
        assertFalse(server.contains("Executors.newVirtualThreadPerTaskExecutor()"));
        assertFalse(server.contains("LoopbackRequestProtectedHttpServer"));
        assertFalse(server.contains("CapabilityProtectedHttpServer"));
        assertFalse(server.contains("startConfigured("));

        assertTrue(bootstrap.contains("new RuntimeSnapshotRecovery(store).recoverAll(Instant.now())"));
        assertTrue(bootstrap.contains("new SqliteSpecificationKnowledgeStore(databasePath)"));
        assertTrue(bootstrap.contains("LoopbackHostPolicy.requireLoopbackAddress(host)"));
        assertTrue(bootstrap.contains("HttpServer.create(new InetSocketAddress(bindAddress, port), 0)"));
        assertTrue(bootstrap.contains("new LoopbackRequestProtectedHttpServer(delegate)"));
        assertTrue(bootstrap.contains("new CapabilityProtectedHttpServer(loopbackProtected, capability)"));
        assertTrue(bootstrap.contains("Executors.newVirtualThreadPerTaskExecutor()"));
        assertTrue(bootstrap.contains("httpServer.createContext(MorpheusHttpServer.API_PREFIX, result::handle)"));
        assertTrue(bootstrap.contains("MorpheusQueryHttpRoutes.register(httpServer, databasePath)"));
        assertTrue(bootstrap.contains("httpServer.start()"));

        assertTrue(server.contains("private final MorpheusChangesHttpRoutes changesRoutes;"));
        assertTrue(server.contains("case \"changes\" -> changesRoutes.route("));
        assertTrue(server.contains("private MorpheusHttpRouteResponse route(HttpExchange exchange)"));
        assertTrue(server.contains("MorpheusHttpResponseWriter responseWriter"));
        assertFalse(bootstrap.contains("MorpheusChangesHttpRoutes"));
        assertFalse(bootstrap.contains("MorpheusHttpResponseWriter"));
        assertFalse(bootstrap.contains("case \"changes\""));
        assertFalse(bootstrap.contains("unknown project API resource"));
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
