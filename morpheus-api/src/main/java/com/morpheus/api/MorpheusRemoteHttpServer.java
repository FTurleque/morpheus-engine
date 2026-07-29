package com.morpheus.api;

import com.morpheus.application.context.TechnicalContextProvider;
import com.morpheus.application.lifecycle.mutation.ChangeWriteCapabilityResolver;
import com.morpheus.application.query.compact.CanonicalJsonSerializer;
import com.morpheus.application.reference.ExternalIntegrationStatusProvider;
import com.morpheus.application.reference.ExternalReferenceResolverRegistry;
import com.morpheus.store.sqlite.SqliteServerMaintenance;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpsConfigurator;
import com.sun.net.httpserver.HttpsParameters;
import com.sun.net.httpserver.HttpsServer;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * M26 opt-in HTTPS facade for team/remote use.
 *
 * <p>The existing local API remains an internal loopback server. Authentication headers are consumed by this
 * facade and are never forwarded to that internal HTTP hop.</p>
 */
public final class MorpheusRemoteHttpServer implements AutoCloseable {
    public static final int DEFAULT_MAX_CONCURRENT_REQUESTS = 64;
    public static final int MAX_CONCURRENT_REQUESTS = 512;
    private static final int MAX_REQUEST_BYTES = MorpheusHttpServer.MAX_REQUEST_BODY_BYTES;

    private final HttpsServer server;
    private final ExecutorService executor;
    private final MorpheusHttpServer localServer;
    private final SqliteServerMaintenance.ServerLease lease;
    private final SqliteServerMaintenance maintenance;
    private final Path databasePath;
    private final Path backupDirectory;
    private final List<MorpheusRemoteIdentityFile.Identity> identities;
    private final Semaphore concurrency;
    private final RuntimeState runtime;
    private final HttpClient proxyClient;
    private final CanonicalJsonSerializer serializer = new CanonicalJsonSerializer();

    private MorpheusRemoteHttpServer(
            HttpsServer server,
            ExecutorService executor,
            MorpheusHttpServer localServer,
            SqliteServerMaintenance.ServerLease lease,
            SqliteServerMaintenance maintenance,
            Path databasePath,
            Path backupDirectory,
            List<MorpheusRemoteIdentityFile.Identity> identities,
            int maxConcurrentRequests) {
        this.server = server;
        this.executor = executor;
        this.localServer = localServer;
        this.lease = lease;
        this.maintenance = maintenance;
        this.databasePath = databasePath.toAbsolutePath().normalize();
        this.backupDirectory = backupDirectory.toAbsolutePath().normalize();
        this.identities = identities;
        this.concurrency = new Semaphore(maxConcurrentRequests, true);
        this.runtime = new RuntimeState(maxConcurrentRequests);
        this.proxyClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    public static MorpheusRemoteHttpServer start(
            Path databasePath,
            Path backupDirectory,
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

        List<MorpheusRemoteIdentityFile.Identity> identities = MorpheusRemoteIdentityFile.load(authFile);
        if (identities.isEmpty()) throw new IllegalArgumentException("remote auth file contains no identities");
        if (identities.stream().noneMatch(identity -> identity.role() == MorpheusRemoteRole.ADMIN)) {
            throw new IllegalArgumentException("remote auth file must contain at least one ADMIN identity");
        }

        SSLContext sslContext = buildSslContext(keyStorePath, keyStorePassword.clone());
        SqliteServerMaintenance maintenance = new SqliteServerMaintenance();
        SqliteServerMaintenance.ServerLease lease = maintenance.acquireServerLease(databasePath);
        MorpheusHttpServer local = null;
        ExecutorService executor = null;
        try {
            local = MorpheusHttpServer.start(
                    databasePath,
                    MorpheusHttpServer.DEFAULT_HOST,
                    0,
                    resolverRegistry,
                    minosStatus,
                    technicalContextProvider,
                    writeCapabilityResolver);
            HttpsServer https = HttpsServer.create(new InetSocketAddress(normalizedHost, port), maxConcurrentRequests);
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
                    https, executor, local, lease, maintenance, databasePath, backupDirectory,
                    identities, maxConcurrentRequests);
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
        applySecurityHeaders(exchange.getResponseHeaders(), requestId);
        runtime.totalRequests.increment();
        if (!concurrency.tryAcquire()) {
            runtime.throttledRequests.increment();
            sendJson(exchange, 429, error("TOO_MANY_REQUESTS", "remote request concurrency limit reached"));
            return;
        }
        runtime.activeRequests.incrementAndGet();
        try {
            MorpheusRemoteIdentityFile.Identity identity = authenticate(exchange);
            MorpheusRemoteRole required = requiredRole(exchange.getRequestMethod(), exchange.getRequestURI().getPath());
            if (!identity.role().allows(required)) {
                runtime.authorizationFailures.increment();
                sendJson(exchange, 403, error("FORBIDDEN", "authenticated principal is not authorized for this operation"));
                return;
            }

            String path = exchange.getRequestURI().getPath();
            if (path.equals(MorpheusHttpServer.API_PREFIX + "/server/status")) {
                requireMethod(exchange, "GET");
                sendJson(exchange, 200, success(runtime.status(host(), port())));
                return;
            }
            if (path.equals(MorpheusHttpServer.API_PREFIX + "/server/backups")) {
                requireMethod(exchange, "POST");
                requireEmptyBody(exchange);
                SqliteServerMaintenance.BackupVerification backup = maintenance.createBackup(databasePath, backupDirectory);
                sendJson(exchange, 201, success(backup));
                return;
            }
            proxy(exchange);
        } catch (RemoteFailure failure) {
            if (failure.status == 401) runtime.authenticationFailures.increment();
            if (failure.status == 403) runtime.authorizationFailures.increment();
            if (failure.status == 401) {
                exchange.getResponseHeaders().set("WWW-Authenticate", "Bearer realm=\"morpheus\"");
            }
            sendJson(exchange, failure.status, error(failure.code, failure.getMessage()));
        } catch (IllegalArgumentException failure) {
            sendJson(exchange, 400, error("BAD_REQUEST", safeMessage(failure)));
        } catch (RuntimeException failure) {
            sendJson(exchange, 500, error("INTERNAL_ERROR", "internal MORPHEUS remote server error"));
        } finally {
            runtime.activeRequests.decrementAndGet();
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
        return MorpheusRemoteIdentityFile.authenticate(identities, token)
                .orElseThrow(() -> new RemoteFailure(
                        401, "UNAUTHENTICATED", "valid Bearer authentication is required"));
    }

    private MorpheusRemoteRole requiredRole(String rawMethod, String path) {
        String method = rawMethod.toUpperCase(Locale.ROOT);
        String prefix = MorpheusHttpServer.API_PREFIX;
        if (!path.startsWith(prefix)) {
            throw new RemoteFailure(404, "NOT_FOUND", "unknown remote API path");
        }
        if (path.equals(prefix + "/metrics")) return MorpheusRemoteRole.ADMIN;
        if (path.equals(prefix + "/server/backups")) return MorpheusRemoteRole.ADMIN;
        if (path.equals(prefix + "/server/status")) return MorpheusRemoteRole.READ;
        if (method.equals("GET") || method.equals("HEAD")) return MorpheusRemoteRole.READ;
        if (method.equals("POST") && isReadOnlyPost(path)) return MorpheusRemoteRole.READ;
        if (method.equals("POST") || method.equals("PUT") || method.equals("PATCH") || method.equals("DELETE")) {
            return MorpheusRemoteRole.WRITE;
        }
        throw new RemoteFailure(405, "METHOD_NOT_ALLOWED", "unsupported remote HTTP method");
    }

    private boolean isReadOnlyPost(String path) {
        String prefix = MorpheusHttpServer.API_PREFIX;
        return path.equals(prefix + "/queries/execute")
                || path.equals(prefix + "/exports")
                || path.equals(prefix + "/policies/evaluate")
                || path.equals(prefix + "/policies/dry-run")
                || path.endsWith("/transition-check")
                || path.endsWith("/augmented-context")
                || path.endsWith("/execute") && path.contains("/saved-views/")
                || path.endsWith("/export") && path.contains("/saved-views/")
                || path.endsWith("/resolve") && path.contains("/external-references/");
    }

    private void proxy(HttpExchange exchange) throws IOException {
        byte[] requestBody = readBoundedBody(exchange);
        URI target = localTarget(exchange.getRequestURI());
        HttpRequest.Builder request = HttpRequest.newBuilder(target)
                .timeout(Duration.ofSeconds(60));
        String contentType = exchange.getRequestHeaders().getFirst("Content-Type");
        if (contentType != null) request.header("Content-Type", contentType);
        String accept = exchange.getRequestHeaders().getFirst("Accept");
        if (accept != null) request.header("Accept", accept);
        request.method(exchange.getRequestMethod(), requestBody.length == 0
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofByteArray(requestBody));
        try {
            HttpResponse<byte[]> response = proxyClient.send(request.build(), HttpResponse.BodyHandlers.ofByteArray());
            String responseType = response.headers().firstValue("Content-Type").orElse("application/json; charset=utf-8");
            exchange.getResponseHeaders().set("Content-Type", responseType);
            response.headers().firstValue("Allow").ifPresent(value -> exchange.getResponseHeaders().set("Allow", value));
            sendRaw(exchange, response.statusCode(), response.body());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new RemoteFailure(503, "UPSTREAM_INTERRUPTED", "local MORPHEUS API proxy was interrupted");
        }
    }

    private URI localTarget(URI requestUri) {
        String suffix = requestUri.getRawPath();
        if (requestUri.getRawQuery() != null) suffix += "?" + requestUri.getRawQuery();
        return URI.create("http://127.0.0.1:" + localServer.port() + suffix);
    }

    private byte[] readBoundedBody(HttpExchange exchange) throws IOException {
        byte[] body = exchange.getRequestBody().readNBytes(MAX_REQUEST_BYTES + 1);
        if (body.length > MAX_REQUEST_BYTES) {
            throw new RemoteFailure(413, "PAYLOAD_TOO_LARGE", "request body exceeds " + MAX_REQUEST_BYTES + " bytes");
        }
        return body;
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

    private Map<String, Object> success(Object data) {
        return Map.of("apiVersion", "v1", "data", data);
    }

    private Map<String, Object> error(String code, String message) {
        return Map.of(
                "apiVersion", "v1",
                "error", Map.of("code", code, "message", message, "details", Map.of()));
    }

    private void sendJson(HttpExchange exchange, int status, Object payload) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        sendRaw(exchange, status, serializer.toUtf8(payload));
    }

    private void sendRaw(HttpExchange exchange, int status, byte[] body) throws IOException {
        exchange.sendResponseHeaders(status, body.length);
        exchange.getResponseBody().write(body);
    }

    private static void applySecurityHeaders(Headers headers, String requestId) {
        headers.set("Cache-Control", "no-store");
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("X-Frame-Options", "DENY");
        headers.set("Referrer-Policy", "no-referrer");
        headers.set("Content-Security-Policy", "default-src 'none'; frame-ancestors 'none'");
        headers.set("X-Request-Id", requestId);
    }

    private static SSLContext buildSslContext(Path keyStorePath, char[] password) {
        Objects.requireNonNull(keyStorePath, "keyStorePath");
        Path path = keyStorePath.toAbsolutePath().normalize();
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
            throw new IllegalArgumentException("remote TLS keystore must be a regular non-symbolic PKCS12 file");
        }
        try {
            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            try (InputStream input = Files.newInputStream(path)) {
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

    private static final class RuntimeState {
        private final Instant startedAt = Instant.now();
        private final int maxConcurrentRequests;
        private final AtomicInteger activeRequests = new AtomicInteger();
        private final LongAdder totalRequests = new LongAdder();
        private final LongAdder authenticationFailures = new LongAdder();
        private final LongAdder authorizationFailures = new LongAdder();
        private final LongAdder throttledRequests = new LongAdder();

        private RuntimeState(int maxConcurrentRequests) {
            this.maxConcurrentRequests = maxConcurrentRequests;
        }

        private Map<String, Object> status(String host, int port) {
            long uptimeSeconds = Math.max(0, Duration.between(startedAt, Instant.now()).toSeconds());
            return Map.of(
                    "mode", "REMOTE",
                    "transport", "HTTPS",
                    "host", host,
                    "port", port,
                    "startedAt", startedAt.toString(),
                    "uptimeSeconds", uptimeSeconds,
                    "activeRequests", activeRequests.get(),
                    "maxConcurrentRequests", maxConcurrentRequests,
                    "totalRequests", totalRequests.sum(),
                    "authenticationFailures", authenticationFailures.sum(),
                    "authorizationFailures", authorizationFailures.sum(),
                    "throttledRequests", throttledRequests.sum());
        }
    }
}
