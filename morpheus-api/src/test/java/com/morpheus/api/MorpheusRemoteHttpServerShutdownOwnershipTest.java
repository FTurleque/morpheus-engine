package com.morpheus.api;

import com.morpheus.application.context.DisabledTechnicalContextProvider;
import com.morpheus.application.lifecycle.mutation.ChangeWriteCapabilityObservation;
import com.morpheus.application.operability.OperationalLogMode;
import com.morpheus.application.operability.OperationalRuntime;
import com.morpheus.application.reference.ExternalIntegrationStatus;
import com.morpheus.application.reference.ExternalReferenceResolverRegistry;
import com.morpheus.store.sqlite.SqliteServerMaintenance;
import com.sun.net.httpserver.HttpsServer;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins that shutting the remote facade down releases everything it owns even when one release fails.
 *
 * <p>{@code close()} was a bare sequence -- stop the TLS server, stop the executor, close the local server,
 * release the lease -- so a failure at any step skipped the rest. The lease is released last and is exclusive:
 * skipping it leaves the database reserved for the rest of the process while the facade reports itself down.</p>
 */
class MorpheusRemoteHttpServerShutdownOwnershipTest {
    @TempDir
    Path temp;

    /**
     * A handler journals its outcome after its exchange is closed, so {@code server.stop(0)} returning does not mean
     * it has finished. Interrupting it at once lost the record, and the journal could recreate its file after the
     * caller had deleted the directory -- which is how two remote tests failed on the Windows runner.
     */
    @Test
    void closeLetsARequestInFlightFinishBeforeItReturns() throws Exception {
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        MorpheusRemoteHttpServer server = facade(executor);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean finished = new AtomicBoolean();
        AtomicBoolean interrupted = new AtomicBoolean();
        executor.execute(() -> {
            started.countDown();
            try {
                release.await();
                finished.set(true);
            } catch (InterruptedException stopped) {
                interrupted.set(true);
            }
        });
        assertTrue(started.await(5, TimeUnit.SECONDS));

        Thread closing = Thread.ofPlatform().start(server::close);
        closing.join(Duration.ofMillis(200));
        assertTrue(closing.isAlive(), "close must not return while a handler is still running");
        release.countDown();
        closing.join(Duration.ofSeconds(5));

        assertFalse(closing.isAlive(), "close must return once the handler has finished");
        assertTrue(finished.get(), "the handler already running must be let finish");
        assertFalse(interrupted.get(), "a handler that finishes within the grace is not interrupted");
    }

    @Test
    void closeInterruptsARequestThatOutlivesTheGrace() throws Exception {
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        MorpheusRemoteHttpServer server = facade(executor);
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        executor.execute(() -> {
            started.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException stopped) {
                interrupted.countDown();
            }
        });
        assertTrue(started.await(5, TimeUnit.SECONDS));

        server.close();

        assertTrue(interrupted.await(5, TimeUnit.SECONDS),
                "a handler still running after " + MorpheusRemoteHttpServer.IN_FLIGHT_GRACE + " must be interrupted");
        assertTrue(executor.isTerminated() || executor.awaitTermination(5, TimeUnit.SECONDS));
    }

    private MorpheusRemoteHttpServer facade(ExecutorService executor) throws Exception {
        Path database = temp.resolve("in-flight.db");
        Path auth = temp.resolve("remote-auth.txt");
        MorpheusRemoteIdentityFile.create(auth, "admin", MorpheusRemoteRole.ADMIN);
        SqliteServerMaintenance maintenance = new SqliteServerMaintenance();
        SqliteServerMaintenance.ServerLease lease = maintenance.acquireServerLease(database);
        MorpheusInternalCapability capability = MorpheusInternalCapability.generate();
        MorpheusHttpServer local = MorpheusHttpServer.startRemote(
                database,
                MorpheusHttpServer.DEFAULT_HOST,
                0,
                new ExternalReferenceResolverRegistry(List.of()),
                () -> new ExternalIntegrationStatus("MINOS", "DISABLED", false, "test", Map.of()),
                new DisabledTechnicalContextProvider("NEXUS", "test"),
                project -> ChangeWriteCapabilityObservation.denied("test"),
                AllowedWorkspaceRoots.of(List.of(Files.createDirectory(temp.resolve("in-flight-workspace")))),
                capability, OperationalRuntime.of(OperationalLogMode.OFF));
        return new MorpheusRemoteHttpServer(
                HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0),
                executor,
                local,
                capability,
                lease,
                maintenance,
                database,
                temp.resolve("backups"),
                temp.resolve("provider-plugins"),
                AllowedWorkspaceRoots.of(List.of(temp)),
                auth,
                1, OperationalRuntime.of(OperationalLogMode.OFF));
    }

    @Test
    void aFailingExecutorShutdownStillReleasesTheLocalServerAndTheExclusiveLease() throws Exception {
        Path database = temp.resolve("shutdown-ownership.db");
        Path auth = temp.resolve("remote-auth.txt");
        MorpheusRemoteIdentityFile.create(auth, "admin", MorpheusRemoteRole.ADMIN);

        SqliteServerMaintenance maintenance = new SqliteServerMaintenance();
        SqliteServerMaintenance.ServerLease lease = maintenance.acquireServerLease(database);
        MorpheusInternalCapability capability = MorpheusInternalCapability.generate();
        MorpheusHttpServer local = MorpheusHttpServer.startRemote(
                database,
                MorpheusHttpServer.DEFAULT_HOST,
                0,
                new ExternalReferenceResolverRegistry(List.of()),
                () -> new ExternalIntegrationStatus("MINOS", "DISABLED", false, "test", Map.of()),
                new DisabledTechnicalContextProvider("NEXUS", "test"),
                project -> ChangeWriteCapabilityObservation.denied("test"),
                AllowedWorkspaceRoots.of(List.of(Files.createDirectory(temp.resolve("workspace")))),
                capability, OperationalRuntime.of(OperationalLogMode.OFF));
        URI localProbe = local.baseUri();

        IllegalStateException injected = new IllegalStateException("injected executor shutdown failure");
        ExecutorService delegate = Executors.newVirtualThreadPerTaskExecutor();
        MorpheusRemoteHttpServer server = new MorpheusRemoteHttpServer(
                HttpsServer.create(new InetSocketAddress("127.0.0.1", 0), 0),
                refusingToReportShutdown(delegate, injected),
                local,
                capability,
                lease,
                maintenance,
                database,
                temp.resolve("backups"),
                temp.resolve("provider-plugins"),
                AllowedWorkspaceRoots.of(List.of(temp)),
                auth,
                1, OperationalRuntime.of(OperationalLogMode.OFF));

        IllegalStateException thrown = assertThrows(IllegalStateException.class, server::close);
        assertSame(injected, thrown, "the caller must see the release that failed, not a later one");

        assertTrue(delegate.isShutdown(), "the executor itself must have been stopped before it reported failure");
        assertThrows(IOException.class, () -> probe(localProbe),
                "the local server must be closed even though the release before it failed");

        // The server lease is exclusive: re-acquiring it is the only proof that the failed shutdown released it
        // rather than holding the database for the rest of the process.
        assertDoesNotThrow(() -> new SqliteServerMaintenance().acquireServerLease(database).close(),
                "the failed shutdown must still have released the exclusive server lease");
    }

    /** Shuts the delegate down for real, then fails, so the test injects a failure without leaking threads. */
    private static ExecutorService refusingToReportShutdown(ExecutorService delegate, RuntimeException failure) {
        return (ExecutorService) Proxy.newProxyInstance(
                MorpheusRemoteHttpServerShutdownOwnershipTest.class.getClassLoader(),
                new Class<?>[]{ExecutorService.class},
                (proxy, method, args) -> {
                    if (method.getName().startsWith("shutdown")) {
                        delegate.shutdownNow();
                        throw failure;
                    }
                    try {
                        return method.invoke(delegate, args);
                    } catch (InvocationTargetException invoked) {
                        throw invoked.getCause();
                    }
                });
    }

    private static void probe(URI localProbe) throws Exception {
        try (HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()) {
            client.send(
                    HttpRequest.newBuilder(localProbe.resolve("health")).GET().build(),
                    HttpResponse.BodyHandlers.discarding());
        }
    }
}
