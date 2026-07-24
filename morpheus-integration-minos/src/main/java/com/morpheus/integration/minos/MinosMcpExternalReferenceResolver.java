package com.morpheus.integration.minos;

import com.morpheus.application.reference.ExternalReferenceResolver;
import com.morpheus.application.reference.ExternalReferenceResolverResult;
import com.morpheus.domain.reference.ExternalReferenceTarget;
import com.morpheus.domain.reference.ResolvedExternalTarget;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Production M12 resolver for exact MINOS SYMBOL external references. */
public final class MinosMcpExternalReferenceResolver implements ExternalReferenceResolver {
    public static final String SYSTEM = "MINOS";
    public static final String RESOURCE_TYPE_SYMBOL = "SYMBOL";
    private static final int MAX_SYMBOL_RESULTS = 1000;

    private final MinosCodeGatewayFactory gatewayFactory;

    public MinosMcpExternalReferenceResolver(MinosCodeGatewayFactory gatewayFactory) {
        this.gatewayFactory = Objects.requireNonNull(gatewayFactory, "gatewayFactory");
    }

    @Override
    public String system() {
        return SYSTEM;
    }

    @Override
    public ExternalReferenceResolverResult resolve(ExternalReferenceTarget target) {
        Objects.requireNonNull(target, "target");
        if (!SYSTEM.equals(target.system()) || !RESOURCE_TYPE_SYMBOL.equals(target.resourceType())) {
            return ExternalReferenceResolverResult.unsupported();
        }
        String project = target.project().orElse(null);
        if (project == null || project.isBlank()) {
            return ExternalReferenceResolverResult.unsupported();
        }

        try (MinosCodeGateway gateway = gatewayFactory.open()) {
            MinosCodeGateway.IndexStatus status = gateway.indexStatus(project);
            String activeSnapshotId = status.activeSnapshotId();
            if (activeSnapshotId == null || activeSnapshotId.isBlank()) {
                return ExternalReferenceResolverResult.unavailable();
            }
            if (target.revision().isPresent()
                    && !target.revision().orElseThrow().equals(activeSnapshotId)) {
                return ExternalReferenceResolverResult.revisionMismatch();
            }

            List<MinosCodeGateway.Symbol> exact = gateway
                    .findSymbols(project, target.externalId(), MAX_SYMBOL_RESULTS).stream()
                    .filter(symbol -> target.externalId().equals(symbol.symbolKey()))
                    .toList();
            if (exact.isEmpty()) {
                return ExternalReferenceResolverResult.notFound();
            }
            if (exact.size() > 1) {
                return ExternalReferenceResolverResult.ambiguous();
            }
            MinosCodeGateway.Symbol symbol = exact.getFirst();

            // Preserve the persisted coordinate exactly. Canonical MINOS identities/revision belong to
            // resolved attributes; changing the ExternalReferenceTarget would rewrite historical intent.
            return ExternalReferenceResolverResult.found(new ResolvedExternalTarget(
                    target,
                    attributes(status, symbol, activeSnapshotId)));
        } catch (MinosIntegrationException failure) {
            return ExternalReferenceResolverResult.unavailable();
        } catch (RuntimeException failure) {
            return ExternalReferenceResolverResult.unavailable();
        }
    }

    private Map<String, String> attributes(
            MinosCodeGateway.IndexStatus status,
            MinosCodeGateway.Symbol symbol,
            String activeSnapshotId) {
        Map<String, String> attributes = new LinkedHashMap<>();
        put(attributes, "minos.projectId", status.projectId());
        put(attributes, "minos.activeSnapshotId", activeSnapshotId);
        put(attributes, "minos.symbolId", symbol.id());
        put(attributes, "minos.symbolKey", symbol.symbolKey());
        put(attributes, "minos.qualifiedName", symbol.qualifiedName());
        put(attributes, "minos.kind", symbol.kind());
        put(attributes, "minos.language", symbol.language());
        put(attributes, "minos.moduleId", symbol.moduleId());
        put(attributes, "minos.fileId", symbol.fileId());
        put(attributes, "minos.resolutionStatus", symbol.resolutionStatus());
        put(attributes, "minos.providerId", symbol.origin().providerId());
        put(attributes, "minos.providerVersion", symbol.origin().providerVersion());
        put(attributes, "minos.indexRunId", symbol.origin().indexRunId());
        return Map.copyOf(attributes);
    }

    private void put(Map<String, String> attributes, String key, String value) {
        if (value != null && !value.isBlank()) {
            attributes.put(key, value);
        }
    }
}
