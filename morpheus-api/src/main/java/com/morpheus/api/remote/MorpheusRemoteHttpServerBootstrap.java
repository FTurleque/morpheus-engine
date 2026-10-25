package com.morpheus.api.remote;

import com.morpheus.api.AllowedWorkspaceRoots;
import com.morpheus.api.local.MorpheusHttpServer;
import com.morpheus.api.local.MorpheusInternalCapability;
import com.morpheus.api.transport.MorpheusHttpRouteTable;
import com.morpheus.application.context.TechnicalContextProvider;
import com.morpheus.application.files.SafeWorkspaceFileResolver;
import com.morpheus.application.lifecycle.mutation.ChangeWriteCapabilityResolver;
import com.morpheus.application.operability.OperationalRuntime;
import com.morpheus.application.operability.StartupOwnership;
import com.morpheus.application.reference.ExternalIntegrationStatusProvider;
import com.morpheus.application.reference.ExternalReferenceResolverRegistry;
import com.morpheus.application.security.LocalWritePermissionHardener;
import com.morpheus.store.sqlite.runtime.SqliteServerMaintenance;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Owns remote HTTPS runtime bootstrap while {@link MorpheusRemoteHttpServer} keeps request policy and proxying. */
final class MorpheusRemoteHttpServerBootstrap {

    private MorpheusRemoteHttpServerBootstrap() {
    }

    static MorpheusRemoteHttpServer start(
            Path databasePath,
            Path backupDirectory,
            Path providerPluginDirectory,
            AllowedWorkspaceRoots allowedWorkspaceRoots,
            String host,
            int port,
            Path authFile,
            Path keyStorePath,
            char[] keyStorePassword,
            int maxConcurrentRequests,
            ExternalReferenceResolverRegistry resolverRegistry,
            ExternalIntegrationStatusProvider minosStatus,
            TechnicalContextProvider technicalContextProvider,
            ChangeWriteCapabilityResolver writeCapabilityResolver,
            OperationalRuntime operational) {
        Objects.requireNonNull(databasePath, "databasePath");
        Objects.requireNonNull(operational, "operational");
        Objects.requireNonNull(backupDirectory, "backupDirectory");
        Objects.requireNonNull(providerPluginDirectory, "providerPluginDirectory");
        Objects.requireNonNull(allowedWorkspaceRoots, "allowedWorkspaceRoots");
        Objects.requireNonNull(resolverRegistry, "resolverRegistry");
        Objects.requireNonNull(minosStatus, "minosStatus");
        Objects.requireNonNull(technicalContextProvider, "technicalContextProvider");
        Objects.requireNonNull(writeCapabilityResolver, "writeCapabilityResolver");
        String normalizedHost = requireHost(host);
        requireServingLimits(port, maxConcurrentRequests, keyStorePassword);

        Path normalizedAuthFile = requireServableIdentityFile(authFile);

        SSLContext sslContext = buildSslContext(keyStorePath, keyStorePassword.clone());
        SqliteServerMaintenance maintenance = new SqliteServerMaintenance();
        // Ownership is entered before the first resource is acquired: a lease taken outside the try would stay
        // held for the rest of the process if anything failed before ownership began. HttpsServer.create binds the
        // TLS socket, so it is registered with the owner like every other resource the recovery path must release.
        try (StartupOwnership owned = new StartupOwnership()) {
            SqliteServerMaintenance.ServerLease lease = owned.keep(
                    maintenance.acquireServerLease(databasePath), SqliteServerMaintenance.ServerLease::close);
            MorpheusInternalCapability internalCapability = MorpheusInternalCapability.generate();
            MorpheusHttpServer local = owned.keep(MorpheusHttpServer.startRemote(
                    databasePath,
                    MorpheusHttpServer.DEFAULT_HOST,
                    0,
                    resolverRegistry,
                    minosStatus,
                    technicalContextProvider,
                    writeCapabilityResolver,
                    allowedWorkspaceRoots,
                    internalCapability,
                    operational), MorpheusHttpServer::close);
            int listenBacklog = Math.max(MorpheusRemoteHttpServer.DEFAULT_MAX_CONCURRENT_REQUESTS, maxConcurrentRequests);
            HttpsServer https = owned.keep(
                    HttpsServer.create(new InetSocketAddress(normalizedHost, port), listenBacklog),
                    server -> server.stop(0));
            https.setHttpsConfigurator(modernTlsOnly(sslContext));
            ExecutorService executor = owned.keep(
                    Executors.newVirtualThreadPerTaskExecutor(), ExecutorService::shutdownNow);
            MorpheusRemoteHttpServer result = new MorpheusRemoteHttpServer(
                    https,
                    executor,
                    local,
                    internalCapability,
                    lease,
                    maintenance,
                    databasePath,
                    backupDirectory,
                    providerPluginDirectory,
                    allowedWorkspaceRoots,
                    normalizedAuthFile,
                    maxConcurrentRequests,
                    operational);
            https.setExecutor(executor);
            https.createContext(MorpheusHttpRouteTable.API_PREFIX, result::handle);
            https.start();
            owned.transferred();
            return result;
        } catch (IOException failure) {
            throw new IllegalStateException("cannot start MORPHEUS remote HTTPS server", failure);
        }
    }

    private static Path requireServableIdentityFile(Path authFile) {
        Path normalizedAuthFile = Objects.requireNonNull(authFile, "authFile").toAbsolutePath().normalize();
        List<MorpheusRemoteIdentityFile.Identity> identities = MorpheusRemoteIdentityFile.load(normalizedAuthFile);
        MorpheusRemoteHttpServer.validateStartupIdentities(identities, Instant.now());
        return normalizedAuthFile;
    }

    private static HttpsConfigurator modernTlsOnly(SSLContext sslContext) {
        return new HttpsConfigurator(sslContext) {
            @Override
            public void configure(HttpsParameters parameters) {
                SSLParameters secure = sslContext.getDefaultSSLParameters();
                secure.setProtocols(new String[]{"TLSv1.3", "TLSv1.2"});
                parameters.setSSLParameters(secure);
            }
        };
    }

    private static void requireServingLimits(int port, int maxConcurrentRequests, char[] keyStorePassword) {
        if (port < 0 || port > 65_535) {
            throw new IllegalArgumentException("port must be between 0 and 65535");
        }
        if (maxConcurrentRequests < 1 || maxConcurrentRequests > MorpheusRemoteHttpServer.MAX_CONCURRENT_REQUESTS) {
            throw new IllegalArgumentException(
                    "maxConcurrentRequests must be between 1 and " + MorpheusRemoteHttpServer.MAX_CONCURRENT_REQUESTS);
        }
        if (keyStorePassword == null || keyStorePassword.length == 0) {
            throw new IllegalArgumentException("remote TLS keystore password is required");
        }
    }

    private static SSLContext buildSslContext(Path keyStorePath, char[] password) {
        Objects.requireNonNull(keyStorePath, "keyStorePath");
        Path path = keyStorePath.toAbsolutePath().normalize();
        byte[] encoded = null;
        try {
            Path parent = path.getParent();
            if (parent == null) {
                throw new IllegalArgumentException("remote TLS keystore must have a parent directory");
            }
            new LocalWritePermissionHardener().requireWriteProtectedDirectory(parent);
            encoded = SafeWorkspaceFileResolver.rootedAt(parent)
                    .readBytes(path.getFileName(), MorpheusRemoteHttpServer.MAX_KEYSTORE_BYTES);

            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            try (InputStream input = new ByteArrayInputStream(encoded)) {
                keyStore.load(input, password);
            }
            KeyManagerFactory keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            keyManagers.init(keyStore, password);
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(keyManagers.getKeyManagers(), null, null);
            return context;
        } catch (IOException | GeneralSecurityException failure) {
            throw new IllegalArgumentException("cannot initialize remote TLS keystore", failure);
        } finally {
            if (encoded != null) {
                java.util.Arrays.fill(encoded, (byte) 0);
            }
            java.util.Arrays.fill(password, '\0');
        }
    }

    private static String requireHost(String host) {
        if (host == null || host.isBlank()) {
            throw new IllegalArgumentException("remote host must not be blank");
        }
        return host.trim();
    }
}
