package com.morpheus.api;

import com.morpheus.application.context.TechnicalContextProvider;
import com.morpheus.application.files.SafeWorkspaceFileResolver;
import com.morpheus.application.lifecycle.mutation.ChangeWriteCapabilityResolver;
import com.morpheus.application.reference.ExternalIntegrationStatusProvider;
import com.morpheus.application.reference.ExternalReferenceResolverRegistry;
import com.morpheus.application.security.LocalWritePermissionHardener;
import com.morpheus.store.sqlite.SqliteServerMaintenance;
import com.sun.net.httpserver.HttpExchange;
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
import java.net.URI;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;

/**
 * M26 opt-in HTTPS facade for team/remote use.
 *
 * <p>The existing local API remains an internal loopback server. Authentication headers are consumed by this
 * facade and are never forwarded to that internal HTTP hop. The private hop is independently protected by a
 * per-process capability that is never exposed through the remote API.</p>
 */
public final class MorpheusRemoteHttpServer implements AutoCloseable {
    public static final int DEFAULT_MAX_CONCURRENT_REQUESTS = 64;
    public static final int MAX_CONCURRENT_REQUESTS = 512;
    static final int MAX_PROXY_RESPONSE_BYTES = 16 * 1024 * 1024;
    static final int MAX_PROXY_IN_FLIGHT_BYTES = 128 * 1024 * 1024;
    static final int MAX_KEYSTORE_BYTES = 4 * 1024 * 1024;
    static final Duration REQUEST_BODY_READ_TIMEOUT = Duration.ofSeconds(15);
    private static final int MAX_PROXY_RESPONSE_SLOTS = MAX_PROXY_IN_FLIGHT_BYTES / MAX_PROXY_RESPONSE_BYTES;
    private static final int MAX_REQUEST_BYTES = MorpheusHttpServer.MAX_REQUEST_BODY_BYTES;

    private final HttpsServer server;
    private final ExecutorService executor;
    private final MorpheusHttpServer localServer;
    private final SqliteServerMaintenance.ServerLease lease;
    private final SqliteServerMaintenance maintenance;
    private final Path databasePath;
    private final Path backupDirectory;
    private final Path authFile;
    private final Semaphore concurrency;
    private final MorpheusRemoteRuntimeState runtime;
    private final MorpheusRemoteResponseWriter responses = new MorpheusRemoteResponseWriter();
    private final MorpheusRemoteProxyTargetResolver proxyTargets;
    private final MorpheusRemoteProxyTransport proxyTransport;

    private MorpheusRemoteHttpServer(
            HttpsServer server,
            ExecutorService executor,
            MorpheusHttpServer localServer,
            MorpheusInternalCapability internalCapability,
            SqliteServerMaintenance.ServerLease lease,
            SqliteServerMaintenance maintenance,
            Path databasePath,
            Path backupDirectory,
            Path providerPluginDirectory,
            AllowedWorkspaceRoots allowedWorkspaceRoots,
            Path authFile,
            int maxConcurrentRequests) {
        this.server = server;
        this.executor = executor;
        this.localServer = localServer;
        this.lease = lease;
        this.maintenance = maintenance;
        this.databasePath = databasePath.toAbsolutePath().normalize();
        this.backupDirectory = backupDirectory.toAbsolutePath().normalize();
        this.authFile = Objects.requireNonNull(authFile, "authFile").toAbsolutePath().normalize();
        this.concurrency = new Semaphore(maxConcurrentRequests, true);
        this.runtime = new MorpheusRemoteRuntimeState(
                maxConcurrentRequests,
                REQUEST_BODY_READ_TIMEOUT,
                MAX_PROXY_RESPONSE_BYTES,
                MAX_PROXY_IN_FLIGHT_BYTES,
                MAX_PROXY_RESPONSE_SLOTS);
        this.proxyTargets = new MorpheusRemoteProxyTargetResolver(
                localServer.port(), providerPluginDirectory, allowedWorkspaceRoots);
        this.proxyTransport = new MorpheusRemoteProxyTransport(
                internalCapability, runtime, MAX_PROXY_RESPONSE_BYTES, MAX_PROXY_RESPONSE_SLOTS);
    }

    public static MorpheusRemoteHttpServer start(
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
        if (port < 0 || port > 65_535) throw new IllegalArgumentException("port must be between 0 and 65535");
        if (maxConcurrentRequests < 1 || maxConcurrentRequests > MAX_CONCURRENT_REQUESTS) {
            throw new IllegalArgumentException("maxConcurrentRequests must be between 1 and " + MAX_CONCURRENT_REQUESTS);
        }
        if (keyStorePassword == null || keyStorePassword.length == 0) {
            throw new IllegalArgumentException("remote TLS keystore password is required");
        }

        Path normalizedAuthFile = Objects.requireNonNull(authFile, "authFile").toAbsolutePath().normalize();
        List<MorpheusRemoteIdentityFile.Identity> identities = MorpheusRemoteIdentityFile.load(normalizedAuthFile);
        validateStartupIdentities(identities, Instant.now());

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
            int listenBacklog = Math.max(DEFAULT_MAX_CONCURRENT_REQUESTS, maxConcurrentRequests);
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
                    https, executor, local, internalCapability, lease, maintenance, databasePath, backupDirectory,
                    providerPluginDirectory, allowedWorkspaceRoots, normalizedAuthFile, maxConcurrentRequests);
            https.setExecutor(executor);
            https.createContext(MorpheusHttpServer.API_PREFIX, result::handle);
            https.start();
            return result;
        } catch (IOException | RuntimeException failure) {
            if (executor != null) executor.shutdownNow();
            if (local != null) local.close();
            lease.close();
            throw failure instanceof RuntimeException runtimeFailure
                    ? runtimeFailure
                    : new IllegalStateException("cannot start MORPHEUS remote HTTPS server", failure);
        }
    }

    static void validateStartupIdentities(
            List<MorpheusRemoteIdentityFile.Identity> identities,
            Instant now) {
        Objects.requireNonNull(identities, "identities");
        Objects.requireNonNull(now, "now");
        if (identities.isEmpty()) {
            throw new IllegalArgumentException("remote auth file contains no identities");
        }
        if (identities.stream().noneMatch(identity ->
                identity.role() == MorpheusRemoteRole.ADMIN && identity.isActiveAt(now))) {
            throw new IllegalArgumentException("remote auth file must contain at least one active ADMIN identity");
        }
    }

    public String host() {
        return server.getAddress().getAddress().getHostAddress();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    public URI baseUri() {
        return URI.create("https://" + hostForUri(host()) + ":" + port() + MorpheusHttpServer.API_PREFIX);
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
        localServer.close();
        lease.close();
    }

    private void handle(HttpExchange exchange) throws IOException {
        String requestId = UUID.randomUUID().toString();
        responses.applySecurityHeaders(exchange.getResponseHeaders(), requestId);
        runtime.recordRequest();
        if (!concurrency.tryAcquire()) {
            runtime.recordThrottledRequest();
            try {
                responses.sendError(exchange, 429, "TOO_MANY_REQUESTS", "remote request concurrency limit reached");
            } finally {
                exchange.close();
            }
            return;
        }
        runtime.requestStarted();
        try {
            MorpheusRemoteIdentityFile.Identity identity = authenticate(exchange);
            MorpheusRemoteRole required = requiredRole(exchange.getRequestMethod(), exchange.getRequestURI().getPath());
            if (!identity.role().allows(required)) {
                runtime.recordAuthorizationFailure();
                responses.sendError(
                        exchange,
                        403,
                        "FORBIDDEN",
                        "authenticated principal is not authorized for this operation");
                return;
            }

            String path = exchange.getRequestURI().getPath();
            if (path.equals(MorpheusHttpServer.API_PREFIX + "/server/status")) {
                requireMethod(exchange, "GET");
                responses.sendSuccess(exchange, 200, runtime.status(host(), port()));
                return;
            }
            if (path.equals(MorpheusHttpServer.API_PREFIX + "/server/backups")) {
                requireMethod(exchange, "POST");
                requireEmptyBody(exchange);
                SqliteServerMaintenance.BackupVerification backup = maintenance.createBackup(databasePath, backupDirectory);
                responses.sendSuccess(exchange, 201, backupView(backup));
                return;
            }
            proxy(exchange);
        } catch (RemoteFailure failure) {
            if (failure.status == 401) runtime.recordAuthenticationFailure();
            if (failure.status == 403) runtime.recordAuthorizationFailure();
            if (failure.status == 401) {
                exchange.getResponseHeaders().set("WWW-Authenticate", "Bearer realm=\"morpheus\"");
            }
            responses.sendError(exchange, failure.status, failure.code, failure.getMessage());
        } catch (IllegalArgumentException failure) {
            responses.sendError(exchange, 400, "BAD_REQUEST", safeMessage(failure));
        } catch (RuntimeException failure) {
            responses.sendError(exchange, 500, "INTERNAL_ERROR", "internal MORPHEUS remote server error");
        } finally {
            runtime.requestFinished();
            concurrency.release();
            exchange.close();
        }
    }

    private MorpheusRemoteIdentityFile.Identity authenticate(HttpExchange exchange) {
        List<String> values = exchange.getRequestHeaders().get("Authorization");
        if (values == null || values.size() != 1) {
            throw new RemoteFailure(401, "UNAUTHENTICATED", "valid Bearer authentication is required");
        }
        String header = values.getFirst();
        if (header == null || header.length() < 8 || !header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            throw new RemoteFailure(401, "UNAUTHENTICATED", "valid Bearer authentication is required");
        }
        String token = header.substring(7).trim();
        final Optional<MorpheusRemoteIdentityFile.Identity> authenticated;
        try {
            authenticated = authenticateCurrent(authFile, token);
        } catch (IllegalArgumentException failure) {
            throw new RemoteFailure(503, "AUTH_STORE_UNAVAILABLE", "remote authentication store is unavailable");
        }
        return authenticated.orElseThrow(() -> new RemoteFailure(
                401, "UNAUTHENTICATED", "valid Bearer authentication is required"));
    }

    static Optional<MorpusRemoteIdentityFile.Identity> authenticateCurrent(Path authFile, String token) {
        return MorpheusRemoteIdentityFile.authenticate(MorpheusRemoteIdentityFile.load(authFile), token);
    }

    private MorpheusRemoteRole requiredRole(String rawMethod, String path) {
        try {
            return MorpheusRemoteRoutePolicy.requiredRole(rawMethod, path);
        } catch (MorpheusRemoteRoutePolicy.RoutePolicyException failure) {
            throw new RemoteFailure(failure.status(), failure.code(), failure.getMessage());
        }
    }

    static boolean usesBoundedUpstreamTimeout(String rawMethod, String path) {
        return MorpheusRemoteRoutePolicy.usesBoundedUpstreamTimeout(rawMethod, path);
    }

    private void proxy(HttpExchange exchange) throws IOException {
        byte[] requestBody = readBoundedBody(exchange);
        URI requestUri = exchange.getRequestURI();
        boolean providerProbe = requestUri.getPath().equals(MorpheusHttpServer.API_PREFIX + "/provider-plugins/probe");
        if (providerProbe && requestBody.length != 0) {
            throw new RemoteFailure(400, "BAD_REQUEST", "provider-plugin probe request body must be empty");
        }
        final URI target;
        try {
            target = proxyTargets.resolve(requestUri);
        } catch (MorpheusRemoteProxyTargetResolver.ResolutionException failure) {
            throw new RemoteFailure(failure.status(), failure.code(), failure.getMessage());
        }
        try {
            proxyTransport.forward(
                    exchange,
                    target,
                    requestBody,
                    usesBoundedUpstreamTimeout(exchange.getRequestMethod(), requestUri.getPath()));
        } catch (MorpheusRemoteProxyTransport.TransportException failure) {
            throw new RemoteFailure(failure.status(), failure.code(), failure.getMessage());
        }
    }

    private byte[] readBoundedBody(HttpExchange exchange) throws IOException {
        try {
            return TimedBoundedInputReader.read(
                    exchange.getRequestBody(), MAX_REQUEST_BYTES, REQUEST_BODY_READ_TIMEOUT, executor);
        } catch (TimedBoundedInputReader.LimitExceededException tooLarge) {
            throw new RemoteFailure(413, "PAYLOAD_TOO_LARGE", "request body exceeds " + MAX_REQUEST_BYTES + " bytes");
        } catch (TimedBoundedInputReader.ReadTimeoutException timeout) {
            runtime.recordRequestTimeout();
            throw new RemoteFailure(408, "REQUEST_TIMEOUT", "request body exceeded its read deadline");
        }
    }

    private void requireEmptyBody(HttpExchange exchange) throws IOException {
        if (readBoundedBody(exchange).length != 0) {
            throw new RemoteFailure(400, "BAD_REQUEST", "request body must be empty");
        }
    }

    private void requireMethod(HttpExchange exchange, String expected) {
        if (!exchange.getRequestMethod().equalsIgnoreCase(expected)) {
            throw new RemoteFailure(405, "METHOD_NOT_ALLOWED", "expected " + expected);
        }
    }

    private Map<String, Object> backupView(SqliteServerMaintenance.BackupVerification backup) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("path", backup.path().toString());
        view.put("bytes", backup.bytes());
        view.put("sha256", backup.sha256());
        view.put("schemaVersion", backup.schemaVersion());
        view.put("integrityOk", backup.integrityOk());
        return Map.copyOf(view);
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
                    .readBytes(path.getFileName(), MAX_KEYSTORE_BYTES);

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
            if (encoded != null) java.util.Arrays.fill(encoded, (byte) 0);
            java.util.Arrays.fill(password, '\0');
        }
    }

    private static String requireHost(String host) {
        if (host == null || host.isBlank()) throw new IllegalArgumentException("remote host must not be blank");
        return host.trim();
    }

    private static String hostForUri(String host) {
        return host.contains(":") && !host.startsWith("[") ? "[" + host + "]" : host;
    }

    private static String safeMessage(RuntimeException failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    private static final class RemoteFailure extends RuntimeException {
        private final int status;
        private final String code;

        private RemoteFailure(int status, String code, String message) {
            super(message);
            this.status = status;
            this.code = code;
        }
    }
}
