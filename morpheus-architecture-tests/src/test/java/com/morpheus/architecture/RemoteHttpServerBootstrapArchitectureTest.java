package com.morpheus.architecture;

import com.sun.net.httpserver.HttpServer;
import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.Executors;

import static com.tngtech.archunit.core.domain.JavaClass.Predicates.simpleNameContaining;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoteHttpServerBootstrapArchitectureTest {

    private static final JavaClasses PRODUCTION = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.morpheus.api");

    /** The calls that create a JDK server or the executor serving it: the bootstrap's, never the server's. */
    private static final DescribedPredicate<JavaMethodCall> STARTS_A_SERVER_OR_ITS_EXECUTOR =
            DescribedPredicate.describe(
            "creates a JDK HTTP server or an executor",
            call -> call.getTargetOwner().getName().equals(Executors.class.getName())
                    || call.getName().equals("create") && call.getTargetOwner().isAssignableTo(HttpServer.class)
                    || call.getName().equals("startRemote")
                    && call.getTargetOwner().getName().equals("com.morpheus.api.local.MorpheusHttpServer"));

    @Test
    void remoteServerKeepsRequestPolicyWhileBootstrapOwnsTlsAndRuntimeStartup() throws IOException {
        String server = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusRemoteHttpServer"));
        String bootstrap = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusRemoteHttpServerBootstrap"));

        assertTrue(server.contains("return MorpheusRemoteHttpServerBootstrap.start("));
        assertFalse(server.contains("acquireServerLease("));
        assertFalse(server.contains("MorpheusInternalCapability.generate()"));
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
        assertTrue(bootstrap.contains("https.createContext(MorpheusHttpRouteTable.API_PREFIX, result::handle)"));
        assertTrue(bootstrap.contains("https.start()"));

        assertTrue(server.contains("private MorpheusRemoteIdentityFile.Identity authenticate(HttpExchange exchange)"));
        assertTrue(server.contains("private MorpheusRemoteRoutePolicy.Authorization authorize(String rawMethod, String rawPath)"));
        assertTrue(server.contains("private void proxy(HttpExchange exchange, MorpheusRemoteRoutePolicy.Authorization route)"));
        assertTrue(server.contains("private byte[] readBoundedBody(HttpExchange exchange)"));
        assertTrue(server.contains("maintenance.createBackup(databasePath, backupDirectory)"));
        assertTrue(server.contains("concurrency.tryAcquire()"));
        assertFalse(bootstrap.contains("authenticate(HttpExchange"));
        assertFalse(bootstrap.contains("requiredRole("));
        assertFalse(bootstrap.contains("proxy(HttpExchange"));
        assertFalse(bootstrap.contains("createBackup("));
    }

    /**
     * The TLS keystore password gets no long-lived copy of MORPHEUS's own making.
     *
     * <p>The JVM already holds it as a {@link String} the moment it arrives from the environment, and nothing
     * here can erase that. The environment is its only source (ADR-0105): a {@code -D} property is readable by
     * every account through {@code /proc/<pid>/cmdline}; restoring that fallback fails this test on the first
     * assertion naming the property (observed 2026-09-22). What MORPHEUS controls is whether it keeps a second
     * one: the parsed launch options carried the password as a field for the entire lifetime of the running
     * server, and the record's generated {@code toString()} would have rendered it into any diagnostic that
     * printed them.</p>
     */
    @Test
    void theTlsKeystorePasswordIsResolvedLateHeldMutablyAndWiped() throws IOException {
        String options = Files.readString(ProductionSources.mainSource("morpheus-cli", "RemoteApiLaunchOptions"));
        String main = Files.readString(ProductionSources.mainSource("morpheus-cli", "MorpheusMain"));
        String handle = Files.readString(ProductionSources.mainSource("morpheus-cli", "TlsKeystorePassword"));
        String bootstrap = Files.readString(ProductionSources.mainSource("morpheus-api", "MorpheusRemoteHttpServerBootstrap"));

        assertFalse(options.contains("String tlsPassword"),
                "the launch options must not retain the TLS password as an immutable field");
        assertTrue(options.contains("TlsKeystorePassword tlsPassword"));
        assertFalse(options.contains("--tls-password"),
                "the TLS password must never become a command-line argument");
        assertFalse(options.contains("morpheus.server.tls.password"),
                "a JVM property is a command-line argument: /proc/<pid>/cmdline is world-readable");
        assertTrue(options.contains("MORPHEUS_SERVER_TLS_PASSWORD"));
        assertFalse(options.contains("protected property"),
                "the refusal must name the environment variable, not a property nothing protects");
        assertFalse(handle.contains("protected property"),
                "the refusal must name the environment variable, not a property nothing protects");

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

    /**
     * {@code MorpheusRemoteHttpServer} applies the request policy and leaves TLS and the hardening of its files to
     * {@code MorpheusRemoteHttpServerBootstrap}. The rule bans a dependency on any class whose simple name contains
     * {@code HttpsConfigurator}, {@code SSLContext}, {@code LocalWritePermissionHardener},
     * {@code SafeWorkspaceFileResolver}, as the text ban it replaces matched the name.
     *
     * <p>The server creates neither the JDK server, nor its executor, nor the local upstream it proxies to.</p>
     *
     * <p>None of these types declares a compile-time constant, so the rule alone covers them. Broken before acceptance,
     * 30/10/2026: a {@code HttpsConfigurator} field added to {@code MorpheusRemoteHttpServer} failed this rule, and so
     * did an {@code HttpsServer.create} call added to it.</p>
     */
    @Test
    void theRemoteServerLeavesTlsAndItsFilesToTheBootstrap() {
        noClasses()
                .that(ClassesOfASource.declaredIn("MorpheusRemoteHttpServer"))
                .should().dependOnClassesThat(simpleNameContaining("HttpsConfigurator")
                        .or(simpleNameContaining("SSLContext"))
                        .or(simpleNameContaining("LocalWritePermissionHardener"))
                        .or(simpleNameContaining("SafeWorkspaceFileResolver")))
                .orShould().callMethodWhere(STARTS_A_SERVER_OR_ITS_EXECUTOR)
                .because("TLS set-up and file hardening happen once, at start-up, in the bootstrap; the server serves "
                + "requests")
                .check(PRODUCTION);
    }

    /**
     * {@code MorpheusRemoteHttpServerBootstrap} starts the remote server and writes no response. The rule bans a
     * dependency on any class whose simple name contains {@code MorpheusRemoteResponseWriter}, as the text ban it
     * replaces matched the name.
     *
     * <p>None of these types declares a compile-time constant, so the rule alone covers them. Broken before acceptance,
     * 30/10/2026: a {@code MorpheusRemoteResponseWriter} field added to {@code MorpheusRemoteHttpServerBootstrap}
     * failed this rule.</p>
     */
    @Test
    void theRemoteBootstrapWritesNoResponse() {
        noClasses()
                .that(ClassesOfASource.declaredIn("MorpheusRemoteHttpServerBootstrap"))
                .should().dependOnClassesThat(simpleNameContaining("MorpheusRemoteResponseWriter"))
                .because("every remote response goes through the server and its writer; a bootstrap that wrote one would "
                + "bypass the request policy")
                .check(PRODUCTION);
    }
}
