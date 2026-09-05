package com.morpheus.api;

import com.morpheus.application.reference.ExternalIntegrationStatusProvider;
import com.morpheus.application.reference.ExternalReferenceResolverRegistry;
import com.morpheus.application.reference.LiveExternalReferenceResolutionService;
import com.morpheus.domain.identity.DomainIdentity;
import com.morpheus.domain.project.ProjectSpecificationId;
import com.morpheus.domain.reference.ExternalReference;
import com.morpheus.domain.reference.ExternalReferenceId;

import java.nio.file.Path;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** M12 HTTP DTO mapper over the generic application reference-resolution use case. */
final class MorpheusExternalReferenceApiService {
    private final Path databasePath;
    private final ExternalReferenceResolverRegistry resolverRegistry;
    private final ExternalIntegrationStatusProvider minosStatus;

    MorpheusExternalReferenceApiService(
            Path databasePath,
            ExternalReferenceResolverRegistry resolverRegistry,
            ExternalIntegrationStatusProvider minosStatus) {
        this.databasePath = Objects.requireNonNull(databasePath, "databasePath").toAbsolutePath().normalize();
        this.resolverRegistry = Objects.requireNonNull(resolverRegistry, "resolverRegistry");
        this.minosStatus = Objects.requireNonNull(minosStatus, "minosStatus");
    }

    Object minosStatus() {
        return IntegrationStatusViews.status(minosStatus.status());
    }

    Object list(String projectIdValue, String ownerIdValue) {
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(projectIdValue);
        DomainIdentity ownerId = DomainIdentity.parse(ownerIdValue);
        try (ApiRuntime runtime = new ApiRuntime(databasePath)) {
            if (runtime.snapshots.findProject(projectId).isEmpty()) {
                throw ApiFailure.notFound("project not found: " + projectId);
            }
            List<ExternalReference> references = new LiveExternalReferenceResolutionService(
                    runtime.snapshots, runtime.externalReferences, resolverRegistry, Clock.systemUTC())
                    .listActive(projectId, ownerId)
                    .orElseThrow(() -> ApiFailure.conflict("project has no ACTIVE snapshot: " + projectId));
            return Map.of(
                    "projectId", projectId.toString(),
                    "ownerId", ownerId.toString(),
                    "items", references.stream().map(this::reference).toList());
        }
    }

    Object resolve(String projectIdValue, String referenceIdValue) {
        ProjectSpecificationId projectId = ProjectSpecificationId.parse(projectIdValue);
        ExternalReferenceId referenceId = ExternalReferenceId.parse(referenceIdValue);
        try (ApiRuntime runtime = new ApiRuntime(databasePath)) {
            if (runtime.snapshots.findProject(projectId).isEmpty()) {
                throw ApiFailure.notFound("project not found: " + projectId);
            }
            var service = new LiveExternalReferenceResolutionService(
                    runtime.snapshots, runtime.externalReferences, resolverRegistry, Clock.systemUTC());
            var result = service.resolveActive(projectId, referenceId)
                    .orElseThrow(() -> ApiFailure.conflict("project has no ACTIVE snapshot: " + projectId));
            Map<String, Object> view = new LinkedHashMap<>();
            view.put("snapshotId", result.snapshot().id().toString());
            view.put("stored", reference(result.storedReference()));
            view.put("observed", reference(result.observedReference()));
            view.put("persisted", false);
            return view;
        }
    }

    private Map<String, Object> reference(ExternalReference reference) {
        Map<String, Object> target = new LinkedHashMap<>();
        target.put("system", reference.target().system());
        target.put("project", reference.target().project().orElse(null));
        target.put("resourceType", reference.target().resourceType());
        target.put("externalId", reference.target().externalId());
        target.put("revision", reference.target().revision().orElse(null));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", reference.id().toString());
        result.put("ownerId", reference.ownerId().toString());
        result.put("target", target);
        result.put("resolutionState", reference.resolutionState().name());
        result.put("resolutionReason", reference.resolutionReason().name());
        result.put("resolvedTarget", reference.resolvedTarget().map(value -> {
            Map<String, Object> resolved = new LinkedHashMap<>();
            resolved.put("system", value.target().system());
            resolved.put("project", value.target().project().orElse(null));
            resolved.put("resourceType", value.target().resourceType());
            resolved.put("externalId", value.target().externalId());
            resolved.put("revision", value.target().revision().orElse(null));
            return Map.of("target", resolved, "attributes", value.attributes());
        }).orElse(null));
        result.put("historyCount", reference.history().size());
        return result;
    }
}
