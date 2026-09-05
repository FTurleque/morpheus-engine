package com.morpheus.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins that runtimes which acquire before they finish assembling stay releasable.
 *
 * <p>Each of these acquires something the process cannot forget -- a bound socket, an executor, a SQLite
 * connection -- and then keeps building. Assigning straight to fields meant a failure partway left those
 * acquisitions with no owner at all: the constructor never returns, so nothing can close a half-built runtime.
 * The behaviour is covered by the tests those modules own; this keeps the shape from coming back by hand.</p>
 */
class PartialRuntimeOwnershipContractTest {
    @Test
    void assemblersThatAcquireBeforeTheyFinishRegisterWithStartupOwnership() throws IOException {
        Path root = repositoryRoot();
        assertTrue(
                Files.isRegularFile(root.resolve(
                        "morpheus-application/src/main/java/com/morpheus/application/operability/StartupOwnership.java")),
                "the shared startup-ownership primitive must exist");

        List<String> assemblers = List.of(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusLocalHttpServerBootstrap.java",
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusRemoteHttpServerBootstrap.java",
                "morpheus-mcp/src/main/java/com/morpheus/mcp/MorpheusMcpRuntime.java");

        for (String assembler : assemblers) {
            String content = Files.readString(root.resolve(assembler));
            assertTrue(content.contains("try (StartupOwnership owned = new StartupOwnership())"),
                    () -> assembler + " must hold what it acquires until assembly finishes");
            assertTrue(content.contains("owned.transferred();"),
                    () -> assembler + " must transfer ownership once the runtime is complete");
        }
    }

    /**
     * The remote bootstrap's TLS server used to be declared inside the try, which put it out of reach of the
     * recovery path: a failure after the bind could not stop it.
     */
    @Test
    void theRemoteBootstrapKeepsItsTlsServerReachableFromTheRecoveryPath() throws IOException {
        String bootstrap = Files.readString(repositoryRoot().resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusRemoteHttpServerBootstrap.java"));

        assertTrue(bootstrap.contains("HttpsServer https = owned.keep("),
                "the bound TLS server must be registered for release");
        assertFalse(bootstrap.contains("HttpsServer https = HttpsServer.create("),
                "an unregistered TLS server cannot be stopped when startup fails after the bind");
    }

    /**
     * Ownership must start before the first acquisition, not after it. The remote bootstrap took the exclusive
     * server lease and generated the internal capability above the try, so a failure in between held the
     * database for the rest of the process with nothing left able to release it.
     */
    @Test
    void theRemoteBootstrapEntersOwnershipBeforeItTakesTheExclusiveServerLease() throws IOException {
        String bootstrap = Files.readString(repositoryRoot().resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusRemoteHttpServerBootstrap.java"));

        int ownership = bootstrap.indexOf("try (StartupOwnership owned = new StartupOwnership())");
        int acquisition = bootstrap.indexOf("maintenance.acquireServerLease(databasePath)");
        assertTrue(ownership >= 0 && acquisition > ownership,
                "the server lease must be acquired inside the block that can release it");
        assertFalse(bootstrap.contains("ServerLease lease = maintenance.acquireServerLease("),
                "an acquisition that is not registered as it happens leaves a window with no owner");
    }

    /**
     * The teardown side of the same invariant. A shutdown written as a bare sequence of close calls stops at the
     * first failure, and what it skips is everything after it -- including, for the remote facade, the exclusive
     * lease it releases last.
     */
    @Test
    void shutdownPathsThatOwnSeveralResourcesReleaseEveryOneOfThem() throws IOException {
        Path root = repositoryRoot();
        assertTrue(
                Files.isRegularFile(root.resolve(
                        "morpheus-application/src/main/java/com/morpheus/application/operability/ExhaustiveShutdown.java")),
                "the shared exhaustive-shutdown primitive must exist");

        List<String> owners = List.of(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusRemoteHttpServer.java",
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusHttpServer.java",
                "morpheus-api/src/main/java/com/morpheus/api/ApiRuntime.java",
                "morpheus-cli/src/main/java/com/morpheus/cli/CliRuntime.java",
                "morpheus-mcp/src/main/java/com/morpheus/mcp/MorpheusMcpRuntime.java");

        for (String owner : owners) {
            String content = Files.readString(root.resolve(owner));
            assertTrue(content.contains("ExhaustiveShutdown.releaseAll("),
                    () -> owner + " must release every resource it owns, not stop at the first failure");
        }

        String remote = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusRemoteHttpServer.java"));
        assertFalse(remote.contains("localServer.close();"),
                "a bare close call leaves the exclusive lease held when an earlier release fails");
    }

    /**
     * The MCP transport owns four single-thread schedulers per configured peer and cannot reach the shared
     * shutdown primitive: morpheus-mcp-transport deliberately depends on neither domain nor application. It
     * disposed them after stopping the peer process, and stopping a peer walks its process tree and reads its
     * exit status -- so a failure there left one set of threads running per peer for the rest of the process.
     */
    @Test
    void theMcpClientTransportDisposesItsSchedulersEvenWhenStoppingThePeerFails() throws IOException {
        String transport = Files.readString(repositoryRoot().resolve(
                "morpheus-mcp-transport/src/main/java/com/morpheus/integration/mcp/BoundedStdioClientTransport.java"));

        String statements = transport.replaceAll("\\s+", " ");
        assertTrue(
                statements.contains(
                        "try { shutdownProcess(); } finally { disposeSchedulers(); state.set(State.CLOSED); }"),
                "scheduler disposal and the terminal state must not depend on the peer shutdown succeeding");
        assertTrue(
                statements.contains(
                        "try { destroyObservedProcessTree(process.get()); } finally { disposeSchedulers();"
                                + " state.set(State.FAILED); }"),
                "the fail-closed path must release the same threads on the same terms");
    }

    private static Path repositoryRoot() throws IOException {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            Path pom = current.resolve("pom.xml");
            if (Files.isRegularFile(pom)) {
                String content = Files.readString(pom);
                // A module pom names morpheus-engine as its <parent>, so the module list is what marks the root.
                if (content.contains("<artifactId>morpheus-engine</artifactId>") && content.contains("<modules>")) {
                    return current;
                }
            }
            current = current.getParent();
        }
        throw new IOException("cannot locate MORPHEUS repository root");
    }
}
