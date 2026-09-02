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

    private static Path repositoryRoot() throws IOException {
        Path current = Path.of("").toAbsolutePath().normalize();
        while (current != null) {
            Path pom = current.resolve("pom.xml");
            if (Files.isRegularFile(pom)
                    && Files.readString(pom).contains("<artifactId>morpheus-engine</artifactId>")) {
                return current;
            }
            current = current.getParent();
        }
        throw new IOException("cannot locate MORPHEUS repository root");
    }
}
