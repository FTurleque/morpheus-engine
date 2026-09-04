package com.morpheus.api;

import com.morpheus.application.context.TechnicalContextProvider;
import com.morpheus.application.lifecycle.mutation.ChangeWriteCapabilityResolver;
import com.morpheus.application.operability.ExhaustiveShutdown;
import com.morpheus.application.reference.ExternalIntegrationStatusProvider;
import com.morpheus.application.reference.ExternalReferenceResolverRegistry;
import com.morpheus.store.sqlite.SqliteServerMaintenance;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsServer;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
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
    private static final int MAX_PRESENTED_BEARER_CHARS = 1024;

    private final HttpsServer server;
    private final ExecutorService executor;
    private final MorpheusHttpServer localServer;
    private final SqliteServerMaintenance.ServerLease lease;
    private final SqliteServerMaintenance maintenance;
    private final Path databasePath;
    private final Path backupDirectory;
    private final Path authFile;
    private final MorpheusRemoteIdentitySnapshotCache identityCache;
    private final Semaphore authenticationConcurrency;
    private final Semaphore concurrency;
    private final Semaphore privilegedConcurrency;
    private final Semaphore observabilityConcurrency;
    private final MorpheusRemoteRuntimeState runtime;
    private final MorpheusRemoteResponseWriter responses = new MorpheusRemoteResponseWriter();
    private final MorpheusRemoteProxyTargetResolver proxyTargets;
    private final MorpheusRemoteProxyTransport proxyTransport;

    // Wires together every transport, persistence, and access-control collaborator of the remote HTTPS facade
    // explicitly, per MorpheusMain's explicit-wiring convention (see .claude/rules/architecture.md, "Pas de
    // framework, pas de magie"); grouping these into holder records would blur which parameter carries a
    // security-sensitive capability (internalCapability, authFile) for this security-critical class.
    @SuppressWarnings("java:S107")
    MorpheusRemoteHttpServer(
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
        this.identityCache = new MorpheusRemoteIdentitySnapshotCache(this.authFile);
        this.authenticationConcurrency = new Semaphore(authenticationConcurrencyLimit(maxConcurrentRequests), true);
        this.concurrency = new Semaphore(maxConcurrentRequests, true);
        this.privilegedConcurrency = new Semaphore(privilegedConcurrencyLimit(maxConcurrentRequests), true);
        this.observabilityConcurrency = new Semaphore(observabilityConcurrencyLimit(maxConcurrentRequests), true);
        this.runtime = new MorpheusRemoteRuntimeState(
                maxConcurrentRequests,
                privilegedConcurrencyLimit(maxConcurrentRequests),
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
        return MorpheusRemoteHttpServerBootstrap.start(
                databasePath,
                backupDirectory,
                providerPluginDirectory,
                allowedWorkspaceRoots,
                host,
                port,
                authFile,
                keyStorePath,
                keyStorePassword,
                maxConcurrentRequests,
                resolverRegistry,
                minosStatus,
                technicalContextProvider,
                writeCapabilityResolver);
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

    // The exclusive server lease is released last and matters most: a failure in any earlier release used to
    // skip it, leaving the database reserved for the rest of the process while the facade reported itself shut
    // down. Every release now runs, and the first failure is the one the caller sees.
    @Override
    public void close() {
        ExhaustiveShutdown.releaseAll(
                "cannot shut down the MORPHEUS remote HTTPS server",
                () -> server.stop(0),
                executor::shutdownNow,
                localServer::close,
                lease::close);
    }

    void handle(HttpExchange exchange) throws IOException {
        String requestId = UUID.randomUUID().toString();
        responses.applySecurityHeaders(exchange.getResponseHeaders(), requestId);
        runtime.recordRequest();
        boolean requestSlot = false;
        boolean privilegedSlot = false;
        long privilegedTicket = 0L;
        try {
            if (!authenticationConcurrency.tryAcquire()) {
                runtime.recordThrottledRequest();
                throw new RemoteFailure(
                        429,
                        "AUTHENTICATION_BUSY",
                        "remote authentication concurrency limit reached");
            }
            MorpheusRemoteIdentityFile.Identity identity;
            try {
                identity = authenticate(exchange);
            } finally {
                authenticationConcurrency.release();
            }

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
                serveStatus(exchange);
                return;
            }

            if (usesPrivilegedConcurrency(exchange.getRequestMethod(), path)) {
                if (!privilegedConcurrency.tryAcquire()) {
                    runtime.recordThrottledPrivilegedRequest();
                    throw new RemoteFailure(
                            429,
                            "PRIVILEGED_CONCURRENCY_LIMIT",
                            "remote write/admin concurrency limit reached");
                }
                privilegedSlot = true;
                privilegedTicket = runtime.privilegedRequestStarted();
            }

            if (!concurrency.tryAcquire()) {
                if (privilegedSlot) {
                    runtime.recordThrottledPrivilegedRequest();
                } else {
                    runtime.recordThrottledRequest();
                }
                throw new RemoteFailure(429, "TOO_MANY_REQUESTS", "remote request concurrency limit reached");
            }
            requestSlot = true;
            runtime.requestStarted();

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
            responses.sendError(exchange, 400, "BAD_REQUEST", BoundaryFailureMessage.safe(failure));
        } catch (RuntimeException failure) {
            responses.sendError(exchange, 500, "INTERNAL_ERROR", "internal MORPHEUS remote server error");
        } finally {
            if (requestSlot) {
                runtime.requestFinished();
                concurrency.release();
            }
            if (privilegedSlot) {
                runtime.privilegedRequestFinished(privilegedTicket);
                privilegedConcurrency.release();
            }
            exchange.close();
        }
    }

    /**
     * Answers the runtime status outside the request budget it reports on.
     *
     * <p>Status is the one route an operator needs precisely when the facade is saturated, and it used to be
     * admitted through the same semaphore as the traffic it describes: a server at its request ceiling answered
     * 429 to the question "why are you at your ceiling?". It reads process-local counters, touches no database
     * and makes no upstream call, so it does not belong in that budget -- but it is still authenticated,
     * authorized and separately bounded, so an authenticated reader cannot turn it into an unmetered lane.</p>
     */
    private void serveStatus(HttpExchange exchange) throws IOException {
        if (!observabilityConcurrency.tryAcquire()) {
            runtime.recordThrottledRequest();
            throw new RemoteFailure(429, "TOO_MANY_REQUESTS", "remote status concurrency limit reached");
        }
        try {
            responses.sendSuccess(exchange, 200, runtime.status(host(), port()));
        } finally {
            observabilityConcurrency.release();
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
        if (token.isBlank() || token.length() > MAX_PRESENTED_BEARER_CHARS) {
            throw new RemoteFailure(401, "UNAUTHENTICATED", "valid Bearer authentication is required");
        }
        final Optional<MorpheusRemoteIdentityFile.Identity> authenticated;
        try {
            authenticated = identityCache.authenticate(token);
        } catch (IllegalArgumentException failure) {
            throw new RemoteFailure(503, "AUTH_STORE_UNAVAILABLE", "remote authentication store is unavailable");
        }
        return authenticated.orElseThrow(() -> new RemoteFailure(
                401, "UNAUTHENTICATED", "valid Bearer authentication is required"));
    }

    static Optional<MorpheusRemoteIdentityFile.Identity> authenticateCurrent(Path authFile, String token) {
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

    static boolean usesPrivilegedConcurrency(String rawMethod, String path) {
        MorpheusRemoteRole role = MorpheusRemoteRoutePolicy.requiredRole(rawMethod, path);
        String method = Objects.requireNonNull(rawMethod, "rawMethod").toUpperCase(Locale.ROOT);
        return role == MorpheusRemoteRole.WRITE
                || role == MorpheusRemoteRole.ADMIN && !method.equals("GET") && !method.equals("HEAD");
    }

    static int authenticationConcurrencyLimit(int maxConcurrentRequests) {
        if (maxConcurrentRequests < 1) throw new IllegalArgumentException("maxConcurrentRequests must be positive");
        return Math.max(4, Math.min(64, maxConcurrentRequests));
    }

    static int privilegedConcurrencyLimit(int maxConcurrentRequests) {
        if (maxConcurrentRequests < 1) throw new IllegalArgumentException("maxConcurrentRequests must be positive");
        return Math.max(1, (maxConcurrentRequests + 3) / 4);
    }

    /**
     * Capacity of the status lane, which is deliberately independent of {@code maxConcurrentRequests}.
     *
     * <p>It does not scale with configured request capacity because it is not request work: an operator polling
     * a saturated server needs a handful of concurrent status reads, not a proportional share of a budget that
     * is by then fully committed.</p>
     */
    static int observabilityConcurrencyLimit(int maxConcurrentRequests) {
        if (maxConcurrentRequests < 1) throw new IllegalArgumentException("maxConcurrentRequests must be positive");
        return 8;
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

    /**
     * Remote projection of a backup verification.
     *
     * <p>The backup directory is server-configured and restore is offline-only, so a remote ADMIN never needs the
     * absolute pathname; sending it would only describe where the server keeps its data. The file name identifies
     * the backup, and the SHA-256 identifies its content. The local CLI keeps the full pathname because an operator
     * passes it straight back to {@code server backup verify --file}.</p>
     */
    private Map<String, Object> backupView(SqliteServerMaintenance.BackupVerification backup) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("fileName", fileNameOf(backup.path()));
        view.put("bytes", backup.bytes());
        view.put("sha256", backup.sha256());
        view.put("schemaVersion", backup.schemaVersion());
        view.put("integrityOk", backup.integrityOk());
        return Map.copyOf(view);
    }

    /** A filesystem root has no file name; fall back to the SHA-256 rather than dereferencing null. */
    private String fileNameOf(Path path) {
        Path fileName = path.getFileName();
        return fileName == null ? "" : fileName.toString();
    }

    private static String hostForUri(String host) {
        return host.contains(":") && !host.startsWith("[") ? "[" + host + "]" : host;
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
