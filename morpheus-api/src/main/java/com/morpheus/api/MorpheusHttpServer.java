package com.morpheus.api;

import com.morpheus.application.context.DisabledTechnicalContextProvider;
import com.morpheus.application.context.TechnicalContextProvider;
import com.morpheus.application.history.PublishedHistoryException;
import com.morpheus.application.lifecycle.mutation.ChangeWriteCapabilityObservation;
import com.morpheus.application.lifecycle.mutation.ChangeWriteCapabilityResolver;
import com.morpheus.application.query.PageRequest;
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
    private final MorpheusPortfolioHttpRoutes portfolioRoutes;
    private final MorpheusProviderPluginHttpRoutes providerPluginRoutes;
    private final MorpheusIntegrationStatusHttpRoutes integrationStatusRoutes;
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
        this.portfolioRoutes = new MorpheusPortfolioHttpRoutes(this.portfolioService, this.requestDecoder);
        this.providerPluginRoutes = new MorpheusProviderPluginHttpRoutes(providerPluginProbeEnabled);
        this.integrationStatusRoutes = new MorpheusIntegrationStatusHttpRoutes(
                this.externalReferenceService, this.augmentedContextService);
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
            case "sync" -> routeSync(exchange, method, segments, query, projectId);
            case "sync-status" -> routeSyncStatus(method, segments, query, projectId);
            case "composition" -> routeComposition(method, segments, query, projectId);
            case "specifications" -> routeSpecifications(method, segments, query, projectId);
            case "requirements" -> routeRequirements(exchange, method, segments, query, projectId);
            case "changes" -> routeChanges(exchange, method, segments, query, projectId);
            case "versions" -> routeVersions(method, segments, query, projectId);
            case "diagnostics" -> routeDiagnostics(method, segments, query, projectId);
            case "external-references" -> routeExternalReferences(method, segments, query, projectId);
            default -> throw ApiFailure.notFound("unknown project API resource: " + resource);
        };
    }

    private MorpheusHttpRouteResponse routeSync(HttpExchange exchange, String method, List<String> segments, MorpheusHttpQuery query, String projectId) {
        requireExactSegments(segments, 3);
        requireMethod(method, "POST");
        query.rejectUnknown(Set.of());
        SyncRequest request = readOptionalJson(exchange, SyncRequest.class, new SyncRequest(null));
        return ok(service.sync(projectId, Optional.ofNullable(request.revision())));
    }

    private MorpheusHttpRouteResponse routeSyncStatus(String method, List<String> segments, MorpheusHttpQuery query, String projectId) {
        requireExactSegments(segments, 3);
        requireMethod(method, "GET");
        query.rejectUnknown(Set.of("maxAgeMinutes"));
        long maxAge = query.longValue(
                "maxAgeMinutes", MorpheusApiService.DEFAULT_MAX_AGE_MINUTES, 1, MorpheusApiService.MAX_MAX_AGE_MINUTES);
        return ok(service.syncStatus(projectId, maxAge));
    }

    private MorpheusHttpRouteResponse routeComposition(String method, List<String> segments, MorpheusHttpQuery query, String projectId) {
        requireMethod(method, "GET");
        if (segments.size() == 3) {
            query.rejectUnknown(Set.of());
            return ok(compositionService.status(projectId));
        }
        if (segments.size() == 4 && segments.get(3).equals("conflicts")) {
            query.rejectUnknown(Set.of("offset", "limit"));
            int offset = query.intValue("offset", 0, 0, Integer.MAX_VALUE);
            int limit = query.intValue("limit", MorpheusCompositionApiService.DEFAULT_LIMIT, 1, MorpheusCompositionApiService.MAX_LIMIT);
            return ok(compositionService.conflicts(projectId, offset, limit));
        }
        throw ApiFailure.notFound("unknown composition route");
    }

    private MorpheusHttpRouteResponse routeSpecifications(String method, List<String> segments, MorpheusHttpQuery query, String projectId) {
        requireMethod(method, "GET");
        if (segments.size() == 3) return ok(service.listSpecifications(projectId, page(query)));
        if (segments.size() == 4) {
            query.rejectUnknown(Set.of());
            return ok(service.specification(projectId, segments.get(3)));
        }
        if (segments.size() == 5 && segments.get(4).equals("context")) {
            return ok(service.specificationContext(projectId, segments.get(3), page(query)));
        }
        throw ApiFailure.notFound("unknown specifications route");
    }

    private MorpheusHttpRouteResponse routeRequirements(HttpExchange exchange, String method, List<String> segments, MorpheusHttpQuery query, String projectId) {
        if (segments.size() == 5 && segments.get(4).equals("augmented-context")) {
            requireMethod(method, "POST");
            query.rejectUnknown(Set.of());
            return ok(augmentedContextService.requirement(
                    projectId, segments.get(3), readRequiredJson(exchange, AugmentedContextRequest.class)));
        }
        requireMethod(method, "GET");
        if (segments.size() == 3) {
            query.rejectUnknown(Set.of("query", "offset", "limit"));
            PageRequest page = new PageRequest(
                    query.intValue("offset", 0, 0, Integer.MAX_VALUE),
                    query.intValue("limit", MorpheusApiService.DEFAULT_LIMIT, 1, MorpheusApiService.MAX_LIMIT));
            return ok(service.requirements(projectId, query.string("query").orElse(""), page));
        }
        if (segments.size() == 4) {
            query.rejectUnknown(Set.of());
            return ok(service.requirement(projectId, segments.get(3)));
        }
        if (segments.size() == 5 && segments.get(4).equals("trace")) {
            query.rejectUnknown(Set.of("depth"));
            int depth = query.intValue("depth", MorpheusApiService.DEFAULT_DEPTH, 1, MorpheusApiService.MAX_DEPTH);
            return ok(service.traceRequirement(projectId, segments.get(3), depth));
        }
        throw ApiFailure.notFound("unknown requirements route");
    }

    private MorpheusHttpRouteResponse routeChanges(HttpExchange exchange, String method, List<String> segments, MorpheusHttpQuery query, String projectId) {
        if (segments.size() == 5 && segments.get(4).equals("augmented-context")) {
            requireMethod(method, "POST");
            query.rejectUnknown(Set.of());
            return ok(augmentedContextService.change(
                    projectId, segments.get(3), readRequiredJson(exchange, AugmentedContextRequest.class)));
        }
        if (segments.size() == 5 && segments.get(4).equals("transition-check")) {
            requireMethod(method, "POST");
            query.rejectUnknown(Set.of());
            return ok(jarvisOrchestrationService.transition(
                    projectId, segments.get(3), readRequiredJson(exchange, TransitionCheckRequest.class)));
        }
        if (segments.size() == 5 && segments.get(4).equals("lifecycle-transitions")) {
            requireMethod(method, "POST");
            query.rejectUnknown(Set.of());
            return ok(controlledLifecycleService.apply(
                    projectId, segments.get(3), readRequiredJson(exchange, LifecycleMutationRequest.class)));
        }
        requireMethod(method, "GET");
        if (segments.size() == 3) return ok(service.listChanges(projectId, page(query)));
        if (segments.size() == 4) {
            query.rejectUnknown(Set.of());
            return ok(service.change(projectId, segments.get(3)));
        }
        if (segments.size() != 5) throw ApiFailure.notFound("unknown changes route");

        String changeId = segments.get(3);
        String child = segments.get(4);
        return switch (child) {
            case "constraints" -> ok(service.constraints(projectId, changeId, page(query)));
            case "acceptance-criteria" -> {
                query.rejectUnknown(Set.of());
                yield ok(service.acceptanceCriteria(projectId, changeId));
            }
            case "design-decisions" -> ok(service.designDecisions(projectId, changeId, page(query)));
            case "implementation-tasks" -> ok(service.implementationTasks(projectId, changeId, page(query)));
            case "context" -> {
                query.rejectUnknown(Set.of("depth"));
                int depth = query.intValue("depth", MorpheusApiService.DEFAULT_DEPTH, 1, MorpheusApiService.MAX_DEPTH);
                yield ok(service.changeContext(projectId, changeId, depth));
            }
            case "status" -> {
                query.rejectUnknown(Set.of());
                yield ok(service.changeStatus(projectId, changeId));
            }
            case "blocking-conditions" -> {
                query.rejectUnknown(Set.of());
                yield ok(service.blockingConditions(projectId, changeId));
            }
            case "orchestration" -> {
                query.rejectUnknown(Set.of("lifecycleState", "abandonmentReason"));
                yield ok(jarvisOrchestrationService.state(
                        projectId,
                        changeId,
                        query.string("lifecycleState").map(String::trim).filter(value -> !value.isEmpty()),
                        query.string("abandonmentReason").map(String::trim).filter(value -> !value.isEmpty())));
            }
            default -> throw ApiFailure.notFound("unknown change subresource: " + child);
        };
    }

    private MorpheusHttpRouteResponse routeVersions(String method, List<String> segments, MorpheusHttpQuery query, String projectId) {
        requireMethod(method, "GET");
        if (segments.size() == 3) {
            query.rejectUnknown(Set.of());
            return ok(service.versions(projectId));
        }
        if (segments.size() == 4 && segments.get(3).equals("compare")) {
            query.rejectUnknown(Set.of("fromSnapshotId", "toSnapshotId"));
            return ok(service.compareVersions(projectId, query.required("fromSnapshotId"), query.required("toSnapshotId")));
        }
        if (segments.size() == 5 && segments.get(4).equals("requirements")) {
            return ok(service.historicalRequirements(projectId, segments.get(3), page(query)));
        }
        throw ApiFailure.notFound("unknown versions route");
    }

    private MorpheusHttpRouteResponse routeDiagnostics(String method, List<String> segments, MorpheusHttpQuery query, String projectId) {
        requireExactSegments(segments, 3);
        requireMethod(method, "GET");
        query.rejectUnknown(Set.of());
        return ok(service.diagnostics(projectId));
    }

    private MorpheusHttpRouteResponse routeExternalReferences(String method, List<String> segments, MorpheusHttpQuery query, String projectId) {
        requireMethod(method, "GET");
        if (segments.size() == 3) {
            query.rejectUnknown(Set.of("ownerId"));
            return ok(externalReferenceService.list(projectId, query.required("ownerId")));
        }
        if (segments.size() == 5 && segments.get(4).equals("resolution")) {
            query.rejectUnknown(Set.of());
            return ok(externalReferenceService.resolve(projectId, segments.get(3)));
        }
        throw ApiFailure.notFound("unknown external-references route");
    }

    private PageRequest page(MorpheusHttpQuery query) {
        query.rejectUnknown(Set.of("offset", "limit"));
        return new PageRequest(
                query.intValue("offset", 0, 0, Integer.MAX_VALUE),
                query.intValue("limit", MorpheusApiService.DEFAULT_LIMIT, 1, MorpheusApiService.MAX_LIMIT));
    }

    private <T> T readRequiredJson(HttpExchange exchange, Class<T> type) {
        return requestDecoder.readRequiredJson(exchange, type);
    }

    private <T> T readOptionalJson(HttpExchange exchange, Class<T> type, T defaultValue) {
        return requestDecoder.readOptionalJson(exchange, type, defaultValue);
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

    private void requireExactSegments(List<String> segments, int expected) {
        MorpheusHttpRouteGuards.requireExactSegments(segments, expected);
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
            if (workspace == null || workspace.isBlank()) throw new IllegalArgumentException("workspace is required");
            workspace = workspace.trim();
        }
    }

    public record SyncRequest(String revision) {
        public SyncRequest {
            revision = revision == null ? null : revision.trim();
            if (revision != null && revision.isEmpty()) revision = null;
        }
    }
}
