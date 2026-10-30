package com.morpheus.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameContaining;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalHttpServerBootstrapArchitectureTest {

    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.morpheus.api");

    @Test
    void localServerKeepsDispatchWhileBootstrapOwnsRuntimeStartup() throws IOException {
        String server = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusHttpServer"));
        String bootstrap = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusLocalHttpServerBootstrap"));

        assertTrue(server.contains("return MorpheusLocalHttpServerBootstrap.start(databasePath, host, port);"));
        assertTrue(server.contains("return MorpheusLocalHttpServerBootstrap.startRemote("));
        assertFalse(server.contains("SqliteSpecificationKnowledgeStore"),
                "the local server names no SQLite store: the stores of each request are built by the runtime of "
                + "the transport");
        assertFalse(server.contains("InetSocketAddress"),
                "the local server binds no address: it reads the address its bootstrap bound through the JDK "
                + "server, a dependency on InetSocketAddress a rule would refuse, and names none");
        assertFalse(server.contains("HttpServer.create("));
        assertFalse(server.contains("Executors.newVirtualThreadPerTaskExecutor()"));
        assertFalse(server.contains("startConfigured("));

        assertTrue(bootstrap.contains("new RuntimeSnapshotRecovery(recovery.snapshots(), recorder).recoverAll(Instant.now())"));
        assertTrue(bootstrap.contains("try (ApiRuntime recovery = new ApiRuntime(databasePath, recorder))"));
        assertTrue(bootstrap.contains("LoopbackHostPolicy.requireLoopbackAddress(host)"));
        assertTrue(bootstrap.contains("HttpServer.create(new InetSocketAddress(bindAddress, port), 0)"));
        assertTrue(bootstrap.contains("new LoopbackRequestProtectedHttpServer(delegate)"));
        assertTrue(bootstrap.contains("new CapabilityProtectedHttpServer(loopbackProtected, capability)"));
        assertTrue(bootstrap.contains("Executors.newVirtualThreadPerTaskExecutor()"));
        assertTrue(bootstrap.contains("DatabaseWorkOffloadingHttpServer.over(httpServer)"));
        assertTrue(bootstrap.contains("databaseWork.createContext(MorpheusHttpRouteTable.API_PREFIX, result::handle)"));
        assertTrue(bootstrap.contains(
                "MorpheusQueryHttpRoutes.register(databaseWork, databasePath, recorder, result.requestDecoder(),"));
        assertFalse(bootstrap.contains("MorpheusQueryHttpRoutes.register(httpServer,"),
                "a router that reaches the database is registered through the database workers: on the request's "
                        + "own virtual thread, a wait inside the SQLite driver holds a carrier of the scheduler");
        assertEquals(1, bootstrap.split("httpServer\\.createContext\\(", -1).length - 1,
                "only the routes answered without the database are registered outside the database workers");
        assertTrue(bootstrap.contains("for (String probe : MorpheusRootHttpRoutes.ANSWERED_WITHOUT_THE_DATABASE) {"));
        assertTrue(bootstrap.contains(
                "httpServer.createContext(MorpheusHttpRouteTable.API_PREFIX + \"/\" + probe, result::handle);"));
        assertTrue(bootstrap.contains("result.responseWriter())"));
        assertTrue(bootstrap.contains("httpServer.start()"));

        assertTrue(server.contains("private final MorpheusChangesHttpRoutes changesRoutes;"));
        assertTrue(server.contains("case \"changes\" -> changesRoutes.route("));
        assertTrue(server.contains("private MorpheusHttpRouteResponse route(HttpExchange exchange)"));
        assertTrue(server.contains("MorpheusHttpResponseWriter responseWriter"));
        assertFalse(bootstrap.contains("case \"changes\""));
        assertFalse(bootstrap.contains("unknown project API resource"));
    }

    /**
     * {@code MorpheusHttpServer} dispatches requests and leaves its start-up to
     * {@code MorpheusLocalHttpServerBootstrap}: it wraps no protection and recovers no snapshot. The rule bans a
     * dependency on any class whose simple name contains {@code RuntimeSnapshotRecovery},
     * {@code LoopbackRequestProtectedHttpServer}, {@code CapabilityProtectedHttpServer}, as the text ban it replaces
     * matched the name.
     *
     * <p>None of these types declares a compile-time constant, so the rule alone covers them. Broken before acceptance,
     * 30/10/2026: a {@code RuntimeSnapshotRecovery} field added to {@code MorpheusHttpServer} failed this rule.</p>
     */
    @Test
    void theLocalServerLeavesItsStartupToTheBootstrap() {
        noClasses()
                .that(ClassesOfASource.declaredIn("MorpheusHttpServer"))
                .should().dependOnClassesThat(simpleNameContaining("RuntimeSnapshotRecovery")
                        .or(simpleNameContaining("LoopbackRequestProtectedHttpServer"))
                        .or(simpleNameContaining("CapabilityProtectedHttpServer")))
                .because("binding, protecting and recovering at start-up are the bootstrap's; the server only dispatches")
                .check(PRODUCTION);
    }

    /**
     * {@code MorpheusLocalHttpServerBootstrap} starts the server and builds neither its routers nor its response
     * writer. The rule bans a dependency on any class whose simple name contains {@code MorpheusChangesHttpRoutes},
     * {@code MorpheusHttpResponseWriter}, as the text ban it replaces matched the name.
     *
     * <p>None of these types declares a compile-time constant, so the rule alone covers them. Broken before acceptance,
     * 30/10/2026: a {@code MorpheusChangesHttpRoutes} field added to {@code MorpheusLocalHttpServerBootstrap} failed
     * this rule.</p>
     */
    @Test
    void theLocalBootstrapBuildsNoRouterAndWritesNoResponse() {
        noClasses()
                .that(ClassesOfASource.declaredIn("MorpheusLocalHttpServerBootstrap"))
                .should().dependOnClassesThat(simpleNameContaining("MorpheusChangesHttpRoutes")
                        .or(simpleNameContaining("MorpheusHttpResponseWriter")))
                .because("the server holds its routers and its writer; the bootstrap only starts it")
                .check(PRODUCTION);
    }
}
