package com.morpheus.api;

import com.morpheus.application.context.DisabledTechnicalContextProvider;
import com.morpheus.application.context.TechnicalContextProvider;
import com.morpheus.application.history.PublishedHistoryException;
import com.morpheus.application.lifecycle.mutation.ChangeWriteCapabilityObservation;
import com.morpheus.application.lifecycle.mutation.ChangeWriteCapabilityResolver;
import com.morpheus.application.portfolio.PortfolioQueryService;
import com.morpheus.application.portfolio.PortfolioTraversalService;
import com.morpheus.application.query.PageRequest;
import com.morpheus.application.query.compact.CanonicalJsonSerializer;
import com.morpheus.application.reference.ExternalIntegrationStatus;
import com.morpheus.application.reference.ExternalIntegrationStatusProvider;
import com.morpheus.application.reference.ExternalReferenceResolverRegistry;
import com.morpheus.application.snapshot.RuntimeSnapshotRecovery;
import com.morpheus.application.store.KnowledgeStoreException;
import com.morpheus.store.sqlite.SqliteSpecificationKnowledgeStore;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
    private final boolean providerPluginProbeEnabled;
    private final CanonicalJsonSerializer serializer = new CanonicalJsonSerializer();
    private final MorpheusHttpRequestDecoder requestDecoder;

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
        this.providerPluginProbeEnabled = providerPluginProbeEnabled;
        this.requestDecoder = new MorpheusHttpRequestDecoder(
                MAX_REQUEST_BODY_BYTES, REQUEST_BODY_READ_TIMEOUT, this.executor);
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
            RouteResponse response = route(exchange);
            send(exchange, response.status(), success(response.data()));
        } catch (ApiFailure failure) {
            if (failure.status() == 405) {
                exchange.getResponseHeaders().set("Allow", allowedMethods(exchange.getRequestURI().getPath()));
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

    private RouteResponse route(HttpExchange exchange) {
        String method = exchange.getRequestMethod().toUpperCase(Locale.ROOT);
        List<String> segments = pathSegments(exchange.getRequestURI().getPath());
        Query query = Query.parse(exchange.getRequestURI().getRawQuery());

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
            return new RouteResponse("READY".equals(readiness.status()) ? 200 : 503, readiness);
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
            MorpheusProviderPluginApiService plugins = new MorpheusProviderPluginApiService();
            return switch (segments.get(1)) {
                case "discover" -> {
                    requireMethod(method, "GET");
                    query.rejectUnknown(Set.of("directory"));
                    yield ok(plugins.discover(query.required("directory")));
                }
                case "probe" -> {
                    if (!providerPluginProbeEnabled) {
                        throw ApiFailure.notFound("provider-plugin probe is remote-only");
                    }
                    requireMethod(method, "POST");
                    query.rejectUnknown(Set.of("directory", "pluginId", "workspace", "sha256"));
                    String directory = query.required("directory");
                    String pluginId = query.required("pluginId");
                    String workspace = query.required("workspace");
                    String sha256 = query.required("sha256");
                    yield ok(plugins.probe(directory, pluginId, workspace, sha256));
                }
                default -> throw ApiFailure.notFound("unknown provider-plugin route");
            };
        }
        if (segments.getFirst().equals("portfolios")) {
            return routePortfolios(exchange, method, segments, query);
        }
        if (segments.size() == 3
                && segments.getFirst().equals("integrations")
                && segments.get(2).equals("status")) {
            requireMethod(method, "GET");
            query.rejectUnknown(Set.of());
            return switch (segments.get(1)) {
                case "minos" -> ok(externalReferenceService.minosStatus());
                case "nexus" -> ok(augmentedContextService.nexusStatus());
                default -> throw ApiFailure.notFound("unknown integration: " + segments.get(1));
            };
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
                return new RouteResponse(result.created() ? 201 : 200, result.project());
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

    private RouteResponse routePortfolios(HttpExchange exchange, String method, List<String> segments, Query query) {
        if (segments.size() == 1) {
            if (method.equals("GET")) {
                query.rejectUnknown(Set.of("offset", "limit"));
                return ok(portfolioService.list(
                        query.intValue("offset", 0, 0, Integer.MAX_VALUE),
                        query.intValue("limit", 100, 1, PortfolioQueryService.MAX_PAGE_SIZE)));
            }
            if (method.equals("POST")) {
                query.rejectUnknown(Set.of());
                Object created = portfolioService.create(
                        readRequiredJson(exchange, MorpheusPortfolioApiService.CreatePortfolioRequest.class));
                return new RouteResponse(201, created);
            }
            throw ApiFailure.methodNotAllowed("portfolios supports GET and POST");
        }

        String portfolioId = segments.get(1);
        if (segments.size() == 2) {
            requireMethod(method, "GET");
            query.rejectUnknown(Set.of());
            return ok(portfolioService.overview(portfolioId));
        }

        String resource = segments.get(2);
        if (resource.equals("members")) {
            requireExactSegments(segments, 3);
            requireMethod(method, "GET");
            query.rejectUnknown(Set.of("offset", "limit"));
            return ok(portfolioService.members(
                    portfolioId,
                    query.intValue("offset", 0, 0, Integer.MAX_VALUE),
                    query.intValue("limit", 100, 1, PortfolioQueryService.MAX_PAGE_SIZE)));
        }
        if (resource.equals("projects")) {
            if (segments.size() == 3) {
                requireMethod(method, "POST");
                query.rejectUnknown(Set.of());
                return new RouteResponse(201, portfolioService.registerProject(
                        portfolioId,
                        readRequiredJson(exchange, MorpheusPortfolioApiService.RegisterProjectRequest.class)));
            }
            if (segments.size() == 5 && segments.get(4).equals("missing")) {
                requireMethod(method, "POST");
                query.rejectUnknown(Set.of());
                return ok(portfolioService.markMissing(portfolioId, segments.get(3)));
            }
            if (segments.size() == 5 && segments.get(4).equals("freshness")) {
                requireMethod(method, "POST");
                query.rejectUnknown(Set.of());
                return ok(portfolioService.observeFreshness(
                        portfolioId,
                        segments.get(3),
                        readRequiredJson(exchange, MorpheusPortfolioApiService.FreshnessRequest.class)));
            }
            throw ApiFailure.notFound("unknown portfolio projects route");
        }
        if (resource.equals("references")) {
            requireExactSegments(segments, 3);
            if (method.equals("GET")) {
                query.rejectUnknown(Set.of("projectId", "offset", "limit"));
                return ok(portfolioService.references(
                        portfolioId,
                        query.string("projectId").map(String::trim).filter(value -> !value.isEmpty()),
                        query.intValue("offset", 0, 0, Integer.MAX_VALUE),
                        query.intValue("limit", 100, 1, PortfolioQueryService.MAX_PAGE_SIZE)));
            }
            if (method.equals("POST")) {
                query.rejectUnknown(Set.of());
                return new RouteResponse(201, portfolioService.addReference(
                        portfolioId,
                        readRequiredJson(exchange, MorpheusPortfolioApiService.CrossProjectReferenceRequest.class)));
            }
            throw ApiFailure.methodNotAllowed("portfolio references supports GET and POST");
        }
        if (resource.equals("conflicts")) {
            requireExactSegments(segments, 3);
            requireMethod(method, "GET");
            query.rejectUnknown(Set.of());
            return ok(portfolioService.conflicts(portfolioId));
        }
        if (resource.equals("traverse")) {
            requireExactSegments(segments, 3);
            requireMethod(method, "POST");
            query.rejectUnknown(Set.of());
            return ok(portfolioService.traverse(
                    portfolioId,
                    readRequiredJson(exchange, MorpheusPortfolioApiService.TraversalRequest.class)));
        }
        throw ApiFailure.notFound("unknown portfolio API resource: " + resource);
    }

    private RouteResponse routeSync(HttpExchange exchange, String method, List<String> segments, Query query, String projectId) {
        requireExactSegments(segments, 3);
        requireMethod(method, "POST");
        query.rejectUnknown(Set.of());
        SyncRequest request = readOptionalJson(exchange, SyncRequest.class, new SyncRequest(null));
        return ok(service.sync(projectId, Optional.ofNullable(request.revision())));
    }

    private RouteResponse routeSyncStatus(String method, List<String> segments, Query query, String projectId) {
        requireExactSegments(segments, 3);
        requireMethod(method, "GET");
        query.rejectUnknown(Set.of("maxAgeMinutes"));
        long maxAge = query.longValue(
                "maxAgeMinutes", MorpheusApiService.DEFAULT_MAX_AGE_MINUTES, 1, MorpheusApiService.MAX_MAX_AGE_MINUTES);
        return ok(service.syncStatus(projectId, maxAge));
    }

    private RouteResponse routeComposition(String method, List<String> segments, Query query, String projectId) {
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

    private RouteResponse routeSpecifications(String method, List<String> segments, Query query, String projectId) {
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

    private RouteResponse routeRequirements(HttpExchange exchange, String method, List<String> segments, Query query, String projectId) {
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

    private RouteResponse routeChanges(HttpExchange exchange, String method, List<String> segments, Query query, String projectId) {
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

    private RouteResponse routeVersions(String method, List<String> segments, Query query, String projectId) {
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

    private RouteResponse routeDiagnostics(String method, List<String> segments, Query query, String projectId) {
        requireExactSegments(segments, 3);
        requireMethod(method, "GET");
        query.rejectUnknown(Set.of());
        return ok(service.diagnostics(projectId));
    }

    private RouteResponse routeExternalReferences(String method, List<String> segments, Query query, String projectId) {
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

    private PageRequest page(Query query) {
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
        byte[] bytes = serializer.toUtf8(body);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", "application/json; charset=utf-8");
        headers.set("Cache-Control", "no-store");
        headers.set("X-Content-Type-Options", "nosniff");
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
    }

    private ApiSuccess success(Object data) {
        return new ApiSuccess("v1", Objects.requireNonNull(data, "data"));
    }

    private ApiErrorEnvelope error(String code, String message, Map<String, Object> details) {
        return new ApiErrorEnvelope("v1", new ApiError(code, message, details));
    }

    private RouteResponse ok(Object data) {
        return new RouteResponse(200, data);
    }

    private void requireMethod(String actual, String expected) {
        if (!actual.equals(expected)) throw ApiFailure.methodNotAllowed("expected HTTP " + expected + " but received " + actual);
    }

    private void requireExactSegments(List<String> segments, int expected) {
        if (segments.size() != expected) throw ApiFailure.notFound("unknown API route");
    }

    private List<String> pathSegments(String path) {
        if (!path.startsWith(API_PREFIX)) throw ApiFailure.notFound("unknown API route: " + path);
        String suffix = path.substring(API_PREFIX.length());
        if (suffix.isEmpty() || suffix.equals("/")) return List.of();
        String normalized = suffix.startsWith("/") ? suffix.substring(1) : suffix;
        if (normalized.endsWith("/")) normalized = normalized.substring(0, normalized.length() - 1);
        if (normalized.isEmpty()) return List.of();
        List<String> result = new ArrayList<>();
        for (String segment : normalized.split("/")) {
            if (segment.isEmpty()) throw ApiFailure.notFound("invalid API path");
            result.add(urlDecode(segment));
        }
        return List.copyOf(result);
    }

    private String allowedMethods(String path) {
        List<String> segments;
        try {
            segments = pathSegments(path);
        } catch (RuntimeException ignored) {
            return "GET";
        }
        if (segments.isEmpty()) return "GET";
        if (segments.size() == 2 && segments.getFirst().equals("provider-plugins")) {
            return switch (segments.get(1)) {
                case "discover" -> "GET";
                case "probe" -> "POST";
                default -> "GET";
            };
        }
        if (segments.size() == 1 && (segments.getFirst().equals("projects") || segments.getFirst().equals("portfolios"))) {
            return "GET, POST";
        }
        if (segments.getFirst().equals("portfolios")) {
            if (segments.size() == 3 && (segments.get(2).equals("projects") || segments.get(2).equals("references"))) {
                return "GET, POST";
            }
            if (segments.size() == 3 && segments.get(2).equals("traverse")) return "POST";
            if (segments.size() == 5 && segments.get(2).equals("projects")
                    && (segments.get(4).equals("missing") || segments.get(4).equals("freshness"))) return "POST";
        }
        if (segments.size() == 3 && segments.getFirst().equals("projects") && segments.get(2).equals("sync")) return "POST";
        if (segments.size() == 5 && segments.getFirst().equals("projects")
                && (segments.get(2).equals("requirements") || segments.get(2).equals("changes"))
                && (segments.get(4).equals("augmented-context")
                    || segments.get(4).equals("transition-check")
                    || segments.get(4).equals("lifecycle-transitions"))) return "POST";
        return "GET";
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

    private static String urlDecode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
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

    private record RouteResponse(int status, Object data) {
        private RouteResponse {
            if (status < 200 || status > 599) throw new IllegalArgumentException("route status must be between 200 and 599");
            Objects.requireNonNull(data, "data");
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

    private record Query(Map<String, String> values) {
        private Query {
            values = Map.copyOf(values);
        }

        static Query parse(String rawQuery) {
            if (rawQuery == null || rawQuery.isBlank()) return new Query(Map.of());
            Map<String, String> values = new LinkedHashMap<>();
            for (String part : rawQuery.split("&")) {
                if (part.isBlank()) continue;
                int separator = part.indexOf('=');
                String key = urlDecode(separator < 0 ? part : part.substring(0, separator));
                String value = urlDecode(separator < 0 ? "" : part.substring(separator + 1));
                if (key.isBlank()) throw ApiFailure.badRequest("query parameter name must not be blank");
                if (values.putIfAbsent(key, value) != null) throw ApiFailure.badRequest("duplicate query parameter: " + key);
            }
            return new Query(values);
        }

        Optional<String> string(String name) {
            return Optional.ofNullable(values.get(name));
        }

        String required(String name) {
            String value = values.get(name);
            if (value == null || value.isBlank()) throw ApiFailure.badRequest("query parameter is required: " + name);
            return value;
        }

        int intValue(String name, int defaultValue, int minimum, int maximum) {
            String raw = values.get(name);
            if (raw == null) return defaultValue;
            try {
                int value = Integer.parseInt(raw);
                if (value < minimum || value > maximum) throw ApiFailure.badRequest(name + " must be between " + minimum + " and " + maximum);
                return value;
            } catch (NumberFormatException failure) {
                throw ApiFailure.badRequest(name + " must be an integer");
            }
        }

        long longValue(String name, long defaultValue, long minimum, long maximum) {
            String raw = values.get(name);
            if (raw == null) return defaultValue;
            try {
                long value = Long.parseLong(raw);
                if (value < minimum || value > maximum) throw ApiFailure.badRequest(name + " must be between " + minimum + " and " + maximum);
                return value;
            } catch (NumberFormatException failure) {
                throw ApiFailure.badRequest(name + " must be an integer");
            }
        }

        void rejectUnknown(Set<String> allowed) {
            for (String key : values.keySet()) {
                if (!allowed.contains(key)) throw ApiFailure.badRequest("unknown query parameter: " + key);
            }
        }
    }
}
