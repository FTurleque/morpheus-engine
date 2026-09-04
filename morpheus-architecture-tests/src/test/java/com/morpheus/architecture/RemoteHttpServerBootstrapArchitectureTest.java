package com.morpheus.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoteHttpServerBootstrapArchitectureTest {

    @Test
    void remoteServerKeepsRequestPolicyWhileBootstrapOwnsTlsAndRuntimeStartup() throws IOException {
        Path root = repositoryRoot();
        String server = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusRemoteHttpServer.java"));
        String bootstrap = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusRemoteHttpServerBootstrap.java"));

        assertTrue(server.contains("return MorpheusRemoteHttpServerBootstrap.start("));
        assertFalse(server.contains("HttpsServer.create("));
        assertFalse(server.contains("HttpsConfigurator"));
        assertFalse(server.contains("SSLContext"));
        assertFalse(server.contains("LocalWritePermissionHardener"));
        assertFalse(server.contains("SafeWorkspaceFileResolver"));
        assertFalse(server.contains("Executors.newVirtualThreadPerTaskExecutor()"));
        assertFalse(server.contains("acquireServerLease("));
        assertFalse(server.contains("MorpheusInternalCapability.generate()"));
        assertFalse(server.contains("MorpheusHttpServer.startRemote("));
        assertFalse(server.contains("buildSslContext("));
        assertFalse(server.contains("requireHost("));

        assertTrue(bootstrap.contains("HttpsServer.create(new InetSocketAddress(normalizedHost, port), listenBacklog)"));
        assertTrue(bootstrap.contains("new HttpsConfigurator(sslContext)"));
        assertTrue(bootstrap.contains("secure.setProtocols(new String[]{\"TLSv1.3\", \"TLSv1.2\"})"));
        assertTrue(bootstrap.contains("new LocalWritePermissionHardener().requireWriteProtectedDirectory(parent)"));
        assertTrue(bootstrap.contains("SafeWorkspaceFileResolver.rootedAt(parent)"));
        assertTrue(bootstrap.contains("maintenance.acquireServerLease(databasePath)"));
        assertTrue(bootstrap.contains("MorpheusInternalCapability.generate()"));
        assertTrue(bootstrap.contains("MorpheusHttpServer.startRemote("));
        assertTrue(bootstrap.contains("Executors.newVirtualThreadPerTaskExecutor()"));
        assertTrue(bootstrap.contains("https.createContext(MorpheusHttpServer.API_PREFIX, result::handle)"));
        assertTrue(bootstrap.contains("https.start()"));

        assertTrue(server.contains("private MorpheusRemoteIdentityFile.Identity authenticate(HttpExchange exchange)"));
        assertTrue(server.contains("private MorpheusRemoteRole requiredRole(String rawMethod, String path)"));
        assertTrue(server.contains("private void proxy(HttpExchange exchange)"));
        assertTrue(server.contains("private byte[] readBoundedBody(HttpExchange exchange)"));
        assertTrue(server.contains("maintenance.createBackup(databasePath, backupDirectory)"));
        assertTrue(server.contains("concurrency.tryAcquire()"));
        assertFalse(bootstrap.contains("authenticate(HttpExchange"));
        assertFalse(bootstrap.contains("requiredRole("));
        assertFalse(bootstrap.contains("proxy(HttpExchange"));
        assertFalse(bootstrap.contains("MorpheusRemoteResponseWriter"));
        assertFalse(bootstrap.contains("createBackup("));
    }

    /**
     * The TLS keystore password gets no long-lived copy of MORPHEUS's own making.
     *
     * <p>The JVM already holds it as a {@link String} the moment it arrives from the environment or a property,
     * and nothing here can erase that. What MORPHEUS controls is whether it keeps a second one: the parsed
     * launch options carried the password as a field for the entire lifetime of the running server, and the
     * record's generated {@code toString()} would have rendered it into any diagnostic that printed them.</p>
     */
    @Test
    void theTlsKeystorePasswordIsResolvedLateHeldMutablyAndWiped() throws IOException {
        Path root = repositoryRoot();
        String options = Files.readString(root.resolve(
                "morpheus-cli/src/main/java/com/morpheus/cli/RemoteApiLaunchOptions.java"));
        String main = Files.readString(root.resolve(
                "morpheus-cli/src/main/java/com/morpheus/cli/MorpheusMain.java"));
        String handle = Files.readString(root.resolve(
                "morpheus-cli/src/main/java/com/morpheus/cli/TlsKeystorePassword.java"));
        String bootstrap = Files.readString(root.resolve(
                "morpheus-api/src/main/java/com/morpheus/api/MorpheusRemoteHttpServerBootstrap.java"));

        assertFalse(options.contains("String tlsPassword"),
                "the launch options must not retain the TLS password as an immutable field");
        assertTrue(options.contains("TlsKeystorePassword tlsPassword"));
        assertFalse(options.contains("--tls-password"),
                "the TLS password must never become a command-line argument");
        assertTrue(options.contains("MORPHEUS_SERVER_TLS_PASSWORD"));

        assertTrue(handle.contains("char[] resolve()"), "the password must reach the keystore as a char[]");
        assertTrue(handle.contains("value=<redacted>"), "the password handle must render redacted");

        assertTrue(main.contains("char[] keyStorePassword = options.tlsPassword().resolve();"));
        assertTrue(main.contains("Arrays.fill(keyStorePassword, '\\0');"),
                "the launcher must wipe the password buffer once the server has started");

        assertTrue(bootstrap.contains("java.util.Arrays.fill(password, '\\0');"),
                "the bootstrap must wipe its own copy once the SSLContext exists");
        assertTrue(bootstrap.contains("java.util.Arrays.fill(encoded, (byte) 0);"),
                "the bootstrap must wipe the decoded keystore bytes");
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
