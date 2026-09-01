package com.morpheus.api;

import com.morpheus.application.context.DisabledTechnicalContextProvider;
import com.morpheus.application.context.TechnicalContextProvider;
import com.morpheus.application.lifecycle.mutation.ChangeWriteCapabilityObservation;
import com.morpheus.application.lifecycle.mutation.ChangeWriteCapabilityResolver;
import com.morpheus.application.reference.ExternalIntegrationStatus;
import com.morpheus.application.reference.ExternalIntegrationStatusProvider;
import com.morpheus.application.reference.ExternalReferenceResolverRegistry;
import com.morpheus.application.snapshot.RuntimeSnapshotRecovery;
import com.morpheus.store.sqlite.SqliteSpecificationKnowledgeStore;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Owns local HTTP runtime bootstrap while {@link MorpheusHttpServer} keeps request dispatch/translation. */
final class MorpheusLocalHttpServerBootstrap {

    private MorpheusLocalHttpServerBootstrap() {
    }

    static MorpheusHttpServer start(Path databasePath, String host, int port) {
        ExternalReferenceResolverRegistry resolvers = new ExternalReferenceResolverRegistry(List.of());
        ExternalIntegrationStatusProvider disabledMinos = () -> new ExternalIntegrationStatus(
                "MINOS", "DISABLED", false, "MINOS integration is not configured", Map.of());
        return start(
                databasePath,
                host,
                port,
                new HttpServerIntegrations(resolvers, disabledMinos, disabledNexus(), deniedWrites()),
                new RemoteAccessControls(Optional.empty(), Optional.empty()));
    }

    static MorpheusHttpServer start(
            Path databasePath,
            String host,
            int port,
            ExternalReferenceResolverRegistry resolverRegistry,
            ExternalIntegrationStatusProvider minosStatus) {
        return start(
                databasePath,
                host,
                port,
                new HttpServerIntegrations(resolverRegistry, minosStatus, disabledNexus(), deniedWrites()),
                new RemoteAccessControls(Optional.empty(), Optional.empty()));
    }

    static MorpheusHttpServer start(
            Path databasePath,
            String host,
            int port,
            ExternalReferenceResolverRegistry resolverRegistry,
            ExternalIntegrationStatusProvider minosStatus,
            TechnicalContextProvider technicalContextProvider) {
        return start(
                databasePath,
                host,
                port,
                new HttpServerIntegrations(resolverRegistry, minosStatus, technicalContextProvider, deniedWrites()),
                new RemoteAccessControls(Optional.empty(), Optional.empty()));
    }

    static MorpheusHttpServer start(
            Path databasePath,
            String host,
            int port,
            ExternalReferenceResolverRegistry resolverRegistry,
            ExternalIntegrationStatusProvider minosStatus,
            TechnicalContextProvider technicalContextProvider,
            ChangeWriteCapabilityResolver writeCapabilityResolver) {
        return start(
                databasePath,
                host,
                port,
                new HttpServerIntegrations(resolverRegistry, minosStatus, technicalContextProvider, writeCapabilityResolver),
                new RemoteAccessControls(Optional.empty(), Optional.empty()));
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
        return start(
                databasePath,
                host,
                port,
                new HttpServerIntegrations(resolverRegistry, minosStatus, technicalContextProvider, writeCapabilityResolver),
                new RemoteAccessControls(
                        Optional.of(Objects.requireNonNull(allowedWorkspaceRoots, "allowedWorkspaceRoots")),
                        Optional.of(Objects.requireNonNull(internalCapability, "internalCapability"))));
    }

    /** Groups the read/write/telemetry integration collaborators the local HTTP server delegates to. */
    private record HttpServerIntegrations(
            ExternalReferenceResolverRegistry resolverRegistry,
            ExternalIntegrationStatusProvider minosStatus,
            TechnicalContextProvider technicalContextProvider,
            ChangeWriteCapabilityResolver writeCapabilityResolver) {
    }

    /** Groups the remote-only access-control extras that are absent for the plain local server. */
    private record RemoteAccessControls(
            Optional<AllowedWorkspaceRoots> allowedWorkspaceRoots,
            Optional<MorpheusInternalCapability> internalCapability) {
    }

    private static MorpheusHttpServer start(
            Path databasePath,
            String host,
            int port,
            HttpServerIntegrations integrations,
            RemoteAccessControls remoteAccessControls) {
        Objects.requireNonNull(databasePath, "databasePath");
        Objects.requireNonNull(integrations, "integrations");
        Objects.requireNonNull(remoteAccessControls, "remoteAccessControls");
        ExternalReferenceResolverRegistry resolverRegistry = integrations.resolverRegistry();
        ExternalIntegrationStatusProvider minosStatus = integrations.minosStatus();
        TechnicalContextProvider technicalContextProvider = integrations.technicalContextProvider();
        ChangeWriteCapabilityResolver writeCapabilityResolver = integrations.writeCapabilityResolver();
        Optional<AllowedWorkspaceRoots> allowedWorkspaceRoots = remoteAccessControls.allowedWorkspaceRoots();
        Optional<MorpheusInternalCapability> internalCapability = remoteAccessControls.internalCapability();
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
            HttpServer loopbackProtected = new LoopbackRequestProtectedHttpServer(delegate);
            HttpServer httpServer = internalCapability
                    .<HttpServer>map(capability -> new CapabilityProtectedHttpServer(loopbackProtected, capability))
                    .orElse(loopbackProtected);
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
            httpServer.createContext(MorpheusHttpServer.API_PREFIX, result::handle);
            MorpheusQueryHttpRoutes.register(httpServer, databasePath);
            httpServer.start();
            return result;
        } catch (IOException failure) {
            throw new IllegalStateException("cannot start MORPHEUS API on " + normalizedHost + ":" + port, failure);
        }
    }

    private static TechnicalContextProvider disabledNexus() {
        return new DisabledTechnicalContextProvider("NEXUS", "NEXUS integration is not configured");
    }

    private static ChangeWriteCapabilityResolver deniedWrites() {
        return projectId -> ChangeWriteCapabilityObservation.denied(
                "No WRITE_CHANGE provider capability resolver is configured for this HTTP server");
    }
}
