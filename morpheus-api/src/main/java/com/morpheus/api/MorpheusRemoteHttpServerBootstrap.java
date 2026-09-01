package com.morpheus.api;

import com.morpheus.application.context.TechnicalContextProvider;
import com.morpheus.application.files.SafeWorkspaceFileResolver;
import com.morpheus.application.lifecycle.mutation.ChangeWriteCapabilityResolver;
import com.morpheus.application.reference.ExternalIntegrationStatusProvider;
import com.morpheus.application.reference.ExternalReferenceResolverRegistry;
import com.morpheus.application.security.LocalWritePermissionHardener;
import com.morpheus.store.sqlite.SqliteServerMaintenance;
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
            ChangeWriteCapabilityResolver writeCapabilityResolver) {
        Objects.requireNonNull(databasePath, "databasePath");
        Objects.requireNonNull(backupDirectory, "backupDirectory");
        Objects.requireNonNull(providerPluginDirectory, "providerPluginDirectory");
        Objects.requireNonNull(allowedWorkspaceRoots, "allowedWorkspaceRoots");
        Objects.requireNonNull(resolverRegistry, "resolverRegistry");
        Objects.requireNonNull(minosStatus, "minosStatus");
        Objects.requireNonNull(technicalContextProvider, "technicalContextProvider");
        Objects.requireNonNull(writeCapabilityResolver, "writeCapabilityResolver");
        String normalizedHost = requireHost(host);
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

        Path normalizedAuthFile = Objects.requireNonNull(authFile, "authFile").toAbsolutePath().normalize();
        List<MorpheusRemoteIdentityFile.Identity> identities = MorpheusRemoteIdentityFile.load(normalizedAuthFile);
        MorpheusRemoteHttpServer.validateStartupIdentities(identities, Instant.now());

        SSLContext sslContext = buildSslContext(keyStorePath, keyStorePassword.clone());
        SqliteServerMaintenance maintenance = new SqliteServerMaintenance();
        SqliteServerMaintenance.ServerLease lease = maintenance.acquireServerLease(databasePath);
        MorpheusInternalCapability internalCapability = MorpheusInternalCapability.generate();
        MorpheusHttpServer local = null;
        ExecutorService executor = null;
        try {
            local = MorpheusHttpServer.startRemote(
                    databasePath,
                    MorpheusHttpServer.DEFAULT_HOST,
                    0,
                    resolverRegistry,
                    minosStatus,
                    technicalContextProvider,
                    writeCapabilityResolver,
                    allowedWorkspaceRoots,
                    internalCapability);
            int listenBacklog = Math.max(MorpheusRemoteHttpServer.DEFAULT_MAX_CONCURRENT_REQUESTS, maxConcurrentRequests);
            HttpsServer https = HttpsServer.create(new InetSocketAddress(normalizedHost, port), listenBacklog);
            https.setHttpsConfigurator(new HttpsConfigurator(sslContext) {
                @Override
                public void configure(HttpsParameters parameters) {
                    SSLParameters secure = sslContext.getDefaultSSLParameters();
                    secure.setProtocols(new String[]{"TLSv1.3", "TLSv1.2"});
                    parameters.setSSLParameters(secure);
                }
            });
            executor = Executors.newVirtualThreadPerTaskExecutor();
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
                    maxConcurrentRequests);
            https.setExecutor(executor);
            https.createContext(MorpheusHttpServer.API_PREFIX, result::handle);
            https.start();
            return result;
        } catch (IOException | RuntimeException failure) {
            if (executor != null) {
                executor.shutdownNow();
            }
            if (local != null) {
                local.close();
            }
            lease.close();
            throw failure instanceof RuntimeException runtimeFailure
                    ? runtimeFailure
                    : new IllegalStateException("cannot start MORPHEUS remote HTTPS server", failure);
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
