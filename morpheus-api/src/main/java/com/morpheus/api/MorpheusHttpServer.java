package com.morpheus.api;

import com.morpheus.application.context.DisabledTechnicalContextProvider;
import com.morpheus.application.context.TechnicalContextProvider;
import com.morpheus.application.history.PublishedHistoryException;
import com.morpheus.application.lifecycle.mutation.ChangeWriteCapabilityObservation;
import com.morpheus.application.lifecycle.mutation.ChangeWriteCapabilityResolver;
import com.morpheus.application.reference.ExternalIntegrationStatus;
import com.morpheus.application.reference.ExternalIntegrationStatusProvider;
import com.morpheus.application.reference.ExternalReferenceResolverRegistry;
import com.morpheus.application.snapshot.RuntimeSnapshotRecovery;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.store.sqlite.SqliteSpecificationKnowledgeStore;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Local HTTP server. Routing and HTTP translation only; business behavior stays in application services. */
public final class MorpheusHttpServer implements AutoCloseable {
    public static final String API_PREFIX = "/api/v1";
    public static final String DEFAULT_HOST = "127.0.0.1";
    public static final int DEFAULT_PORT = 8765;
    public static final int MAX_REQUEST_BODY_BYTES = 65_536;
    static final Duration REQUEST_BODY_READ_TIMEOUT = Duration.ofSeconds(15);

    private final HttpServer server;
    private final ExecutorService executor;
    private final MorpheusApiService service;
    private final MorpheusExternalReferenceApiService externalReferenceService;
    private final MorpheusAugmentedContextApiService augmentedContextService;
    private final MorpheusJarvisOrchestrationApiService jarvisOrchestrationService;
    private final MorpheusControlledLifecycleApiService controlledLifecycleService;
    private final MorpheusCompositionApiService compositionService;
    private final MorpheusPortfolioApiService portfolioService;
    private final MorpheusOperabilityApiService operabilityService;
    private final MorpheusHttpRequestDecoder requestDecoder;
    private final MorpheusProjectSyncHttpRoutes projectSyncRoutes;
    private final MorpheusPortfolioHttpRoutes portfolioRoutes;
    private final MorpheusProviderPluginHttpRoutes providerPluginRoutes;
    private final MorpheusIntegrationStatusHttpRoutes integrationStatusRoutes;
    private final MorpheusCompositionHttpRoutes compositionRoutes;
    private final MorpheusSpecificationsHttpRoutes specificationsRoutes;
    private final MorpheusDiagnosticsHttpRoutes diagnosticsRoutes;
    private final MorpheusExternalReferenceHttpRoutes externalReferenceRoutes;
    private final MorpheusVersionsHttpRoutes versionsRoutes;
    private final MorpheusRequirementsHttpRoutes requirementsRoutes;
    private final MorpheusChangesHttpRoutes changesRoutes;
    private final MorpheusHttpResponseWriter responseWriter = new MorpheusHttpResponseWriter();
    private final MorpheusHttpPathParser pathParser = new MorpheusHttpPathParser(API_PREFIX);
    private final MorpheusHttpAllowedMethods allowedMethods = new MorpheusHttpAllowedMethods(pathParser);

    private MorpheusHttpServer(
            HttpServer server,
            ExecutorService executor,
            MorpheusApiService service,
            MorpheusExternalReferenceApiService externalReferenceService,
            MorpheusAugmentedContextApiService augmentedContextService,
            MorpheusJarvisOrchestrationApiService jarvisOrchestrationService,
            MorpheusControlledLifecycleApiService controlledLifecycleService,
            MorpheusCompositionApiService compositionService,
            MorpheusPortfolioApiService portfolioService,
            MorpheusOperabilityApiService operabilityService,
            boolean providerPluginProbeEnabled) {
        this.server = Objects.requireNonNull(server, "server");
        this.executor = Objects.requireNonNull(executor, "executor");
        this.service = Objects.requireNonNull(service, "service");
        this.externalReferenceService = Objects.requireNonNull(externalReferenceService, "externalReferenceService");
        this.augmentedContextService = Objects.requireNonNull(augmentedContextService, "augmentedContextService");
        this.jarvisOrchestrationService = Objects.requireNonNull(jarvisOrchestrationService, "jarvisOrchestrationService");
        this.controlledLifecycleService = Objects.requireNonNull(controlledLifecycleService, "controlledLifecycleService");
        this.compositionService = Objects.requireNonNull(compositionService, "compositionService");
        this.portfolioService = Objects.requireNonNull(portfolioService, "portfolioService");
        this.operabilityService = Objects.requireNonNull(operabilityService, "operabilityService");
        this.requestDecoder = new MorpheusHttpRequestDecoder(
                MAX_REQUEST_BODY_BYTES, REQUEST_BODY_READ_TIMEOUT, this.executor);
        this.projectSyncRoutes = new MorpheusProjectSyncHttpRoutes(this.service, this.requestDecoder);
        this.portfolioRoutes = new MorpheusPortfolioHttpRoutes(this.portfolioService, this.requestDecoder);
        this.providerPluginRoutes = new MorpheusProviderPluginHttpRoutes(providerPluginProbeEnabled);
        this.integrationStatusRoutes = new MorpheusIntegrationStatusHttpRoutes(
                this.externalReferenceService, this.augmentedContextService);
        this.compositionRoutes = new MorpheusCompositionHttpRoutes(this.compositionService);
        this.specificationsRoutes = new MorpheusSpecificationsHttpRoutes(this.service);
        this.diagnosticsRoutes = new MorpheusDiagnosticsHttpRoutes(this.service);
        this.externalReferenceRoutes = new MorpheusExternalReferenceHttpRoutes(this.externalReferenceService);
        this.versionsRoutes = new MorpheusVersionsHttpRoutes(this.service);
        this.requirementsRoutes = new MorpheusRequirementsHttpRoutes(
                this.service, this.augmentedContextService, this.requestDecoder);
        this.changesRoutes = new MorpheusChangesHttpRoutes(
                this.service,
                this.augmentedContextService,
                this.jarvisOrchestrationService,
                this.controlledLifecycleService,
                this.requestDecoder);
    }

    public static MorpheusHttpServer start(Path databasePath, String host, int port) {
        ExternalReferenceResolverRegistry resolvers = new ExternalReferenceResolverRegistry(List.of());
        ExternalIntegrationStatusProvider disabledMinos = () -> new ExternalIntegrationStatus(
                "MINOS", "DISABLED", false, "MINOS integration is not configured", Map.of());
        return start(databasePath, host, port, resolvers, disabledMinos, disabledNexus(), deniedWrites());
    }

    public static MorpheusHttpServer start(
            Path databasePath,
            String host,
            int port,
            ExternalReferenceResolverRegistry resolverRegistry,
            ExternalIntegrationStatusProvider minosStatus) {
        return start(databasePath, host, port, resolverRegistry, minosStatus, disabledNexus(), deniedWrites());
    }

    public static MorpheusHttpServer start(
            Path databasePath,
            String host,
            int port,
            ExternalReferenceResolverRegistry resolverRegistry,
            ExternalIntegrationStatusProvider minosStatus,
            TechnicalContextProvider technicalContextProvider) {
        return start(databasePath, host, port, resolverRegistry, minosStatus, technicalContextProvider, deniedWrites());
    }

    public static MorpheusHttpServer start(
            Path databasePath,
            String host,
            int port,
            ExternalReferenceResolverRegistry resolverRegistry,
            ExternalIntegrationStatusProvider minosStatus,
            TechnicalContextProvider technicalContextProvider,
            ChangeWriteCapabilityResolver writeCapabilityResolver) {
        return startConfigured(
                databasePath,
                host,
                port,
                resolverRegistry,
                minosStatus,
                technicalContextProvider,
                writeCapabilityResolver,
                Optional.empty(),
                Optional.empty());
    }

    static MorpheusHttpServer startRemote(
            Path databasePath,
            String host,
            int port,
            ExternalReferenceResolverRegistry resolverRegistry,
            ExternalIntegrationStatusProvider minosStatus,
            TechnicalContextProvider technicalContextProvider,
            ChangeWriteCapabilityResolver writeCapabilityResolver,
            AllowedWorkspaceRoots allowedWorkspaceRoots,
            MorpheusInternalCapability internalCapability) {
        return startConfigured(
                databasePath,
                host,
                port,
                resolverRegistry,
                minosStatus,
                technicalContextProvider,
                writeCapabilityResolver,
                Optional.of(Objects.requireNonNull(allowedWorkspaceRoots, "allowedWorkspaceRoots")),
                Optional.of(Objects.requireNonNull(internalCapability, "internalCapability")));
    }

    private static MorpheusHttpServer startConfigured(
            Path databasePath,
            String host,
            int port,
            ExternalReferenceResolverRegistry resolverRegistry,
            ExternalIntegrationStatusProvider minosStatus,
            TechnicalContextProvider technicalContextProvider,
            ChangeWriteCapabilityResolver writeCapabilityResolver,
            Optional<AllowedWorkspaceRoots> allowedWorkspaceRoots,
            Optional<MorpheusInternalCapability> internalCapability) {
        Objects.requireNonNull(databasePath, "databasePath");
        Objects.requireNonNull(resolverRegistry, "resolverRegistry");
        Objects.requireNonNull(minosStatus, "minosStatus");
        Objects.requireNonNull(technicalContextProvider, "technicalContextProvider");
        Objects.requireNonNull(writeCapabilityResolver, "writeCapabilityResolver");
        Objects.requireNonNull(allowedWorkspaceRoots, "allowedWorkspaceRoots");
        Objects.requireNonNull(internalCapability, "internalCapability");
        if (port < 0 || port > 65_535) {
            throw new IllegalArgumentException("port must be between 0 and 65535");
        }
        var bindAddress = LoopbackHostPolicy.requireLoopbackAddress(host);
        String normalizedHost = bindAddress.getHostAddress();
        try (SqliteSpecificationKnowledgeStore store = new SqliteSpecificationKnowledgeStore(databasePath)) {
            new RuntimeSnapshotRecovery(store).recoverAll(Instant.now());
        }
        try {
            HttpServer delegate = HttpServer.create(new InetSocketAddress(bindAddress, port), 0);
            HttpServer httpServer = internalCapability
                    .<HttpServer>map(capability -> new CapabilityProtectedHttpServer(delegate, capability))
                    .orElse(delegate);
            ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
            MorpheusHttpServer result = new MorpheusHttpServer(
                    httpServer,
                    executor,
                    allowedWorkspaceRoots
                            .map(policy -> new MorpheusApiService(databasePath, policy))
                            .orElseGet(() -> new MorpheusApiService(databasePath)),
                    new MorpheusExternalReferenceApiService(databasePath, resolverRegistry, minosStatus),
                    new MorpheusAugmentedContextApiService(databasePath, technicalContextProvider),
                    new MorpheusJarvisOrchestrationApiService(databasePath),
                    new MorpheusControlledLifecycleApiService(databasePath, writeCapabilityResolver),
                    new MorpheusCompositionApiService(databasePath),
                    new MorpheusPortfolioApiService(databasePath),
                    new MorpheusOperabilityApiService(databasePath),
                    allowedWorkspaceRoots.isPresent());
            httpServer.setExecutor(executor);
            httpServer.createContext(API_PREFIX, result::handle);
            MorpheusQueryHttpRoutes.register(httpServer, databasePath);
            httpServer.start();
            return result;
        } catch (IOException failure) {
            throw new IllegalStateException("cannot start MORPHEUS API on " + normalizedHost + ":" + port, failure);
        }
    }

    public String host() {
        return server.getAddress().getAddress().getHostAddress();
    }

    public int port() {
        return server.getAddress().getPort();
    }

    public URI baseUri() {
        return URI.create("http://" + hostForUri(host()) + ":" + port() + API_PREFIX);
    }

    @Override
    public void close() {
        server.stop(0);
        executor.shutdownNow();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try {
            MorpheusHttpRouteResponse response = route(exchange);
            send(exchange, response.status(), success(response.data()));
        } catch (ApiFailure failure) {
            if (failure.status() == 405) {
                exchange.getResponseHeaders().set("Allow", allowedMethods.forPath(exchange.getRequestURI().getPath()));
            }
            send(exchange, failure.status(), error(failure.code(), failure.getMessage(), failure.details()));
        } catch (IllegalArgumentException failure) {
            send(exchange, 400, error("BAD_REQUEST", safeMessage(failure), Map.of()));
        } catch (KnowledgeStoreException | PublishedHistoryException | IllegalStateException failure) {
            send(exchange, 409, error("STATE_CONFLICT", safeMessage(failure), Map.of()));
        } catch (RuntimeException failure) {
            send(exchange, 500, error("INTERNAL_ERROR", "internal MORPHEUS API error", Map.of()));
        } finally {
            exchange.close();
        }
    }

    private MorpheusHttpRouteResponse route(HttpExchange exchange) {
        String method = exchange.getRequestMethod().toUpperCase(Locale.ROOT);
        List<String> segments = pathSegments(exchange.getRequestURI().getPath());
        MorpheusHttpQuery query = MorpheusHttpQuery.parse(exchange.getRequestURI().getRawQuery());

        if (segments.isEmpty()) {
            requireMethod(method, "GET");
            query.rejectUnknown(Set.of());
            return ok(Map.of("service", "morpheus", "apiVersion", "v1"));
        }
        if (segments.size() == 1 && segments.getFirst().equals("health")) {
            requireMethod(method, "GET");
            query.rejectUnknown(Set.of());
            return ok(service.health());
        }
        if (segments.size() == 1 && segments.getFirst().equals("readiness")) {
            requireMethod(method, "GET");
            query.rejectUnknown(Set.of());
            MorpheusOperabilityApiService.ReadinessView readiness = operabilityService.readiness();
            return new MorpheusHttpRouteResponse("READY".equals(readiness.status()) ? 200 : 503, readiness);
        }
        if (segments.size() == 1 && segments.getFirst().equals("metrics")) {
            requireMethod(method, "GET");
            query.rejectUnknown(Set.of());
            return ok(operabilityService.metrics());
        }
        if (segments.size() == 1 && segments.getFirst().equals("version")) {
            requireMethod(method, "GET");
            query.rejectUnknown(Set.of());
            return ok(service.version());
        }
        if (segments.size() == 2 && segments.getFirst().equals("provider-plugins")) {
            return providerPluginRoutes.route(method, segments, query);
        }
        if (segments.getFirst().equals("portfolios")) {
            return portfolioRoutes.route(exchange, method, segments, query);
        }
        if (segments.size() == 3
                && segments.getFirst().equals("integrations")
                && segments.get(2).equals("status")) {
            return integrationStatusRoutes.route(method, segments, query);
        }
        if (!segments.getFirst().equals("projects")) {
            throw ApiFailure.notFound("unknown API route: " + exchange.getRequestURI().getPath());
        }

        if (segments.size() == 1) {
            query.rejectUnknown(Set.of());
            if (method.equals("GET")) {
                return ok(service.listProjects());
            }
            if (method.equals("POST")) {
                ProjectRegistrationRequest request = readRequiredJson(exchange, ProjectRegistrationRequest.class);
                MorpheusApiService.RegistrationResult result = service.registerProject(request.workspace());
                return new MorpheusHttpRouteResponse(result.created() ? 201 : 200, result.project());
            }
            throw ApiFailure.methodNotAllowed("projects supports GET and POST");
        }

        String projectId = segments.get(1);
        if (segments.size() == 2) {
            requireMethod(method, "GET");
            query.rejectUnknown(Set.of());
            return ok(service.project(projectId));
        }

        String resource = segments.get(2);
        return switch (resource) {
            case "sync" -> projectSyncRoutes.routeSync(exchange, method, segments, query, projectId);
            case "sync-status" -> projectSyncRoutes.routeSyncStatus(method, segments, query, projectId);
            case "composition" -> compositionRoutes.route(method, segments, query, projectId);
            case "specifications" -> specificationsRoutes.route(method, segments, query, projectId);
            case "requirements" -> requirementsRoutes.route(exchange, method, segments, query, projectId);
            case "changes" -> changesRoutes.route(exchange, method, segments, query, projectId);
            case "versions" -> versionsRoutes.route(method, segments, query, projectId);
            case "diagnostics" -> diagnosticsRoutes.route(method, segments, query, projectId);
            case "external-references" -> externalReferenceRoutes.route(method, segments, query, projectId);
            default -> throw ApiFailure.notFound("unknown project API resource: " + resource);
        };
    }

    private <T> T readRequiredJson(HttpExchange exchange, Class<T> type) {
        return requestDecoder.readRequiredJson(exchange, type);
    }

    private void send(HttpExchange exchange, int status, Object body) throws IOException {
        responseWriter.send(exchange, status, body);
    }

    private ApiSuccess success(Object data) {
        return new ApiSuccess("v1", Objects.requireNonNull(data, "data"));
    }

    private ApiErrorEnvelope error(String code, String message, Map<String, Object> details) {
        return new ApiErrorEnvelope("v1", new ApiError(code, message, details));
    }

    private MorpheusHttpRouteResponse ok(Object data) {
        return new MorpheusHttpRouteResponse(200, data);
    }

    private void requireMethod(String actual, String expected) {
        MorpheusHttpRouteGuards.requireMethod(actual, expected);
    }

    private List<String> pathSegments(String path) {
        return pathParser.segments(path);
    }

    private static TechnicalContextProvider disabledNexus() {
        return new DisabledTechnicalContextProvider("NEXUS", "NEXUS integration is not configured");
    }

    private static ChangeWriteCapabilityResolver deniedWrites() {
        return projectId -> ChangeWriteCapabilityObservation.denied(
                "No WRITE_CHANGE provider capability resolver is configured for this HTTP server");
    }

    private static String hostForUri(String host) {
        return host.contains(":") && !host.startsWith("[") ? "[" + host + "]" : host;
    }

    private static String safeMessage(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    public record ApiSuccess(String apiVersion, Object data) {
        public ApiSuccess {
            Objects.requireNonNull(apiVersion, "apiVersion");
            Objects.requireNonNull(data, "data");
        }
    }

    public record ApiError(String code, String message, Map<String, Object> details) {
        public ApiError {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(message, "message");
            details = Map.copyOf(Objects.requireNonNull(details, "details"));
        }
    }

    public record ApiErrorEnvelope(String apiVersion, ApiError error) {
        public ApiErrorEnvelope {
            Objects.requireNonNull(apiVersion, "apiVersion");
            Objects.requireNonNull(error, "error");
        }
    }

    public record ProjectRegistrationRequest(String workspace) {
        public ProjectRegistrationRequest {
            if (workspace == null || workspace.isBlank()) {
                throw new IllegalArgumentException("workspace is required");
            }
            workspace = workspace.trim();
        }
    }

    public record SyncRequest(String revision) {
        public SyncRequest {
            revision = revision == null ? null : revision.trim();
            if (revision != null && revision.isEmpty()) {
                revision = null;
            }
        }
    }
}
