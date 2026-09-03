package com.morpheus.api;

import com.morpheus.application.context.TechnicalContextProvider;
import com.morpheus.application.history.PublishedHistoryException;
import com.morpheus.application.lifecycle.mutation.ChangeWriteCapabilityResolver;
import com.morpheus.application.operability.ExhaustiveShutdown;
import com.morpheus.application.reference.ExternalIntegrationStatusProvider;
import com.morpheus.application.reference.ExternalReferenceResolverRegistry;
import com.morpheus.application.store.KnowledgeStoreException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;

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
    private final MorpheusHttpRequestDecoder requestDecoder;
    private final MorpheusRootHttpRoutes rootRoutes;
    private final MorpheusProjectRootHttpRoutes projectRootRoutes;
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

    MorpheusHttpServer(
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
        this.requestDecoder = new MorpheusHttpRequestDecoder(
                MAX_REQUEST_BODY_BYTES, REQUEST_BODY_READ_TIMEOUT, this.executor);
        this.rootRoutes = new MorpheusRootHttpRoutes(
                this.service, Objects.requireNonNull(operabilityService, "operabilityService"));
        this.projectRootRoutes = new MorpheusProjectRootHttpRoutes(this.service, this.requestDecoder);
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
        return MorpheusLocalHttpServerBootstrap.start(databasePath, host, port);
    }

    public static MorpheusHttpServer start(
            Path databasePath,
            String host,
            int port,
            ExternalReferenceResolverRegistry resolverRegistry,
            ExternalIntegrationStatusProvider minosStatus) {
        return MorpheusLocalHttpServerBootstrap.start(databasePath, host, port, resolverRegistry, minosStatus);
    }

    public static MorpheusHttpServer start(
            Path databasePath,
            String host,
            int port,
            ExternalReferenceResolverRegistry resolverRegistry,
            ExternalIntegrationStatusProvider minosStatus,
            TechnicalContextProvider technicalContextProvider) {
        return MorpheusLocalHttpServerBootstrap.start(
                databasePath, host, port, resolverRegistry, minosStatus, technicalContextProvider);
    }

    public static MorpheusHttpServer start(
            Path databasePath,
            String host,
            int port,
            ExternalReferenceResolverRegistry resolverRegistry,
            ExternalIntegrationStatusProvider minosStatus,
            TechnicalContextProvider technicalContextProvider,
            ChangeWriteCapabilityResolver writeCapabilityResolver) {
        return MorpheusLocalHttpServerBootstrap.start(
                databasePath,
                host,
                port,
                resolverRegistry,
                minosStatus,
                technicalContextProvider,
                writeCapabilityResolver);
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
        return MorpheusLocalHttpServerBootstrap.startRemote(
                databasePath,
                host,
                port,
                resolverRegistry,
                minosStatus,
                technicalContextProvider,
                writeCapabilityResolver,
                allowedWorkspaceRoots,
                internalCapability);
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
        ExhaustiveShutdown.releaseAll(
                "cannot shut down the MORPHEUS local HTTP server",
                () -> server.stop(0),
                executor::shutdownNow);
    }

    void handle(HttpExchange exchange) throws IOException {
        try {
            MorpheusHttpRouteResponse response = route(exchange);
            send(exchange, response.status(), success(response.data()));
        } catch (ApiFailure failure) {
            if (failure.status() == 405) {
                exchange.getResponseHeaders().set("Allow", allowedMethods.forPath(exchange.getRequestURI().getPath()));
            }
            send(exchange, failure.status(), error(failure.code(), failure.getMessage(), failure.details()));
        } catch (IllegalArgumentException failure) {
            send(exchange, 400, error("BAD_REQUEST", BoundaryFailureMessage.safe(failure), Map.of()));
        } catch (KnowledgeStoreException | PublishedHistoryException | IllegalStateException failure) {
            send(exchange, 409, error("STATE_CONFLICT", BoundaryFailureMessage.safe(failure), Map.of()));
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

        if (rootRoutes.handles(segments)) {
            return rootRoutes.route(method, segments, query);
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

        if (segments.size() <= 2) {
            return projectRootRoutes.route(exchange, method, segments, query);
        }

        String projectId = segments.get(1);
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

    private void send(HttpExchange exchange, int status, Object body) throws IOException {
        responseWriter.send(exchange, status, body);
    }

    private ApiSuccess success(Object data) {
        return new ApiSuccess("v1", Objects.requireNonNull(data, "data"));
    }

    private ApiErrorEnvelope error(String code, String message, Map<String, Object> details) {
        return new ApiErrorEnvelope("v1", new ApiError(code, message, details));
    }

    private List<String> pathSegments(String path) {
        return pathParser.segments(path);
    }

    private static String hostForUri(String host) {
        return host.contains(":") && !host.startsWith("[") ? "[" + host + "]" : host;
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
