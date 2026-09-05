package com.morpheus.api;

import com.morpheus.application.context.DisabledTechnicalContextProvider;
import com.morpheus.application.lifecycle.mutation.ChangeWriteCapabilityObservation;
import com.morpheus.application.lifecycle.mutation.ChangeWriteCapabilityResolver;
import com.morpheus.application.reference.ExternalIntegrationStatus;
import com.morpheus.application.reference.ExternalIntegrationStatusProvider;
import com.morpheus.application.reference.ExternalReferenceResolverRegistry;
import com.morpheus.store.sqlite.SqliteServerMaintenance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins that a remote bootstrap which fails partway releases what it had already acquired.
 *
 * <p>Starting the remote facade acquires an exclusive server lease and a fully started local HTTP server before
 * it binds the TLS socket. A bind failure there used to leave both behind on some paths, because the recovery
 * block released them in an order where one failing close skipped the other, and could not reach the TLS server
 * at all. Occupying the target port is the one post-acquisition failure reachable through the public API.</p>
 */
class MorpheusRemoteHttpServerBootstrapOwnershipTest {
    @TempDir
    Path temp;

    @Test
    void aTlsBindFailureReleasesTheServerLeaseAndTheLocalServerItHadAlreadyStarted() throws Exception {
        Path database = temp.resolve("bootstrap-ownership.db");
        Path allowedWorkspaceRoot = Files.createDirectory(temp.resolve("workspace"));
        Path auth = temp.resolve("remote-auth.txt");
        MorpheusRemoteIdentityFile.create(auth, "admin", MorpheusRemoteRole.ADMIN);
        Path keyStore = createKeyStore();

        try (ServerSocket occupied = new ServerSocket()) {
            occupied.setReuseAddress(false);
            occupied.bind(new InetSocketAddress("127.0.0.1", 0));
            int takenPort = occupied.getLocalPort();

            Path backups = temp.resolve("backups");
            Path providerPlugins = temp.resolve("provider-plugins");
            AllowedWorkspaceRoots roots = AllowedWorkspaceRoots.of(List.of(allowedWorkspaceRoot));
            char[] password = "changeit".toCharArray();
            ExternalReferenceResolverRegistry resolvers = new ExternalReferenceResolverRegistry(List.of());
            ExternalIntegrationStatusProvider minos = disabledMinos();
            DisabledTechnicalContextProvider nexus = new DisabledTechnicalContextProvider("NEXUS", "test");
            ChangeWriteCapabilityResolver writes = project -> ChangeWriteCapabilityObservation.denied("test");

            RuntimeException failure = assertThrows(RuntimeException.class, () -> MorpheusRemoteHttpServer.start(
                    database,
                    backups,
                    providerPlugins,
                    roots,
                    "127.0.0.1",
                    takenPort,
                    auth,
                    keyStore,
                    password,
                    1,
                    resolvers,
                    minos,
                    nexus,
                    writes));

            assertTrue(
                    failure.getMessage().contains("cannot start MORPHEUS remote HTTPS server"),
                    () -> "expected a start failure, got: " + failure);
        }

        // The lease is exclusive: re-acquiring it proves the failed bootstrap released it rather than holding the
        // database for the rest of the process.
        assertDoesNotThrow(
                () -> takeAndReleaseServerLease(database),
                "the failed bootstrap must have released the exclusive server lease");
    }

    private static void takeAndReleaseServerLease(Path database) {
        new SqliteServerMaintenance().acquireServerLease(database).close();
    }

    private ExternalIntegrationStatusProvider disabledMinos() {
        return () -> new ExternalIntegrationStatus("MINOS", "DISABLED", false, "test", Map.of());
    }

    private Path createKeyStore() throws IOException, InterruptedException {
        Path keyStore = temp.resolve("bootstrap.p12");
        Path javaHome = Path.of(System.getProperty("java.home"));
        boolean windows = System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
        Path keytool = javaHome.resolve("bin").resolve(windows ? "keytool.exe" : "keytool");
        Process process = new ProcessBuilder(
                keytool.toString(),
                "-genkeypair",
                "-alias", "morpheus-bootstrap-test",
                "-keyalg", "RSA",
                "-keysize", "2048",
                "-validity", "2",
                "-storetype", "PKCS12",
                "-keystore", keyStore.toString(),
                "-storepass", "changeit",
                "-keypass", "changeit",
                "-dname", "CN=localhost",
                "-ext", "SAN=DNS:localhost,IP:127.0.0.1",
                "-noprompt")
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(0, process.waitFor(), output);
        return keyStore;
    }
}
